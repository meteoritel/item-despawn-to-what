package com.meteorite.itemdespawntowhat.client.edit;

import java.util.List;
import java.util.Objects;

/** 字段取值的唯一计算表示；注册表提示与单位换算分别由字段和控件承载。 */
public sealed interface FieldDomain permits FieldDomain.Numeric, FieldDomain.Choices, FieldDomain.None {
    /** 数值合法域及交互元数据；展示单位不改变 JSON 合法域。 */
    record Numeric(NumericDomain values) implements FieldDomain {
        /** 数值声明必须给出完整元数据对象。 */
        public Numeric { Objects.requireNonNull(values, "values"); }
    }

    /** 按声明顺序排列的枚举 wire 值，发布后不可修改。 */
    record Choices(List<String> values) implements FieldDomain {
        /** 固定枚举顺序，不将候选列表编码为待解析字符串。 */
        public Choices { values = List.copyOf(values); }
    }

    /** 没有取值域的文本、引用、结构或展示字段。 */
    enum None implements FieldDomain { INSTANCE }
}
