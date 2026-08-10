package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 检查物品实体上方是否露天。
 */
public class OutdoorConditionChecker extends AbstractConditionChecker{

    @Override
    public AbstractConditionChecker createChecker(ConditionContext ctx) {
        return ctx.needOutdoor() ? new OutdoorConditionChecker() : null;
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.needOutdoor();
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        BlockPos pos = itemEntity.blockPosition();

        // 检查从当前位置到世界顶部的路径上是否有阻挡方块
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(checkPos);

            // 如果遇到非空气且非透明方块，则不是露天
            if (!state.isAir() && state.canOcclude()) {
                return false;
            }
        }
        return true;
    }
}
