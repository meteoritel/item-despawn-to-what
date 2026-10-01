package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件类型 time_of_day：按世界时间（0..23999 刻）区间匹配。
 * 参数 from / to 必填且均在 [0,23999]；跨零点用 from > to 表达（如 22000 → 2000），不视为错误。
 * 说明：世界时间的读取属阶段③ 的求值器，本阶段只做参数模型与校验。
 * JSON 示例：{ "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
 */
public record TimeOfDayCondition(boolean negated, int from, int to) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "time_of_day");

    // 参数 JSON 字段名
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";

    // 一天内的刻数范围（与原版 dayTime 取模 24000 一致）
    private static final int MIN_TICK = 0;
    private static final int MAX_TICK = 23999;

    // 参数编解码器：from / to 均必填
    public static final MapCodec<TimeOfDayCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(TimeOfDayCondition::negated),
            Codec.INT.fieldOf(FIELD_FROM).forGetter(TimeOfDayCondition::from),
            Codec.INT.fieldOf(FIELD_TO).forGetter(TimeOfDayCondition::to)
    ).apply(instance, TimeOfDayCondition::new));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<TimeOfDayCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, TimeOfDayCondition::validateParams);
    }

    // 参数语义校验：两端都必须落在 [0,23999]；from > to 表示跨零点，合法
    public static boolean validateParams(TimeOfDayCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.inRange(params.from(), MIN_TICK, MAX_TICK, FIELD_FROM, issues,
                ParamChecks.child(fieldPath, FIELD_FROM));
        valid &= ParamChecks.inRange(params.to(), MIN_TICK, MAX_TICK, FIELD_TO, issues,
                ParamChecks.child(fieldPath, FIELD_TO));
        return valid;
    }
}
