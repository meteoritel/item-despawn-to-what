package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * loot_table 执行器：在成功转化当刻用真实源实体开表一次并固定产物，随后在共享预算内分批交付。
 * 交付期不再读取源实体、不再打开战利品表，也不再次随机抽取；空表是已完成的空结果。
 * 真实完成量按实际写入世界的物品件数逐堆回执，行为轮数与世界工作量分别表达；
 * 区块未加载时保留已构造的这堆产物，退避后重试，不重抽散点与初速。
 */
public final class LootTableExecutor {

    // 单个调度步骤最多写入的掉落物实体数：行为上界，只限制单步产出量
    private static final int LOOT_DROPS_PER_STEP = 16;
    // 回执说明：同步入口不承担分批，交由预算执行路径推进
    private static final String BUDGET_REQUIRED = "budget_required: loot_table";
    // 回执说明：成功转化当刻没有固定输入，属于运行时接线缺失，不退回到期重新抽取
    private static final String MISSING_PREPARATION = "missing_preparation: loot_table";
    // 回执说明：本次结果已按真实写入逐堆回执，终态不再重复计入
    private static final String DELIVERED = "delivered";
    // 回执说明：战利品表本次开出空结果，属于已完成的空产出
    private static final String EMPTY_RESULT = "empty_result";
    // 产物生成的水平散布半径与抬升高度：与既有产物外观保持一致
    private static final double SCATTER_RADIUS = 0.3;
    private static final double SPAWN_HEIGHT = 0.1;

    /**
     * 成功转化当刻固定的本次战利品产物与落点：只保存触发位置快照与已抽取的堆叠副本。
     * 不保留 {@link LootParams} 与源实体引用，交付期无需也不得再访问原源实体。
     * 堆叠列表在构造时复制成不可变快照，此后由执行状态共享持有；交付只在真正写入世界时按堆复制，
     * 不再为每个执行状态整表深拷贝一次。
     *
     * @param position 成功当刻固定的触发点位置
     * @param drops    本次开表抽取到的非空堆叠，按抽取顺序保存
     */
    public record LootPlan(Vec3 position, List<ItemStack> drops) implements EffectPreparation {

        // 快照隔离：位置复制为不可变值，堆叠逐份复制后收成不可变列表，外部持有者的改动不会污染已定稿产物
        public LootPlan {
            position = new Vec3(position.x, position.y, position.z);
            drops = List.copyOf(drops.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
        }

        /**
         * 交付用的只读快照：列表本身不可变，元素在写入世界时才逐堆复制。
         *
         * @return 本组固定的战利品堆叠，按抽取顺序排列
         */
        @Override
        public List<ItemStack> drops() {
            return drops;
        }
    }

    private LootTableExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 同步入口：本类型的产物必须在成功转化当刻开表固定后再按批次交付，此处不提供一次性完成的执行方式。
     *
     * @param effect  战利品表效果参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    public static EffectResult execute(LootTableEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED);
    }

