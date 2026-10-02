package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** 场景规则通过真实 Codec、语义校验和 RuleIndex 构建，仅作用于本轮标记实体。 */
final class DebugScenarioDefinition {
    static final List<String> FUNCTIONAL = List.of("convert", "retry", "delay", "expiry", "stack", "priority", "excluded", "reload");
    final RuleIndex index;
    final JsonArray parameters;
    final int sources;
    final int stackSize;
    final long expectedConversions;
    final long expectedOutput;

    // 原始参数与解码后的规则均保留，默认值在 JSON 中显式声明。
    private DebugScenarioDefinition(RuleIndex index, JsonArray parameters, int sources, int stackSize,
                                    long expectedConversions, long expectedOutput) {
        this.index = index;
        this.parameters = parameters;
        this.sources = sources;
        this.stackSize = stackSize;
        this.expectedConversions = expectedConversions;
        this.expectedOutput = expectedOutput;
    }

    // 构建测试负载；性能 retry 保持条件失败，功能 retry 后续会移动源实体让条件成立。
    static DebugScenarioDefinition create(MinecraftServer server, BuiltinTypeRegistries types,
                                          String name, boolean benchmark, int count, int y) {
        int sources = name.equals("excluded") ? 2 : count;
        int stack = name.equals("stack") ? 16 : 1;
        boolean noConversion = name.equals("baseline") || name.equals("normal")
                || name.equals("excluded") || benchmark && name.equals("retry");
        List<JsonObject> raw = new ArrayList<>();
        if (!name.equals("baseline") && !name.equals("normal")) {
            int trigger = name.equals("expiry") ? 3600 : name.equals("converted") ? 5 : benchmark ? 10 : 2;
            raw.add(rule(name, trigger, 0, name.equals("delay") || name.equals("reload") ? 100 : 0));
        }
        if (name.equals("priority")) { raw.add(rule("priority_later", 8, 100, 0)); }
        if (name.equals("retry")) {
            // 条件树：取反改用 inverted 节点，叶级 negated 已删除
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "itemdespawntowhat:y_level");
            condition.addProperty("min", y + 3);
            JsonObject leaf = new JsonObject();
            leaf.addProperty("op", "leaf");
            leaf.add("condition", condition);
            raw.getFirst().add("conditions", leaf);
        }
        var codec = RuleCodecs.codec(types.effectTypes(), types.conditionTypes());
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonArray parameters = new JsonArray();
        List<LoadedRule<Rule>> loaded = new ArrayList<>();
        IssueCollector issues = new IssueCollector();
        for (JsonObject json : raw) {
            Rule decoded = codec.parse(ops, json).getOrThrow();
            if (!RuleValidation.validate(decoded, types.effectTypes(), types.conditionTypes(), issues, "debug:" + name)) {
                throw new IllegalArgumentException(issues.errors().toString());
            }
            parameters.add(json);
            loaded.add(new LoadedRule<>(decoded.id(), new RuleOrigin(RuleSourceLayer.BUILTIN,
                    "development-memory", "debug/scenes/" + name), decoded));
        }
        return new DebugScenarioDefinition(RuleIndex.build(loaded, issues), parameters, sources, stack,
                noConversion ? 0 : sources, noConversion ? 0 : (long) sources * stack);
    }

    // 所有场景使用同一源与产物，便于比较不同配置和构建的后端成本。
    private static JsonObject rule(String name, int trigger, int priority, int delay) {
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "itemdespawntowhat:debug/" + name);
        rule.addProperty("enabled", true);
        rule.addProperty("priority", priority);
        rule.addProperty("trigger_after_seconds", trigger);
        JsonObject source = new JsonObject();
        JsonArray items = new JsonArray();
        items.add("minecraft:paper");
        source.add("items", items);
        source.add("exclude", new JsonArray());
        rule.add("source", source);
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "itemdespawntowhat:spawn_item");
        effect.addProperty("item", name.equals("priority_later") ? "minecraft:gold_nugget" : "minecraft:prismarine_shard");
        effect.addProperty("count", 1);
        effect.addProperty("delay_ticks", delay);
        effect.addProperty("chance", 1.0);
        JsonArray effects = new JsonArray();
        effects.add(effect);
        rule.add("effects", effects);
        return rule;
    }
}
