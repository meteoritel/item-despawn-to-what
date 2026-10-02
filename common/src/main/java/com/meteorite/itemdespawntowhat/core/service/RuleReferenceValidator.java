package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.DimensionCondition;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

/** 服务端动态引用校验：使用当前维度、群系注册表和真正加载成功的战利品表。 */
public final class RuleReferenceValidator {
    private RuleReferenceValidator() {}

    public static boolean validate(Rule rule, MinecraftServer server, IssueCollector issues, String origin) {
        int before = issues.errors().size();
        validateConditions(rule.conditions(), server, issues, origin, "conditions");
        for (int i = 0; i < rule.effects().size(); i++) {
            var effect = rule.effects().get(i);
            String path = "effects[" + i + "]";
            if (effect instanceof LootTableEffect loot && server.reloadableRegistries().lookup()
                    .lookup(Registries.LOOT_TABLE).flatMap(tables -> tables.get(ResourceKey.create(Registries.LOOT_TABLE, loot.lootTable()))).isEmpty()) {
                issues.error("未找到已加载的战利品表: " + loot.lootTable(), origin, path + ".loot_table");
            }
            var conditions = effect.conditions();
            if (conditions != null) { validateConditions(conditions, server, issues, origin, path + ".conditions"); }
        }
        return issues.errors().size() == before;
    }

    private static void validateConditions(ConditionExpression expression, MinecraftServer server,
                                           IssueCollector issues, String origin, String path) {
        for (int g = 0; g < expression.groups().size(); g++) {
            var leaves = expression.groups().get(g).conditions();
            for (int l = 0; l < leaves.size(); l++) {
                var leaf = leaves.get(l);
                String field = path + "[" + g + "][" + l + "]";
                if (leaf instanceof DimensionCondition dimension) {
                    for (var id : dimension.dimensions()) {
                        if (!server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, id))) {
                            issues.error("未知维度: " + id, origin, field + ".dimensions");
                        }
                    }
                } else if (leaf instanceof BiomeCondition biome && biome.mode() == BiomeCondition.Mode.EXACT) {
                    IssueCollector local = new IssueCollector();
                    RefChecks.checkAll(biome.biomes(), server.registryAccess().registryOrThrow(Registries.BIOME),
                            "biomes", local, field + ".biomes");
                    local.issues().forEach(issue -> issues.add(new com.meteorite.itemdespawntowhat.core.api.Issue(
                            issue.severity(), issue.message(), origin, issue.fieldPath())));
                }
            }
        }
    }
}
