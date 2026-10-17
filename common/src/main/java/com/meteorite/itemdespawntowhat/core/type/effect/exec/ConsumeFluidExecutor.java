package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
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
import org.jetbrains.annotations.Nullable;

/**
 * consume_fluid 执行器：消耗触发位置处的流体；fluid 为空表示任意流体，require_source 要求必须为源头。
 * 含水方块只移除含水状态，其余情况整体移除该位置的方块。
 * rounds 语义：按「每轮消耗 1 格」实现，最多尝试 rounds 次；
 * 同一位置在同一 tick 内只能被消耗一次（消耗后流体状态立即变空），
 * 因此实际通常只消耗 1 格，未消耗的轮次被剔除并记录，不会静默截断。
 * 流体只作为实时存在条件，不参与组数与份数预留。
 * 执行形态：位置与轮数上界在开组时定稿，逐格真实消耗在共享预算内推进，每个可拆单元前检查预算，
 * 预算耗尽即让出并由后续 tick 从同一起点继续；每成功消耗一格立即回执一格完成量，终态不重复计数；
 * 取消时不回滚已经发生过的格消耗，也不会把已完成的格数从账目里抹掉。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeFluidExecutor {

    // 执行期结果说明：位置本来就没有匹配流体 / 流体不足全部轮次；仅本类使用
    private static final String FLUID_ABSENT = "fluid_absent";
    private static final String SHORT_OF_FLUID = "short_of_fluid";
    // 无共享预算的同步调用不可用：逐格消耗必须在调度器预算内推进
    private static final String BUDGET_REQUIRED = "budget_required";

    private static final Logger LOGGER = LogManager.getLogger();

    private ConsumeFluidExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 拒绝没有共享预算的直接执行，避免逐格消耗绕过服务端调度。
     * @param effect 效果参数
     * @param context 执行上下文
     * @return 表示必须使用预算入口的失败回执，不修改任何方块
     */
    public static EffectResult execute(ConsumeFluidEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 创建逐格消耗的执行状态；位置与轮数上界在此定稿，方块读写留到推进时实时完成。
     * @param effect 效果参数
     * @param context 本组上下文
     * @return 按共享预算推进的执行状态
     */
    public static EffectExecution start(ConsumeFluidEffect effect, EffectContext context) {
        return new FluidConsumption(effect, context);
    }

    // 每轮消耗 1 格：位置与轮数在构造期定稿，逐格消耗按预算推进，每个单元一次真实世界操作
    private static final class FluidConsumption implements EffectExecution {
        private final ConsumeFluidEffect effect;
        private final EffectContext context;
        private final ServerLevel level;
        private final BlockPos pos;
        private final int rounds;
        private int consumed;
        private EffectResult result;

        private FluidConsumption(ConsumeFluidEffect effect, EffectContext context) {
            this.effect = effect;
            this.context = context;
            this.level = context.level();
            Vec3 position = context.position();
            this.pos = BlockPos.containing(position.x, position.y, position.z);
            this.rounds = context.rounds();
        }

        @Override
        public @Nullable EffectResult advance(ServerTickBudget budget) {
            if (result != null) {
                return result;
            }
            // 一格方块写入是一个不可抢占单元：预算耗尽即让出、下刻从同一位置续接；
            // 每成功消耗一格立即经受控进度回执落账，取消时已发生的消耗不会从账目里消失。
            while (consumed < rounds) {
                if (budget.exhausted()) {
                    return null;
                }
                if (!consumeOne(level, pos, effect)) {
                    break;
                }
                consumed++;
                context.reportProgress(1);
            }
            return finish(terminal());
        }

        // 终态：逐格完成量已经由进度回执落账，这里不再重复计数；
        // 位置本就没有匹配流体才跳过，消耗过部分格数按不足回执，不静默截断
        private EffectResult terminal() {
            if (consumed > 0) {
                return consumed < rounds ? EffectResult.applied(0, SHORT_OF_FLUID) : EffectResult.applied(0);
            }
            return EffectResult.skipped(FLUID_ABSENT);
        }

        private EffectResult finish(EffectResult terminal) {
            if (consumed < rounds) {
                LOGGER.debug("consume_fluid 未能消耗足够轮次：规则={} 期望={} 实际={} 位置={}",
                        context.ruleId(), rounds, consumed, pos);
            }
            result = terminal;
            return result;
        }
    }

    // 消耗触发位置的一格匹配流体；该位置已无匹配流体时返回 false，且不计费
    private static boolean consumeOne(ServerLevel level, BlockPos pos, ConsumeFluidEffect effect) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty() || !matches(effect.fluid(), fluidState)) {
            return false;
        }
        if (effect.requireSource() && !fluidState.isSource()) {
            return false;
        }
        BlockState blockState = level.getBlockState(pos);
        // 计价：一次方块读取加一次方块写入记 1 个工作单位，与放置类执行器同一口径
        StepWorkCounter.report(1);
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
