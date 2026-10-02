package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.condition.SurroundingBlocksCondition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * surrounding_blocks 条件的求值器：逐方向比对掉落物相邻方块。
 * 只检查已填写的方向，全部满足才成立；标签判定走 TagLookup（带缓存）。
 * 纯谓词：只读取上下文，不修改世界；不处理取反（取反由条件树的 inverted 节点承担）。
 */
public final class SurroundingBlocksEvaluator {

    private SurroundingBlocksEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 六个方向中已填写的必须全部命中；一个都没填属非法配置，fail-closed
    public static boolean test(SurroundingBlocksCondition condition, ConditionContext context) {
        boolean constrained = false;
        if (condition.up() != null) {
            constrained = true;
            if (!matches(condition.up(), Direction.UP, context)) {
                return false;
            }
        }
        if (condition.down() != null) {
            constrained = true;
            if (!matches(condition.down(), Direction.DOWN, context)) {
                return false;
            }
        }
        if (condition.north() != null) {
            constrained = true;
            if (!matches(condition.north(), Direction.NORTH, context)) {
                return false;
            }
        }
        if (condition.south() != null) {
            constrained = true;
            if (!matches(condition.south(), Direction.SOUTH, context)) {
                return false;
            }
        }
        if (condition.east() != null) {
            constrained = true;
            if (!matches(condition.east(), Direction.EAST, context)) {
                return false;
            }
        }
        if (condition.west() != null) {
            constrained = true;
            if (!matches(condition.west(), Direction.WEST, context)) {
                return false;
            }
        }
        return constrained;
    }

    // 单个方向匹配：取相邻方块的注册 id，标签走 TagLookup
    private static boolean matches(TaggedId reference, Direction direction, ConditionContext context) {
        BlockState state = context.level().getBlockState(context.pos().relative(direction));
        Optional<ResourceKey<Block>> key = state.getBlockHolder().unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        ResourceLocation blockId = key.get().location();
        return reference.tag()
                ? context.tags().blockInTag(reference.id(), blockId)
                : reference.id().equals(blockId);
    }
}
