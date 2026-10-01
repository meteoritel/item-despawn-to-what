package com.meteorite.itemdespawntowhat.core.type;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.registry.SimpleTypeRegistry;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnItemEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnXpEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.LightningEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ExplosionEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.WeatherEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeCatalystEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeFluidEffect;
import com.mojang.serialization.Codec;

/**
 * 内置效果类型注册表。
 * 效果记录带有「效果级 conditions」通用字段，因此需要条件表达式编解码器作为入参。
 * 说明：静态工厂命名为 effectType(...)，因为 Effect 接口已有无参实例方法 type()。
 */
public final class BuiltinEffectTypes {

    private BuiltinEffectTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 构建并冻结内置效果类型注册表
    public static TypeRegistry<EffectType<?>> create(Codec<ConditionExpression> expressionCodec) {
        SimpleTypeRegistry<EffectType<?>> registry = new SimpleTypeRegistry<>();
        registry.register(SpawnItemEffect.effectType(expressionCodec));
        registry.register(SpawnEntityEffect.effectType(expressionCodec));
        registry.register(PlaceBlockEffect.effectType(expressionCodec));
        registry.register(SpawnXpEffect.effectType(expressionCodec));
        registry.register(LootTableEffect.effectType(expressionCodec));
        registry.register(LightningEffect.effectType(expressionCodec));
        registry.register(ExplosionEffect.effectType(expressionCodec));
        registry.register(ArrowRainEffect.effectType(expressionCodec));
        registry.register(WeatherEffect.effectType(expressionCodec));
        registry.register(ConsumeSourceEffect.effectType(expressionCodec));
        registry.register(ConsumeCatalystEffect.effectType(expressionCodec));
        registry.register(ConsumeFluidEffect.effectType(expressionCodec));
        registry.freeze();
        return registry;
    }
}
