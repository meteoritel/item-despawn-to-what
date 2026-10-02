package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 覆盖层权威落盘器（服务端权威保存协议的写侧）。
 * 关键语义（A1 闭环）：写回依据是**磁盘上的文件内容**，逐 id 应用 upsert/delete，
 * 不基于运行时快照整文件覆盖，因此 disabled / 编译失败 / 未命中标签的规则不会被保存动作删除。
 * 文件形状保持不变：原文件是单条对象就写单条，是数组就写数组；新增规则落到 rules/<ns>/<path>.json。
 */
public final class RuleOverlayWriter {

    // 一次应用的结果：写入文件数 + 被跳过的冲突说明
    public record ApplyResult(int writtenFiles, List<String> conflicts) {
        public ApplyResult {
            conflicts = List.copyOf(conflicts);
        }
    }

    // 磁盘上的一个规则文件及其规则列表
    private static final class RuleFile {
        private final Path path;
        private final boolean wasArray;
        private final List<JsonObject> rules = new ArrayList<>();

        RuleFile(Path path, boolean wasArray) {
            this.path = path;
            this.wasArray = wasArray;
        }
    }

    // 规则 id → 所在文件与下标
    private record RuleLocation(Path file, int index) {
    }

    private final Path overlayRoot;
    private final String defaultNamespace;

    public RuleOverlayWriter(Path overlayRoot, String defaultNamespace) {
        this.overlayRoot = overlayRoot;
        this.defaultNamespace = defaultNamespace;
    }

    // 应用变更集；单条编辑失败只记录问题并跳过，不影响其余编辑
    public ApplyResult apply(RuleEditChangeSet changeSet, IssueCollector issues) {
        Path rulesDir = overlayRoot.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        Map<Path, RuleFile> files = new LinkedHashMap<>();
        Map<ResourceLocation, RuleLocation> index = new HashMap<>();
        try {
            loadAll(rulesDir, files, index, issues);
        } catch (IOException e) {
            issues.error("读取覆盖层目录失败: " + e.getMessage(), rulesDir.toString(), null);
            return new ApplyResult(0, List.of());
        }

        List<String> conflicts = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        Set<Path> dirty = new HashSet<>();

        for (RuleEdit edit : changeSet.edits()) {
            ResourceLocation id = edit.id();
            if (!seen.add(id)) {
                conflicts.add("同一变更集中重复出现规则 " + id + "，已跳过后者");
                continue;
            }
            RuleLocation location = index.get(id);
            if (edit.action() == RuleEdit.Action.DELETE) {
                if (location == null) {
                    // 删除不存在的规则是幂等操作
                    continue;
                }
                RuleFile file = files.get(location.file());
                if (file != null && location.index() < file.rules.size()) {
                    file.rules.remove(location.index());
                    dirty.add(file.path);
                    reindex(rulesDir, file, index);
                }
                continue;
            }
            // UPSERT
            JsonObject rule = edit.rule() == null ? new JsonObject() : edit.rule().deepCopy();
            rule.addProperty(RuleFields.ID, id.toString());
            if (location != null) {
                RuleFile file = files.get(location.file());
                if (file != null && location.index() < file.rules.size()) {
                    file.rules.set(location.index(), rule);
                    dirty.add(file.path);
                }
                continue;
            }
            // 新规则：落到 rules/<ns>/<path>.json
            Path target = rulesDir.resolve(id.getNamespace()).resolve(id.getPath() + RulePaths.RULE_FILE_EXTENSION);
            RuleFile file = files.computeIfAbsent(target, key -> new RuleFile(key, false));
            file.rules.add(rule);
            dirty.add(target);
            index.put(id, new RuleLocation(target, file.rules.size() - 1));
        }

        int written = 0;
        for (Path path : dirty) {
            RuleFile file = files.get(path);
            if (file == null) {
                continue;
            }
            try {
                if (file.rules.isEmpty() && !file.wasArray) {
                    // 单条文件的唯一条目被删除：写成空数组而不是删文件，避免旧链路/人工编辑误判
                    writeFile(file, new JsonArray(), issues);
                } else {
                    writeFile(file, toContent(file), issues);
                }
                written++;
            } catch (IOException e) {
                issues.error("写入规则文件失败: " + e.getMessage(), path.toString(), null);
            }
        }
        return new ApplyResult(written, conflicts);
    }

