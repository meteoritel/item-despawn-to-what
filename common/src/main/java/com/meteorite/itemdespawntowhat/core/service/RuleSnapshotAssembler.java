package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 规则快照装配器：把"当前生效规则全集"组装成下发给客户端的快照。
 * 条目形状（冻结契约）：{ "rule": {...}, "origin": "...", "editable": true|false }
 * - 覆盖层规则使用**文件原始 JSON**（不做模型往返，避免丢失未识别字段），editable=true；
 * - 其它来源（内置/世界数据包）的规则由模型编码得到，editable=false（GUI 只读）。
 */
public final class RuleSnapshotAssembler {

    public static final String RULE_KEY = "rule";
    public static final String ORIGIN_KEY = "origin";
    public static final String EDITABLE_KEY = "editable";

    private RuleSnapshotAssembler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装快照；merged 为加载合并结果，overlayRoot 为 config 覆盖层根目录
    public static RuleSnapshot assemble(List<LoadedRule<Rule>> merged,
                                        Path overlayRoot,
                                        String overlayNamespace,
                                        int version,
                                        TypeRegistry<EffectType<?>> effectTypes,
                                        TypeRegistry<ConditionType<?>> conditionTypes,
                                        List<String> issues) {
        Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();

        // 1) 覆盖层：文件原始 JSON 优先，保持可编辑
        for (RawOverlayRule raw : readOverlayRules(overlayRoot, overlayNamespace)) {
            entries.put(raw.id(), entry(raw.rule(), raw.origin(), true));
        }

        // 2) 其它来源：由模型编码，标记为只读
        Codec<Rule> codec = RuleCodecs.codec(effectTypes, conditionTypes);
        for (LoadedRule<Rule> loaded : merged) {
            if (loaded.origin().layer() == RuleSourceLayer.OVERLAY || entries.containsKey(loaded.id())) {
                continue;
            }
            DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, loaded.value());
            JsonElement json = encoded.result().orElse(null);
            if (json == null || !json.isJsonObject()) {
                continue;
            }
            entries.put(loaded.id(), entry(json.getAsJsonObject(), loaded.origin().display(), false));
        }

        return new RuleSnapshot(version, List.copyOf(entries.values()), issues == null ? List.of() : List.copyOf(issues));
    }

    // 覆盖层原始规则：id 优先取字段，缺省由文件相对路径推导（仅单条文件允许）
    private static List<RawOverlayRule> readOverlayRules(Path overlayRoot, String overlayNamespace) {
        List<RawOverlayRule> result = new ArrayList<>();
        if (overlayRoot == null) {
            return result;
        }
        Path rulesDir = overlayRoot.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        if (!Files.isDirectory(rulesDir)) {
            return result;
        }
        try (Stream<Path> walk = Files.walk(rulesDir)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(RulePaths.RULE_FILE_EXTENSION))
                    .sorted()
                    .toList();
            for (Path file : files) {
                JsonElement element;
                try {
                    element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException e) {
                    continue;
                }
                String relative = rulesDir.relativize(file).toString().replace('\\', '/');
                String origin = RuleSourceLayer.OVERLAY.displayName() + ":" + relative;
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    // 与 RuleFileParser 的 canDerive 对齐：数组恰好 1 个元素时同样允许由文件路径推导 id
                    String derivablePath = array.size() == 1 ? relative : null;
                    for (int index = 0; index < array.size(); index++) {
                        JsonObject rule = asObject(array.get(index));
                        if (rule == null || isControlEntry(rule)) {
                            continue;
                        }
                        ResourceLocation id = resolveId(rule, derivablePath, overlayNamespace);
                        if (id != null) {
                            result.add(new RawOverlayRule(id, withId(rule, id), origin + "#" + index));
                        }
                    }
                } else {
                    JsonObject rule = asObject(element);
                    if (rule == null || isControlEntry(rule)) {
                        continue;
                    }
                    ResourceLocation id = resolveId(rule, relative, overlayNamespace);
                    if (id != null) {
                        result.add(new RawOverlayRule(id, withId(rule, id), origin));
                    }
                }
            }
        } catch (IOException ignored) {
            // 目录不可读时返回空列表，由加载层的问题收集器负责报错
        }
        return result;
    }

    // id 解析：字段优先，其次由相对路径推导（仅单条文件调用时会传入 relative）
    private static ResourceLocation resolveId(JsonObject rule, String relativePath, String overlayNamespace) {
        if (rule != null && rule.has("id") && rule.get("id").isJsonPrimitive()) {
            return ResourceLocation.tryParse(rule.get("id").getAsString());
        }
        if (relativePath == null) {
            return null;
        }
        return ResourceLocation.tryParse(overlayNamespace + ":" + RulePaths.stripExtension(relativePath));
    }

    // 组装单个条目
    private static JsonObject entry(JsonObject rule, String origin, boolean editable) {
        JsonObject entry = new JsonObject();
        entry.add(RULE_KEY, rule.deepCopy());
        entry.addProperty(ORIGIN_KEY, origin);
        entry.addProperty(EDITABLE_KEY, editable);
        return entry;
    }

    // 把最终 id 注入下发的规则 JSON：省略 id 的单条文件其 id 由路径推导，
    // 客户端必须拿到与服务端权威一致的 id，否则保存会落到错误目标
    private static JsonObject withId(JsonObject rule, ResourceLocation id) {
        JsonObject copy = rule.deepCopy();
        copy.addProperty("id", id.toString());
        return copy;
    }

    // 覆盖层控制条目（disabled/delete 为 true）不是规则，不下发给编辑器；
    // 必须判取值：`"disabled": false` 是普通规则，与加载层 readFlag 的语义保持一致
    private static boolean isControlEntry(JsonObject rule) {
        return isTrueFlag(rule, com.meteorite.itemdespawntowhat.core.api.RuleFields.DISABLED)
                || isTrueFlag(rule, com.meteorite.itemdespawntowhat.core.api.RuleFields.DELETE);
    }

    // 字段存在且为布尔 true 时才算控制标志
    private static boolean isTrueFlag(JsonObject rule, String key) {
        if (!rule.has(key)) {
            return false;
        }
        JsonElement value = rule.get(key);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    // 元素转对象；非对象返回 null
    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    // 覆盖层原始规则
    private record RawOverlayRule(ResourceLocation id, JsonObject rule, String origin) {
    }
}
