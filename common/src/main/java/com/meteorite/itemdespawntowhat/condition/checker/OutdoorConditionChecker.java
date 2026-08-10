package com.meteorite.itemdespawntowhat.condition.checker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 检查物品实体上方是否露天。
 */
public final class OutdoorConditionChecker extends PositionCachedConditionChecker {

    @Override
    public String debugName() {
        return "outdoor";
    }

    @Override
    protected boolean checkAtPosition(BlockPos position, ServerLevel level) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(), position.getZ());
        return surfaceY <= position.getY() + 1;
    }
}
