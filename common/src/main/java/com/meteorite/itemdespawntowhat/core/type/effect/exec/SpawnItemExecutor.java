package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 掉落物产出子类执行器：产出量按物品件数计算，在共享预算内按件数分批写入世界。
 * 产物引用与每轮件数在成功转化当刻固定，执行期只读固定产物，不访问源实体、不重新抽取引用。
 * 每个真正写入世界的掉落物按件数回执真实完成量，终态只收束任务。
 */
public final class SpawnItemExecutor {

    /**
     * 单批最多写入的掉落物实体数：行为上界，只限制单步产出量。
     * 每个真正写入世界的 ItemEntity 由交付循环按 1 个工作单位计费，
     * 故该上界同时是一个调度步骤最多计入的计费单位数。
     */
    private static final int ITEM_SPAWNS_PER_RUN = 16;
    // 回执说明：同步入口不承担分批，交由预算执行路径推进
    private static final String BUDGET_REQUIRED = "budget_required: spawn_entity.item";
    // 回执说明：本次产出已按真实写入逐件回执，终态不再重复计入
    private static final String BATCHED = "batched";
    // 产物生成的水平散布半径与抬升高度
    private static final double SCATTER_RADIUS = 0.3;
    private static final double SPAWN_HEIGHT = 0.1;

    private SpawnItemExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 同步入口：掉落物产出按批次在共享预算内续接，由预算执行路径推进。
     *
     * @param effect  物品产出参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    // 形参由效果类型的分发签名固定，同步入口不读取其中任何参数
    public static EffectResult execute(@SuppressWarnings("unused") EntityProduct.Item effect,
                                       @SuppressWarnings("unused") EffectContext context) {
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

    /**
     * 掉落物的分批交付游标：保留剩余件数，每批最多 {@link #ITEM_SPAWNS_PER_RUN} 个实体。
     * 待写实体由交付循环持有，本类只描述每叠产物怎么造、怎么写入与完成量口径。
     */
    private static final class ItemDelivery extends DeliveryExecution {
        private final Item item;
        private final ChunkRequirement chunks;
        private final int maxStack;
        private long remaining;

        private ItemDelivery(EffectContext context, Item item, long count) {
            super(context);
            this.item = item;
            this.chunks = ChunkRequirement.around(BlockPos.containing(context.position()), 1);
            // 单件堆叠上限是物品的固定属性：一次算定，产出与单步收窄共用同一口径
            this.maxStack = Math.max(1, item.getDefaultMaxStackSize());
            this.remaining = count;
        }

        @Override
        protected @Nullable ChunkRequirement declaredRequirement() {
            // 交付结束即不再需要区块；待写实体所在区块由基类并入声明
            return remaining <= 0 ? null : chunks;
        }

        @Override
        protected Production produce(StepBudget budget) {
            if (remaining <= 0) {
                // 没有待写单位：直接给出终态，不额外写世界
                return new Production.Finished(result());
            }
            int size = (int) Math.min(remaining, maxStack);
            ServerLevel level = context.level();
            Vec3 position = context.position();
            ItemEntity drop = new ItemEntity(level,
                    position.x + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, position.y + SPAWN_HEIGHT,
                    position.z + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, new ItemStack(item, size));
            // 初速由源物品被破坏前的运动决定：水平反向，便于捡回抛入的产物
            drop.setDeltaMovement(EffectTargets.productInitialVelocity(context));
            return new Production.Next(new Unit(drop, size));
        }

        @Override
        protected boolean write(Entity entity) {
            // 转化产物：先授予保护与冷却再写入；未写入时不计完成量，也不重复授予
            return EffectTargets.addConversionProductNow(context, (ItemEntity) entity);
        }

        @Override
        protected void onUnitWritten(Unit unit) {
            // 完成量就是这一叠实际写入世界的件数
            remaining -= unit.appliedUnits();
        }

        @Override
        protected int unitsPerStep() {
            // 不足一批时按精确的剩余实体数收窄：最后一件写完即在同一次推进里返回终态，不额外让出一步
            return (int) Math.min(ITEM_SPAWNS_PER_RUN, Math.ceilDiv(remaining, maxStack));
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
            // 件数已逐叠回执，终态只收束任务
            return EffectResult.applied(0, BATCHED);
        }
    }
}
