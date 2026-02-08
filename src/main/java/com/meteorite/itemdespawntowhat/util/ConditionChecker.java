package com.meteorite.itemdespawntowhat.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

// 条件检查接口
@FunctionalInterface
public interface ConditionChecker {
    boolean checkCondition(ItemEntity itemEntity, ServerLevel level);
}
