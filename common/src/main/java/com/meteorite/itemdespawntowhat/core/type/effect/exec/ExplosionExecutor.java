package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.ExplosionEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * explosion 执行器：产生一次爆炸；visual_only 为真时只播放粒子与音效，不破坏方块、不伤害实体。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制。
 * 基准位置在创建执行状态时固定，之后不再读取源实体；爆炸是一次不可抢占的原子原版操作，
 * 只能在任务之间检查软预算，因此等待区块期间不推进，一旦开始执行就在同一步内完成整次爆炸。
 * 约定：delay_ticks / chance 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ExplosionExecutor {

    /** 单次破坏型爆炸的计费工作量：一步内完成整次原版 explode，
     * 方块破坏与实体伤害判定覆盖半径内的全部方块，
     * 代价远高于单次方块读写，故按与其代价相称的常量计入本 tick 预算（见 {@link StepWorkCounter#report(int)}）。
     * 该常量只影响计费，不改变爆炸的行为与产出。
     */
    private static final int EXPLOSION_WORK_UNITS = 128;
    // 拒绝直接调用的原因：没有共享预算的实现无法按 tick 推进
    private static final String BUDGET_REQUIRED = "budget_required";
    // 缺少成功转化当刻固定输入的原因：本类型只能在准备阶段之后执行
    private static final String MISSING_PREPARATION = "missing_preparation";

    private ExplosionExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 拒绝没有共享预算的直接执行，爆炸只能经执行状态在预算内推进。
     * @param effect 爆炸效果参数
     * @param context 规则执行上下文
     * @return 表示必须使用执行状态入口的失败回执，不做任何世界写入
     */
    public static EffectResult execute(ExplosionEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态；基准位置取自本次转换。
     * @param effect 爆炸效果参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示本类型未按约定准备，属真失败
     * @return 可跨 tick 等待区块的爆炸执行状态
     */
    public static EffectExecution start(ExplosionEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 没有准备结果说明调用方绕过了准备阶段：概率无从固定，按失败回执而非静默降级
            return budget -> EffectStep.done(EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId()));
        }
        if (preparation.gate() != null) {
            // 已判定不执行：不重新判定概率，也不做世界写入
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        return new AtomicExplosion(effect, context);
    }

    /** 单次爆炸的推进状态：未加载时按同一基准位置退避重试，就绪后一步完成。 */
    private static final class AtomicExplosion implements EffectExecution {
        private final ExplosionEffect effect;
        private final EffectContext context;
        private final ChunkRequirement chunks;
        // 转换当刻固定的基准位置：后续不再读取源实体
        private final Vec3 position;
        private EffectResult terminal;

        AtomicExplosion(ExplosionEffect effect, EffectContext context) {
            this.effect = effect;
            this.context = context;
            this.position = context.position();
            this.chunks = ChunkRequirement.around(BlockPos.containing(position),
                    (int) Math.ceil(effect.power() * 2) + 1);
        }

        @Override
        public ChunkRequirement requiredChunks() {
            return chunks;
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            if (terminal != null) {
                return EffectStep.done(terminal);
            }
            ServerLevel level = context.level();
            if (budget.exhausted()) {
                return EffectStep.Yield.INSTANCE;
            }
            terminal = explode(level);
            return EffectStep.done(terminal);
        }

        // 一次不可抢占的原子世界操作；回执在同一调用内确定
        private EffectResult explode(ServerLevel level) {
            if (effect.visualOnly()) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE,
                        SoundSource.BLOCKS, 4.0F,
                        (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
                // 计价口径与破坏型一致：只有破坏型爆炸按具名常量上报工作量，视觉分支不额外计费
                return EffectResult.applied(1).asOneShot();
            }
            level.explode(null, position.x, position.y, position.z, effect.power(), effect.fire(),
                    Level.ExplosionInteraction.TNT);
            // 计价：一步完成整次世界操作，按具名常量回执
            StepWorkCounter.report(EXPLOSION_WORK_UNITS);
            return EffectResult.applied(1).asOneShot();
        }
    }
}
