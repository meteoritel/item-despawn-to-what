package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * 一次表单装配中的行身份；路径随嵌套重排更新，描述符替换后重新取得句柄。
 * 行配置在此集中触发布局失效，不向宿主暴露控件内部状态。
 */
public final class FormField {
    private final FormView owner;
    private final FormRow row;

    FormField(FormView owner, FormRow row) {
        this.owner = owner;
        this.row = row;
    }

    private void requireAttached() {
        if (!row.attached) throw new IllegalStateException("Detached form field: " + row.field.name());
    }

    /** 字段声明，用于装配阶段的能力分类。 */
    public EditorField descriptor() { requireAttached(); return row.field; }

    /** 当前数据地址；同一行重排后返回新地址。 */
    public String path() { requireAttached(); return row.path; }

    /** 当前控件的未提交值，不改变草稿。 */
    public @Nullable RuleValue pendingValue() { requireAttached(); return row.control.store(); }

    /** 覆盖行高；非正数恢复控件默认高度。 */
    public void setHeight(int height) { requireAttached(); row.control.setHeightOverride(height); owner.invalidateMeasure(); }

    /** 卡片已表达含义时省去行标签。 */
    public void hideLabel() { requireAttached(); row.labelVisible = false; owner.invalidateMeasure(); }

    /** 隐藏行结束交互和焦点，保留字段与输入内容。 */
    public void setVisible(boolean visible) { requireAttached(); owner.setRowVisible(row, visible); }

    /** 显隐随当前输入和历史回填求值。 */
    public void visibleWhen(BooleanSupplier visibility) { requireAttached(); row.visibility = visibility; owner.refreshRowVisibility(); }

    /** 关闭整行提示。 */
    public void suppressTooltip() { requireAttached(); row.tooltipEnabled = false; }

    /** 关闭已显示在控件中的 hint，保留问题与截断提示。 */
    public void suppressHintTooltip() { requireAttached(); row.hintTooltipEnabled = false; }

    /** 缺省显示值不落入未编辑字段。 */
    public void defaultValue(Supplier<RuleValue> value) { requireAttached(); row.defaultValue = value; owner.reload(); }

    /** 只改变常用交互窗口，保留合法域与输入缓冲。 */
    public void sliderWindow(double min, double max) {
        requireAttached();
        if (!(row.control instanceof NumberControl number)) throw new IllegalArgumentException("Not numeric: " + path());
        number.setWindow(min, max);
    }

    /** 设置数量行当前引用身份。 */
    public void itemCountReferences(List<String> references) {
        requireAttached();
        if (!(row.control instanceof ItemCountsControl counts)) throw new IllegalArgumentException("Not item counts: " + path());
        counts.setReferences(references); owner.invalidateMeasure();
    }

    /** 同表单的数量门槛/消耗行按物品交错排布。 */
    public void pairBelow(FormField lower) {
        requireAttached(); lower.requireAttached();
        if (owner != lower.owner || row == lower.row || !(row.control instanceof ItemCountsControl)
                || !(lower.row.control instanceof ItemCountsControl)) throw new IllegalArgumentException("Incompatible count rows");
        row.pairedBelow = lower.row; lower.row.pairedAbove = row; owner.invalidateMeasure();
    }

    /** 目录筛选只影响浏览，不改写已有选择。 */
    public void catalogFilter(@Nullable Predicate<String> filter) { requireAttached(); row.catalogFilter = filter; }

    /** 当前目录过滤条件。 */
    public @Nullable Predicate<String> catalogFilter() { requireAttached(); return row.catalogFilter; }

    /** 用一个动作替换全部行尾动作。 */
    public void setAction(UiButton action) { requireAttached(); row.actions.clear(); addAction(action); }

    /** 添加行尾动作，按钮不作为内容字段进入撤销历史。 */
    public void addAction(UiButton action) {
        requireAttached(); row.actions.add(action); row.refreshButtons(); owner.invalidateMeasure();
    }
}
