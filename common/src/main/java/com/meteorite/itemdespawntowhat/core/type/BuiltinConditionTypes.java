package com.meteorite.itemdespawntowhat.core.type;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistrar;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.DimensionCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.WeatherCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.OutdoorCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.SurroundingBlocksCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.FluidPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.TimeOfDayCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.YLevelCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.LightLevelCondition;

/**
 * 内置条件类型注册表。
 * 条件类型之间没有依赖，因此不需要表达式编解码器即可构建；
 * 规则级条件树在注册表冻结后用 RuleCodecs.conditionExpressionCodec 构建，构建顺序为：
 * BuiltinConditionTypes → RuleCodecs.conditionExpressionCodec → BuiltinEffectTypes。
 * 说明：静态工厂命名为 conditionType()，因为 Condition 接口已有无参实例方法 type()。
 */
public final class BuiltinConditionTypes {

    private BuiltinConditionTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 将内置类型登记到装配器提供的窗口；本方法不负责冻结。 */
    public static void registerInto(TypeRegistrar<ConditionType<?>> registry) {
        registry.register(DimensionCondition.conditionType());
        registry.register(BiomeCondition.conditionType());
        registry.register(WeatherCondition.conditionType());
        registry.register(OutdoorCondition.conditionType());
        registry.register(SurroundingBlocksCondition.conditionType());
        registry.register(CatalystPresentCondition.conditionType());
        registry.register(FluidPresentCondition.conditionType());
        registry.register(TimeOfDayCondition.conditionType());
        registry.register(YLevelCondition.conditionType());
        registry.register(LightLevelCondition.conditionType());
    }
}
