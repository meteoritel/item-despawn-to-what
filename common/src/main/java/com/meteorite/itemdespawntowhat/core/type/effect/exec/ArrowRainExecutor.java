package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * arrow_rain 执行器：从触发位置上方的可容纳空腔内分波生成箭矢，每支箭朝各自抽定的落点斜射，可携带药水效果。
 * rounds 语义：不随 rounds 缩放（不是「每个物品一份」的结果），也不声明产出容量需求。
 * 同候选、同槽位的多组在派发阶段合并为一次延迟任务，由批执行器按序打完并统一回执一次。
 * 次数、拾取模式与基准位置在创建执行状态时固定，之后只按各自到期刻逐波推进；
 * 每支箭矢真正加入世界后才通过 context.reportProgress(1) 回执真实完成量，
 * 全部落完的终态回执不再重复计入产出，只给出剩余增量 0。
 * 云底高度属于整场序列的性质：开场按触发点所在列向上扫描一次，之后每支箭都在该空腔内取生成点。
 * 每波的落点、生成点、初速与不准确度在产出阶段一次抽定并随待写单位保留：区块未加载时只等待，
 * 退避后重试，不提前落下也不重新抽取。
 * 药水效果属于本次转化准备的输入，在成功转化当刻按标签抽定后随准备结果携带，执行状态构造时接收，
 * 到期执行时既不重新解析也不重新抽取。
 * 约定：delay_ticks / chance 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ArrowRainExecutor {

    // 相邻两波箭矢的间隔范围（刻）：每波独立抽取，让箭成阵而不是整齐队列
    private static final int MIN_INTERVAL_TICKS = 1;
    private static final int MAX_INTERVAL_TICKS = 3;

    // 云底扫描起点（触发位置之上）与向上扫描的最大格数：遇到阻挡即停，室内自然贴着天花板
    private static final double CLOUD_SCAN_START_HEIGHT = 1.0;
    private static final int MAX_CLOUD_SCAN = 20;

    // 落点圆盘半径
    private static final double LANDING_RADIUS = 5.0;
    // 生成点相对落点的最大水平偏移：与云底高度之比决定入射角上界
    private static final double MAX_CLOUD_OFFSET = 7.0;
    private static final double MAX_ENTRY_ANGLE_DEGREES = 25.0;
    // 生成点竖直抖动幅度
    private static final double VERTICAL_JITTER = 1.0;

    // 每箭独立初速范围：低初速配合低云底，滞空时间长且落地时间彼此错开
    private static final float MIN_SPEED = 0.5F;
    private static final float MAX_SPEED = 2.0F;

    // 不准确度：多数箭带散布，少量箭接近完美，避免整片轨迹过于规整
    private static final float MIN_INACCURACY = 4.0F;
    private static final float MAX_INACCURACY = 10.0F;
    private static final float PERFECT_SHOT_CHANCE = 0.25F;

    // 生成点的空位重试次数：找不到就回退到云底中心
    private static final int SPAWN_AIR_RETRIES = 6;

    // 序列开始前要求的整片已加载半径（方块）：覆盖落点圆盘与生成点最大水平偏移，抽样阶段不再逐点查区块
    private static final int SERIES_CHUNK_RADIUS = (int) Math.ceil(LANDING_RADIUS + MAX_CLOUD_OFFSET);
    // 拒绝直接调用的原因：没有共享预算的实现无法按 tick 推进
    private static final String BUDGET_REQUIRED = "budget_required";
    // 缺少成功转化当刻固定输入的原因：本类型只能在准备阶段之后执行
    private static final String MISSING_PREPARATION = "missing_preparation";
    // 终态回执的详情：真实完成量已逐波回执，终态只描述序列本身
    private static final String ARROW_SERIES = "arrow_series";

    private ArrowRainExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 拒绝没有共享预算的直接执行，箭雨序列只能经执行状态在预算内推进。
     * @param effect 箭雨参数
     * @param context 规则执行上下文
     * @return 表示必须使用执行状态入口的失败回执，不做任何世界写入
     */
    public static EffectResult execute(ArrowRainEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态：药水效果取自准备结果，次数、拾取模式与基准位置取自本次转换。
     * @param effect 箭雨参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示本类型未按约定准备，属真失败
     * @return 可跨 tick 续接的箭雨序列执行状态
     */
    public static EffectExecution start(ArrowRainEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 没有准备结果说明调用方绕过了准备阶段：概率与抽定输入无从固定，按失败回执而非静默降级
            return budget -> EffectStep.done(EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId()));
        }
        if (preparation.gate() != null) {
            // 已判定不执行：不重新判定概率，也不做世界写入
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        if (preparation instanceof SeriesBatchPlan batch) {
            // 同候选同槽位的多组已合并：各场按自己的到期刻依次打完，只回执一次
            return new ArrowBatch(effect, batch.requests());
        }
        if (!(preparation instanceof ArrowRainEffect.Prepared(List<MobEffectInstance> potionEffects, String reason))) {
            // 准备结果由本类型自己的记录承载；其它实现表示固定输入不匹配，同样不降级执行
            return budget -> EffectStep.done(EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId()));
        }
        if (reason != null) {
            // 成功转化当刻就无法解析药水引用：按固定下来的原因失败，执行期不再重试也不改写结果
            return budget -> EffectStep.done(EffectResult.failed(reason));
        }
        return new ArrowSeries(effect, context, potionEffects);
    }

    /**
     * 同候选、同槽位的多组箭雨合并成的排期单：每组保留自己的到期刻与成功转化当刻固定下来的药水效果，
     * 由批执行器按序打完后统一回执一次。只在任务排期前累积请求，执行期不再修改。
     */
    public static final class SeriesBatchPlan implements EffectPreparation {

        /**
         * 一组箭雨的排期与本组固定输入。
         * @param context 本组上下文，进度与随机源都按组归属
         * @param potionEffects 本组这一场共用的药水效果；套用到箭矢时逐个复制
         * @param readyTick 本组的到期刻，未到期时不推进
         */
        public record Request(EffectContext context, List<MobEffectInstance> potionEffects, long readyTick) {
            public Request {
                potionEffects = List.copyOf(potionEffects);
            }
        }

        private final List<Request> requests = new ArrayList<>();

        /**
         * 追加一组请求。次数与拾取模式取自效果参数，同一槽位各组恒定，不逐组复制。
         * @param context 本组上下文
         * @param potionEffects 本组固定下来的药水效果
         * @param readyTick 本组到期刻
         */
        public void add(EffectContext context, List<MobEffectInstance> potionEffects, long readyTick) {
            requests.add(new Request(context, potionEffects, readyTick));
        }

        /**
         * 排期完成后交给批执行器的只读请求序列。
         * @return 本次合并的全部请求，按所属组顺序排列
         */
        public List<Request> requests() {
            return List.copyOf(requests);
        }
    }

    /**
     * 批执行器：按请求顺序推进各组箭雨序列，各组到期刻与固定输入互不影响。
     * 一场打完后立即接下一场，场间不额外空档；全部场次结束才回执一次。
     */
    private static final class ArrowBatch implements EffectExecution {
        private final ArrowRainEffect effect;
        private final List<SeriesBatchPlan.Request> requests;
        private int requestIndex;
        // 当前正在推进的这一场：场次之间置空，等待期间保留以续接区块与抽样状态
        private @Nullable ArrowSeries current;

        ArrowBatch(ArrowRainEffect effect, List<SeriesBatchPlan.Request> requests) {
            this.effect = effect;
            this.requests = requests;
        }

        @Override
        public EffectStep advance(ServerTickBudget budget) {
            while (true) {
                if (requestIndex >= requests.size()) {
                    return EffectStep.done(EffectResult.applied(0, ARROW_SERIES));
                }
                SeriesBatchPlan.Request request = requests.get(requestIndex);
                if (request.readyTick() > budget.gameTime()) {
                    // 未到期：交给调用方按到期刻唤醒，不占用本次预算
                    return new EffectStep.Delay(request.readyTick());
                }
                if (current == null) {
                    current = new ArrowSeries(effect, request.context(), request.potionEffects());
                }
                EffectStep step = current.advance(budget);
                if (step instanceof EffectStep.Done) {
                    // 这一场打完：游标前进后接着打下一场
                    current = null;
                    requestIndex++;
                    continue;
                }
                return step;
            }
        }

        // 区块需求跟着当前场走：场次之间为 null，由调用方沿用触发位置需求
        @Override
        public @Nullable ChunkRequirement requiredChunks() {
            return current == null ? null : current.requiredChunks();
        }

        @Override
        public void onFinished(EffectResult result) {
            if (current != null) {
                current.onFinished(result);
                current = null;
            }
        }

        @Override
        public void onCancelled(CancelReason reason) {
            if (current != null) {
                current.onCancelled(reason);
                current = null;
            }
        }
    }

    /**
     * 箭雨序列的推进状态：逐波生成，区块等待、计费与回执交由交付循环统一处理。
     * 云底在开场扫描一次；每波的生成点、落点、初速与不准确度在产出阶段一次抽定、箭矢在写入前一次构造，
     * 二者都跨等待保留：区块未就绪只返回等待，不重抽也不重建；每支箭矢真正加入世界之后才回执完成量。
     */
    private static final class ArrowSeries extends DeliveryExecution {
        private final ChunkRequirement chunks;
        private final int count;
        private final AbstractArrow.Pickup pickup;
        // 本次转化准备阶段解析好的药水效果：整场序列共用同一份，不在到期时重抽
        private final List<MobEffectInstance> potionEffects;
        // 转换当刻固定的基准位置：后续不再读取源实体
        private final Vec3 origin;
        private int launched;
        // 尚未落下的这一波：已抽取的生成点与弹道参数保留到真正生成，等待区块时不重抽
        private @Nullable PendingShot pendingShot;
        // 云底高度只与触发位置有关：整场序列共用一次扫描结果
        private boolean cloudResolved;
        private double cloudY;

        ArrowSeries(ArrowRainEffect effect, EffectContext context, List<MobEffectInstance> potionEffects) {
            super(context);
            this.count = effect.count();
            this.pickup = toVanillaPickup(effect.pickup());
            this.potionEffects = potionEffects;
            this.origin = context.position();
            this.chunks = ChunkRequirement.around(BlockPos.containing(origin), SERIES_CHUNK_RADIUS);
        }

        // 整片已加载范围覆盖云底扫描列与全部可能的生成点、落点，抽样与写入都落在这里面
        @Override
        protected @Nullable ChunkRequirement declaredRequirement() {
            return chunks;
        }

        // 产出这一波待写入的箭矢：扫描、抽样与箭矢构造都在区块就绪之后进行，等待期间不重抽也不重建
        @Override
        protected Production produce(StepBudget budget) {
            ServerLevel level = context.level();
            if (!chunks.isReady(level)) {
                // 云底扫描与空位检查都读取方块，整片范围就绪后才开始抽样
                return new Production.Await(chunks);
            }
            if (!cloudResolved) {
                cloudY = scanCloud(level);
                cloudResolved = true;
            }
            PendingShot shot = pendingShot;
            if (shot == null) {
                shot = sampleShot(level);
                pendingShot = shot;
            }
            return new Production.Next(new Unit(build(level, shot), 1));
        }

        // 真正写入世界：区块在写入前卸载时返回 false，这一支箭矢由交付循环保留到下次推进
        @Override
        protected boolean write(Entity entity) {
            return EffectTargets.addEntityNow(context, entity, false);
        }

        @Override
        protected void onUnitWritten(Unit unit) {
            launched++;
            pendingShot = null;
        }

        @Override
        protected boolean isComplete() {
            return launched >= count;
        }

        // 全部落完：真实完成量已逐波回执，终态只给出剩余增量，不重复累计
        @Override
        protected EffectResult result() {
            return EffectResult.applied(0, ARROW_SERIES);
        }

        // 单步只落一波：单次实体写入不可抢占，波间用正常间隔让出
        @Override
        protected int unitsPerStep() {
            return 1;
        }

        // 波间间隔每波独立抽取：固定间隔会让整片箭矢看起来像排队下落
        @Override
        protected long intervalTicks() {
            return MIN_INTERVAL_TICKS
                    + context.random().nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
        }

        // 从触发点上方逐格向上找可容纳箭矢的空腔，遇阻即停；一个空位都没有时回退到触发点上方一格，
        // 该极端情况下箭可能仍生成在方块里，属可接受退化
        private double scanCloud(ServerLevel level) {
            BlockPos base = BlockPos.containing(origin.x, origin.y + CLOUD_SCAN_START_HEIGHT, origin.z);
            BlockPos lastAir = null;
            for (int i = 0; i < MAX_CLOUD_SCAN; i++) {
                BlockPos pos = base.above(i);
                if (pos.getY() >= level.getMaxBuildHeight() - 1) {
                    break;
                }
                if (!isPassable(level, pos)) {
                    break;
                }
                lastAir = pos;
            }
            return lastAir != null
                    ? lastAir.getY() + 0.5
                    : origin.y + CLOUD_SCAN_START_HEIGHT + 0.5;
        }

        // 抽取这一波的落点、生成点、初速与不准确度：等待区块期间保留，不重复抽取。
        // 生成点相对落点偏移而不是相对触发点，入射角上界才由最大偏移与云底高度之比稳定限住
        private PendingShot sampleShot(ServerLevel level) {
            RandomSource random = context.random();
            Vec3 aim = EffectTargets.randomHorizontalDisc(random, origin, LANDING_RADIUS, 0.0);
            double drop = cloudY - aim.y;
            double maxOffset = Math.min(MAX_CLOUD_OFFSET,
                    drop * Math.tan(Math.toRadians(MAX_ENTRY_ANGLE_DEGREES)));
            Vec3 spawn = null;
            for (int attempt = 0; attempt < SPAWN_AIR_RETRIES; attempt++) {
                double jitterY = (random.nextDouble() * 2.0 - 1.0) * VERTICAL_JITTER;
                Vec3 candidate = EffectTargets.randomHorizontalDisc(random,
                        new Vec3(aim.x, cloudY + jitterY, aim.z), maxOffset, 0.0);
                if (candidate.y > aim.y + 0.5 && isPassable(level, BlockPos.containing(candidate))) {
                    spawn = candidate;
                    break;
                }
            }
            if (spawn == null) {
                // 云底中心由扫描保证是空位
                spawn = new Vec3(origin.x, cloudY, origin.z);
            }
            float speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
            float inaccuracy = random.nextFloat() < PERFECT_SHOT_CHANCE
                    ? random.nextFloat()
                    : MIN_INACCURACY + random.nextFloat() * (MAX_INACCURACY - MIN_INACCURACY);
            return new PendingShot(spawn, aim, speed, inaccuracy);
        }

        // 构造单支箭矢：从生成点朝落点射出，射向与弹道参数在此之前已抽定；
        // 重力会让实际落点比落点略偏短，这一点由观感兜住，不做解析补偿
        private Arrow build(ServerLevel level, PendingShot shot) {
            Arrow arrow = EntityType.ARROW.create(level);
            if (arrow == null) {
                throw new IllegalStateException("无法创建箭矢实体");
            }
            Vec3 direction = shot.aim().subtract(shot.spawn());
            arrow.moveTo(shot.spawn().x, shot.spawn().y, shot.spawn().z, 0.0F, 90.0F);
            arrow.shoot(direction.x, direction.y, direction.z, shot.speed(), shot.inaccuracy());
            arrow.setOwner(null);
            arrow.pickup = pickup;
            for (MobEffectInstance potionEffect : potionEffects) {
                arrow.addEffect(new MobEffectInstance(potionEffect));
            }
            return arrow;
        }

        // 可容纳箭矢的空位：碰撞形状为空且不是岩浆；水可以生成，箭落进去会减速
        private static boolean isPassable(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.getCollisionShape(level, pos).isEmpty()
                    && !level.getFluidState(pos).is(FluidTags.LAVA);
        }

        // 一波已抽定的弹道：生成点、落点、初速与不准确度整体保留，写入重试期间原样复用
        private record PendingShot(Vec3 spawn, Vec3 aim, float speed, float inaccuracy) {}
    }

    // 参数枚举到原版拾取模式的映射
    private static AbstractArrow.Pickup toVanillaPickup(ArrowRainEffect.Pickup pickup) {
        return switch (pickup) {
            case DISALLOWED -> AbstractArrow.Pickup.DISALLOWED;
            case ALLOWED -> AbstractArrow.Pickup.ALLOWED;
            case CREATIVE_ONLY -> AbstractArrow.Pickup.CREATIVE_ONLY;
        };
    }

    /**
     * 解析药水效果引用：非 tag 引用解析失败即抛异常，tag 展开为空则跳过该条。
     * 只在成功转化当刻的准备阶段调用一次，结果随准备结果携带，序列推进期间不再重新抽取。
     * 返回值是只读列表，元素是原版可变的 {@link MobEffectInstance}；真正复制发生在套用到箭矢时
     * （生成每支箭都 {@code new MobEffectInstance(potionEffect)}），因此本次抽取结果不会被某支箭的后续修改污染。
     * @param specs 参数中声明的药水效果列表，可为 null
     * @param random 抽取 tag 成员所用的随机源
     * @return 本次解析出的药水效果实例的只读列表；元素为原版可变实例，套用时逐个复制
     * @throws IllegalStateException 参数里写的是具体药水 id 却解析不到该药水时抛出（tag 展开为空不抛，只跳过该条）
     */
    public static List<MobEffectInstance> resolvePotionEffects(List<ArrowRainEffect.PotionEffectSpec> specs,
                                                               RandomSource random) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<MobEffectInstance> result = new ArrayList<>(specs.size());
        for (ArrowRainEffect.PotionEffectSpec spec : specs) {
            TaggedId reference = spec.effect();
            MobEffect effect = EffectTargets.resolve(reference, BuiltInRegistries.MOB_EFFECT, random);
            if (effect == null) {
                if (reference != null && !reference.tag()) {
                    throw new IllegalStateException("potion_effects 无法解析药水效果 " + reference.serialized());
                }
                continue;
            }
            result.add(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                    spec.durationTicks(), spec.amplifier()));
        }
        return List.copyOf(result);
    }
}
