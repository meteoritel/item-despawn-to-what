package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * loot_table 执行器：在成功转化当刻用真实源实体开表一次并固定产物，随后在共享预算内分批交付。
 * 交付期不再读取源实体、不再打开战利品表，也不再次随机抽取；空表是已完成的空结果。
 * 真实完成量按实际写入世界的物品件数逐堆回执，行为轮数与世界工作量分别表达；
 * 区块未加载时保留已构造的这堆产物，退避后重试，不重抽散点与初速。
 */
public final class LootTableExecutor {

    // 单个调度步骤最多写入的掉落物实体数：行为上界，只限制单步产出量
    private static final int LOOT_DROPS_PER_STEP = 16;
    // 计价：一个真正写入世界的掉落物实体记 1 个工作单位
    private static final int WORK_UNITS_PER_DROP = 1;
    // 邻区块未加载时的重试间隔：不在等待期反复查世界，也不提前记为成功
    private static final int CHUNK_RETRY_TICKS = 20;
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
            return budget -> EffectResult.failed(MISSING_PREPARATION);
        }
        if (preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectResult.skipped(reason);
        }
        if (!(preparation instanceof LootPlan plan)) {
            return budget -> EffectResult.failed(MISSING_PREPARATION);
        }
        return new LootDelivery(context, effect.lootTable(), plan);
    }

    /**
     * 战利品交付游标：每批最多 {@link #LOOT_DROPS_PER_STEP} 个掉落物实体，
     * 时间或工作量预算耗尽即让出并保留未交付部分，下一次推进从断点继续。
     */
    private static final class LootDelivery implements EffectExecution {
        private final EffectContext context;
        private final ResourceLocation table;
        private final Vec3 position;
        private final List<ItemStack> drops;
        private final BlockPos center;
        private int cursor;
        // 已构造但尚未加入世界的这一堆产物：散布位置与初速已确定，等待区块时不重造
        private ItemEntity pendingDrop;
        // 邻区块未加载时的下次重试刻，0 表示不等待
        private long nextAttemptTick;

        private LootDelivery(EffectContext context, ResourceLocation table, LootPlan plan) {
            this.context = context;
            this.table = table;
            this.position = plan.position();
            this.drops = plan.drops();
            this.center = BlockPos.containing(this.position);
        }

        @Override
        public @Nullable EffectResult advance(ServerTickBudget budget) {
            if (drops.isEmpty()) {
                // 空表是已完成的空产出：不写世界、不回执件数，用独立结果与「已交付」区分
                return EffectResult.applied(0, EMPTY_RESULT);
            }
            if (cursor >= drops.size()) {
                return EffectResult.applied(0, DELIVERED);
            }
            ServerLevel level = context.level();
            long gameTime = level.getGameTime();
            if (gameTime < nextAttemptTick) {
                return null;
            }
            // 掉落物散布半径小于一格，邻区块就绪即可写入
            if (!LoadedChunks.containsArea(level, center, 1)) {
                nextAttemptTick = gameTime + CHUNK_RETRY_TICKS;
                return null;
            }
            int written = 0;
            // 本步已报告但尚未由调度器计费的额度：单步内 remainingWorkUnits 不会随上报立即减少，需自行扣除
            int reportedUnits = 0;
            while (written < LOOT_DROPS_PER_STEP && cursor < drops.size()) {
                // 每堆写入前都守一次：时间或本步额度（含已报告未计费部分）耗尽即让出，未交付部分保留到下次推进
                if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                    return null;
                }
                ItemStack stack = drops.get(cursor);
                if (pendingDrop == null) {
                    pendingDrop = new ItemEntity(level,
                            position.x + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, position.y + SPAWN_HEIGHT,
                            position.z + (level.random.nextDouble() - 0.5) * SCATTER_RADIUS, stack.copy());
                    // 初速由源物品被破坏前的运动决定：水平反向，便于捡回抛入的产物
                    pendingDrop.setDeltaMovement(EffectTargets.productInitialVelocity(context));
                }
                // 转化产物：走既有显式产物入口，先授予临时保护与冷却再写入世界。
                // 未写入时保留这堆产物，退避后重试，不重造散点与初速。
                if (!EffectTargets.addConversionProductNow(context, pendingDrop)) {
                    nextAttemptTick = gameTime + CHUNK_RETRY_TICKS;
                    return null;
                }
                pendingDrop = null;
                // 真实完成量回执：按本次实际写入世界的件数计，轮次不在此处放大
                context.reportProgress(stack.getCount());
                StepWorkCounter.report(WORK_UNITS_PER_DROP);
                reportedUnits++;
                written++;
                cursor++;
            }
            if (cursor < drops.size()) {
                return null;
            }
            if (RuntimeObserver.current().enabled()) {
                // 行为轮数与真实产出件数分别记录：轮次是本组的执行轮数，不代表物品件数
                RuntimeObserver.current().observe(context.source(), "LOOT_TABLE_RESULT",
                        "table", table,
                        "drops", drops.size(),
                        "rounds", context.rounds(),
                        "x", position.x,
                        "y", position.y,
                        "z", position.z);
            }
            return EffectResult.applied(0, DELIVERED);
        }

        @Override
        public void onFinished(EffectResult result) {
            // 正常完成或失败：不再持有未交付部分与已构造未写入的产物。
            // 取消不经过本回调，由任务摘掉执行状态后不再推进
            cursor = drops.size();
            pendingDrop = null;
        }
    }
}
