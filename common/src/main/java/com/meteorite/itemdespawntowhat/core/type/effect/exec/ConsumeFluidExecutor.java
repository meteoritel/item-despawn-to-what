package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeFluidEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * consume_fluid 执行器：消耗触发位置处的流体；fluid 为空表示任意流体，require_source 要求必须为源头。
 * 含水方块只移除含水状态，其余情况整体移除该位置的方块。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeFluidExecutor {

    private ConsumeFluidExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(ConsumeFluidEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        Vec3 position = context.position();
        BlockPos pos = BlockPos.containing(position.x, position.y, position.z);
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty() || !matches(effect.fluid(), fluidState)) {
            return;
        }
        if (effect.requireSource() && !fluidState.isSource()) {
            return;
        }
        BlockState blockState = level.getBlockState(pos);
        // 含水方块：只移除含水状态，保留方块本身
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
            return;
        }
        // 流体方块或依赖流体存在的方块（气泡柱、海带等）：整体移除
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // fluid 为空表示任意流体；tag 引用按标签匹配，非 tag 比注册表对象
    private static boolean matches(TaggedId fluid, FluidState fluidState) {
        if (fluid == null) {
            return true;
        }
        if (fluid.tag()) {
            return fluidState.is(TagKey.create(Registries.FLUID, fluid.id()));
        }
        if (!BuiltInRegistries.FLUID.containsKey(fluid.id())) {
            return false;
        }
        Fluid target = BuiltInRegistries.FLUID.get(fluid.id());
        return fluidState.is(target);
    }
}
