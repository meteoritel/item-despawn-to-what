package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 统一实体生成分发入口：按产出子类（物品 / 生物 / 其他实体 / 经验）分派到对应执行策略。
 * 物品与实体产出在成功转化当刻固定产物引用与产出量，随后在共享预算内分批交付；
 * 实体的安全位置搜索有界（5×5×5 共 125 个候选，垂直方向权重更高），同一次交付内每个候选只判定一次、
 * 命中位置只向后推进；搜索范围内没有任何安全落点时本次效果失败，不再回退到原点附近。
 */
public final class SpawnEntityExecutor {

    // 单个运行批次最多生成的实体数：行为上界，取值与语义保持不变；每个成功生成的实体回执 1 个工作单位
    private static final int SPAWNS_PER_RUN = 8;
    // 安全搜索范围：水平 ±2（5×5），上下各 2 格，共 125 个候选
    private static final int SEARCH_HORIZONTAL = 2;
    private static final int SEARCH_VERTICAL = 2;
    // 关闭安全生成时的区块需求半径：沿用「原点附近随机偏移落点」的既有行为
    private static final int FALLBACK_CHUNK_RADIUS = 1;
    /**
     * 安全选址失败原因：搜索范围内没有任何安全落点。
     * 结算层据此把本组已支付的源成本退回，因此该字符串是跨层契约，不得改写。
     */
    public static final String NO_SAFE_SPAWN_POSITION = "no_safe_spawn_position";
    // 已生成部分实体后才发现无安全落点：真实产出已逐单位回执，终态只做普通收尾，不回退也不返还
    private static final String NO_SAFE_SPAWN_POSITION_PARTIAL = NO_SAFE_SPAWN_POSITION + ":partial";
    // 虚空判定的向下探测层数：只覆盖世界底部这一段，更高的悬空落点交给重力与地面判定
    private static final int VOID_FALL_PROBE = 8;
    // 回执说明：同步入口不承担分批，交由预算执行路径推进
    private static final String BUDGET_REQUIRED = "budget_required: spawn_entity";
    // 回执说明：成功转化当刻没有固定输入，属于运行时接线缺失，不退回到期重判
    private static final String MISSING_PREPARATION = "missing_preparation: spawn_entity";
    // 回执说明：本次产出已按真实写入逐单位回执，终态不再重复计入
    private static final String STEPPED = "stepped";
    // 候选偏移按「水平距离平方 + 4×垂直距离平方」升序，等距用 y/x/z 稳定排序
    private static final List<BlockPos> SEARCH_OFFSETS = buildSearchOffsets();

    private SpawnEntityExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 成功转化当刻固定的本次产出输入：已解析的产物引用与每轮产出量。
     * 物品产出的 count 为件数，实体产出的 count 为个数，两者单位不同且都保持参数原值；
     * failure 非空表示引用在成功转化当刻已无法解析，该槽位执行时直接给出失败回执。
     *
     * @param item       物品产出解析出的物品；实体产出与失败情形为 null
     * @param entityType 实体产出解析出的实体类型；物品与失败情形为 null
     * @param count      参数声明的每轮产出量（件数或个数），本组总量由交付态乘以组轮数得出
     * @param failure    引用解析失败的原因；成功固定时为 null
     */
    public record SpawnPlan(@Nullable Item item, @Nullable EntityType<?> entityType, int count,
                            @Nullable String failure) implements EffectPreparation {
    }

    /**
     * 同一结算槽位已支付各组的物品汇总输入；概率与标签选择已逐组完成。
     * @param item 固定的物品类型，直接物品产出使用该物品的默认组件
     * @param count 汇总后的总件数，不再乘执行上下文的轮数
     */
    public record ItemBatchPlan(Item item, long count) implements EffectPreparation {
        public ItemBatchPlan {
            java.util.Objects.requireNonNull(item, "item");
            if (count < 0) { throw new IllegalArgumentException("count must be nonnegative"); }
        }
    }

