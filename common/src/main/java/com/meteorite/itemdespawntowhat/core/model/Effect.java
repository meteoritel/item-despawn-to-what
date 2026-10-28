package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;

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

    /** 行为相对成功触发点的整数格偏移，准备、执行与邻近容量共用。 */
    EffectOffset offset();


    // 字段名常量的引用点，确保实现类与常量定义使用同一套命名
    String TYPE_FIELD = RuleFields.TYPE;
}
