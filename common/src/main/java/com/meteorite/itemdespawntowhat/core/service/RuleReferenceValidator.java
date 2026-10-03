package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionTrees;
import com.meteorite.itemdespawntowhat.core.model.Effect;
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
        validateConditions(rule.conditions(), server, issues, origin, RuleFields.CONDITIONS);
        for (int i = 0; i < rule.effects().size(); i++) {
            validateEffectReferences(rule.effects().get(i), server, issues, origin,
                    RuleFields.EFFECTS + "[" + i + "]");
        }
        // 候选结果内效果与顶层 effects 同等校验：路径与 JSON 形状一致（outcomes[i].effects[j]）
        for (int i = 0; i < rule.outcomes().size(); i++) {
            var outcome = rule.outcomes().get(i);
            for (int j = 0; j < outcome.effects().size(); j++) {
                validateEffectReferences(outcome.effects().get(j), server, issues, origin,
                        RuleFields.OUTCOMES + "[" + i + "]." + RuleFields.CANDIDATE_EFFECTS + "[" + j + "]");
            }
        }
        return issues.errors().size() == before;
    }

    // 单个效果的动态引用校验：战利品表必须真实加载成功，效果级条件沿树递归展开
    private static void validateEffectReferences(Effect effect, MinecraftServer server, IssueCollector issues,
                                                 String origin, String path) {
        if (effect instanceof LootTableEffect loot && server.reloadableRegistries().lookup()
                .lookup(Registries.LOOT_TABLE).flatMap(tables -> tables.get(ResourceKey.create(Registries.LOOT_TABLE, loot.lootTable()))).isEmpty()) {
            issues.error("未找到已加载的战利品表: " + loot.lootTable(), origin, path + ".loot_table");
        }
        var conditions = effect.conditions();
        if (conditions != null) { validateConditions(conditions, server, issues, origin, path + ".conditions"); }
    }

    // 沿条件树逐叶做动态引用校验：路径与 JSON 形状一致（如 conditions.terms[0].condition.dimensions）
    private static void validateConditions(ConditionExpression expression, MinecraftServer server,
                                           IssueCollector issues, String origin, String path) {
        ConditionTrees.forEachLeaf(expression.root(), path, (field, leaf) -> {
            if (leaf == null) {
                return;
            }
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
        });
    }
}
