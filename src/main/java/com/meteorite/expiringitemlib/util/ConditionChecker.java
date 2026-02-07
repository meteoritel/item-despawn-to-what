package com.meteorite.expiringitemlib.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

// 条件检查接口
@FunctionalInterface
public interface ConditionChecker {
    boolean checkCondition(ItemEntity itemEntity, ServerLevel level);
}