    // 读取覆盖层全部规则文件，建立 id → 位置索引
    private void loadAll(Path rulesDir, Map<Path, RuleFile> files,
                         Map<ResourceLocation, RuleLocation> index, IssueCollector issues) throws IOException {
        if (!Files.isDirectory(rulesDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(rulesDir)) {
            List<Path> candidates = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(RulePaths.RULE_FILE_EXTENSION))
                    .sorted()
                    .toList();
            for (Path path : candidates) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                JsonElement element;
                try {
                    element = JsonParser.parseString(text);
                } catch (RuntimeException e) {
                    // 坏文件只跳过、不在此处报错：加载链路（OverlayRuleReader）已负责报告，
                    // 否则每次保存的回执都会重复携带磁盘上早已存在的问题
                    continue;
                }
                if (element.isJsonArray()) {
                    RuleFile file = new RuleFile(path, true);
                    for (JsonElement entry : element.getAsJsonArray()) {
                        if (entry.isJsonObject()) {
                            file.rules.add(entry.getAsJsonObject());
                        }
                    }
                    files.put(path, file);
                } else if (element.isJsonObject()) {
                    RuleFile file = new RuleFile(path, false);
                    file.rules.add(element.getAsJsonObject());
                    files.put(path, file);
                } else {
                    // 同上：非对象/数组的文件不参与编辑，也不在写入路径重复报错
                    continue;
                }
                reindex(rulesDir, files.get(path), index);
            }
        }
    }

    // 重建某个文件内规则的 id 索引
    private void reindex(Path rulesDir, RuleFile file, Map<ResourceLocation, RuleLocation> index) {
        for (int i = 0; i < file.rules.size(); i++) {
            ResourceLocation id = resolveId(rulesDir, file, file.rules.get(i));
            if (id != null) {
                index.put(id, new RuleLocation(file.path, i));
            }
        }
    }

    // 取规则 id：优先字段，缺省由文件相对路径推导（仅单条文件允许推导）
    private @Nullable ResourceLocation resolveId(Path rulesDir, RuleFile file, JsonObject rule) {
        if (rule.has(RuleFields.ID) && rule.get(RuleFields.ID).isJsonPrimitive()) {
            return ResourceLocation.tryParse(rule.get(RuleFields.ID).getAsString());
        }
        if (file.rules.size() != 1) {
            return null;
        }
        String relative = rulesDir.relativize(file.path).toString().replace('\\', '/');
        return ResourceLocation.tryParse(defaultNamespace + ":" + RulePaths.stripExtension(relative));
    }

    // 决定写回的形状：原数组或规则数不为 1 时写数组，否则写单条对象
    private JsonElement toContent(RuleFile file) {
        if (file.wasArray || file.rules.size() != 1) {
            JsonArray array = new JsonArray();
            for (JsonObject rule : file.rules) {
                array.add(rule);
            }
            return array;
        }
        return file.rules.get(0);
    }

    // 原子写入：先备份原文件（同名 .bak），再临时文件 + 移动
    private void writeFile(RuleFile file, JsonElement content, IssueCollector issues) throws IOException {
        Path parent = file.path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(file.path)) {
            Files.copy(file.path, backupPath(file.path), StandardCopyOption.REPLACE_EXISTING);
        }
        Path temp = Files.createTempFile(parent, file.path.getFileName().toString(), ".tmp");
        Files.writeString(temp, content.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file.path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 备份路径：同目录同名加 .bak（不会被规则读取器扫描）
    private static Path backupPath(Path file) {
        return file.resolveSibling(file.getFileName().toString() + ".bak");
    }
}
