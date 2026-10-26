package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 经验产出执行器：先按本组实际扣减的源物品件数算出总点数，再按单球安全点数分批生成。
 * 总点数只算一次：每件源物品的点数 × 本组实际扣减件数，不随分批重复计算，也不因邻近经验球数量拒绝或截断。
 * 条件、概率与延迟由运行时处理，经验不跨行为或转化组合并。
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
     * 同步入口：经验产出按批次在共享预算内续接，由预算执行路径推进。
     *
     * @param effect 经验参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    // 形参由效果类型的分发签名固定，同步入口不读取其中任何参数
    public static EffectResult execute(@SuppressWarnings("unused") EntityProduct.Experience effect,
                                       @SuppressWarnings("unused") EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED);
    }

    /**
     * 按成功转化当刻固定的输入创建经验执行状态。
     * 总点数在本方法内一次算定：每件源物品的点数 × 本组实际扣减件数。
     *
     * @param effect 经验点数参数
     * @param context 本组执行上下文
     * @param preparation 成功转化当刻固定的输入；null 表示接线缺失，gate 有值表示已判定跳过
     * @return 仅供本次效果使用的执行状态
     */
    public static EffectExecution start(EntityProduct.Experience effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 缺少固定输入属于接线缺失：直接失败，不重新判定
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

    /**
     * 单个经验行为的交付游标，仅由服务端线程访问。
     * 已构造但尚未加入世界的球由交付循环持有：原版构造内的朝向与初速随机已确定，等待区块时不重造。
     */
    private static final class XpDelivery extends DeliveryExecution {
        private final ChunkRequirement chunks;
        private long remaining;
        private boolean feedbackSent;

        private XpDelivery(EffectContext context, long total) {
            super(context);
            this.chunks = ChunkRequirement.around(BlockPos.containing(context.position()), 1);
            this.remaining = total;
        }

        @Override
        protected @Nullable ChunkRequirement declaredRequirement() {
            // 交付结束即不再需要区块；待写经验球所在区块由基类并入声明
            return remaining <= 0 ? null : chunks;
        }

        @Override
        protected Production produce(ServerTickBudget budget) {
            if (remaining <= 0) {
                // 没有待写单位：直接给出终态，不额外写世界
                return new Production.Finished(result());
            }
            int points = (int) Math.min(remaining, MAX_POINTS_PER_ORB);
            Vec3 position = EffectTargets.randomHorizontalDisc(context.random(), context.position(),
                    SCATTER_RADIUS, SPAWN_HEIGHT);
            // award 会再次按面额拆球；直接构造保留原版初速度、拾取和后续同值合并。
            ExperienceOrb orb = new ExperienceOrb(context.level(), position.x, position.y, position.z, points);
            return new Production.Next(new Unit(orb, points));
        }

        @Override
        protected boolean write(Entity entity) {
            // 圆盘可能跨越源区块边界：写入失败只表示目标区块未加载，球留到下次推进
            return EffectTargets.addEntityNow(context, entity, false);
        }

        @Override
        protected void onUnitWritten(Unit unit) {
            // 回执量取这颗球实际携带的点数
            remaining -= unit.appliedUnits();
            if (!feedbackSent) {
                VisualFeedback.entitySpawn(context.level(), context.position());
                feedbackSent = true;
            }
        }

        @Override
        protected int unitsPerStep() {
            // 不足一批时按精确的剩余球数收窄：最后一球写完即在同一次推进里返回终态，不额外让出一步
            return (int) Math.min(ORBS_PER_BATCH, Math.ceilDiv(remaining, MAX_POINTS_PER_ORB));
        }

        @Override
        protected long intervalTicks() {
            // 本步还有额度就继续写，用尽或写满一批后由基类 Yield 到下一 tick
            return 0;
        }

        @Override
        protected boolean isComplete() {
            return remaining <= 0;
        }

        @Override
        protected EffectResult result() {
            // 实际点数已逐球回执，终态不再重复计入产出
            return EffectResult.applied(0, AWARDED);
        }
    }
}
