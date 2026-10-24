package com.meteorite.itemdespawntowhat.core.type;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.registry.SimpleTypeRegistry;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.LightningEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ExplosionEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.WeatherEffect;

/**
 * 内置效果类型注册表。
 * 说明：静态工厂命名为 effectType()，因为 Effect 接口已有无参实例方法 type()。
 */
public final class BuiltinEffectTypes {

    private BuiltinEffectTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 构建并冻结内置效果类型注册表 */
    public static TypeRegistry<EffectType<?>> create() {
        SimpleTypeRegistry<EffectType<?>> registry = createMutable();
        registry.freeze();
        return registry;
    }

    /** 装配器在第三方 SPI 注册结束后统一冻结。 */
    public static SimpleTypeRegistry<EffectType<?>> createMutable() {
        SimpleTypeRegistry<EffectType<?>> registry = new SimpleTypeRegistry<>();
        registry.register(SpawnEntityEffect.effectType());
        registry.register(PlaceBlockEffect.effectType());
        registry.register(LootTableEffect.effectType());
        registry.register(LightningEffect.effectType());
        registry.register(ExplosionEffect.effectType());
        registry.register(ArrowRainEffect.effectType());
        registry.register(WeatherEffect.effectType());
        return registry;
    }
}