    /**
     * 在成功转化当刻用真实源实体开表一次，把本次产物固定下来。
     * 源实体只在本次调用内作为 {@code THIS_ENTITY} 参数参与求值，随后会被清空并被移除；
     * 抽取结果逐份复制后随固定输入携带，交付期不再重开战利品表，也不再次随机抽取。
     *
     * @param effect       战利品表效果参数
     * @param level        触发维度，提供战利品表注册表
     * @param position     成功当刻固定的触发点位置
     * @param sourceEntity 成功当刻的源实体，仅在本次调用内作为战利品求值的实体参数
     * @param sourceStack  源实体清空前的只读库存快照；本类型不使用源物品
     * @param fillOrigin   候选的位置策略；本类型不使用
     * @return 本次战利品产物的固定输入
     */
    public static @Nullable EffectPreparation prepare(LootTableEffect effect, ServerLevel level, Vec3 position,
                                                      ItemEntity sourceEntity, ItemStack sourceStack,
                                                      boolean fillOrigin) {
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, effect.lootTable()));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, position)
                .withParameter(LootContextParams.THIS_ENTITY, sourceEntity)
                .withLuck(effect.luck())
                .create(LootContextParamSets.CHEST);
        List<ItemStack> drops = new ArrayList<>();
        lootTable.getRandomItems(params, stack -> {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        });
        return new LootPlan(position, drops);
    }

    /**
     * 按成功转化当刻固定的产物创建交付状态。
     *
     * @param effect      战利品表效果参数
     * @param context     本组执行上下文
     * @param preparation 成功转化当刻固定的输入；null 表示该槽位未固定
     * @return 仅在共享预算内推进的交付状态
     */
    public static EffectExecution start(LootTableEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 迁移到固定输入路径后，缺少固定输入不再退回到期重新抽取
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        }
        if (preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        if (!(preparation instanceof LootPlan plan)) {
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        }
        return new LootDelivery(context, effect.lootTable(), plan);
    }

    /**
     * 战利品交付游标：每批最多 {@link #LOOT_DROPS_PER_STEP} 个掉落物实体，
     * 时间或工作量预算耗尽即让出并保留未交付部分，下一次推进从断点继续。
     * 已构造但尚未加入世界的这一堆产物由交付循环持有，等待区块时不重造散点与初速。
     */
    private static final class LootDelivery extends DeliveryExecution {
        private final ChunkRequirement chunks;
        private final ResourceLocation table;
        private final Vec3 position;
        private final List<ItemStack> drops;
        private int cursor;

        private LootDelivery(EffectContext context, ResourceLocation table, LootPlan plan) {
            super(context);
            this.table = table;
            this.position = plan.position();
            this.drops = plan.drops();
            this.chunks = ChunkRequirement.around(BlockPos.containing(this.position), 1);
        }

        @Override
        protected @Nullable ChunkRequirement declaredRequirement() {
            // 交付结束即不再需要区块；待写堆叠所在区块由基类并入声明
            return isComplete() ? null : chunks;
        }

        @Override
        protected Production produce(ServerTickBudget budget) {
            if (isComplete()) {
                // 没有待写单位：空表与已交付由终态结果区分
                return new Production.Finished(result());
            }
            ItemStack stack = drops.get(cursor);
            ServerLevel level = context.level();
            ItemEntity drop = new ItemEntity(level,
                    position.x + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, position.y + SPAWN_HEIGHT,
                    position.z + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, stack.copy());
            // 初速由源物品被破坏前的运动决定：水平反向，便于捡回抛入的产物
            drop.setDeltaMovement(EffectTargets.productInitialVelocity(context));
            return new Production.Next(new Unit(drop, stack.getCount()));
        }

        @Override
        protected boolean write(Entity entity) {
            // 转化产物：走既有显式产物入口，先授予临时保护与冷却再写入世界；未写入时保留这堆产物
            return EffectTargets.addConversionProductNow(context, (ItemEntity) entity);
        }

        @Override
        protected void onUnitWritten(Unit unit) {
            cursor++;
            if (cursor >= drops.size()) {
                observeDelivered();
            }
        }

        @Override
        protected int unitsPerStep() {
            // 不足一批时按精确的剩余堆数收窄：最后一堆写完即在同一次推进里返回终态，不额外让出一步
            return Math.min(LOOT_DROPS_PER_STEP, drops.size() - cursor);
        }

        @Override
        protected long intervalTicks() {
            // 本步还有额度就继续写，用尽或写满一批后由基类 Yield 到下一 tick
            return 0;
        }

        @Override
        protected boolean isComplete() {
            return cursor >= drops.size();
        }

        @Override
        protected EffectResult result() {
            // 空表是已完成的空产出，与「已交付」区分
            return EffectResult.applied(0, drops.isEmpty() ? EMPTY_RESULT : DELIVERED);
        }

        // 完成前观测：最后一批真实写入之后、终态回执之前上报一次，与原实现同在一次推进内
        private void observeDelivered() {
            if (!RuntimeObserver.current().enabled()) {
                return;
            }
            // 行为轮数与真实产出件数分别记录：轮次是本组的执行轮数，不代表物品件数
            RuntimeObserver.current().observe(context.source(), "LOOT_TABLE_RESULT",
                    "table", table,
                    "drops", drops.size(),
                    "rounds", context.rounds(),
                    "x", position.x,
                    "y", position.y,
                    "z", position.z);
        }
    }
}
