package com.meteorite.itemdespawntowhat.core.type.effect;

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
import org.jetbrains.annotations.Nullable;

/*** 统一实体生成效果：按 variant 分发产物，公共执行字段只定义一次。 */
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

    public static EffectType<SpawnEntityEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), SpawnEntityEffect::validateParams, SpawnEntityExecutor::execute);
    }

    public static boolean validateParams(SpawnEntityEffect effect, IssueCollector issues, String path) {
        if (effect.product() == null) {
            issues.error("请选择实体产出子类", null, path + ".variant");
            return false;
        }
        return effect.product().validate(issues, path);
    }

    @Override public ResourceLocation type() { return ID; }
}
