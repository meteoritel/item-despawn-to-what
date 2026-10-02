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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/** 覆盖层逐 id 写入：保留未编辑 JSON，整批预备、备份、提交；失败时回滚已经写入的文件。 */
public final class RuleOverlayWriter {
    /** 写入结果；零文件或存在冲突均不能解释为保存成功。 */
    public record ApplyResult(int writtenFiles, List<String> conflicts) {
        public ApplyResult { conflicts = List.copyOf(conflicts); }
    }
    /** 文件原始形状与完整条目，非对象条目也必须原样保留。 */
    private static final class RuleFile {
        private final Path path;
        private final boolean array;
        private final byte[] original;
        private final List<JsonElement> entries = new ArrayList<>();
        private RuleFile(Path path, boolean array, byte[] original) {
            this.path = path; this.array = array; this.original = original;
        }
        private JsonElement content() {
            if (!array && entries.size() == 1) { return entries.getFirst(); }
            JsonArray result = new JsonArray();
            entries.forEach(result::add);
            return result;
        }
    }
    /** 规则所在文件及条目下标。 */
    private record Location(RuleFile file, int index) {}
    private final Path root;
    private final String namespace;
    public RuleOverlayWriter(Path overlayRoot, String defaultNamespace) {
        root = overlayRoot.toAbsolutePath().normalize().resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        namespace = defaultNamespace;
    }