    /**
     * 同步入口：本类型需要按批次在共享预算内续接，不提供一次性完成的执行方式。
     *
     * @param effect 实体生成效果参数
     * @param context 本组执行上下文
     * @return 恒为要求走预算执行路径的失败回执
     */
    public static EffectResult execute(SpawnEntityEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED);
    }

    /**
     * 在成功转化当刻固定本次产出：解析产物引用并取定每轮产出量。
     * 物品与实体的标签成员在此刻抽定，执行期不再向注册表随机取值，也不读取原源实体；
     * 经验产出的点数依赖本组实际扣减的源物品件数，因此只登记参与判定，不带固定负载。
     * 引用解析失败不抛出：失败原因随固定输入携带，等该槽位执行时再给失败回执，不影响同批其它候选。
     *
     * @param effect       实体生成效果参数
     * @param level        触发维度，提供注册表随机源
     * @param position     成功当刻固定的触发点位置
     * @param sourceEntity 成功当刻的源实体，仅准备期可读；本类型不使用实体整体
     * @param sourceStack  源实体清空前的只读库存快照；本类型不使用源物品
     * @param fillOrigin   候选的位置策略；本类型不使用
     * @return 本次产出的固定输入
     */
    public static @Nullable EffectPreparation prepare(SpawnEntityEffect effect, ServerLevel level, Vec3 position,
                                                      ItemEntity sourceEntity, ItemStack sourceStack,
                                                      boolean fillOrigin) {
        return switch (effect.product()) {
            case EntityProduct.Item item -> planItem(item, level);
            // 生物与其他实体共用实体产出计划：年龄只影响交付时写入 AgeableMob，不参与选址
            case EntityProduct.Mob mob -> planEntity(mob.entity(), mob.count(), level);
            case EntityProduct.Generic entity -> planEntity(entity.entity(), entity.count(), level);
            case EntityProduct.Experience ignored -> EffectPreparation.ready();
        };
    }

    // 物品产出的固定输入：标签成员在此刻抽定，件数取参数原值（本组总件数由交付态乘以组轮数得出）
    private static SpawnPlan planItem(EntityProduct.Item product, ServerLevel level) {
        try {
            Item item = EffectTargets.resolveOrThrow(product.item(), BuiltInRegistries.ITEM, level.random, "item");
            return new SpawnPlan(item, null, product.count(), null);
        } catch (IllegalStateException failure) {
            return new SpawnPlan(null, null, product.count(), failure.getMessage());
        }
    }

    // 实体产出的固定输入：标签成员在此刻抽定，并沿用专用子类拒载判据
    private static SpawnPlan planEntity(TaggedId reference, int count, ServerLevel level) {
        try {
            EntityType<?> type = EffectTargets.resolveOrThrow(reference, BuiltInRegistries.ENTITY_TYPE,
                    level.random, "entity");
            if (EntityProduct.isSpecial(type)) {
                throw new IllegalStateException("掉落物和经验球必须使用专用产出子类");
            }
            return new SpawnPlan(null, type, count, null);
        } catch (IllegalStateException failure) {
            return new SpawnPlan(null, null, count, failure.getMessage());
        }
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态：物品与实体在共享预算内分批交付，
     * 经验产出沿用按源物品计点的既有执行状态。
     *
     * @param effect      实体生成效果参数
     * @param context     本组执行上下文
     * @param preparation 成功转化当刻固定的输入；null 表示该槽位未固定
     * @return 仅供本次效果使用的执行状态
     */
    public static EffectExecution start(SpawnEntityEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 迁移到固定输入路径后，缺少固定输入不再退回到期重新取值
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        }
        if (preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        if (effect.product() instanceof EntityProduct.Experience experience) {
            return SpawnXpExecutor.start(experience, context, preparation);
        }
        if (effect.product() instanceof EntityProduct.Item && preparation instanceof ItemBatchPlan(Item item, long count)) {
            return SpawnItemExecutor.startBatch(context, item, count);
        }
        if (!(preparation instanceof SpawnPlan plan)) {
            return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        }
        if (plan.failure() != null) {
            String failure = plan.failure();
            return budget -> EffectStep.done(EffectResult.failed(failure));
        }
        return switch (effect.product()) {
            case EntityProduct.Item ignored -> SpawnItemExecutor.start(context, plan.item(), plan.count());
            case EntityProduct.Mob mob -> new EntityDelivery(context, plan.entityType(), plan.count(),
                    mob.age(), context.safeSpawn());
            case EntityProduct.Generic ignored -> new EntityDelivery(context, plan.entityType(), plan.count(),
                    0, context.safeSpawn());
            // 经验产出已在上面按自己的固定输入创建执行状态；此处仅为 switch 完整覆盖
            case EntityProduct.Experience ignored -> budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        };
    }

    // 落点判据：世界边界 + 不落在虚空 + 类型自身的地面/水域要求 + 完整生成碰撞箱 + 脚部与头部的危险方块
    private static boolean isSafeSpawnPosition(EntityType<?> type, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (isOverVoid(level, pos)) {
            return false;
        }
        // 未登记放置类型的实体回退为「不限制」，仍由碰撞箱与危险方块判据把关
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            return false;
        }
        // 用实体类型的生成碰撞箱检查完整体积，不创建实体（避免触发入世界副作用）
        AABB box = type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(box)) {
            return false;
        }
        // 脚部与头部都不得是危险方块
        if (isHazardous(level, pos)) {
            return false;
        }
        return !isHazardous(level, pos.above());
    }

    // 虚空：落点低于世界下界，或落点位于世界底部且向下直到下界都没有可踏脚的方块（实体会直接坠出世界）。
    // 只在底部 VOID_FALL_PROBE 层内逐格探测，更高的悬空落点交给重力与地面判定，不在这里扫描整列。
    private static boolean isOverVoid(ServerLevel level, BlockPos pos) {
        int floor = level.getMinBuildHeight();
        if (pos.getY() < floor) {
            return true;
        }
        if (pos.getY() > floor + VOID_FALL_PROBE) {
            return false;
        }
        for (int y = pos.getY() - 1; y >= floor; y--) {
            if (!level.getBlockState(pos.atY(y)).isAir()) {
                return false;
            }
        }
        return true;
    }

    // 脚部与头部的危险方块，按原版 WalkNodeEvaluator.getPathTypeFromState 的分类收敛：
    // 熔岩流体与熔岩方块、火/灵魂火、岩浆块、营火/灵魂营火、熔岩炼药锅属于燃烧伤害（DAMAGE_FIRE 一类），
    // 仙人掌与甜浆果丛属于 DAMAGE_OTHER，凋灵玫瑰与滴水石锥属于 DAMAGE_CAUTIOUS，细雪属于 POWDER_SNOW。
    // 判据与实体类型无关：原版 EntityType.isBlockDangerous 会按火焰免疫豁免，这里不豁免，同一落点对任何产物给出同一结论。
    private static boolean isHazardous(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
        }
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.LAVA_CAULDRON) || state.is(BlockTags.CAMPFIRES)) {
            return true;
        }
        return state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.POWDER_SNOW);
    }

    // 安全搜索真正覆盖的区块半径（区块数）：候选水平半径加上生成碰撞箱半宽，按 16 格一个区块向上取整。
    // 常见的 5×5 候选只需 1 圈（3×3 区块），只有极宽的实体会用到 2 圈
    private static ChunkRequirement searchChunks(EntityType<?> type, BlockPos center) {
        double halfWidth = type.getSpawnAABB(0.0D, 0.0D, 0.0D).getXsize() / 2.0D;
        int margin = (int) Math.ceil((SEARCH_HORIZONTAL + halfWidth) / 16.0D);
        return ChunkRequirement.around(center, Math.max(1, margin));
    }

    // 候选偏移的稳定顺序：近处优先，垂直方向权重更高（4×dy²）；首项为原点本身
    private static List<BlockPos> buildSearchOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -SEARCH_HORIZONTAL; dx <= SEARCH_HORIZONTAL; dx++) {
            for (int dz = -SEARCH_HORIZONTAL; dz <= SEARCH_HORIZONTAL; dz++) {
                for (int dy = -SEARCH_VERTICAL; dy <= SEARCH_VERTICAL; dy++) {
                    offsets.add(new BlockPos(dx, dy, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ()
                        + 4 * pos.getY() * pos.getY())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    /**
     * 实体产出的分批交付游标：每批最多 {@link #SPAWNS_PER_RUN} 个实体、每刻候选检查数受配置约束，
     * 时间或工作量预算耗尽即让出。安全搜索的候选游标在整次交付内单调前进，已判定失败的候选不再复查；
     * 搜索范围内没有安全落点时按已生成数量收尾：一个都没生成按失败回执，已生成部分按真实产出结束。
     */
    private static final class EntityDelivery implements EffectExecution {
        private final EffectContext context;
        private final ChunkRequirement chunks;
        private final EntityType<?> type;
        private final ServerLevel level;
        private final Vec3 origin;
        private final BlockPos center;
        private final int age;
        private final boolean safeSpawn;
        private final int target;
        private int spawned;
        // 下一个待判定的候选序号：整次交付内只前进不回退，保证同一候选最多判定一次
        private int searchCursor;
        // 当前实体的安全落点；positionResolved 为 true 且此处为 null 表示搜索范围内已无安全落点
        private @Nullable BlockPos hit;
        private boolean positionResolved;
        private Entity pendingEntity;

        private EntityDelivery(EffectContext context, EntityType<?> type, int count, int age, boolean safeSpawn) {
            this.context = context;
            this.type = type;
            this.level = context.level();
            this.origin = context.position();
            this.center = BlockPos.containing(origin);
            // 安全搜索只强加载搜索盒真正覆盖的区块；关闭安全生成时沿用原点落点的单圈区块需求
            this.chunks = safeSpawn ? searchChunks(type, center) : ChunkRequirement.around(center, FALLBACK_CHUNK_RADIUS);
            this.age = age;
            this.safeSpawn = safeSpawn;
            // 产出量按「参数个数 × 本组轮数」封顶，轮数由本组固定、执行期不重新读取产出参数
            this.target = EffectTargets.saturatedMultiply(count, context.rounds());
        }

        @Override
        public @Nullable ChunkRequirement requiredChunks() {
            if (spawned >= target) { return null; }
            // 已确定没有安全落点：下一步就收尾，不再等待任何区块
            if (safeSpawn && positionResolved && hit == null) { return null; }
            return pendingEntity == null ? chunks : chunks.including(pendingEntity.blockPosition());
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            if (spawned >= target) {
                return EffectStep.done(EffectResult.applied(0, STEPPED));
            }
            int end = (int) Math.min(target, (long) spawned + SPAWNS_PER_RUN);
            int checks = 0;
            // 本步已报告但尚未由调度器计费的额度：单步内 remainingWorkUnits 不会随上报立即减少，需自行扣除
            int reportedUnits = 0;
            // 每刻候选检查上限来自配置（position_search_checks_per_tick），未完成则下刻从断点继续
            int checkLimit = Math.max(1, context.positionSearchChecksPerTick());
            while (spawned < end) {
                // 每个实体写入前都复查双预算：时间耗尽或本步已用额度（含已报告未计费部分）耗尽时让出，
                // 剩余数量与搜索游标原样保留，下一 tick 重获预算后继续
                if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                    return EffectStep.Yield.INSTANCE;
                }
                if (safeSpawn && !positionResolved) {
                    // 快速路径：候选表首项就是原点本身，绝大多数情况一次判定即中，不进入候选表遍历
                    if (searchCursor == 0) {
                        searchCursor = 1;
                        checks++;
                        // 计价：每检查一个候选点（含地面判据与碰撞箱、危险方块读取）记 1 个工作单位
                        StepWorkCounter.report(1);
                        reportedUnits++;
                        if (isSafeSpawnPosition(type, level, center)) {
                            hit = center;
                            positionResolved = true;
                        }
                    }
                    while (!positionResolved && checks < checkLimit) {
                        if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                            break;
                        }
                        if (searchCursor >= SEARCH_OFFSETS.size()) {
                            positionResolved = true;
                            break;
                        }
                        BlockPos candidate = center.offset(SEARCH_OFFSETS.get(searchCursor++));
                        checks++;
                        StepWorkCounter.report(1);
                        reportedUnits++;
                        if (isSafeSpawnPosition(type, level, candidate)) {
                            hit = candidate;
                            positionResolved = true;
                        }
                    }
                    if (!positionResolved) {
                        // 搜索未完成（候选上限或预算用尽）：从断点下刻继续，保持「最近优先」语义
                        return EffectStep.Yield.INSTANCE;
                    }
                    observeSearch();
                    if (hit == null) {
                        return noSafePositionStep();
                    }
                }
                if (checks >= checkLimit && spawned < end) {
                    return EffectStep.Yield.INSTANCE;
                }
                // 搜索可能已把本步余量耗尽，写入前再复查一次双预算，避免越过本 tick 额度
                if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                    return EffectStep.Yield.INSTANCE;
                }
                if (!spawnOne(hit)) {
                    return new EffectStep.AwaitChunks(ChunkRequirement.at(pendingEntity.blockPosition()));
                }
                spawned++;
                // spawnOne 成功时内部已上报 1 个工作单位，同步计入本步已用额度
                reportedUnits++;
                // 命中的格子已被本实体占用：游标继续向后推进，下一个实体只判定未用过的候选
                hit = null;
                positionResolved = false;
            }
            if (spawned < target) {
                return EffectStep.Yield.INSTANCE;
            }
            // 真实产出已逐实体回执，终态不再重复计入
            return EffectStep.done(EffectResult.applied(0, STEPPED));
        }

        // 搜索范围内没有任何安全落点：一个实体都没生成时整次效果按失败回执（不登记完成量），
        // 已生成部分只按真实产出结束，避免「生成 + 整单失败」双记
        private EffectStep noSafePositionStep() {
            return spawned == 0
                    ? EffectStep.done(EffectResult.failed(NO_SAFE_SPAWN_POSITION))
                    : EffectStep.done(EffectResult.applied(0, NO_SAFE_SPAWN_POSITION_PARTIAL));
        }

        // 每次落点搜索结束时观测一条：candidates_checked 为整次交付累计判定过的候选数
        private void observeSearch() {
            if (!RuntimeObserver.current().enabled()) {
                return;
            }
            RuntimeObserver.current().observe(context.source(), "SPAWN_POS_SEARCH",
                    "entity", BuiltInRegistries.ENTITY_TYPE.getKey(type),
                    "candidates_checked", searchCursor,
                    "hit", hit != null,
                    "exhausted", hit == null,
                    "spawned", spawned,
                    "x", hit != null ? hit.getX() : center.getX(),
                    "y", hit != null ? hit.getY() : center.getY(),
                    "z", hit != null ? hit.getZ() : center.getZ());
        }

        @Override
        public void onFinished(EffectResult result) {
            // 交付完成或已失败：不再持有剩余数量，取消路径也不补做
            spawned = target;
            pendingEntity = null;
        }

        // 生成一个实体：安全搜索命中时用命中方块中心，未开启安全生成时沿用原点附近的随机偏移。
        // 返回是否已真正写入世界：未写入时不回执完成量，由调用方带退避重试。
        private boolean spawnOne(@Nullable BlockPos position) {
            if (pendingEntity == null) {
                pendingEntity = type.create(level);
                if (pendingEntity == null) {
                    throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " 在该维度无法创建实体");
                }
                if (position != null) {
                    pendingEntity.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
                } else {
                    pendingEntity.moveTo(origin.x + (level.random.nextDouble() - 0.5) * 0.5,
                            origin.y,
                            origin.z + (level.random.nextDouble() - 0.5) * 0.5,
                            0.0F, 0.0F);
                }
                if (pendingEntity instanceof AgeableMob ageableMob) {
                    ageableMob.setAge(age);
                }
            }
            // 真实写入世界后才回执：加入失败由运行时统一捕获记为失败，不提前累计完成量
            if (!EffectTargets.addEntityNow(context, pendingEntity, true)) {
                return false;
            }
            pendingEntity = null;
            context.reportProgress(1);
            // 计价：一个真正写入世界的实体记 1 个工作单位
            StepWorkCounter.report(1);
            return true;
        }
    }
}
