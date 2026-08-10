package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 根据强类型配置上下文创建运行时条件检查器的基类。
 */
public abstract class AbstractConditionChecker implements ConditionChecker {

    /** 根据强类型条件上下文创建检查器实例。 */
    public abstract AbstractConditionChecker createChecker(ConditionContext ctx);

    // 是否可以应用，由子类覆盖
    public abstract boolean shouldApply(ConditionContext ctx);

    // 检查条件是否满足
    @Override
    public abstract boolean checkCondition(ItemEntity itemEntity, ServerLevel level);
}