    // 拒绝路径穿越、符号链接、重复编辑和文件占用；任何预备错误均不写入。
    public ApplyResult apply(RuleEditChangeSet changes, IssueCollector issues) {
        Map<Path, RuleFile> files = new LinkedHashMap<>();
        Set<Path> dirty = new LinkedHashSet<>();
        List<String> conflicts = new ArrayList<>();
        try {
            safePath(root);
            load(files);
            Set<ResourceLocation> seen = new HashSet<>();
            for (RuleEdit change : changes.edits()) {
                if (!seen.add(change.id())) { throw new IOException("重复编辑规则: " + change.id()); }
                Path target = target(change.id());
                Location location = locate(files, change.id());
                if (change.action() == RuleEdit.Action.DELETE) {
                    if (location != null) {
                        location.file.entries.remove(location.index);
                        dirty.add(location.file.path);
                    }
                    continue;
                }
                JsonObject body = Objects.requireNonNull(change.rule(), "upsert 缺少 rule").deepCopy();
                body.addProperty(RuleFields.ID, change.id().toString());
                if (location != null) {
                    location.file.entries.set(location.index, body);
                    dirty.add(location.file.path);
                } else {
                    // 包括损坏 JSON 在内的任何现存文件都不能被新规则覆盖。
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || files.containsKey(target)) {
                        throw new IOException("新增规则目标文件已被占用: " + target);
                    }
                    RuleFile file = new RuleFile(target, false, null);
                    file.entries.add(body);
                    files.put(target, file);
                    dirty.add(target);
                }
            }
            commit(dirty.stream().map(files::get).toList());
            return new ApplyResult(dirty.size(), List.of());
        } catch (IOException | RuntimeException failure) {
            org.apache.logging.log4j.LogManager.getLogger().error("覆盖层整批保存失败", failure);
            String message = "覆盖层整批保存失败: " + failure.getMessage();
            if (failure.getSuppressed().length > 0) { message += "；部分文件回滚失败，请用 .bak 恢复并检查日志"; }
            issues.error(message, root.toString(), null);
            conflicts.add(message);
            return new ApplyResult(0, conflicts);
        }
    }

    // 对任意 id 执行同样的目录边界校验，不允许 . 或 .. 路径段。
    private Path target(ResourceLocation id) throws IOException {
        for (String segment : id.getPath().split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("规则 id 含非法路径段: " + id);
            }
        }
        Path target = root.resolve(id.getNamespace()).resolve(id.getPath() + RulePaths.RULE_FILE_EXTENSION).normalize();
        if (!target.startsWith(root)) { throw new IOException("规则路径越过 rules 目录: " + id); }
        safePath(target);
        safePath(target.resolveSibling(target.getFileName() + ".bak"));
        return target;
    }

    // 不跟随任何现存祖先链接，避免通过 rules 或备份链接越界写文件。
    private static void safePath(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) { throw new IOException("不允许写入符号链接路径: " + cursor); }
        }
    }

    private void load(Map<Path, RuleFile> files) throws IOException {
        if (!Files.isDirectory(root)) { return; }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.toString().endsWith(RulePaths.RULE_FILE_EXTENSION)).sorted().toList()) {
                safePath(path);
                byte[] original = Files.readAllBytes(path);
                JsonElement json;
                try { json = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)); }
                catch (RuntimeException invalid) { continue; }
                if (!json.isJsonObject() && !json.isJsonArray()) { continue; }
                RuleFile file = new RuleFile(path, json.isJsonArray(), original);
                if (file.array) { json.getAsJsonArray().forEach(file.entries::add); }
                else { file.entries.add(json); }
                files.put(path, file);
            }
        }
    }

    private Location locate(Map<Path, RuleFile> files, ResourceLocation id) throws IOException {
        Location found = null;
        for (RuleFile file : files.values()) {
            for (int i = 0; i < file.entries.size(); i++) {
                JsonElement element = file.entries.get(i);
                if (!element.isJsonObject()) { continue; }
                JsonObject body = element.getAsJsonObject();
                ResourceLocation candidate = null;
                try {
                    if (body.has(RuleFields.ID)) { candidate = ResourceLocation.tryParse(body.get(RuleFields.ID).getAsString()); }
                    else if (file.entries.size() == 1) {
                        candidate = ResourceLocation.tryParse(namespace + ":" + RulePaths.stripExtension(root.relativize(file.path).toString().replace('\\', '/')));
                    }
                } catch (RuntimeException invalid) { /* 未编辑的坏条目原样保留。 */ }
                if (id.equals(candidate)) {
                    if (found != null) { throw new IOException("覆盖层同 id 多处定义，需先手动消歧: " + id); }
                    found = new Location(file, i);
                }
            }
        }
        return found;
    }

    // 所有临时文件与备份准备成功后才替换；普通 I/O 失败时恢复整批原内容。
    private static void commit(List<RuleFile> files) throws IOException {
        Map<RuleFile, Path> temporary = new LinkedHashMap<>();
        List<RuleFile> committed = new ArrayList<>();
        try {
            for (RuleFile file : files) {
                safePath(file.path);
                Files.createDirectories(file.path.getParent());
                Path temp = Files.createTempFile(file.path.getParent(), ".idtw-", ".tmp");
                temporary.put(file, temp);
                Files.writeString(temp, file.content().toString(), StandardCharsets.UTF_8);
                verifyUnchanged(file);
                if (file.original != null) {
                    Path backup = file.path.resolveSibling(file.path.getFileName() + ".bak");
                    safePath(backup);
                    Files.copy(file.path, backup, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            for (RuleFile file : files) {
                verifyUnchanged(file);
                move(temporary.get(file), file.path);
                committed.add(file);
            }
        } catch (IOException failure) {
            Collections.reverse(committed);
            for (RuleFile file : committed) {
                try {
                    if (file.original == null) { Files.deleteIfExists(file.path); }
                    else {
                        Path restore = Files.createTempFile(file.path.getParent(), ".idtw-restore-", ".tmp");
                        try { Files.write(restore, file.original); move(restore, file.path); }
                        finally { Files.deleteIfExists(restore); }
                    }
                } catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw failure;
        } finally {
            for (Path temp : temporary.values()) {
                try { Files.deleteIfExists(temp); }
                catch (IOException cleanupFailure) {
                    // 清理暂存文件失败不改变已经完成的提交结果。
                    org.apache.logging.log4j.LogManager.getLogger().warn("暂存文件清理失败: {}", temp, cleanupFailure);
                }
            }
        }
    }

    private static void verifyUnchanged(RuleFile file) throws IOException {
        boolean exists = Files.exists(file.path, LinkOption.NOFOLLOW_LINKS);
        if (file.original == null ? exists : !exists || !Arrays.equals(file.original, Files.readAllBytes(file.path))) {
            throw new IOException("准备保存时文件已被外部修改: " + file.path);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}