package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * consume_fluid 执行器：消耗触发位置处的流体；fluid 为空表示任意流体，require_source 要求必须为源头。
 * 含水方块只移除含水状态，其余情况整体移除该位置的方块。
 * rounds 语义：按「每轮消耗 1 格」实现，最多尝试 rounds 次；
 * 同一位置在同一 tick 内只能被消耗一次（消耗后流体状态立即变空），
 * 因此实际通常只消耗 1 格，未消耗的轮次被剔除并记录，不会静默截断。
 * 流体只作为实时存在条件，不参与组数与份数预留（ADR-0001）。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeFluidExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    private ConsumeFluidExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static EffectResult execute(ConsumeFluidEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        Vec3 position = context.position();
        BlockPos pos = BlockPos.containing(position.x, position.y, position.z);
        int rounds = context.rounds();
        int consumed = 0;
        // 每轮消耗 1 格；位置已无匹配流体时立即停止，剩余轮次在此剔除
        while (consumed < rounds && consumeOne(level, pos, effect)) {
            consumed++;
        }
        if (consumed < rounds) {
            LOGGER.debug("consume_fluid 未能消耗足够轮次：规则={} 期望={} 实际={} 位置={}",
                    context.ruleId(), rounds, consumed, pos);
        }
        if (consumed <= 0) {
            return EffectResult.skipped("fluid_absent");
        }
        return EffectResult.applied(consumed, consumed < rounds ? "short_of_fluid" : "");
    }

    // 消耗触发位置的一格匹配流体；该位置已无匹配流体时返回 false
    private static boolean consumeOne(ServerLevel level, BlockPos pos, ConsumeFluidEffect effect) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty() || !matches(effect.fluid(), fluidState)) {
            return false;
        }
        if (effect.requireSource() && !fluidState.isSource()) {
            return false;
        }
        BlockState blockState = level.getBlockState(pos);
        // 含水方块：只移除含水状态，保留方块本身
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
            return true;
        }
        // 流体方块或依赖流体存在的方块（气泡柱、海带等）：整体移除
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
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
