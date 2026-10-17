package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * arrow_rain 执行器：按间隔在触发位置上方分波生成箭矢，可携带药水效果。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制。
 * 次数、拾取模式与基准位置在创建执行状态时固定，之后只按各自到期刻逐波推进；
 * 每支箭矢真正加入世界后才通过 context.reportProgress(1) 回执真实完成量，
 * 全部落完的终态回执不再重复计入产出，只给出剩余增量 0。
 * 区块未加载时保留尚未落下的这一波及其已抽取的落点，退避后重试，不提前落下也不重新抽取。
 * 药水效果属于本次转化准备的输入，在成功转化当刻按标签抽定后随准备结果携带，执行状态构造时接收，
 * 到期执行时既不重新解析也不重新抽取。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ArrowRainExecutor {

    // 相邻两波箭矢的间隔（刻）
    public static final int INTERVAL_TICKS = 2;
    // 生成高度（触发位置之上）
    public static final int SPAWN_HEIGHT = 80;
    // 水平散布半径
    public static final double SPREAD_RADIUS = 5.0;
    // 箭矢初速度
    public static final float ARROW_SPEED = 4.5F;
    // 水平抖动幅度
    public static final float HORIZONTAL_JITTER = 0.08F;

    // 等待目标区块加载时的退避刻数
    private static final int CHUNK_RETRY_TICKS = 20;
    // 序列开始前要求的整片已加载半径：生成位置可能越过源区块边界
    private static final int SERIES_CHUNK_RADIUS = 6;
    // 拒绝直接调用的原因：没有共享预算的实现无法按 tick 推进
    private static final String BUDGET_REQUIRED = "budget_required";
    // 缺少成功转化当刻固定输入的原因：本类型只能在准备阶段之后执行
    private static final String MISSING_PREPARATION = "missing_preparation";

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
            // 没有准备结果说明调用方绕过了准备阶段：条件与概率无从固定，按失败回执而非静默降级
            return budget -> EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId());
        }
        if (preparation.gate() != null) {
            // 已判定不执行：不重新判定条件或概率，也不做世界写入
            String reason = preparation.gate();
            return budget -> EffectResult.skipped(reason);
        }
        if (!(preparation instanceof ArrowRainEffect.Prepared fixed)) {
            // 准备结果由本类型自己的记录承载；其它实现表示固定输入不匹配，同样不降级执行
            return budget -> EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId());
        }
        if (fixed.failure() != null) {
            // 成功转化当刻就无法解析药水引用：按固定下来的原因失败，执行期不再重试也不改写结果
            String reason = fixed.failure();
            return budget -> EffectResult.failed(reason);
        }
        return new ArrowSeries(effect, context, fixed.potionEffects());
    }

    /** 箭雨序列的推进状态：逐波生成，间隔与区块等待由本状态内的到期刻门控。 */
    private static final class ArrowSeries implements EffectExecution {
        private final EffectContext context;
        private final int count;
        private final AbstractArrow.Pickup pickup;
        // 本次转化准备阶段解析好的药水效果：整场序列共用同一份，不在到期时重抽
        private final List<MobEffectInstance> potionEffects;
        // 转换当刻固定的基准位置：后续不再读取源实体
        private final Vec3 origin;
        private int launched;
        private long nextDueTick;
        // 尚未落下的这一波：已抽取的落点保留到真正生成，等待区块时不重抽
        private boolean pendingTarget;
        private double pendingX;
        private double pendingZ;
        private float pendingJitterX;
        private float pendingJitterZ;
        private EffectResult terminal;

        ArrowSeries(ArrowRainEffect effect, EffectContext context, List<MobEffectInstance> potionEffects) {
            this.context = context;
            this.count = effect.count();
            this.pickup = toVanillaPickup(effect.pickup());
            this.potionEffects = potionEffects;
            this.origin = context.position();
            this.nextDueTick = context.level().getGameTime();
        }

        @Override
        public @Nullable EffectResult advance(ServerTickBudget budget) {
            if (terminal != null) {
                return terminal;
            }
            ServerLevel level = context.level();
            long now = level.getGameTime();
            if (now < nextDueTick) {
                return null;
            }
            if (!LoadedChunks.containsArea(level, BlockPos.containing(origin), SERIES_CHUNK_RADIUS)) {
                // 整片未加载：本次不计入账目，退避后仍在同一基准位置重来
                nextDueTick = now + CHUNK_RETRY_TICKS;
                return null;
            }
            if (launched >= count) {
                return finish();
            }
            if (budget.exhausted()) {
                return null;
            }
            if (!pendingTarget) {
                sampleTarget(level.random);
                pendingTarget = true;
            }
            BlockPos column = BlockPos.containing(pendingX, origin.y + SPAWN_HEIGHT, pendingZ);
            if (!LoadedChunks.contains(level, column)) {
                // 生成列未加载：保留这一波及其落点，退避后重试，不推进次数
                nextDueTick = now + CHUNK_RETRY_TICKS;
                return null;
            }
            spawn(level);
            context.reportProgress(1);
            launched++;
            pendingTarget = false;
            nextDueTick = now + INTERVAL_TICKS;
            if (launched >= count) {
                return finish();
            }
            return null;
        }

        // 全部落完：真实完成量已逐波回执，终态只给出剩余增量，不重复累计
        private EffectResult finish() {
            terminal = EffectResult.applied(0, "arrow_series").asOneShot();
            return terminal;
        }

        // 抽取这一波的落点与水平抖动：等待区块期间保留，不重复抽取
        private void sampleTarget(RandomSource random) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double spread = Math.sqrt(random.nextDouble()) * SPREAD_RADIUS;
            pendingX = origin.x + Math.cos(angle) * spread;
            pendingZ = origin.z + Math.sin(angle) * spread;
            pendingJitterX = (random.nextFloat() * 2.0F - 1.0F) * HORIZONTAL_JITTER;
            pendingJitterZ = (random.nextFloat() * 2.0F - 1.0F) * HORIZONTAL_JITTER;
        }

        // 生成单支向下坠落的箭矢
        private void spawn(ServerLevel level) {
            Arrow arrow = EntityType.ARROW.create(level);
            if (arrow == null) {
                throw new IllegalStateException("无法创建箭矢实体");
            }
            arrow.moveTo(pendingX, origin.y + SPAWN_HEIGHT, pendingZ, 0.0F, 90.0F);
            arrow.shoot(pendingJitterX, -1.0, pendingJitterZ, ARROW_SPEED, 0.0F);
            arrow.setOwner(null);
            arrow.pickup = pickup;
            for (MobEffectInstance potionEffect : potionEffects) {
                arrow.addEffect(new MobEffectInstance(potionEffect));
            }
            // 生成列已确认加载：此处加入世界是即时完成的，回执才代表真实落箭
            EffectTargets.addEntity(context, arrow);
            StepWorkCounter.report(1);
        }
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
     * @param specs 参数中声明的药水效果列表，可为 null
     * @param random 抽取 tag 成员所用的随机源
     * @return 本次解析出的药水效果实例，元素为不可变值
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
