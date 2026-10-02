package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.client.ui.form.FormDefinition;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 单效果编辑表单：字段定义 + 条件字段的写回绑定。
 * 条件子屏编辑完成后需要立刻写回草稿，否则父屏重建时会用旧值覆盖编辑结果。
 */
public record RuleForm(FormDefinition<RuleView> definition, List<ConditionBinding> conditions) {

    public RuleForm {
        conditions = List.copyOf(conditions);
    }

    // 条件输入控件与它在草稿上的写回目标
    public record ConditionBinding(ConditionFieldInput input, BiConsumer<RuleView, ConditionView> writer) {
    }
}
