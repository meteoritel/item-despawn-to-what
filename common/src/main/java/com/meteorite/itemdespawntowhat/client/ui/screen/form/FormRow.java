package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * 表单的一行：字段、字段控件与行级布局/装载状态。
 *
 * <p>只承载数据，不含布局与渲染逻辑：行高、控件矩形、按钮换行结果由 {@link FormView} 的布局流程写入，
 * 渲染与命中再读同一份结果。按钮列表在装配变化时合并一次，避免每帧测量与同步坐标分配临时列表。</p>
 */
final class FormRow {
    @Nullable FormRow pairedBelow;
    @Nullable FormRow pairedAbove;
    boolean loaded;
    boolean wasEditing;
    List<String> loadedInputs = List.of();
    boolean visible = true;
    @Nullable BooleanSupplier visibility;
    boolean tooltipEnabled = true;
    // 提示已经明写在控件内（如文本框占位符）时关闭 hint 兜底提示，其余提示通道保持开启
    boolean hintTooltipEnabled = true;
    @Nullable Supplier<RuleValue> defaultValue;
    boolean defaultDisplayed;
    boolean labelVisible = true;
    final List<UiButton> pickers = new ArrayList<>();
    final List<UiButton> actions = new ArrayList<>();
    final List<UiButton> buttons = new ArrayList<>();

    // 仅在装配变化时合并按钮，避免每帧测量和同步坐标时分配临时列表。
    void refreshButtons() {
        buttons.clear();
        buttons.addAll(pickers);
        buttons.addAll(actions);
    }

    final EditorField field;
    String path;
    final FormControl control;
    int contentY;
    int contentHeight;
    int controlRelX;
    int controlRelY;
    int controlWidth;
    int labelWidth;
    int buttonHeight;
    // 行尾按钮换行用的可用宽度：与测量共用同一口径（见 FormView.layoutRows/syncBounds）
    int buttonWrapWidth;
    boolean buttonsBelow;
    boolean narrow;
    // 布局结论：本行是否绘制字段标签（叠排的独立数量行同样为真）
    boolean labelShown;

    FormRow(EditorField field, String path, FormControl control) {
        this.field = field;
        this.path = path;
        this.control = control;
    }
}
