package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
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
 * 统一实体生成分发入口：按产出子类（物品 / 经验 / 通用实体）分派到对应执行策略。
 * 物品与通用实体在成功转化当刻固定产物引用与产出量，随后在共享预算内分批交付；
 * 通用实体的安全位置搜索有界（5×5×5 共 125 个候选，垂直方向权重更高），搜索不成功时回退到原点附近。
 */
public final class SpawnEntityExecutor {

    // 单个运行批次最多生成的实体数：行为上界，取值与语义保持不变；每个成功生成的实体回执 1 个工作单位
    private static final int SPAWNS_PER_RUN = 8;
    // 安全搜索范围：水平 ±2（5×5），上下各 2 格，共 125 个候选
    private static final int SEARCH_HORIZONTAL = 2;
    private static final int SEARCH_VERTICAL = 2;
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
     * 物品产出的 count 为件数，通用实体产出的 count 为个数，两者单位不同且都保持参数原值；
     * failure 非空表示引用在成功转化当刻已无法解析，该槽位执行时直接给出失败回执。
     *
     * @param item       物品产出解析出的物品；通用实体与失败情形为 null
     * @param entityType 通用实体产出解析出的实体类型；物品与失败情形为 null
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
     * 物品与通用实体的标签成员在此刻抽定，执行期不再向注册表随机取值，也不读取原源实体；
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
            case EntityProduct.Generic entity -> planEntity(entity, level);
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

    // 通用实体产出的固定输入：标签成员在此刻抽定，并沿用专用子类拒载判据
    private static SpawnPlan planEntity(EntityProduct.Generic product, ServerLevel level) {
        try {
            EntityType<?> type = EffectTargets.resolveOrThrow(product.entity(), BuiltInRegistries.ENTITY_TYPE,
                    level.random, "entity");
            if (EntityProduct.Generic.isSpecial(type)) {
                throw new IllegalStateException("掉落物和经验球必须使用专用产出子类");
            }
            return new SpawnPlan(null, type, product.count(), null);
        } catch (IllegalStateException failure) {
            return new SpawnPlan(null, null, product.count(), failure.getMessage());
        }
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态：物品与通用实体在共享预算内分批交付，
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
            case EntityProduct.Generic entity -> new EntityDelivery(context, plan.entityType(), plan.count(),
                    entity.age(), context.safeSpawn());
            // 经验产出已在上面按自己的固定输入创建执行状态；此处仅为 switch 完整覆盖
            case EntityProduct.Experience ignored -> budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
        };
    }

    // 无实体位置判据：世界边界 + 类型自身的地面/水域要求 + 完整生成碰撞箱 + 火/岩浆/仙人掌危险
    private static boolean isSafeSpawnPosition(EntityType<?> type, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
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
        return isHarmless(level, pos) && isHarmless(level, pos.above());
    }

    // 脚部与头部位置的火、岩浆、仙人掌（含岩浆流体）都视为危险
    private static boolean isHarmless(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS)) {
            return false;
        }
        return !level.getFluidState(pos).is(FluidTags.LAVA);
    }

    // 候选偏移的稳定顺序：近处优先，垂直方向权重更高（4×dy²）
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
     * 通用实体的分批交付游标：每批最多 {@link #SPAWNS_PER_RUN} 个实体、每刻候选检查数受配置约束，
     * 时间或工作量预算耗尽即让出，下一次推进从保留的搜索游标与已生成数继续。
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
        private int searchCursor;
        private @Nullable BlockPos hit;
        private boolean targetReady;
        private Entity pendingEntity;

        private EntityDelivery(EffectContext context, EntityType<?> type, int count, int age, boolean safeSpawn) {
            this.context = context;
            this.type = type;
            this.level = context.level();
            this.origin = context.position();
            this.center = BlockPos.containing(origin);
            this.chunks = ChunkRequirement.around(center, safeSpawn ? SEARCH_HORIZONTAL : 1);
            this.age = age;
            this.safeSpawn = safeSpawn;
            // 产出量按「参数个数 × 本组轮数」封顶，轮数由本组固定、执行期不重新读取产出参数
            this.target = EffectTargets.saturatedMultiply(count, context.rounds());
        }

        @Override
        public @Nullable ChunkRequirement requiredChunks() {
            if (spawned >= target) { return null; }
            return pendingEntity == null ? chunks : chunks.including(pendingEntity.blockPosition());
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            if (spawned >= target) {
                return EffectStep.done(EffectResult.applied(0, STEPPED));
            }
            // 每个实体都重新搜索：上一个命中位置可能已被占用；候选检查数按刻累计，用尽即让出并保留搜索游标
            // 安全搜索可能读取相邻方块，候选范围比原生成点更宽，需多留一圈区块
            // 阈值只检查开组准入，本批继续完成已开始组。
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
                if (safeSpawn && !targetReady) {
                    while (searchCursor < SEARCH_OFFSETS.size() && checks < checkLimit) {
                        if (budget.exhausted() || budget.remainingWorkUnits() - reportedUnits <= 0) {
                            break;
                        }
                        BlockPos candidate = center.offset(SEARCH_OFFSETS.get(searchCursor++));
                        checks++;
                        // 计价：每检查一个候选点（含地面判据与碰撞箱、危险方块读取）记 1 个工作单位
                        StepWorkCounter.report(1);
                        reportedUnits++;
                        if (isSafeSpawnPosition(type, level, candidate)) {
                            hit = candidate;
                            break;
                        }
                    }
                    if (hit == null && searchCursor < SEARCH_OFFSETS.size()) {
                        // 搜索未完成（候选上限或预算用尽）：从断点下刻继续，保持「最近优先」语义
                        return EffectStep.Yield.INSTANCE;
                    }
                    targetReady = true;
                    if (RuntimeObserver.current().enabled()) {
                        RuntimeObserver.current().observe(context.source(), "SPAWN_POS_SEARCH",
                                "entity", BuiltInRegistries.ENTITY_TYPE.getKey(type),
                                "candidates_checked", searchCursor,
                                "hit", hit != null,
                                "fallback_origin", hit == null,
                                "x", hit != null ? hit.getX() : origin.x,
                                "y", hit != null ? hit.getY() : origin.y,
                                "z", hit != null ? hit.getZ() : origin.z);
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
                if (safeSpawn) {
                    searchCursor = 0;
                    hit = null;
                    targetReady = false;
                }
            }
            if (spawned < target) {
                return EffectStep.Yield.INSTANCE;
            }
            // 真实产出已逐实体回执，终态不再重复计入
            return EffectStep.done(EffectResult.applied(0, STEPPED));
        }

        @Override
        public void onFinished(EffectResult result) {
            // 交付完成或已失败：不再持有剩余数量，取消路径也不补做
            spawned = target;
            pendingEntity = null;
        }

        // 生成一个实体：安全搜索命中时用命中方块中心，未开启搜索或回退时沿用原点附近的随机偏移。
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
