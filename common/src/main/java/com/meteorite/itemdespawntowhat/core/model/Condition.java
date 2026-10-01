package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件叶：DNF 表达式中的原子谓词。
 * 具体条件类型各自实现本接口（其参数对象即实现类本身），negated 为叶级取反标志。
 */
public interface Condition {

    // 条件类型 id，同时是 JSON 中 type 字段的取值
    ResourceLocation type();

    // 叶级取反：true 表示本叶求值结果取反
    boolean negated();

    // 字段名常量引用点
    String TYPE_FIELD = RuleFields.TYPE;
    String NEGATED_FIELD = RuleFields.NEGATED;
}
