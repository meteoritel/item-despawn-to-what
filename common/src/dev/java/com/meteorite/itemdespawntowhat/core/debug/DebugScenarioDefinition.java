package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RuleOrigin;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 所有场景通过同一资源声明、参数实例化、正式 Codec 与规则校验构建独立索引。 */
final class DebugScenarioDefinition {
    final DebugScenarioSpec spec;
    final RuleIndex index;
    final JsonArray parameters;
    final JsonObject expected;
    final int sources;
    final int stackSize;
    final Item sourceItem;
    final Item catalystItem;
    final long expectedConversions;
    final long expectedOutput;

    // 一轮实例保留已解析的资源与预期，日志和校验读取同一份数据。
    private DebugScenarioDefinition(DebugScenarioSpec spec, RuleIndex index, JsonArray parameters,
                                    JsonObject expected, int sources) {
        this.spec = spec;
        this.index = index;
        this.parameters = parameters;
        this.expected = expected;
        this.sources = sources;
        stackSize = spec.stackSize();
        sourceItem = BuiltInRegistries.ITEM.get(spec.sourceItem());
        catalystItem = BuiltInRegistries.ITEM.get(spec.catalystItem());
        expectedConversions = expected.getAsJsonObject("metrics").get("conversions").getAsLong();
        expectedOutput = expected.getAsJsonObject("outputs").entrySet().stream().mapToLong(entry -> entry.getValue().getAsLong()).sum();
    }

    // 功能与性能仅在输入规模、声明动作及观测详细度上不同，规则解码路径完全共用。
    static DebugScenarioDefinition create(MinecraftServer server, BuiltinTypeRegistries types,
                                          String name, boolean benchmark, int count, int y) {
        DebugScenarioSpec spec = DebugScenarioCatalog.find(name, benchmark);
        int sources = benchmark ? count : spec.sources();
        int maximum = benchmark ? DebugScenarioSpec.MAX_BENCHMARK_SOURCES : DebugScenarioSpec.MAX_FUNCTIONAL_SOURCES;
        if (sources < 0 || sources > maximum) { throw new IllegalArgumentException("场景源数量越界，最大允许：" + maximum); }
        if (!BuiltInRegistries.ITEM.containsKey(spec.sourceItem()) || !BuiltInRegistries.ITEM.containsKey(spec.catalystItem())) {
            throw new IllegalArgumentException("场景物品未注册：" + spec.key());
        }
        Map<String, Long> variables = Map.of("$sources", (long) sources, "$source_items", (long) sources * spec.stackSize(),
                "$origin_y_plus_3", (long) y + 3);
        JsonArray parameters = resolve(spec.rules(), variables).getAsJsonArray();
        JsonObject expected = resolve(spec.expected(), variables).getAsJsonObject();
        var codec = RuleCodecs.codec(types.effectTypes(), types.conditionTypes());
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<LoadedRule<Rule>> loaded = new ArrayList<>();
        IssueCollector issues = new IssueCollector();
        for (JsonElement element : parameters) {
            Rule decoded = codec.parse(ops, element).getOrThrow();
            if (!RuleValidation.validate(decoded, types.effectTypes(), types.conditionTypes(), issues, "debug:" + spec.key())) {
                throw new IllegalArgumentException(issues.errors().toString());
            }
            loaded.add(new LoadedRule<>(decoded.id(), new RuleOrigin(RuleSourceLayer.BUILTIN,
                    "development-memory", DebugScenarioCatalog.ROOT + spec.key() + ".json"), decoded));
        }
        RuleIndex index = RuleIndex.build(loaded, issues);
        if (!issues.errors().isEmpty()) { throw new IllegalArgumentException(issues.errors().toString()); }
        return new DebugScenarioDefinition(spec, index, parameters, expected, sources);
    }

    // 只支持固定数值变量，不执行表达式；返回副本，防止本轮参数污染目录模板。
    private static JsonElement resolve(JsonElement source, Map<String, Long> variables) {
        if (source.isJsonObject()) {
            JsonObject resolved = new JsonObject();
            source.getAsJsonObject().entrySet().forEach(entry -> resolved.add(entry.getKey(), resolve(entry.getValue(), variables)));
            return resolved;
        }
        if (source.isJsonArray()) {
            JsonArray resolved = new JsonArray();
            source.getAsJsonArray().forEach(element -> resolved.add(resolve(element, variables)));
            return resolved;
        }
        if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isString() && source.getAsString().startsWith("$")) {
            Long value = variables.get(source.getAsString());
            if (value == null) { throw new IllegalArgumentException("未知场景变量：" + source.getAsString()); }
            return new JsonPrimitive(value);
        }
        return source.deepCopy();
    }
}
