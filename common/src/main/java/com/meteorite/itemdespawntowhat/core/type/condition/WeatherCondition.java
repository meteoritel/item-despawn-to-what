package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.WeatherEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件类型 weather：按掉落物所在维度当前的天气匹配。
 * 参数 weather 必填，取值为 clear / rain / thunder（解析大小写不敏感，输出统一小写）。
 * 实际天气判定见 WeatherEvaluator（语义与旧实现一致：clear 需无雨无雷、rain 需有雨无雷、thunder 只看雷暴）。
 * JSON 示例：{ "type": "itemdespawntowhat:weather", "weather": "thunder" }
 */
public record WeatherCondition(boolean negated, Kind weather) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "weather");

    // 参数 JSON 字段名
    private static final String FIELD_WEATHER = "weather";

    // 参数编解码器：weather 必填
    public static final MapCodec<WeatherCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(WeatherCondition::negated),
            EnumCodecs.lowerCase(Kind.class).fieldOf(FIELD_WEATHER).forGetter(WeatherCondition::weather)
    ).apply(instance, WeatherCondition::new));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<WeatherCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, WeatherCondition::validateParams, WeatherEvaluator::test);
    }

    // 参数语义校验：天气取值必填（codec 已拦截缺失，这里兜底程序化构造）
    public static boolean validateParams(WeatherCondition params, IssueCollector issues, String fieldPath) {
        return ParamChecks.required(params.weather(), FIELD_WEATHER, issues,
                ParamChecks.child(fieldPath, FIELD_WEATHER));
    }

    /**
     * 天气取值；JSON 取值由 core/type/EnumCodecs 统一为小写下划线且解析大小写不敏感。
     */
    public enum Kind {

        // 晴朗
        CLEAR,
        // 下雨
        RAIN,
        // 雷暴
        THUNDER
    }
}
