package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 经验产出执行器：先按本组实际扣减的源物品件数算出总点数，再按单球安全点数分批生成。
 * 总点数只算一次：每件源物品的点数 × 本组实际扣减件数，不随分批重复计算，也不因邻近经验球数量拒绝或截断。
 * 条件、概率与延迟由运行时处理，不跨行为或转化组合并经验。
 * 每球成功加入世界后回执真实点数；未完成部分与已构造但尚未写入的球留到后续步骤继续，取消后不补做。
 */
public final class SpawnXpExecutor {

    // 原版 Value 的 NBT 与生成数据包均使用有符号 short，构造参数为 int 不代表可安全存储 int。
    private static final int MAX_POINTS_PER_ORB = Short.MAX_VALUE;
    // 单步最多生成的球数：执行策略上界，与点数上限无关
    private static final int ORBS_PER_BATCH = 16;
    private static final double SCATTER_RADIUS = 0.5;
    private static final double SPAWN_HEIGHT = 0.2;
    // 回执说明：同步入口不承担分批，交由预算执行路径推进
    private static final String BUDGET_REQUIRED = "budget_required: experience";
    // 回执说明：成功转化当刻没有固定输入，属于运行时接线缺失，不退回到期重判
    private static final String MISSING_PREPARATION = "missing_preparation: experience";
    private static final String ZERO_AMOUNT = "zero_amount";
    private static final String AWARDED = "awarded";

    private SpawnXpExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 同步入口：经验产出需要跨步骤续接，不提供一次性完成的执行方式。
     *
     * @param effect 经验参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    public static EffectResult execute(EntityProduct.Experience effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED);
    }

    /**
     * 按成功转化当刻固定的输入创建经验执行状态。
     * 总点数在本方法内一次算定：每件源物品的点数 × 本组实际扣减件数。
     *
     * @param effect 经验点数参数
     * @param context 本组执行上下文
     * @param preparation 成功转化当刻固定的输入；为跳过时不生成任何经验球
     * @return 仅供本次效果使用的执行状态
     */
    public static EffectExecution start(EntityProduct.Experience effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 迁移到固定输入路径后，缺少固定输入不再退回到期重新判定
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        }
        // 成功转化当刻已判定的跳过原因：直接产出跳过回执，不重判也不生成经验球
        if (preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        int covered = context.coveredSourceItems();
        long total = covered <= 0 ? 0L : (long) effect.amount() * covered;
        if (total <= 0) {
            return budget -> EffectStep.done(EffectResult.skipped(ZERO_AMOUNT));
        }
        return new XpDelivery(context, total);
    }

    /** 单个经验行为的交付游标，仅由服务端线程访问。 */
    private static final class XpDelivery implements EffectExecution {
        private final EffectContext context;
        private final ChunkRequirement chunks;
        private long remaining;
        private boolean feedbackSent;
        // 已构造但尚未加入世界的这个球：原版构造内的朝向与初速随机已确定，等待区块时不重造
        private ExperienceOrb pendingOrb;
        private int pendingPoints;

        private XpDelivery(EffectContext context, long total) {
            this.context = context;
            this.chunks = ChunkRequirement.around(BlockPos.containing(context.position()), 1);
            this.remaining = total;
        }

        @Override
        public @Nullable ChunkRequirement requiredChunks() {
            if (remaining <= 0) { return null; }
            return pendingOrb == null ? chunks : chunks.including(pendingOrb.blockPosition());
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            // 圆盘可能跨越源区块边界；等待邻区块加载，不生成区块、不提前记为成功
            int spawned = 0;
            // 本步已报告但尚未由调度器计费的额度：单步内 remainingWorkUnits 不会随上报立即减少，需自行扣除
            int reportedUnits = 0;
            while (spawned < ORBS_PER_BATCH && remaining > 0) {
                // 每球前守一次：时间耗尽，或本步已用额度（含已报告未计费部分）耗尽时本步不再写入世界，
                // 剩余点数原样留到后续步骤，不截断总量
                if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                    return EffectStep.Yield.INSTANCE;
                }
                if (pendingOrb == null) {
                    int points = (int) Math.min(remaining, MAX_POINTS_PER_ORB);
                    Vec3 position = EffectTargets.randomHorizontalDisc(context.random(), context.position(),
                            SCATTER_RADIUS, SPAWN_HEIGHT);
                    // award 会再次按原版面额拆球；直接构造保留原版初速度、拾取和后续同值合并。
                    pendingOrb = new ExperienceOrb(context.level(), position.x, position.y, position.z, points);
                    pendingPoints = points;
                }
                if (!EffectTargets.addEntityNow(context, pendingOrb, false)) {
                    return new EffectStep.AwaitChunks(ChunkRequirement.at(pendingOrb.blockPosition()));
                }
                int points = pendingPoints;
                pendingOrb = null;
                pendingPoints = 0;
                remaining -= points;
                // 真实完成量回执：球已加入世界后才累计点数
                context.reportProgress(points);
                // 计价：一个真正写入世界的实体记 1 个工作单位，与物品、实体产出同一口径
                StepWorkCounter.report(1);
                reportedUnits++;
                spawned++;
                if (!feedbackSent) {
                    VisualFeedback.entitySpawn(context.level(), context.position());
                    feedbackSent = true;
                }
            }
            if (remaining > 0) {
                return EffectStep.Yield.INSTANCE;
            }
            // 实际点数已逐球回执，终态不再重复计入产出
            return EffectStep.done(EffectResult.applied(0, AWARDED));
        }

        @Override
        public void onFinished(EffectResult result) {
            // 交付完成或已失败：不再持有剩余点数与已构造未写入的球，取消路径也不补做
            remaining = 0;
            pendingOrb = null;
            pendingPoints = 0;
        }
    }
}
