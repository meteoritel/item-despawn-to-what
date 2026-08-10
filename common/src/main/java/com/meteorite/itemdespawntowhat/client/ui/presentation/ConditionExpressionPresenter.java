package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.google.gson.Gson;
import com.meteorite.itemdespawntowhat.client.ui.condition.ClientConditionTypeDefinition;
import com.meteorite.itemdespawntowhat.client.ui.condition.ClientConditionTypeRegistry;
import com.meteorite.itemdespawntowhat.config.condition.ConditionExpression;
import com.meteorite.itemdespawntowhat.config.condition.ConditionLeaf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 将 DNF 条件表达式转换为可读的本地化摘要。
 */
public final class ConditionExpressionPresenter {
    private static final Gson GSON = new Gson();

    private ConditionExpressionPresenter() {
    }

    public static Component summary(ConditionExpression expression) {
        if (expression == null || expression.leafCount() == 0) {
            return Component.translatable("gui.itemdespawntowhat.condition.always");
        }
        MutableComponent result = Component.empty();
        for (int groupIndex = 0; groupIndex < expression.groups().size(); groupIndex++) {
            if (groupIndex > 0) {
                result.append(Component.translatable("gui.itemdespawntowhat.condition.or"));
            }
            var conditions = expression.groups().get(groupIndex).conditions();
            for (int leafIndex = 0; leafIndex < conditions.size(); leafIndex++) {
                if (leafIndex > 0) {
                    result.append(Component.translatable("gui.itemdespawntowhat.condition.and"));
                }
                result.append(leafSummary(conditions.get(leafIndex)));
            }
        }
        return result;
    }

    private static Component leafSummary(ConditionLeaf leaf) {
        ClientConditionTypeDefinition definition = ClientConditionTypeRegistry.byId(leaf.typeId());
        Component name = definition == null
                ? Component.literal(leaf.rawTypeId())
                : definition.displayName();
        MutableComponent result = Component.empty();
        if (leaf.negated()) {
            result.append(Component.translatable("gui.itemdespawntowhat.condition.not"));
        }
        result.append(name);
        String params = GSON.toJson(leaf.params());
        if (!params.equals("{}")) {
            result.append(Component.literal(" ")).append(Component.literal(params));
        }
        return result;
    }
}
