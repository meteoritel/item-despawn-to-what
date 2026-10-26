package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.LightLevelEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 条件类型 light_level：按掉落物所在位置的光照等级（0..15）区间匹配。
 * 参数 min / max 均可空（null = 该端不限制），取值域 [0,15]，且 min 不得大于 max。
 * 光照口径：LevelReader#getMaxLocalRawBrightness（天空光按时间衰减后与方块光取较大值），见 LightLevelEvaluator。
 * JSON 示例：{ "type": "idtw:light_level", "max": 7 }
 */
public record LightLevelCondition(Integer min, Integer max) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "light_level");

    // 参数 JSON 字段名
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";

    // 原版光照等级范围
    private static final int MIN_LIGHT = 0;
    private static final int MAX_LIGHT = 15;

    // 参数声明：解码期接受任意整数，语义校验落在 [0,15]；两端全空表示不限制亮度
    public static final IntSpec MIN = IntSpec.nullable(FIELD_MIN, MIN_LIGHT, MAX_LIGHT).widened();
    public static final IntSpec MAX = IntSpec.nullable(FIELD_MAX, MIN_LIGHT, MAX_LIGHT).widened();
    public static final ParamList PARAMS = ParamList.of(MIN, MAX);

    // 参数编解码器：两端均可选，缺省为 null
    public static MapCodec<LightLevelCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                MIN.fieldForNullable(value -> Optional.ofNullable(value.min())),
                MAX.fieldForNullable(value -> Optional.ofNullable(value.max()))
        ).apply(instance, (min, max) -> new LightLevelCondition(min.orElse(null), max.orElse(null))));
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<LightLevelCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), LightLevelCondition::validateParams, LightLevelEvaluator::test,
                PARAMS);
    }

    // 参数语义校验：两端取值域 + 区间有序
    public static boolean validateParams(LightLevelCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = MIN.check(params.min(), issues, fieldPath);
        valid &= MAX.check(params.max(), issues, fieldPath);
        valid &= ParamChecks.orderedRange(params.min(), params.max(), FIELD_MIN + "/" + FIELD_MAX, issues, fieldPath);
        return valid;
    }
}
