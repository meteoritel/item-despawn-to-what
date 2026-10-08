package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 效果：规则被触发后要执行的一个动作。
 * 具体效果类型各自实现本接口（其参数对象即实现类本身），通用字段由 core/model/CommonFields 统一提供编解码片段。
 */
public interface Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    ResourceLocation type();

    // 相对规则触发时刻的延迟（刻），0 表示立即执行
    int delayTicks();

    // 执行概率，取值 [0,1]，1 表示必定执行
    double chance();

    // 效果级附加条件，null 表示无条件
    @Nullable
    ConditionExpression conditions();

    // 返回仅替换效果级条件、其余参数原样保留的新实例；运行期门槛投影据此重建真实效果类型
    Effect withConditions(@Nullable ConditionExpression conditions);

    // 效果级条件的恒真替代，便于实现类在空值场景下直接复用
    default ConditionExpression effectiveConditions() {
        ConditionExpression conditions = conditions();
        return conditions == null ? ConditionExpression.EMPTY : conditions;
    }

    // 字段名常量引用点，确保实现类与文档使用同一套命名
    String TYPE_FIELD = RuleFields.TYPE;
    String DELAY_TICKS_FIELD = RuleFields.DELAY_TICKS;
    String CHANCE_FIELD = RuleFields.CHANCE;
}
