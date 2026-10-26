package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
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
 * 实际天气判定见 WeatherEvaluator（clear 需无雨无雷、rain 需有雨无雷、thunder 只看雷暴）。
 * JSON 示例：{ "type": "idtw:weather", "weather": "thunder" }
 */
public record WeatherCondition(Kind weather) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "weather");

    // 参数 JSON 字段名
    private static final String FIELD_WEATHER = "weather";

    // 参数声明：weather 必填；新建规则预写 clear，与编辑器「未填写」的空白叶区分开
    public static final EnumSpec<Kind> WEATHER = EnumSpec
            .required(FIELD_WEATHER, EnumCodecs.lowerCase(Kind.class), "weather_kind", Kind.values())
            .initial(Kind.CLEAR);
    public static final ParamList PARAMS = ParamList.of(WEATHER);

    // 参数编解码器：weather 必填
    public static MapCodec<WeatherCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                WEATHER.fieldFor(WeatherCondition::weather)
        ).apply(instance, WeatherCondition::new));
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<WeatherCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), WeatherCondition::validateParams, WeatherEvaluator::test,
                PARAMS);
    }

    // 参数语义校验：天气取值必填（codec 已拦截缺失，这里兜底程序化构造）
    public static boolean validateParams(WeatherCondition params, IssueCollector issues, String fieldPath) {
        return WEATHER.check(params.weather(), issues, fieldPath);
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
