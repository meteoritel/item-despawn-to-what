package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkPresence;
import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import org.jetbrains.annotations.Nullable;

/**
 * 方块效果的执行器：在共享预算内分批完成邻域计数与放置，摆放结果不依赖执行时机。
 *
 * <p>只替换可替换方块，并按原版支撑规则摆正朝向；站不住的位置跳过，不为凑数突破 limit 或 radius。
 * 立方体与球体在触发位置上下展开，候选落到世界高度之外时直接跳过。
 * 同候选同槽位的各组共享候选游标与邻域账，limit 是该形状候选范围内所有组的共同上限。
 * 需要计数时先扫完候选再放置；区块未加载时保留游标等待，不丢弃候选。
 * 每成功放置一个方块经 {@link EffectContext#reportProgress(int)} 回执 1，终态回执只收束任务。
 */
public final class PlaceBlockExecutor {
    private static final Map<ShapeKey, List<BlockPos>> OFFSETS = new HashMap<>();
    // 单步工作量上界：区段探测、位置读取与放置尝试合计，各计 1 个工作单位。
    private static final int MAX_VISITS_PER_STEP = 128;
    // 固定水平方向顺序，让同一位置的摆放结果可复现。
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    // 朝向属性的固定名字：face 表示贴地、贴墙与贴天花板，facing 表示水平朝向。
    private static final String FACE_PROPERTY = "face";
    private static final String FACING_PROPERTY = "facing";
    private static final String BUDGET_REQUIRED = "budget_required";
    private static final String PLACED_BY_STEPS = "placed_by_steps";
    // 到期时源实体已清空，缺少固定输入直接失败，不再重新抽取。
    private static final String MISSING_PREPARATION = "missing_preparation";
    private static final String PLACE_TARGET_UNRESOLVED = "place_target_unresolved";
    // 不含维度引用；起点填充开关会改变候选集合，须参与缓存键。
    private record ShapeKey(PlaceBlockEffect.Shape shape, int radius, boolean fillOrigin) {}
    /**
     * 成功转化当刻固定的输入：目标方块、候选偏移、limit 上界与解析失败原因。
     *
     * <p>只由效果参数与当时的源栈快照决定，一次求值后不再随执行时机或源实体状态改变。
     * 解析失败时保留原因，由该槽位执行时产出失败回执，不影响其它候选与组。
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

        /** 由形状、半径与起点开关决定的候选偏移，按由内向外的层序排列。 */
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
        @Nullable NeighbourCount sharedCount(BlockPos center, long target) {
            // 候选位置就是 limit 的统计范围：上限覆盖全部候选时，区域里装不下更多同类方块，无需计数。
            if (target <= 0 || limit == null || limit >= candidates.size()) {
                return null;
            }
            if (counts == null) {
                counts = new HashMap<>();
            }
            return counts.computeIfAbsent(block, ignored -> new NeighbourCount(limit, center, candidates));
        }
    }

    // 可跨组共享的固定输入：同候选同槽位拿到同一实例，邻域账因而只有一份，limit 是该方框内所有组的共同上限。
    // 只有不含按组随机取定的输入可以共享。
    private static final class SharedPlacementPlan extends PlacementPlan
            implements EffectPreparation.SharedPreparation {
        private SharedPlacementPlan(@Nullable Block block, List<BlockPos> candidates, @Nullable Integer limit) {
            super(block, candidates, limit, null);
        }
    }

    /** 同候选同槽位的预算批次：共享一次位置扫描，各组按自己的到期刻推进并回执成本。 */
    public static final class BatchPlan implements EffectPreparation {
        private final PlacementPlan plan;
        private final List<Request> requests = new ArrayList<>();
        private long resultBudget;

        /** 创建一个尚未排期的批次。 */
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
     * 拒绝没有共享预算的直接执行：本类型必须按预算分批推进。
     * @param effect 方块效果参数
     * @param context 本组上下文
     * @return 要求改用预算入口的失败回执，不放置任何方块
     */
    public static EffectResult execute(PlaceBlockEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 在成功转化当刻固定本次放置的输入：目标方块、候选偏移与 limit。
     * tag 成员与源物品栈都在此刻取定，之后不再受源实体或执行时机影响；
     * 不含按组随机取定的输入声明为可共享，由派发阶段在组间复用首份。
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
            // 标签引用按组各自取定成员，只有直接引用与源物品映射可以共享
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
     * 按固定输入创建执行状态：上限按本组已付轮数缩放并受候选数封顶，执行期不再重新抽取。
     * 已判定跳过的输入直接回执原因，不重判概率，也不写世界。
     * 缺少固定输入时不回退到执行期解析：目标方块与候选只在准备阶段固定，此时源实体已清空。
     * @param effect 方块效果参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示未固定
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

    // useSourceBlock 关闭或物品没有对应方块时抛异常，由计划阶段转为槽位级失败
    private static Block sourceBlock(PlaceBlockEffect effect, ItemStack sourceStack) {
        Item item = sourceStack.getItem();
        Block block = Block.byItem(item);
        if (!effect.useSourceBlock() || block == Blocks.AIR) {
            throw new IllegalStateException("源物品没有可放置的方块: " + BuiltInRegistries.ITEM.getKey(item));
        }
        return block;
    }

    // 按名字取属性；没有对应属性的方块保持默认状态
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

    // 按形状缓存，坐标只生成一次，并按由内向外的层序排序；三维形状在触发位置上下展开。
    private static List<BlockPos> offsets(PlaceBlockEffect effect, boolean fillOrigin) {
        return OFFSETS.computeIfAbsent(new ShapeKey(effect.shape(), effect.radius(), fillOrigin), key -> {
            List<BlockPos> result = new ArrayList<>();
            int vertical = key.shape.threeDimensional() ? key.radius : 0;
            for (int x = -key.radius; x <= key.radius; x++) {
                for (int y = -vertical; y <= vertical; y++) {
                    for (int z = -key.radius; z <= key.radius; z++) {
                        // 起点填充关闭时跳过触发位置本身，其余候选与顺序保持不变
                        if (!key.fillOrigin && x == 0 && y == 0 && z == 0) { continue; }
                        if (!key.shape.accepts(x, y, z, key.radius)) { continue; }
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
            result.sort(Comparator.comparingLong((BlockPos pos) ->
                            orderKey(key.shape, pos.getX(), pos.getY(), pos.getZ()))
                    .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
            return List.copyOf(result);
        });
    }

    // 由内向外的排序键：切比雪夫距离用于方形、十字与立方体，欧氏距离用于圆形与球体。
    // 圆形沿用既有截断口径，避免改动既有形状的摆放顺序。
    private static long orderKey(PlaceBlockEffect.Shape shape, int dx, int dy, int dz) {
        return switch (shape) {
            case SQUARE, CROSS -> Math.max(Math.abs(dx), Math.abs(dz));
            case CUBE -> Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
            case CIRCLE -> (long) (int) Math.hypot(dx, dz);
            case SPHERE -> squaredDistance(dx, dy, dz);
        };
    }

    // 球体用整数平方距离比较，避免开方的浮点误差
    private static long squaredDistance(int dx, int dy, int dz) {
        return (long) dx * dx + (long) dy * dy + (long) dz * dz;
    }

    // 放置游标：每步最多处理 MAX_VISITS_PER_STEP 个位置，时间或工作量任一耗尽即让出。
    // 未完成时返回续接或区块需求，区块未加载不改变计划量；计数状态在共享的 NeighbourCount 上。
    private static final class Placement extends BoundedExecution {
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
        private Placement(PlaceBlockEffect effect, EffectContext context, PlacementPlan plan, @Nullable BatchPlan batch) {
            this.context = context;
            this.block = plan.block();
            this.center = BlockPos.containing(context.position());
            this.originChunks = ChunkRequirement.at(center);
            this.offsets = plan.candidates();
            // 结果预算独立于环境容量，半径耗尽只终止扫描，不抹掉未消耗的结果预算。
            this.batch = batch;
            this.target = batch == null ? (long) effect.count() * context.rounds() : batch.resultBudget;
            this.count = plan.sharedCount(center, target);
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
        protected int workUnitsPerStep() {
            return MAX_VISITS_PER_STEP;
        }

        @Override
        protected Advancement advanceStep(StepBudget budget) {
            if (count != null && count.counting()) {
                // 计数和放置组合使用同一额度，计数完成前各组都不写入。
                ChunkRequirement awaiting = count.advance(context.level(), block, budget);
                if (awaiting != null) {
                    return stop(new EffectStep.AwaitChunks(awaiting));
                }
                if (count.counting()) {
                    return stop(EffectStep.Yield.INSTANCE);
                }
            }
            if (cursor >= offsets.size() || !canPlaceMore()) {
                return stop(EffectStep.done(EffectResult.applied(0, PLACED_BY_STEPS)));
            }
            if (batch != null && batch.requests.get(requestIndex).readyTick() > budget.gameTime()) {
                return stop(new EffectStep.Delay(batch.requests.get(requestIndex).readyTick()));
            }
            if (budget.exhausted()) {
                return stop(EffectStep.Yield.INSTANCE);
            }
            BlockPos pos = center.offset(offsets.get(cursor));
            // 三维形状可能探出世界高度：越界位置既不能读也不能写，跳过并按一次候选计费。
            if (context.level().isOutsideBuildHeight(pos.getY())) {
                cursor++;
                budget.spend();
                return Advancement.Continue.INSTANCE;
            }
            // canSurvive 可能读取相邻方块，等待时不推进候选游标。
            if (!ChunkPresence.containsArea(context.level(), pos, 1)) {
                return stop(new EffectStep.AwaitChunks(ChunkRequirement.around(pos, 1)));
            }
            cursor++;
            // 一次候选尝试统一计费；不可替换、无支撑和写入失败都继续下一个候选。
            budget.spend();
            if (context.level().getBlockState(pos).canBeReplaced()) {
                BlockState state = orientedState(pos);
                if (state != null && context.level().setBlock(pos, state, Block.UPDATE_ALL)) {
                    placed++;
                    if (count != null) {
                        count.recordPlaced();
                    }
                    reportPlaced();
                }
            }
            return Advancement.Continue.INSTANCE;
        }

        @Override
        protected void beforeFinish() {
            if (RuntimeObserver.current().enabled()) {
                RuntimeObserver.current().observe(context.source(), "PLACE_BLOCK_RESULT",
                        "candidates", offsets.size(), "placed", placed, "target", target);
            }
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

    // 同槽位各组共用的环境余额：沿该形状的候选位置逐个读取，命中同类方块即计入已用量。
    private static final class NeighbourCount {
        private final int limit;
        private final BlockPos center;
        private final List<BlockPos> candidates;
        private @Nullable BlockPos pending;
        private int cursor;
        private int existing;
        private int placed;
        private boolean done;

        private NeighbourCount(int limit, BlockPos center, List<BlockPos> candidates) {
            this.limit = limit;
            this.center = center;
            this.candidates = candidates;
        }

        private boolean counting() { return !done; }
        private int allowance() { return done ? Math.max(0, limit - existing - placed) : 0; }
        private void recordPlaced() { placed++; }

        // 方块读取计一次工作量；待读位置保留为跨刻的区块等待锚点，区块未加载时不推进游标。
        private @Nullable ChunkRequirement advance(ServerLevel level, Block block, StepBudget budget) {
            while (!done) {
                if (cursor >= candidates.size()) {
                    done = true;
                    break;
                }
                if (budget.exhausted()) {
                    break;
                }
                if (pending == null) {
                    BlockPos pos = center.offset(candidates.get(cursor));
                    // 越界位置既不能读也不能写，与放置阶段同样直接跳过
                    if (level.isOutsideBuildHeight(pos.getY())) {
                        cursor++;
                        continue;
                    }
                    pending = pos;
                }
                if (!ChunkPresence.contains(level, pending)) {
                    return ChunkRequirement.at(pending);
                }
                budget.spend();
                if (level.getBlockState(pending).is(block) && ++existing >= limit) {
                    done = true;
                }
                cursor++;
                pending = null;
            }
            return null;
        }
    }
}
