package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
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
 * 命中位置只向后推进；候选按危险度评分挑选：完全安全的落点立即命中，整片范围都有危险时取危险度最低的落点生成，
 * 只有候选全部不可生成（虚空、被方块堵死、越界）时本次效果才按失败处理。
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
     * 安全选址失败原因：整片候选都不可生成（虚空、被方块堵死、越界）。
     * 结算层据此把本组已支付的源成本退回，因此该字符串是跨层契约，不得改写。
     */
    public static final String NO_SAFE_SPAWN_POSITION = "no_safe_spawn_position";
    // 已生成部分实体后才发现整片候选都不可生成：真实产出已逐单位回执，终态只做普通收尾，不回退也不返还
    private static final String NO_SAFE_SPAWN_POSITION_PARTIAL = NO_SAFE_SPAWN_POSITION + ":partial";
    // 落点危险度：0 表示完全安全的落点，分数越高越危险；达到 FATAL 表示该落点根本不能生成
    private static final int SCORE_FATAL = 1_000_000;
    // 伤害方块的分值：按原版寻路的减分与伤害语义从重到轻排列
    private static final int SCORE_LAVA = 400;
    private static final int SCORE_FIRE = 300;
    private static final int SCORE_LAVA_CAULDRON = 250;
    private static final int SCORE_MAGMA_BLOCK = 200;
    private static final int SCORE_WITHER_ROSE = 150;
    private static final int SCORE_CACTUS = 120;
    private static final int SCORE_POINTED_DRIPSTONE = 100;
    private static final int SCORE_SWEET_BERRY_BUSH = 60;
    private static final int SCORE_POWDER_SNOW = 50;
    // 悬空惩罚：脚下第 d 格才有着落方块时记 (d - 1) × 10，掉落伤害随悬空高度增加
    private static final int SCORE_FALL_PER_BLOCK = 10;
    // 不满足该实体原版放置要求属于软惩罚：仍可生成，只是不在它本来的地面或水域环境
    private static final int SCORE_PLACEMENT_MISMATCH = 40;
    // 无落点探测层数：脚下连续这么多格都站不住就视为虚空或深空，不再逐格往下扫描整列
    private static final int GROUND_PROBE = 8;
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
    // 形参由效果类型的分发签名固定，同步入口不读取其中任何参数
    public static EffectResult execute(@SuppressWarnings("unused") SpawnEntityEffect effect,
                                       @SuppressWarnings("unused") EffectContext context) {
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
    public static @Nullable EffectPreparation prepare(SpawnEntityEffect effect, ServerLevel level,
                                                      @SuppressWarnings("unused") Vec3 position,
                                                      @SuppressWarnings("unused") ItemEntity sourceEntity,
                                                      @SuppressWarnings("unused") ItemStack sourceStack,
                                                      @SuppressWarnings("unused") boolean fillOrigin) {
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
            return missingPreparation();
        }
        if (preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        return switch (effect.product()) {
            case EntityProduct.Experience experience -> SpawnXpExecutor.start(experience, context, preparation);
            case EntityProduct.Item ignored when preparation instanceof ItemBatchPlan(Item item, long count) ->
                    SpawnItemExecutor.startBatch(context, item, count);
            case EntityProduct.Item ignored -> fromPlan(preparation,
                    (item, entityType, count) -> SpawnItemExecutor.start(context, item, count));
            case EntityProduct.Mob mob -> fromPlan(preparation,
                    (item, entityType, count) -> new EntityDelivery(context, entityType, count,
                            mob.age(), context.safeSpawn()));
            case EntityProduct.Generic ignored -> fromPlan(preparation,
                    (item, entityType, count) -> new EntityDelivery(context, entityType, count,
                            0, context.safeSpawn()));
        };
    }

    // 物品与实体产出的固定输入都携带「已解析产物 + 每轮产出量」，按产出子类取用其一；
    // 引用解析失败与固定输入缺失都按失败回执，不退回执行期重新取值
    private static EffectExecution fromPlan(EffectPreparation preparation, SpawnPlanFactory factory) {
        if (preparation instanceof SpawnPlan(Item item, EntityType<?> entityType, int count, String failure)) {
            if (failure != null) {
                return failedExecution(failure);
            }
            return factory.create(item, entityType, count);
        }
        return missingPreparation();
    }

    // 固定输入缺失属于运行时接线缺失，直接失败而不是到期重新判定
    private static EffectExecution missingPreparation() {
        return budget -> EffectStep.done(EffectResult.failed(MISSING_PREPARATION));
    }

    // 引用在成功转化当刻已无法解析：按当刻记录的原因失败
    private static EffectExecution failedExecution(String detail) {
        return budget -> EffectStep.done(EffectResult.failed(detail));
    }

    // 从 SpawnPlan 创建执行状态：物品产出用 item，实体产出用 entityType
    @FunctionalInterface
    private interface SpawnPlanFactory {
        EffectExecution create(@Nullable Item item, @Nullable EntityType<?> entityType, int count);
    }

    /**
     * 候选落点的危险度评分：0 表示完全安全，分数越高越危险，{@link #SCORE_FATAL} 表示该落点根本不能生成。
     * 不可生成的三类硬边界是「没地方站」：越界、下方探测不到落点（虚空或深空）、生成碰撞箱与方块相交（被方块堵死）；
     * 其余情形（伤害方块、悬空高度、不符合原版放置要求）只累加分数，供整片范围都有危险时挑最轻的一个。
     *
     * @param type  产物实体类型，决定生成碰撞箱与原版放置要求
     * @param level 目标维度
     * @param pos   候选落点
     * @return 危险度分数
     */
    private static int spawnScore(EntityType<?> type, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() < level.getMinBuildHeight()) {
            return SCORE_FATAL;
        }
        // 用实体类型的生成碰撞箱检查完整体积，不创建实体（避免触发入世界副作用）
        AABB box = type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(box)) {
            return SCORE_FATAL;
        }
        int groundDistance = groundDistance(level, pos);
        // 脚下方探测范围内没有可落脚的位置：虚空或深空，实体只会一直下坠
        if (groundDistance == 0) {
            return SCORE_FATAL;
        }
        int score = (groundDistance - 1) * SCORE_FALL_PER_BLOCK;
        // 未登记放置类型的实体回退为「不限制」，不满足时只记一次轻罚
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            score += SCORE_PLACEMENT_MISMATCH;
        }
        score += hazardScore(level, pos) + hazardScore(level, pos.above());
        // 脚下的承托方块也可能是伤害方块（岩浆池上方、火堆顶面），实体落上去同样受伤
        score += hazardScore(level, pos.atY(pos.getY() - groundDistance));
        return score;
    }

    // 脚下方最近的落脚距离：1 表示正下方就站得住，0 表示探测范围内全是空气（虚空或深空）。
    // 非空气方块要有碰撞体积或本身是液体才算站得住：草、雪层一类的装饰方块不承托实体。
    private static int groundDistance(ServerLevel level, BlockPos pos) {
        int floor = level.getMinBuildHeight();
        for (int distance = 1; distance <= GROUND_PROBE; distance++) {
            int y = pos.getY() - distance;
            if (y < floor) {
                return 0;
            }
            BlockPos below = pos.atY(y);
            BlockState state = level.getBlockState(below);
            if (state.isAir()) {
                continue;
            }
            if (!state.getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty()) {
                return distance;
            }
        }
        return 0;
    }

    // 单个方块位置的伤害分值：按原版寻路的减分与伤害语义从重到轻收敛
    // （熔岩 > 火与营火 > 岩浆块 > 凋灵玫瑰 > 仙人掌 > 滴水石锥 > 甜浆果丛 > 细雪）。
    // 判据与实体类型无关：原版 EntityType.isBlockDangerous 会按火焰免疫豁免，这里不豁免，同一落点对任何产物给出同一结论。
    private static int hazardScore(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return SCORE_LAVA;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LAVA)) {
            return SCORE_LAVA;
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return SCORE_LAVA_CAULDRON;
        }
        if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)) {
            return SCORE_FIRE;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return SCORE_MAGMA_BLOCK;
        }
        if (state.is(Blocks.WITHER_ROSE)) {
            return SCORE_WITHER_ROSE;
        }
        if (state.is(Blocks.CACTUS)) {
            return SCORE_CACTUS;
        }
        if (state.is(Blocks.POINTED_DRIPSTONE)) {
            return SCORE_POINTED_DRIPSTONE;
        }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return SCORE_SWEET_BERRY_BUSH;
        }
        return state.is(Blocks.POWDER_SNOW) ? SCORE_POWDER_SNOW : 0;
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
     * 时间或工作量预算耗尽即让出。安全搜索的候选游标在整次交付内单调前进，已判定过的候选不再复查；
     * 完全安全的候选直接命中，整片范围都有危险时按危险度从低到高依次取用；
     * 所有候选都不可生成时按已生成数量收尾：一个都没生成按失败回执，已生成部分按真实产出结束。
     *
     * <p>区块等待、写前写后的预算双检、逐单位回执与收尾清理由 {@link DeliveryExecution} 承担，
     * 这里只描述实体怎么造出来、写到哪里、按什么节奏推进。候选点的检查额度只在本步有效，
     * 每步开头由基类的 {@code onStepStart()} 重置，用完就返回 {@code Busy} 让出，下个 tick 从断点继续。
     */
    private static final class EntityDelivery extends DeliveryExecution {
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
        // 可生成但带危险的候选：整片候选都有危险时按危险度从低到高依次取用；致命候选不进入此表
        private final List<FallbackCandidate> fallbacks = new ArrayList<>();
        // 当前实体的落点；positionResolved 为 true 且此处为 null 表示整片候选都不可生成
        private @Nullable BlockPos hit;
        // 命中候选的危险度：0 表示完全安全，大于 0 表示本次命中来自危险度回退
        private int hitScore;
        private boolean positionResolved;
        // 本步已做的候选判定数与本步判定上限：随步重置，避免把「每刻检查上限」累成整次交付上限
        private int checks;
        private int checkLimit;

        // 可生成但带危险的候选：危险度分数与落点
        private record FallbackCandidate(int score, BlockPos pos) {
        }

        private EntityDelivery(EffectContext context, EntityType<?> type, int count, int age, boolean safeSpawn) {
            super(context);
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
        protected @Nullable ChunkRequirement declaredRequirement() {
            if (spawned >= target) { return null; }
            // 已确定整片候选都不可生成：下一步就收尾，不再等待任何区块
            if (safeSpawn && positionResolved && hit == null) { return null; }
            return chunks;
        }

        @Override
        protected void onStepStart() {
            checks = 0;
            checkLimit = Math.max(1, context.positionSearchChecksPerTick());
        }

        // 产出下一个待写入世界的实体：安全落点未定时先在有界候选表里搜索，检查额度按步计费
        @Override
        protected Production produce(StepBudget budget) {
            if (spawned >= target) {
                return new Production.Finished(EffectResult.applied(0, STEPPED));
            }
            if (safeSpawn && !positionResolved) {
                // 快速路径：候选表首项就是原点本身，原点完全安全时一次判定即中，不进入候选表遍历
                if (searchCursor == 0) {
                    searchCursor = 1;
                    checks++;
                    // 计价：每检查一个候选点（含地面判据与碰撞箱、危险方块读取）记 1 个工作单位
                    budget.spend();
                    consider(center);
                }
                while (!positionResolved && checks < checkLimit) {
                    if (budget.exhausted()) {
                        break;
                    }
                    if (searchCursor >= SEARCH_OFFSETS.size()) {
                        // 候选表遍历完毕：没有完全安全的落点时按危险度从低到高取可生成候选
                        positionResolved = true;
                        FallbackCandidate fallback = takeLeastHazard();
                        hit = fallback != null ? fallback.pos() : null;
                        hitScore = fallback != null ? fallback.score() : -1;
                        break;
                    }
                    BlockPos candidate = center.offset(SEARCH_OFFSETS.get(searchCursor++));
                    checks++;
                    budget.spend();
                    consider(candidate);
                }
                if (!positionResolved) {
                    // 搜索未完成（候选上限或预算用尽）：从断点下刻继续，保持「最近优先」语义
                    return new Production.Busy();
                }
                observeSearch();
                if (hit == null) {
                    return new Production.Finished(noSafePositionResult());
                }
            }
            // 本步候选检查额度已用尽：先把写入推到下一步，保证单步的检查代价有上界
            if (checks >= checkLimit) {
                return new Production.Busy();
            }
            // 搜索可能已把本步余量耗尽，写前复查预算；回执与计费由基类在真实写入后统一处理
            if (budget.exhausted()) {
                return new Production.Busy();
            }
            return new Production.Next(new Unit(createEntity(hit), 1));
        }

        // 判定一个候选：完全安全（0 分）立即命中；否则记进可生成候选表，等整片候选遍历完按分数取用。
        // 致命候选不进入候选表，回退落点因此一定「下方有可落脚方块、没有被方块堵死」
        private void consider(BlockPos candidate) {
            int score = spawnScore(type, level, candidate);
            if (score == 0) {
                hit = candidate;
                hitScore = 0;
                positionResolved = true;
                return;
            }
            if (score < SCORE_FATAL) {
                fallbacks.add(new FallbackCandidate(score, candidate));
            }
        }

        // 取剩余候选里危险度最低的一个：同分取先判定的，保证落点可复现；表空表示整片候选都不可生成
        private @Nullable FallbackCandidate takeLeastHazard() {
            int bestIndex = -1;
            int bestScore = SCORE_FATAL;
            for (int index = 0; index < fallbacks.size(); index++) {
                FallbackCandidate candidate = fallbacks.get(index);
                if (candidate.score() < bestScore) {
                    bestScore = candidate.score();
                    bestIndex = index;
                }
            }
            return bestIndex < 0 ? null : fallbacks.remove(bestIndex);
        }

        // 整片候选都不可生成（虚空、被方块堵死、越界）：一个实体都没生成时整次效果按失败回执（不登记完成量），
        // 已生成部分只按真实产出结束，避免「生成 + 整单失败」双记
        private EffectResult noSafePositionResult() {
            return spawned == 0
                    ? EffectResult.failed(NO_SAFE_SPAWN_POSITION)
                    : EffectResult.applied(0, NO_SAFE_SPAWN_POSITION_PARTIAL);
        }

        // 每次落点搜索结束时观测一条：candidates_checked 为整次交付累计判定过的候选数，
        // score 为命中候选的危险度（-1 表示没有命中），least_hazard_fallback 标记本次命中是否来自危险度回退
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
                    "score", hit != null ? hitScore : -1,
                    "least_hazard_fallback", hit != null && hitScore > 0,
                    "x", hit != null ? hit.getX() : center.getX(),
                    "y", hit != null ? hit.getY() : center.getY(),
                    "z", hit != null ? hit.getZ() : center.getZ());
        }

        // 构造待写入世界的实体：安全搜索命中时用命中方块中心，未开启安全生成时沿用原点附近的随机偏移
        private Entity createEntity(@Nullable BlockPos position) {
            Entity entity = type.create(level);
            if (entity == null) {
                throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " 在该维度无法创建实体");
            }
            if (position != null) {
                entity.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            } else {
                entity.moveTo(origin.x + (level.random.nextDouble() - 0.5) * 0.5,
                        origin.y,
                        origin.z + (level.random.nextDouble() - 0.5) * 0.5,
                        0.0F, 0.0F);
            }
            if (entity instanceof AgeableMob ageableMob) {
                ageableMob.setAge(age);
            }
            return entity;
        }

        // 真实写入世界：加入失败由运行时统一捕获记为失败，不提前累计完成量；产物带新产物环境伤害保护
        @Override
        protected boolean write(Entity entity) {
            return EffectTargets.addEntityProductNow(context, entity);
        }

        @Override
        protected boolean isComplete() {
            return spawned >= target;
        }

        // 真实产出已逐实体回执，终态不再重复计入
        @Override
        protected EffectResult result() {
            return EffectResult.applied(0, STEPPED);
        }

        // 单步写入上限同时按剩余数量收敛：最后一批写到目标即出循环，终态不因预算正好用尽而拖到下一 tick
        @Override
        protected int unitsPerStep() {
            return Math.min(SPAWNS_PER_RUN, target - spawned);
        }

        // 单位之间没有最小间隔：本步还有余量就继续，用尽或写完一批后立即 Yield 续接
        @Override
        protected long intervalTicks() {
            return 0L;
        }

        @Override
        protected void onUnitWritten(Unit unit) {
            spawned++;
            // 命中的格子已被本实体占用：游标继续向后推进，下一个实体只判定未用过的候选
            hit = null;
            hitScore = 0;
            positionResolved = false;
        }
    }
}