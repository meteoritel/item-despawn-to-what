package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnEntityExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 统一实体生成效果：按 variant 分发产物，公共执行字段只定义一次。 */
public record SpawnEntityEffect(EntityProduct product, int delayTicks, double chance,
                                @Nullable ConditionExpression conditions) implements Effect {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "spawn_entity");

    // 专用参数与公共字段同层编码，方便直接使用子类编辑表单。
    public static MapCodec<SpawnEntityEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                EntityProduct.CODEC.forGetter(SpawnEntityEffect::product),
                CommonFields.delayTicks(SpawnEntityEffect::delayTicks),
                CommonFields.chance(SpawnEntityEffect::chance),
                CommonFields.optionalConditions(SpawnEntityEffect::conditions, expressionCodec)
        ).apply(instance, (product, delay, chance, conditions) ->
                new SpawnEntityEffect(product, delay, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现准备入口：经验产出要在成功转化当刻固定条件与概率，物品与通用实体不参与准备。
    public static EffectType<SpawnEntityEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        EffectExecutor<SpawnEntityEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(SpawnEntityEffect effect, EffectContext context) {
                return SpawnEntityExecutor.execute(effect, context);
            }

            @Override
            public @Nullable EffectPreparation prepare(SpawnEntityEffect effect, ServerLevel level, Vec3 position,
                                                       ItemStack sourceStack, boolean fillOrigin) {
                return SpawnEntityExecutor.prepare(effect, level, position, sourceStack, fillOrigin);
            }

            @Override
            public EffectExecution start(SpawnEntityEffect effect, EffectContext context,
                                         @Nullable EffectPreparation preparation) {
                return SpawnEntityExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(expressionCodec), SpawnEntityEffect::validateParams, executor);
    }

    // 参数语义校验：子类选择必填，随后交给对应子类校验自身字段
    public static boolean validateParams(SpawnEntityEffect effect, IssueCollector issues, String path) {
        if (effect.product() == null) {
            issues.error("请选择实体产出子类", null, path + ".variant");
            return false;
        }
        return effect.product().validate(issues, path);
    }

    @Override public ResourceLocation type() { return ID; }

    // 效果级条件替换：其余参数原样保留，供运行期门槛投影重建真实效果类型
    @Override public SpawnEntityEffect withConditions(@Nullable ConditionExpression conditions) {
        return new SpawnEntityEffect(product, delayTicks, chance, conditions);
    }
}
