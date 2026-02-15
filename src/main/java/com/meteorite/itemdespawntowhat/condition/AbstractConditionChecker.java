package com.meteorite.itemdespawntowhat.condition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Map;

public abstract class AbstractConditionChecker implements ConditionChecker{

    // 解析并构建检查器
    public abstract AbstractConditionChecker parse(Map<String, String> conditions);

    // 检查条件是否满足
    @Override
    public abstract boolean checkCondition(ItemEntity itemEntity, ServerLevel level);

    // 安全获得字符串值
    protected String getConditionValue(Map<String, String> conditions, String key, String defaultValue) {
        String value = conditions.get(key);
        return value != null ? value : defaultValue;
    }

    // 安全获得布尔值
    protected boolean getConditionBoolean(Map<String, String> conditions, String key, boolean defaultValue) {
        String value = conditions.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

}
