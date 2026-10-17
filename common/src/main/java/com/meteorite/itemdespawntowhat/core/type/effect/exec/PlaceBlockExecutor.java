package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 方块效果的执行器：在共享预算内分批完成邻域计数与放置，形状偏移按参数缓存复用。
 * 单个调度步骤最多处理的方块位置数同时是计费上界；区块未加载时保留游标并退避等待，不从候选集合中丢弃位置。
 * 每成功放置一个方块通过 {@link EffectContext#reportProgress(int)} 回执 1，真实放置量以逐块回执累加；
 * 终态回执只收束任务，不再重复计入产量。
 * 放置策略：只替换可替换方块（canBeReplaced），不满足目标状态合法放置的位置只跳过并继续下一个候选，
 * 不为凑数突破自身 limit/radius；候选级 fill_origin 关闭时不尝试触发位置本身。
 */
public final class PlaceBlockExecutor {
    private static final Map<ShapeKey, List<BlockPos>> OFFSETS = new HashMap<>();
    /**
     * 单步最多处理的方块位置数：计数段与放置段共享同一上界，取值与语义保持不变，只限制本步处理量。
     * 实际单步上界取本值与 {@link ServerTickBudget#remainingWorkUnits()} 的较小者，且本 tick 时间或工作量任一耗尽即让出；
     * 每个已处理位置按 1 个工作单位回执（见 {@link StepWorkCounter#report(int)}），
     * 因此该上界同时决定一个调度步骤最多向本 tick 预算计入多少工作单位。
     */
    private static final int MAX_VISITS_PER_STEP = 128;
    /** 区块未加载时的退避刻数：保留游标与待处理位置，等待区块加载后从断点续接。 */
    private static final int CHUNK_RETRY_TICKS = 20;
    /** 无预算入口的直接执行回执原因：本类型必须经共享预算推进。 */
    private static final String BUDGET_REQUIRED = "budget_required";
    /** 终态回执说明：真实放置量由逐块进度回执累加，终态不再重复计数。 */
    private static final String PLACED_BY_STEPS = "placed_by_steps";
    /** 缺少本类型固定输入时的失败原因：到期不得用活源重新抽取，直接以明确失败收束。 */
    private static final String MISSING_PREPARATION = "missing_preparation";
    /** 固定输入里没有解析出目标方块时的兜底失败原因（解析异常本身不带消息时使用）。 */
    private static final String PLACE_TARGET_UNRESOLVED = "place_target_unresolved";
    /** 不含维度引用的形状缓存键；起点填充开关会改变候选集合，须参与缓存键。 */
    private record ShapeKey(PlaceBlockEffect.Shape shape, int radius, boolean fillOrigin) {}
    /**
     * 本类型在成功转化当刻固定的输入：目标方块、候选偏移、limit 上界与解析失败原因。
     * 前三项只由效果参数与成功转化当刻的源栈快照决定，一次求值后不再随执行时机或源实体状态改变；
     * 作为 {@link EffectPreparation} 的类型私有负载随任务携带，执行入口按类型取回，不做通用容器。
     * 目标方块解析失败时保留原因，由该槽位真正执行时产出失败回执：准备阶段会遍历未被选中的候选与尚未启动的组，
     * 那里的解析失败不应影响其它候选。
     * @param block 已解析的目标方块；解析失败时为 null
     * @param candidates 由形状、半径与起点开关决定的候选偏移，按层序排列
     * @param limit 邻域已存在同类方块的数量上限；null 表示不限
     * @param failure 目标方块解析失败原因；null 表示解析成功
     */
    public record PlacementPlan(@Nullable Block block, List<BlockPos> candidates, @Nullable Integer limit,
                                @Nullable String failure) implements EffectPreparation {}
    private PlaceBlockExecutor() {}

    /**
     * 拒绝没有共享预算的直接执行：本类型必须按预算分批推进，不能一次同步完成。
     * @param effect 方块效果参数
     * @param context 本组上下文
     * @return 表示必须使用预算入口的失败回执，不放置任何方块
     */
    public static EffectResult execute(PlaceBlockEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 在成功转化当刻一次性固定本次放置的输入：目标方块、候选偏移与 limit。
     * tag 引用在此刻取定成员，use_source_block 只读传入的源物品栈快照，因此源实体清空后不再影响结果。
     * 只读注册表与效果参数，不读取任何需要等待的世界状态。
     * @param effect 方块效果参数
     * @param level 触发维度，提供注册表随机源
     * @param sourceStack 成功转化当刻的源物品栈快照
     * @param fillOrigin 候选级起点填充开关
     * @return 本次放置的固定输入
     */
    public static PlacementPlan plan(PlaceBlockEffect effect, ServerLevel level,
                                     ItemStack sourceStack, boolean fillOrigin) {
        List<BlockPos> candidates = offsets(effect, fillOrigin);
        try {
            Block block = effect.block() != null
                    ? EffectTargets.resolveOrThrow(effect.block(), BuiltInRegistries.BLOCK, level.random, PlaceBlockEffect.BLOCK_FIELD)
                    : sourceBlock(effect, sourceStack);
            return new PlacementPlan(block, candidates, effect.limit(), null);
        } catch (IllegalStateException failure) {
            // 引用在当前注册表已无法解析：失败原因随固定输入携带，等该槽位执行时再给失败回执
            return new PlacementPlan(null, candidates, effect.limit(), failure.getMessage());
        }
    }

    /**
     * 无固定输入的执行入口：本类型的目标方块必须由准备阶段按组固定，此处只给出明确失败。
     * @param effect 方块效果参数
     * @param context 本组上下文
     * @return 表示缺少固定输入的失败执行状态
     */
    public static EffectExecution start(PlaceBlockEffect effect, EffectContext context) {
        return start(effect, context, null);
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态：计划上限按本组已付轮数缩放并受候选位置数封顶，执行期不再重新抽取。
     * 已判定跳过的固定输入直接给出该原因的回执，不重判条件与概率，也不做世界写入。
     * 缺少本类型固定输入时不回退到到期解析：目标方块与候选只在准备阶段固定，重抽会改变结果且此时源实体已清空。
     * @param effect 方块效果参数
     * @param context 本组上下文
     * @param preparation 本组在成功转化当刻固定的输入；null 表示未固定
     * @return 可跨 tick 续接的放置执行状态，或缺少固定输入时的失败状态
     */
    public static EffectExecution start(PlaceBlockEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation != null && preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectResult.skipped(reason);
        }
        if (!(preparation instanceof PlacementPlan plan)) {
            return budget -> EffectResult.failed(MISSING_PREPARATION + ": " + effect.type());
        }
        if (plan.block() == null) {
            String failure = plan.failure() == null ? PLACE_TARGET_UNRESOLVED : plan.failure();
            return budget -> EffectResult.failed(failure);
        }
        return new Placement(effect, context, plan);
    }

    // 源物品对应的方块：不同物品栈快照与同一效果参数的组合已由固定输入承载
    private static Block sourceBlock(PlaceBlockEffect effect, ItemStack sourceStack) {
        Item item = sourceStack.getItem();
        Block block = Block.byItem(item);
        if (!effect.useSourceBlock() || block == Blocks.AIR) {
            throw new IllegalStateException("源物品没有可放置的方块: " + BuiltInRegistries.ITEM.getKey(item));
        }
        return block;
    }

    // 每个坐标只生成一次，按由内向外的层序排序；避免每次转化创建各层列表。
    private static List<BlockPos> offsets(PlaceBlockEffect effect, boolean fillOrigin) {
        return OFFSETS.computeIfAbsent(new ShapeKey(effect.shape(), effect.radius(), fillOrigin), key -> {
            List<BlockPos> result = new ArrayList<>();
            for (int x = -key.radius; x <= key.radius; x++) {
                for (int z = -key.radius; z <= key.radius; z++) {
                    // 起点填充关闭时跳过触发位置本身，其余候选与顺序保持不变
                    if (!key.fillOrigin && x == 0 && z == 0) { continue; }
                    if (key.shape == PlaceBlockEffect.Shape.CROSS && x != 0 && z != 0) { continue; }
                    if (key.shape == PlaceBlockEffect.Shape.CIRCLE && Math.hypot(x, z) >= key.radius + 1) { continue; }
                    result.add(new BlockPos(x, 0, z));
                }
            }
            result.sort(Comparator.comparingInt((BlockPos pos) -> key.shape == PlaceBlockEffect.Shape.CIRCLE
                    ? (int) Math.hypot(pos.getX(), pos.getZ()) : Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())))
                    .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
            return List.copyOf(result);
        });
    }

    /**
     * 计数与放置的持续游标：每步最多处理 {@link #MAX_VISITS_PER_STEP} 个方块位置，并在本 tick 时间或工作量耗尽时让出。
     * 每个已处理位置向本 tick 预算回执 1 个工作单位：计数段位置为一次方块读取，放置段位置含读取、放置合法性与 setBlock 写入。
     * 未完成时 {@code advance} 返回 null 让出本刻，下一次推进从保留的游标继续；区块未加载只推迟推进时机，不改变计划量。
     */
    private static final class Placement implements EffectExecution {
        private final EffectContext context;
        private final Block block;
        private final BlockState state;
        private final BlockPos center;
        private final List<BlockPos> offsets;
        private final Iterator<BlockPos> countPositions;
        private final int limit;
        private BlockPos pendingCount;
        private int existing;
        private int cursor;
        private int placed;
        private int target;
        private boolean counting;
        private long readyAt = Long.MIN_VALUE;
        private @Nullable EffectResult result;
        private Placement(PlaceBlockEffect effect, EffectContext context, PlacementPlan plan) {
            this.context = context;
            this.block = plan.block();
            this.state = plan.block().defaultBlockState();
            this.center = BlockPos.containing(context.position());
            this.offsets = plan.candidates();
            // 计划上限：count × 本组已付轮数，再受候选位置数封顶；两项都不含随机抽取
            this.target = Math.min(EffectTargets.saturatedMultiply(effect.count(), context.rounds()), plan.candidates().size());
            int radius = effect.radius();
            this.countPositions = BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius)).iterator();
            this.counting = plan.limit() != null;
            this.limit = plan.limit() == null ? Integer.MAX_VALUE : plan.limit();
        }

        @Override
        public @Nullable EffectResult advance(ServerTickBudget budget) {
            if (result != null) {
                return result;
            }
            long now = context.level().getGameTime();
            // 区块退避：未到解禁刻直接让出，游标与待处理位置原样保留
            if (now < readyAt) {
                return null;
            }
            int visits = 0;
            // 本步已上报但尚未由调度器计入预算的工作量，用于把单步处理量夹在本 tick 余量内
            int reported = 0;
            while (counting) {
                if (visits >= MAX_VISITS_PER_STEP || reported >= budget.remainingWorkUnits() || budget.exhausted()) {
                    return null;
                }
                if (pendingCount == null && countPositions.hasNext()) { pendingCount = countPositions.next().immutable(); }
                if (pendingCount == null || existing >= limit) {
                    target = Math.clamp(target, 0, limit - existing);
                    counting = false;
                    break;
                }
                if (!LoadedChunks.contains(context.level(), pendingCount)) {
                    readyAt = now + CHUNK_RETRY_TICKS;
                    return null;
                }
                visits++;
                reported++;
                // 计价：一次方块读取记 1 个工作单位，循环上界即单个调度步骤的计费上界
                StepWorkCounter.report(1);
                if (context.level().getBlockState(pendingCount).is(block)) { existing++; }
                pendingCount = null;
            }
            while (cursor < offsets.size() && placed < target) {
                if (visits >= MAX_VISITS_PER_STEP || reported >= budget.remainingWorkUnits() || budget.exhausted()) {
                    return null;
                }
                BlockPos pos = center.offset(offsets.get(cursor));
                // canSurvive 可能读取相邻方块，边界两侧都须已加载。
                if (!LoadedChunks.contains(context.level(), pos.offset(-1, 0, -1)) || !LoadedChunks.contains(context.level(), pos.offset(1, 0, 1))) {
                    readyAt = now + CHUNK_RETRY_TICKS;
                    return null;
                }
                cursor++;
                visits++;
                reported++;
                // 计价：一个候选位置的读取与放置尝试记 1 个工作单位
                StepWorkCounter.report(1);
                if (context.level().getBlockState(pos).canBeReplaced() && state.canSurvive(context.level(), pos)
                        && context.level().setBlock(pos, state, Block.UPDATE_ALL)) {
                    placed++;
                    // 真实完成量回执：每成功放置一个方块计 1
                    context.reportProgress(1);
                }
            }
            // 放置结束：记录真实成功数、计划上限与候选数，便于验收与排查半径不足
            if (RuntimeObserver.current().enabled()) {
                RuntimeObserver.current().observe(context.source(), "PLACE_BLOCK_RESULT",
                        "candidates", offsets.size(), "placed", placed, "target", target);
            }
            result = EffectResult.applied(0, PLACED_BY_STEPS);
            return result;
        }
    }
}