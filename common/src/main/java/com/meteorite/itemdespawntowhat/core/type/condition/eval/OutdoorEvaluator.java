package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.OutdoorCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * outdoor 条件的求值器：判定掉落物上方是否露天。
 * 语义与旧实现一致：用 MOTION_BLOCKING_NO_LEAVES 高度图取该柱最高阻挡面，其高度不高于物品所在 Y + 1 即露天。
 * 纯谓词：只读取上下文，不修改世界；不处理取反（取反由条件树的 inverted 节点承担）。
 */
public final class OutdoorEvaluator {

    private OutdoorEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 高度图最高阻挡面 <= 物品 Y + 1 视为露天
    public static boolean test(OutdoorCondition condition, ConditionContext context) {
        BlockPos pos = context.pos();
        int surfaceY = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return surfaceY <= pos.getY() + 1;
    }
}
