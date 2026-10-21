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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 方块效果的执行器：在共享预算内分批完成邻域计数与放置，形状偏移按参数缓存复用。
 * 需要按 limit 计数时，同候选同槽位的各组共用一份邻域账：整次转化只扫一遍方框，
 * 并按区段调色板跳过确定不含目标方块的区段；计数完成前各组都只推进计数、不放置。
 * 单个调度步骤最多处理的方块位置数同时是计费上界；区块未加载时保留游标并退避等待，不从候选集合中丢弃位置。
 * 每成功放置一个方块通过 {@link EffectContext#reportProgress(int)} 回执 1，真实放置量以逐块回执累加；
 * 终态回执只收束任务，不再重复计入产量。
 * 放置策略：只替换可替换方块（canBeReplaced），并按原版支撑规则为每个候选位置摆正朝向——
 * 先试默认状态，再依次尝试贴地、贴天花板与四向贴墙，取第一个能站稳的朝向；都站不住的位置只跳过并继续下一个候选，
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
    /**
     * 单步最多检查的方框位置数：区段剪枝跳过的位置不读方块、不计工作量，用独立上限约束单步循环时间，
     * 使整块不含目标方块的区段能被快速跳过；真正读取的位置仍受 {@link #MAX_VISITS_PER_STEP} 与本 tick 预算约束。
     */
    private static final int MAX_PROBES_PER_STEP = 1024;
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
    public static class PlacementPlan implements EffectPreparation {
        private final @Nullable Block block;
        private final List<BlockPos> candidates;
        private final @Nullable Integer limit;
        private final @Nullable String failure;

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

        // 跨组共享的邻域账：本份输入不可共享时没有
        @Nullable NeighbourCount sharedCount(BlockPos center, int radius, int target) {
            return null;
        }
    }

    /**
     * 可跨组共享的固定输入：同一候选同一槽位的各组拿到同一份实例，于是「方框内已有同类方块」只数一遍，
     * limit 余额（含本次已放量）也只有一份账，limit 因此是该方框内所有组共同的上限。
     * 只用于求值不含按组独立随机结果的输入：直接引用与源物品映射都在转化当刻确定；
     * 标签引用要按随机源取定成员、各组各自取定，因此不共享。
     */
    private static final class SharedPlacementPlan extends PlacementPlan
            implements EffectPreparation.SharedPreparation {
        private @Nullable NeighbourCount count;

        private SharedPlacementPlan(@Nullable Block block, List<BlockPos> candidates, @Nullable Integer limit) {
            super(block, candidates, limit, null);
        }

        @Override
        @Nullable NeighbourCount sharedCount(BlockPos center, int radius, int target) {
            Integer limit = limit();
            // 计数只为把计划量压到「limit 减去方框已有量」；整块填满同类方块也压不动时，计数不影响任何结果
            long span = radius * 2L + 1L;
            long cubePositions = span * span * span;
            if (limit == null || limit - cubePositions >= target) {
                return null;
            }
            if (count == null) {
                count = new NeighbourCount(limit, center, radius);
            }
            return count;
        }
    }

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
     * 未完成时 {@code advance} 返回 null 让出本刻，下一次推进从保留的游标继续；区块未加载只推迟推进时机，不改变计划量。
     * 需要计数时计数游标与已数/已放量都在共享账上（见 {@link NeighbourCount}），本类只持有自己的放置游标。
     */
    private static final class Placement implements EffectExecution {
        private final EffectContext context;
        private final Block block;
        private final BlockPos center;
        private final List<BlockPos> offsets;
        private final @Nullable NeighbourCount count;
        private int cursor;
        private int placed;
        private final int target;
        private long readyAt = Long.MIN_VALUE;
        private @Nullable EffectResult result;
        private Placement(PlaceBlockEffect effect, EffectContext context, PlacementPlan plan) {
            this.context = context;
            this.block = plan.block();
            this.center = BlockPos.containing(context.position());
            this.offsets = plan.candidates();
            // 计划上限：count × 本组已付轮数，再受候选位置数封顶；两项都不含随机抽取
            this.target = Math.min(EffectTargets.saturatedMultiply(effect.count(), context.rounds()), plan.candidates().size());
            this.count = plan.sharedCount(center, effect.radius(), target);
        }

        // 本组还能放多少：不受 limit 约束时为计划上限，否则取计划上限与共享账余额的较小者
        private int allowed() {
            return count == null ? target : Math.min(target, count.allowance());
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
            if (count != null && count.counting()) {
                // 计数由先走到这里的一组推进共享账：数完之前任何组都不放置，避免边数边放突破 limit
                visits += count.advance(context.level(), block, budget, now, MAX_VISITS_PER_STEP - visits);
                if (count.counting()) {
                    // 计数未完成或区块未加载：游标留在共享账上，按共享账给出的退避刻续接
                    readyAt = count.deferredUntil();
                    return null;
                }
            }
            while (cursor < offsets.size() && placed < allowed()) {
                if (visits >= MAX_VISITS_PER_STEP || reported >= budget.remainingWorkUnits() || budget.exhausted()) {
                    return null;
                }
                BlockPos pos = center.offset(offsets.get(cursor));
                // canSurvive 可能读取相邻方块，边界两侧都须已加载。
                if (!LoadedChunks.contains(context.level(), pos.offset(-1, 0, -1)) || !LoadedChunks.contains(context.level(), pos.offset(1, 0, 1))) {
                    readyAt = now + LoadedChunks.RETRY_TICKS;
                    return null;
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
                        context.reportProgress(1);
                    }
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

    /**
     * 一次转化内跨组共享的邻域账：方框内已有的同类方块数与本次已放量都只有一份，各组按同一余额分摊 limit。
     * 计数游标也在这里，因此整个转化只扫一遍方框，而不是每组各扫一遍；计数完成前任何组都不放置。
     * 区段剪枝：位置所在区段声明确实不含目标方块时跳过该位置，不读方块、不计工作单位，但仍受单步检查数上限约束。
     * 剪枝只用于计数阶段：此时还没有本效果写入，区段调色板就是计数依据。
     */
    private static final class NeighbourCount {
        private final int limit;
        private final BlockPos center;
        private final int radius;
        private @Nullable Iterator<BlockPos> positions;
        private @Nullable BlockPos pending;
        private int existing;
        private int placed;
        private boolean done;
        private long deferredUntil = Long.MIN_VALUE;
        // 区段探测缓存：同一区段内的连续位置只查一次调色板
        private boolean probeCached;
        private int probeSectionX;
        private int probeSectionY;
        private int probeSectionZ;
        private boolean probeFound;

        private NeighbourCount(int limit, BlockPos center, int radius) {
            this.limit = limit;
            this.center = center;
            this.radius = radius;
        }

        // 是否仍在计数：计数期间各组只推进计数、不放置
        private boolean counting() {
            return !done;
        }

        // 计数因区块未加载推迟到该刻之后再续；未被推迟时为 Long.MIN_VALUE
        private long deferredUntil() {
            return deferredUntil;
        }

        // limit 余额：上限减去方框已有量与本次已放量
        private int allowance() {
            return done ? Math.max(0, limit - existing - placed) : 0;
        }

        // 记录一次成功放置：各组共用同一份已放量
        private void recordPlaced() {
            placed++;
        }

        /**
         * 在本步剩余工作量内推进共享计数，数完或到达单步上限后停下。
         * @param level 触发维度
         * @param block 目标方块
         * @param budget 本 tick 预算
         * @param now 当前刻
         * @param readCap 本步还能读取的方块数，与放置段共享 {@link #MAX_VISITS_PER_STEP}
         * @return 本步实际读取的方块数
         */
        private int advance(ServerLevel level, Block block, ServerTickBudget budget, long now, int readCap) {
            if (done) {
                return 0;
            }
            if (now < deferredUntil) {
                return 0;
            }
            int reads = 0;
            int probes = 0;
            while (probes < MAX_PROBES_PER_STEP && reads < readCap) {
                if (budget.exhausted() || reads >= budget.remainingWorkUnits()) {
                    return reads;
                }
                if (pending == null) {
                    if (!iterator().hasNext()) {
                        done = true;
                        return reads;
                    }
                    pending = iterator().next().immutable();
                }
                BlockPos pos = pending;
                if (!LoadedChunks.contains(level, pos)) {
                    // 区块未加载：保留待处理位置，退避后再续
                    deferredUntil = now + LoadedChunks.RETRY_TICKS;
                    return reads;
                }
                probes++;
                if (sectionLacks(level, pos, block)) {
                    // 该区段确定不含目标方块：本位置不必读取，也不计工作量
                    pending = null;
                    continue;
                }
                pending = null;
                reads++;
                // 计价：一次方块读取记 1 个工作单位
                StepWorkCounter.report(1);
                if (level.getBlockState(pos).is(block)) {
                    existing++;
                    if (existing >= limit) {
                        // 已有量已到上限：本次转化不再有可放量，剩余位置不必再数
                        done = true;
                        return reads;
                    }
                }
            }
            return reads;
        }

        // 区段调色板是否确定不含目标方块：不含时该区段的位置可直接跳过
        private boolean sectionLacks(ServerLevel level, BlockPos pos, Block block) {
            int sectionX = pos.getX() >> 4;
            int sectionY = pos.getY() >> 4;
            int sectionZ = pos.getZ() >> 4;
            if (!probeCached || sectionX != probeSectionX || sectionY != probeSectionY || sectionZ != probeSectionZ) {
                LevelChunk chunk = level.getChunk(sectionX, sectionZ);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(pos.getY()));
                probeFound = section.maybeHas(state -> state.is(block));
                probeCached = true;
                probeSectionX = sectionX;
                probeSectionY = sectionY;
                probeSectionZ = sectionZ;
            }
            return !probeFound;
        }

        // 方框遍历器：只在第一次推进时创建，坐标按需转成不可变 BlockPos
        private Iterator<BlockPos> iterator() {
            if (positions == null) {
                positions = BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                        center.offset(radius, radius, radius)).iterator();
            }
            return positions;
        }
    }
}