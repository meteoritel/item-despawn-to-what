package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.YLevelEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 条件类型 y_level：按掉落物的 Y 坐标区间匹配。
 * 参数 min / max 均可空（null = 该端不限制），不得低于 -2048，且 min 不得大于 max。
 * 不按世界限高设置参数上限，以兼容其它模组扩展的世界高度；参数仍使用 int 表示。
 * JSON 示例：{ "type": "idtw:y_level", "min": 60 }
 */
public record YLevelCondition(Integer min, Integer max) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "y_level");

    // 参数 JSON 字段名
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";

    /** 高度参数的合法下限，供校验与编辑器域描述共用。 */
    public static final int MIN_Y = -2048;

    // 新建规则的高度初值：与编辑器滑条窗口一致，避免新建出两端留空的恒真叶
    private static final int INITIAL_MIN = -64;
    private static final int INITIAL_MAX = 512;

    // 参数声明：解码期接受任意整数，语义校验消费声明值域；滑条窗口不是提交硬限额
    public static final IntSpec MIN = IntSpec.nullable(FIELD_MIN, MIN_Y, Integer.MAX_VALUE)
            .widened()
            .window(INITIAL_MIN, INITIAL_MAX)
            .initial(INITIAL_MIN);
    public static final IntSpec MAX = IntSpec.nullable(FIELD_MAX, MIN_Y, Integer.MAX_VALUE)
            .widened()
            .window(INITIAL_MIN, INITIAL_MAX)
            .initial(INITIAL_MAX);
    public static final ParamList PARAMS = ParamList.of(MIN, MAX);

    // 参数编解码器：两端均可选，缺省为 null
    public static MapCodec<YLevelCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                MIN.fieldForNullable(value -> Optional.ofNullable(value.min())),
                MAX.fieldForNullable(value -> Optional.ofNullable(value.max()))
        ).apply(instance, (min, max) -> new YLevelCondition(min.orElse(null), max.orElse(null))));
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<YLevelCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), YLevelCondition::validateParams, YLevelEvaluator::test,
                PARAMS);
    }

    /**
     * 校验高度参数下界和区间顺序；世界限高不构成参数上限。
     *
     * @param params 待校验的高度区间
     * @param issues 参数问题收集器
     * @param fieldPath 当前条件的字段路径
     * @return 两个端点及区间顺序是否均合法
     */
    public static boolean validateParams(YLevelCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        if (params.min() != null) {
            valid &= MIN.check(params.min(), issues, fieldPath,
                    FIELD_MIN + " 不得低于 " + MIN.min() + "，当前为 " + params.min());
        }
        if (params.max() != null) {
            valid &= MAX.check(params.max(), issues, fieldPath,
                    FIELD_MAX + " 不得低于 " + MAX.min() + "，当前为 " + params.max());
        }
        valid &= ParamChecks.orderedRange(params.min(), params.max(), FIELD_MIN + "/" + FIELD_MAX, issues, fieldPath);
        return valid;
    }

}
