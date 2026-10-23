package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 掉落物产出子类执行器：产出量按物品件数计算，在共享预算内按件数分批写入世界。
 * 产物引用与每轮件数在成功转化当刻固定，执行期不再读原源实体、也不再重新抽取引用。
 * 每个真正写入世界的掉落物按件数回执真实完成量，终态只收束任务。
 */
public final class SpawnItemExecutor {

    /**
     * 单批最多写入的掉落物实体数：行为上界，取值与语义保持不变，只限制单步产出量。
     * 每个真正写入世界的 ItemEntity 按 1 个工作单位回执（见 {@link StepWorkCounter#report(int)}），
     * 故该上界同时是一个调度步骤最多计入的计费单位数。
     */
    private static final int ITEM_SPAWNS_PER_RUN = 16;
    // 回执说明：同步入口不承担分批，交由预算执行路径推进
    private static final String BUDGET_REQUIRED = "budget_required: spawn_entity.item";
    // 回执说明：本次产出已按真实写入逐件回执，终态不再重复计入
    private static final String BATCHED = "batched";
    // 产物生成的水平散布半径与抬升高度：与既有产物外观保持一致
    private static final double SCATTER_RADIUS = 0.3;
    private static final double SPAWN_HEIGHT = 0.1;

    private SpawnItemExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 同步入口：本类型需要按批次在共享预算内续接，不提供一次性完成的执行方式。
     *
     * @param effect  物品产出参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    public static EffectResult execute(EntityProduct.Item effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED);
    }

    /**
     * 按成功转化当刻固定的物品与每轮件数创建交付状态。
     *
     * @param context 本组执行上下文
     * @param item    成功转化当刻已解析的物品
     * @param count   每轮产出件数
     * @return 仅在共享预算内推进的交付状态
     */
    static EffectExecution start(EffectContext context, Item item, int count) {
        return new ItemDelivery(context, item, EffectTargets.saturatedMultiply(count, context.rounds()));
    }

    // 汇总输入已经是已支付组的总件数，保留 long 以免聚合后截断产物。
    static EffectExecution startBatch(EffectContext context, Item item, long count) {
        return new ItemDelivery(context, item, count);
    }

    /** 掉落物的分批交付游标：保留剩余件数和未写入的产物，每批最多 {@link #ITEM_SPAWNS_PER_RUN} 个实体。 */
    private static final class ItemDelivery implements EffectExecution {
        private final EffectContext context;
        private final ChunkRequirement chunks;
        private final Item item;
        private long remaining;
        private ItemEntity pendingDrop;

        private ItemDelivery(EffectContext context, Item item, long count) {
            this.context = context;
            this.chunks = ChunkRequirement.around(BlockPos.containing(context.position()), 1);
            this.item = item;
            this.remaining = count;
        }

        @Override
        public @Nullable ChunkRequirement requiredChunks() {
            if (remaining <= 0) { return null; }
            return pendingDrop == null ? chunks : chunks.including(pendingDrop.blockPosition());
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            if (remaining <= 0) {
                return EffectStep.done(EffectResult.applied(0, BATCHED));
            }
            ServerLevel level = context.level();
            Vec3 position = context.position();
            int maxStack = Math.max(1, item.getDefaultMaxStackSize());
            // 本步已报告但尚未由调度器计费的额度：单步内 remainingWorkUnits 不会随上报立即减少，需自行扣除
            int reportedUnits = 0;
            int written = 0;
            while (written < ITEM_SPAWNS_PER_RUN && remaining > 0) {
                // 每件写入前都守一次：时间或本步额度（含已报告未计费部分）耗尽即让出，剩余件数保留到下次推进
                if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                    return EffectStep.Yield.INSTANCE;
                }
                int size = (int) Math.min(remaining, maxStack);
                if (pendingDrop == null) {
                    pendingDrop = new ItemEntity(level,
                            position.x + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, position.y + SPAWN_HEIGHT,
                            position.z + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, new ItemStack(item, size));
                    // 初速由源物品被破坏前的运动决定：水平反向，便于捡回抛入的产物
                    pendingDrop.setDeltaMovement(EffectTargets.productInitialVelocity(context));
                }
                // 转化产物：先授予保护与冷却再写入；未写入时不计完成量，也不重复授予
                if (!EffectTargets.addConversionProductNow(context, pendingDrop)) {
                    return new EffectStep.AwaitChunks(ChunkRequirement.at(pendingDrop.blockPosition()));
                }
                pendingDrop = null;
                // 真实完成量回执：按实际写入世界的件数计
                context.reportProgress(size);
                // 计价：一个真正写入世界的实体记 1 个工作单位
                StepWorkCounter.report(1);
                reportedUnits++;
                written++;
                remaining -= size;
            }
            if (remaining > 0) {
                return EffectStep.Yield.INSTANCE;
            }
            return EffectStep.done(EffectResult.applied(0, BATCHED));
        }

        @Override
        public void onFinished(EffectResult result) {
            // 交付完成或已失败：不再持有剩余件数，取消路径也不补做
            remaining = 0;
            pendingDrop = null;
        }
    }
}
