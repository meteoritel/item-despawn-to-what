package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * arrow_rain 执行器：按间隔在触发位置上方分波生成箭矢，可携带药水效果。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果）。
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
    public static final float ARROW_SPEED = 3.5F;
    // 水平抖动幅度
    public static final float HORIZONTAL_JITTER = 0.08F;

    private ArrowRainExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(ArrowRainEffect effect, EffectContext context) {
        AbstractArrow.Pickup pickup = toVanillaPickup(effect.pickup());
        List<MobEffectInstance> potionEffects = resolvePotionEffects(effect.potionEffects(), context.random());
        ServerLevel level = context.level();
        Vec3 origin = context.position();
        for (int i = 0; i < effect.count(); i++) {
            int delay = i * INTERVAL_TICKS;
            // 延迟任务绑定维度与位置，不依赖源实体存活
            context.schedule(delay, () -> spawnArrow(level, origin, pickup, potionEffects));
        }
    }

    // 生成单支向下坠落的箭矢
    private static void spawnArrow(ServerLevel level, Vec3 origin, AbstractArrow.Pickup pickup,
                                   List<MobEffectInstance> potionEffects) {
        RandomSource random = level.random;
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            throw new IllegalStateException("无法创建箭矢实体");
        }
        double angle = random.nextDouble() * 2.0 * Math.PI;
        double spread = Math.sqrt(random.nextDouble()) * SPREAD_RADIUS;
        arrow.moveTo(origin.x + Math.cos(angle) * spread,
                origin.y + SPAWN_HEIGHT,
                origin.z + Math.sin(angle) * spread,
                0.0F, 90.0F);
        float jitterX = (random.nextFloat() * 2.0F - 1.0F) * HORIZONTAL_JITTER;
        float jitterZ = (random.nextFloat() * 2.0F - 1.0F) * HORIZONTAL_JITTER;
        arrow.shoot(jitterX, -1.0, jitterZ, ARROW_SPEED, 0.0F);
        arrow.setOwner(null);
        arrow.pickup = pickup;
        for (MobEffectInstance potionEffect : potionEffects) {
            arrow.addEffect(new MobEffectInstance(potionEffect));
        }
        level.addFreshEntity(arrow);
    }

    // 参数枚举到原版拾取模式的映射
    private static AbstractArrow.Pickup toVanillaPickup(ArrowRainEffect.Pickup pickup) {
        return switch (pickup) {
            case DISALLOWED -> AbstractArrow.Pickup.DISALLOWED;
            case ALLOWED -> AbstractArrow.Pickup.ALLOWED;
            case CREATIVE_ONLY -> AbstractArrow.Pickup.CREATIVE_ONLY;
        };
    }

    // 预先解析药水效果引用：非 tag 引用解析失败即抛异常，tag 展开为空则跳过该条
    private static List<MobEffectInstance> resolvePotionEffects(List<ArrowRainEffect.PotionEffectSpec> specs,
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
        return result;
    }
}
