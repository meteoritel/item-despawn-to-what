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

import java.util.Optional;

/**
 * 条件类型 light_level：按掉落物所在位置的光照等级（0..15）区间匹配。
 * 参数 min / max 均可空（null = 该端不限制），取值域 [0,15]，且 min 不得大于 max。
 * 说明：取哪种光照（天空光 / 方块光 / 两者取大）属阶段③ 的求值器语义，本阶段只做参数模型与校验。
 * JSON 示例：{ "type": "itemdespawntowhat:light_level", "max": 7 }
 */
public record LightLevelCondition(boolean negated, Integer min, Integer max) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "light_level");

    // 参数 JSON 字段名
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";

    // 原版光照等级范围
    private static final int MIN_LIGHT = 0;
    private static final int MAX_LIGHT = 15;

    // 参数编解码器：两端均可选，缺省为 null
    public static final MapCodec<LightLevelCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(LightLevelCondition::negated),
            Codec.INT.optionalFieldOf(FIELD_MIN).forGetter(value -> Optional.ofNullable(value.min())),
            Codec.INT.optionalFieldOf(FIELD_MAX).forGetter(value -> Optional.ofNullable(value.max()))
    ).apply(instance, (negated, min, max) -> new LightLevelCondition(negated, min.orElse(null), max.orElse(null))));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<LightLevelCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, LightLevelCondition::validateParams);
    }

    // 参数语义校验：两端取值域 + 区间有序
    public static boolean validateParams(LightLevelCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        if (params.min() != null) {
            valid &= ParamChecks.inRange(params.min(), MIN_LIGHT, MAX_LIGHT, FIELD_MIN, issues,
                    ParamChecks.child(fieldPath, FIELD_MIN));
        }
        if (params.max() != null) {
            valid &= ParamChecks.inRange(params.max(), MIN_LIGHT, MAX_LIGHT, FIELD_MAX, issues,
                    ParamChecks.child(fieldPath, FIELD_MAX));
        }
        valid &= ParamChecks.orderedRange(params.min(), params.max(), FIELD_MIN + "/" + FIELD_MAX, issues, fieldPath);
        return valid;
    }
}
