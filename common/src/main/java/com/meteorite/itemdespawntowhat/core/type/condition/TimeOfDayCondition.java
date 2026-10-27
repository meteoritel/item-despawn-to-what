package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.TimeOfDayEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件类型 time_of_day：按世界时间（0..23999 刻）区间匹配。
 * 参数 from / to 必填且均在 [0,23999]；跨零点用 from > to 表达（如 22000 → 2000），不视为错误。
 * 世界时间读取与区间判定见 TimeOfDayEvaluator（把 dayTime 取模到一天之内）。
 * JSON 示例：{ "type": "idtw:time_of_day", "from": 13000, "to": 23000 }
 */
public record TimeOfDayCondition(int from, int to) implements Condition {

    /** 类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "time_of_day");

    // 参数 JSON 字段名
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";

    // 一天内的刻数范围（与原版 dayTime 取模 24000 一致）
    private static final int MIN_TICK = 0;
    private static final int MAX_TICK = 23999;

    /** 参数声明：codec 接受任意整数（沿用「解码宽、校验严」），语义校验落在 [0,23999]；新建规则预写整日区间 */
    public static final IntSpec FROM = IntSpec.required(FIELD_FROM, MIN_TICK, MAX_TICK)
            .widened()
            .initial(MIN_TICK);
    public static final IntSpec TO = IntSpec.required(FIELD_TO, MIN_TICK, MAX_TICK)
            .widened()
            .initial(MAX_TICK);
    public static final ParamList<TimeOfDayCondition> PARAMS = ParamList.of(
            FROM.bind(TimeOfDayCondition::from),
            TO.bind(TimeOfDayCondition::to));

    /** 参数编解码器：from / to 均必填 */
    public static MapCodec<TimeOfDayCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(FROM),
                PARAMS.field(TO)
        ).apply(instance, TimeOfDayCondition::new));
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    /** 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突） */
    public static ConditionType<TimeOfDayCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), TimeOfDayEvaluator::test,
                PARAMS);
    }

}
