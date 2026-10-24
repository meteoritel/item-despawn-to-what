package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkPresence;
import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * 方块效果的执行器：在共享预算内分批完成邻域计数与放置，形状偏移按参数缓存复用。
 * 需要按 limit 计数时，同候选同槽位的各组共用一份邻域账：整次转化只扫一遍方框，
 * 并按区段调色板跳过确定不含目标方块的区段；计数完成前各组都只推进计数、不放置。
 * 同目标的多组共享一个候选游标；区段探测与位置读取共用单步工作量上界，
 * 区块未加载时保留游标并退避等待，不从候选集合中丢弃位置。
 * 每成功放置一个方块通过 {@link EffectContext#reportProgress(int)} 回执 1，真实放置量以逐块回执累加；
 * 终态回执只收束任务，不再重复计入产量。
 * 放置策略：只替换可替换方块（canBeReplaced），并按原版支撑规则为每个候选位置摆正朝向——
 * 先试默认状态，再依次尝试贴地、贴天花板与四向贴墙，取第一个能站稳的朝向；都站不住的位置只跳过并继续下一个候选，
 * 不为凑数突破自身 limit/radius；候选级 fill_origin 关闭时不尝试触发位置本身。
 */
public final class PlaceBlockExecutor {
    private static final Map<ShapeKey, List<BlockPos>> OFFSETS = new HashMap<>();
    /**
     * 单步工作量上界：区段探测、计数位置读取与放置尝试共同计费。
     * 实际单步上界取本值与 {@link ServerTickBudget#remainingWorkUnits()} 的较小者，且本 tick 时间或工作量任一耗尽即让出；
     * 每次区段探测或位置处理按 1 个工作单位回执（见 {@link StepWorkCounter#report(int)}），
     * 因此该上界同时决定一个调度步骤最多向本 tick 预算计入多少工作单位。
     */
    private static final int MAX_VISITS_PER_STEP = 128;
    /** 朝向尝试使用的水平方向顺序：固定顺序让同一位置的摆放结果可复现。 */
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** 朝向类属性在方块状态里的固定名字：面朝向（贴地/贴墙/贴天花板）与水平朝向。 */
    private static final String FACE_PROPERTY = "face";
    private static final String FACING_PROPERTY = "facing";
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
     */
    public static class PlacementPlan implements EffectPreparation.GroupedPreparation {
        private final @Nullable Block block;
        private final List<BlockPos> candidates;
        private final @Nullable Integer limit;
        private final @Nullable String failure;
        private @Nullable Map<Block, NeighbourCount> counts;

        PlacementPlan(@Nullable Block block, List<BlockPos> candidates, @Nullable Integer limit,
                      @Nullable String failure) {
            this.block = block;
            this.candidates = candidates;
            this.limit = limit;
            this.failure = failure;
        }

        /** 已解析的目标方块；解析失败时为 null。 */
        public @Nullable Block block() {
            return block;
        }

        /** 由形状、半径与起点开关决定的候选偏移，按层序排列。 */
        public List<BlockPos> candidates() {
            return candidates;
        }

        /** 邻域已存在同类方块的数量上限；null 表示不限。 */
        public @Nullable Integer limit() {
            return limit;
        }

        /** 目标方块解析失败原因；null 表示解析成功。 */
        public @Nullable String failure() {
            return failure;
        }

        /** 同一槽位按实际抽取的方块共享邻域账，各组仍保留自己的随机目标。 */
        @Override
        public void shareStateWith(EffectPreparation.GroupedPreparation first) {
            if (first instanceof PlacementPlan plan) {
                if (plan.counts == null) {
                    plan.counts = new HashMap<>();
                }
                counts = plan.counts;
            }
        }

        // 邻域账按实际目标方块区分；同类方块跨组只有一份余额。
        @Nullable NeighbourCount sharedCount(BlockPos center, int radius, long target) {
            long span = radius * 2L + 1L;
            long cubePositions = span * span * span;
            // 所有放置位置均在统计方框内；limit 覆盖其总容量时，已有量与新增量之和天然不超限。
            if (target <= 0 || limit == null || limit >= cubePositions) {
                return null;
            }
            if (counts == null) {
                counts = new HashMap<>();
            }
            return counts.computeIfAbsent(block, ignored -> new NeighbourCount(limit, center, radius));
        }
    }

    /**
     * 可跨组共享的固定输入：同一候选同一槽位的各组拿到同一份实例，于是「方框内已有同类方块」只数一遍，
     * limit 余额（含本次已放量）也只有一份账，limit 因此是该方框内所有组共同的上限。
     * 只用于求值不含按组独立随机结果的输入：直接引用与源物品映射都在转化当刻确定；
     * 标签引用按组独立取定成员、固定输入各自保留，邻域账仍按实际目标方块共享。
     */
    private static final class SharedPlacementPlan extends PlacementPlan
            implements EffectPreparation.SharedPreparation {
        private SharedPlacementPlan(@Nullable Block block, List<BlockPos> candidates, @Nullable Integer limit) {
            super(block, candidates, limit, null);
        }
    }

    /**
     * 同一候选槽位、同一目标方块的结果预算批次。各组的概率与目标已固定，
     * 共享一次位置扫描，每组仍按自己的到期刻执行并回执自己的成本进度。
     */
    public static final class BatchPlan implements EffectPreparation {
        private final PlacementPlan plan;
        private final List<Request> requests = new ArrayList<>();
        private long resultBudget;

        /** 创建一个尚未排期的批次，固定形状、目标与环境上限。 */
        public BatchPlan(PlacementPlan plan) {
            this.plan = plan;
        }

        /** 添加一组已通过概率判定的结果预算；仅在任务排期前调用。 */
        public void add(int count, EffectContext context, long readyTick) {
            requests.add(new Request(count, context, readyTick));
            resultBudget = Math.addExact(resultBudget, count);
        }
    }

    private record Request(int count, EffectContext context, long readyTick) {}

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
     * 求值不含按组随机取定的输入声明为可共享（见 {@link EffectPreparation.SharedPreparation}），由派发阶段在组间复用首份。
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
            // 标签引用按随机源取定成员、各组各自取定，因此只有直接引用与源物品映射的输入可跨组共享
            return effect.block() == null || !effect.block().tag()
                    ? new SharedPlacementPlan(block, candidates, effect.limit())
                    : new PlacementPlan(block, candidates, effect.limit(), null);
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
     * 已判定跳过的固定输入直接给出该原因的回执，不重判概率，也不做世界写入。
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
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        if (preparation instanceof BatchPlan batch) {
            return new Placement(effect, context, batch.plan, batch);
        }
        if (!(preparation instanceof PlacementPlan plan)) {
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION + ": " + effect.type()));
        }
        if (plan.block() == null) {
            String failure = plan.failure() == null ? PLACE_TARGET_UNRESOLVED : plan.failure();
            return budget -> EffectStep.done(EffectResult.failed(failure));
        }
        return new Placement(effect, context, plan, null);
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

    // 按属性名取同名属性：朝向类属性在方块状态里名字固定（face / facing），其余方块保持默认状态
    private static @Nullable Property<?> propertyNamed(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    // 泛型擦除后的属性赋值：取值不在该属性合法集合里时返回 null，由调用方换下一个朝向
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable BlockState withValue(BlockState state, Property property, Object value) {
        if (!property.getPossibleValues().contains(value)) {
            return null;
        }
        return state.setValue(property, (Comparable) value);
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
     * 放置的持续游标：每步最多读取 {@link #MAX_VISITS_PER_STEP} 个方块位置，并在本 tick 时间或工作量耗尽时让出。
     * 每个已处理位置向本 tick 预算回执 1 个工作单位：计数段位置为一次方块读取，放置段位置含读取、放置合法性与 setBlock 写入。
     * 未完成时返回续接或区块需求，下一次推进从保留的游标继续；区块未加载不改变计划量。
     * 需要计数时计数游标与已数/已放量都在共享账上（见 {@link NeighbourCount}），本类只持有自己的放置游标。
     */
    private static final class Placement implements EffectExecution {
        private final EffectContext context;
        private final Block block;
        private final BlockPos center;
        private final ChunkRequirement originChunks;
        private final List<BlockPos> offsets;
        private final @Nullable NeighbourCount count;
        private int cursor;
        private long placed;
        private final long target;
        private final @Nullable BatchPlan batch;
        private int requestIndex;
        private int requestPlaced;
        private @Nullable EffectResult result;
        private Placement(PlaceBlockEffect effect, EffectContext context, PlacementPlan plan, @Nullable BatchPlan batch) {
            this.context = context;
            this.block = plan.block();
            this.center = BlockPos.containing(context.position());
            this.originChunks = ChunkRequirement.at(center);
            this.offsets = plan.candidates();
            // 结果预算独立于环境容量，半径耗尽只终止扫描，不抹掉未消耗的结果预算。
            this.batch = batch;
            this.target = batch == null ? (long) effect.count() * context.rounds() : batch.resultBudget;
            this.count = plan.sharedCount(center, effect.radius(), target);
        }

        // 结果预算与共享环境余额分别检查；共享余额已经扣除了本批成功量。
        private boolean canPlaceMore() {
            return placed < target && (count == null || count.allowance() > 0);
        }

        // 为候选位置挑一个能站稳的朝向：默认状态站不住时，按原版支撑规则依次尝试地面、天花板与四向贴墙
        private @Nullable BlockState orientedState(BlockPos pos) {
            ServerLevel level = context.level();
            BlockState base = block.defaultBlockState();
            if (base.canSurvive(level, pos)) {
                return base;
            }
            Property<?> face = propertyNamed(base, FACE_PROPERTY);
            Property<?> facing = propertyNamed(base, FACING_PROPERTY);
            return face != null
                    ? faceAttachedState(level, pos, base, face, facing)
                    : facedState(level, pos, base, facing);
        }

        // 面朝向方块（按钮、拉杆等）：先贴地，再贴天花板，最后四向贴墙且朝向背对支撑方块
        private static @Nullable BlockState faceAttachedState(ServerLevel level, BlockPos pos, BlockState base,
                                                              Property<?> face, @Nullable Property<?> facing) {
            if (isFaceSturdy(level, pos.below(), Direction.UP)) {
                BlockState floor = withValue(base, face, AttachFace.FLOOR);
                if (floor != null && floor.canSurvive(level, pos)) {
                    return floor;
                }
            }
            if (isFaceSturdy(level, pos.above(), Direction.DOWN)) {
                BlockState ceiling = withValue(base, face, AttachFace.CEILING);
                if (ceiling != null && ceiling.canSurvive(level, pos)) {
                    return ceiling;
                }
            }
            if (facing == null) {
                return null;
            }
            BlockState wall = withValue(base, face, AttachFace.WALL);
            if (wall == null) {
                return null;
            }
            for (Direction direction : HORIZONTALS) {
                if (!isFaceSturdy(level, pos.relative(direction), direction.getOpposite())) {
                    continue;
                }
                BlockState attached = withValue(wall, facing, direction.getOpposite());
                if (attached != null && attached.canSurvive(level, pos)) {
                    return attached;
                }
            }
            return null;
        }

        // 只有水平朝向的方块（梯子等）：逐方向尝试，由 canSurvive 决定哪个方向贴着支撑方块
        private static @Nullable BlockState facedState(ServerLevel level, BlockPos pos, BlockState base,
                                                       @Nullable Property<?> facing) {
            if (facing == null) {
                return null;
            }
            for (Direction direction : HORIZONTALS) {
                BlockState candidate = withValue(base, facing, direction);
                if (candidate != null && candidate.canSurvive(level, pos)) {
                    return candidate;
                }
            }
            return null;
        }

        // 与 canSurvive 同源的支撑判定：指定方块在指定面上是否足够坚固
        private static boolean isFaceSturdy(ServerLevel level, BlockPos pos, Direction direction) {
            return level.getBlockState(pos).isFaceSturdy(level, pos, direction);
        }

        @Override
        public ChunkRequirement requiredChunks() {
            if (count != null && count.counting()) {
                BlockPos pending = count.pending;
                return pending == null || context.level().isOutsideBuildHeight(pending.getY())
                        ? originChunks : ChunkRequirement.at(pending);
            }
            return cursor < offsets.size() && canPlaceMore()
                    ? ChunkRequirement.around(center.offset(offsets.get(cursor)), 1) : originChunks;
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            if (result != null) {
                return EffectStep.done(result);
            }
            int visits = 0;
            // 本步已上报但尚未由调度器计入预算的工作量，用于把单步处理量夹在本 tick 余量内
            int reported = 0;
            if (count != null && count.counting()) {
                // 计数由先走到这里的一组推进共享账：数完之前任何组都不放置，避免边数边放突破 limit
                CountStep counted = count.advance(context.level(), block, budget, MAX_VISITS_PER_STEP - visits);
                visits += counted.reads();
                reported += counted.reads();
                if (counted.awaiting() != null) {
                    return new EffectStep.AwaitChunks(counted.awaiting());
                }
                if (count.counting()) {
                    return EffectStep.Yield.INSTANCE;
                }
            }
            while (cursor < offsets.size() && canPlaceMore()) {
                if (batch != null && batch.requests.get(requestIndex).readyTick() > budget.gameTime()) {
                    return new EffectStep.Delay(batch.requests.get(requestIndex).readyTick());
                }
                if (visits >= MAX_VISITS_PER_STEP || reported >= budget.remainingWorkUnits() || budget.exhausted()) {
                    return EffectStep.Yield.INSTANCE;
                }
                BlockPos pos = center.offset(offsets.get(cursor));
                // canSurvive 可能读取相邻方块，边界两侧都须已加载。
                if (!ChunkPresence.containsArea(context.level(), pos, 1)) {
                    return new EffectStep.AwaitChunks(ChunkRequirement.around(pos, 1));
                }
                cursor++;
                visits++;
                reported++;
                // 计价：一个候选位置的读取与放置尝试记 1 个工作单位
                StepWorkCounter.report(1);
                if (context.level().getBlockState(pos).canBeReplaced()) {
                    BlockState state = orientedState(pos);
                    if (state != null && context.level().setBlock(pos, state, Block.UPDATE_ALL)) {
                        placed++;
                        if (count != null) {
                            count.recordPlaced();
                        }
                        // 真实完成量回执：每成功放置一个方块计 1
                        reportPlaced();
                    }
                }
            }
            // 放置结束：记录真实成功数、计划上限与候选数，便于验收与排查半径不足
            if (RuntimeObserver.current().enabled()) {
                RuntimeObserver.current().observe(context.source(), "PLACE_BLOCK_RESULT",
                        "candidates", offsets.size(), "placed", placed, "target", target);
            }
            result = EffectResult.applied(0, PLACED_BY_STEPS);
            return EffectStep.done(result);
        }

        // 先填满当前组的结果预算，再推进下一组；每组只向自己的成本账回执成功量。
        private void reportPlaced() {
            if (batch == null) {
                context.reportProgress(1);
                return;
            }
            Request request = batch.requests.get(requestIndex);
            request.context().reportProgress(1);
            if (++requestPlaced == request.count()) {
                requestIndex++;
                requestPlaced = 0;
            }
        }
    }

    private record CountStep(int reads, @Nullable ChunkRequirement awaiting) {}

    /** 同槽位各组共用的环境预算；按区段检查调色板，只遍历含目标方块区段与统计方框的交集。 */
    private static final class NeighbourCount {
        private final int limit;
        private final BlockPos center;
        private final int radius;
        private @Nullable Iterator<BlockPos> sections;
        private @Nullable Iterator<BlockPos> positions;
        private @Nullable BlockPos pending;
        private int existing;
        private int placed;
        private boolean done;

        private NeighbourCount(int limit, BlockPos center, int radius) {
            this.limit = limit;
            this.center = center;
            this.radius = radius;
        }

        private boolean counting() { return !done; }
        private int allowance() { return done ? Math.max(0, limit - existing - placed) : 0; }
        private void recordPlaced() { placed++; }

        // 区段探测和方块读取各计一次工作量；当前区段保留为跨刻的区块等待锚点。
        private CountStep advance(ServerLevel level, Block block, ServerTickBudget budget, int readCap) {
            int work = 0;
            while (!done && work < readCap) {
                if (budget.exhausted() || work >= budget.remainingWorkUnits()) {
                    break;
                }
                if (pending == null) {
                    if (sections == null) {
                        int minY = Math.max(center.getY() - radius, level.getMinBuildHeight());
                        int maxY = Math.min(center.getY() + radius, level.getMaxBuildHeight() - 1);
                        if (minY > maxY) {
                            done = true;
                            break;
                        }
                        sections = BlockPos.betweenClosed(
                                new BlockPos((center.getX() - radius) >> 4, minY >> 4, (center.getZ() - radius) >> 4),
                                new BlockPos((center.getX() + radius) >> 4, maxY >> 4, (center.getZ() + radius) >> 4)).iterator();
                    }
                    if (!sections.hasNext()) {
                        done = true;
                        break;
                    }
                    BlockPos section = sections.next();
                    pending = new BlockPos(section.getX() << 4, section.getY() << 4, section.getZ() << 4);
                }
                if (!ChunkPresence.contains(level, pending)) {
                    return new CountStep(work, ChunkRequirement.at(pending));
                }
                if (positions == null) {
                    LevelChunkSection section = level.getChunk(pending.getX() >> 4, pending.getZ() >> 4)
                            .getSection(level.getSectionIndex(pending.getY()));
                    work++;
                    StepWorkCounter.report(1);
                    if (!section.maybeHas(state -> state.is(block))) {
                        pending = null;
                        continue;
                    }
                    BlockPos min = new BlockPos(Math.max(center.getX() - radius, pending.getX()),
                            Math.max(Math.max(center.getY() - radius, pending.getY()), level.getMinBuildHeight()),
                            Math.max(center.getZ() - radius, pending.getZ()));
                    BlockPos max = new BlockPos(Math.min(center.getX() + radius, pending.getX() + 15),
                            Math.min(Math.min(center.getY() + radius, pending.getY() + 15), level.getMaxBuildHeight() - 1),
                            Math.min(center.getZ() + radius, pending.getZ() + 15));
                    positions = BlockPos.betweenClosed(min, max).iterator();
                    continue;
                }
                work++;
                StepWorkCounter.report(1);
                if (level.getBlockState(positions.next()).is(block) && ++existing >= limit) {
                    done = true;
                }
                if (!positions.hasNext()) {
                    positions = null;
                    pending = null;
                }
            }
            return new CountStep(work, null);
        }
    }
}
