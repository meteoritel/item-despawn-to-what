package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ExplosionExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * explosion：在触发位置产生爆炸。
 * visual_only 为真时只播放爆炸视觉与音效，不破坏方块、不伤害实体。
 * 注册为一次性效果（oneShot）：不参与候选容量计算，对同一源最多尝试一组。本类只承载参数模型与参数校验。
 */
public record ExplosionEffect(
        float power,
        boolean fire,
        boolean visualOnly,
        int delayTicks,
        double chance
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "explosion");

    // 类型专属参数字段名
    public static final String POWER_FIELD = "power";
    public static final String FIRE_FIELD = "fire";
    public static final String VISUAL_ONLY_FIELD = "visual_only";

    // 参数默认值与取值区间
    public static final float DEFAULT_POWER = 3.0F;
    public static final float MIN_POWER = 0.0F;
    public static final float MAX_POWER = 16.0F;
    public static final boolean DEFAULT_FIRE = false;
    public static final boolean DEFAULT_VISUAL_ONLY = false;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ExplosionEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.floatRange(MIN_POWER, MAX_POWER).optionalFieldOf(POWER_FIELD, DEFAULT_POWER)
                        .forGetter(ExplosionEffect::power),
                Codec.BOOL.optionalFieldOf(FIRE_FIELD, DEFAULT_FIRE).forGetter(ExplosionEffect::fire),
                Codec.BOOL.optionalFieldOf(VISUAL_ONLY_FIELD, DEFAULT_VISUAL_ONLY)
                        .forGetter(ExplosionEffect::visualOnly),
                CommonFields.delayTicks(ExplosionEffect::delayTicks),
                CommonFields.chance(ExplosionEffect::chance)
        ).apply(instance, (power, fire, visualOnly, delayTicks, chance) ->
                new ExplosionEffect(power, fire, visualOnly, delayTicks, chance)));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现执行器契约：爆炸只能经预算内的执行状态推进，直接 execute 一律拒绝；
    // 本类型没有自有的成功时输入，只声明准备阶段以让概率在成功转化当刻判定一次。
    public static EffectType<ExplosionEffect> effectType() {
        EffectExecutor<ExplosionEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(ExplosionEffect effect, EffectContext context) {
                return ExplosionExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(ExplosionEffect effect, ServerLevel level, Vec3 position,
                                             ItemEntity sourceEntity, ItemStack sourceStack, boolean fillOrigin) {
                // 覆盖半径的区块加载与爆炸本身都属执行期实时量，此时只标记本次参与成功当刻判定
                return EffectPreparation.ready();
            }

            @Override
            public EffectExecution start(ExplosionEffect effect, EffectContext context, @Nullable EffectPreparation preparation) {
                return ExplosionExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(), ExplosionEffect::validateParams, executor, true,
                ProductKind.WORLD);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ExplosionEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.power(), MIN_POWER, MAX_POWER, POWER_FIELD, issues,
                ParamChecks.child(fieldPath, POWER_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
