package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Map;

public abstract class AbstractConditionChecker implements ConditionChecker {

    public abstract String getConditionKey();
    // 解析并构建检查器
    public abstract AbstractConditionChecker parse(Map<String, String> conditions);

    // 是否可以应用，由子类覆盖
    public boolean shouldApply(ConditionContext ctx) {
        return true;
    }
    // 用于强类型参数的统一解析路径
    public abstract void applyCondition(
            Map<String, String> conditions,
            ConditionContext ctx
    );
    // 检查条件是否满足
    @Override
    public abstract boolean checkCondition(ItemEntity itemEntity, ServerLevel level);

    protected String getConditionValue(Map<String, String> conditions, String defaultValue) {
        String value = conditions.get(getConditionKey());
        return value != null ? value : defaultValue;
    }

    protected boolean getConditionBoolean(Map<String, String> conditions, boolean defaultValue) {
        String value = conditions.get(getConditionKey());
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
