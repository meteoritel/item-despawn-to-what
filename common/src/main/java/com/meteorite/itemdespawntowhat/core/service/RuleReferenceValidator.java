package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceCheck;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceContext;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionTrees;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 服务端动态引用校验：只负责遍历规则级条件树与效果清单，并把每一项交给类型自带的动态引用扩展点
 * （见 {@link DynamicReferenceCheck}），议题由统一收集通道汇总。
 * 本类不感知任何具体效果或条件类型，第三方类型实现扩展点即可获得同等校验。
 */
public final class RuleReferenceValidator {
    private RuleReferenceValidator() {}

    /** 动态校验要求非空 server；离线装配须由调用方跳过本步。 */
    public static boolean validate(Rule rule, MinecraftServer server, IssueCollector issues, String origin) {
        Objects.requireNonNull(server, "动态引用校验需要 MinecraftServer");
        int before = issues.errors().size();
        validateConditions(rule.conditions(), server, issues, origin);
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

    // 单个效果：交给效果自身的动态引用扩展点
    private static void validateEffectReferences(Effect effect, MinecraftServer server, IssueCollector issues,
                                                 String origin, String path) {
        checkDynamicReferences(effect, server, issues, origin, path);
    }

    // 沿规则级条件树逐叶做动态引用校验：路径与 JSON 形状一致（如 conditions.terms[0].condition.dimensions）
    private static void validateConditions(ConditionExpression expression, MinecraftServer server,
                                           IssueCollector issues, String origin) {
        ConditionTrees.forEachLeaf(expression.root(), RuleFields.CONDITIONS, (field, leaf) ->
                checkDynamicReferences(leaf, server, issues, origin, field));
    }

    // 扩展点分派：效果与条件叶共用同一契约，未实现该契约（含 null）的类型视为无动态引用
    private static void checkDynamicReferences(@Nullable Object target, MinecraftServer server,
                                               IssueCollector issues, String origin, String fieldPath) {
        if (target instanceof DynamicReferenceCheck check) {
            check.checkDynamicReferences(
                    new DynamicReferenceContext(server, issues.originScoped(origin), fieldPath));
        }
    }
}
