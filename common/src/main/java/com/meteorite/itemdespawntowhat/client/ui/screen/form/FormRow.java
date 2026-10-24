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
        // 按钮数量或顺序变化后旧的内容坐标失效，等下一次布局测量重建
        buttonLayoutCount = 0;
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

    // 行尾按钮的内容坐标：x 相对控件列左缘，y 相对控件行顶端（不含控件行的竖直偏移）。
    // 测量时写入一次，渲染同步只叠加屏幕原点，避免每帧重算换行结果与逐按钮文本宽度。
    // 失效口径：按钮列表/文本、字体或语言度量、表单可用宽度与标签列宽、行高、可见性变化时
    // 由 FormView 重新测量并覆盖；按钮数量变化时 refreshButtons 清零，避免按旧坐标摆放新按钮。
    int buttonLayoutCount;
    int[] buttonLayoutX = new int[0];
    int[] buttonLayoutY = new int[0];
    int[] buttonLayoutW = new int[0];
    int[] buttonLayoutH = new int[0];

    /**
     * 准备按钮内容坐标缓冲：只在本行按钮数量变化时重建数组，测量路径复用同一份缓冲。
     *
     * @param count 本行按钮数量
     */
    void ensureButtonLayout(int count) {
        buttonLayoutCount = count;
        if (buttonLayoutX.length == count) return;
        buttonLayoutX = new int[count];
        buttonLayoutY = new int[count];
        buttonLayoutW = new int[count];
        buttonLayoutH = new int[count];
    }

    /**
     * 叠排数量行的下行按钮跟随上行：两行按钮列表相同，只需共享同一份内容坐标。
     *
     * @param source 上行行状态
     */
    void copyButtonLayoutFrom(FormRow source) {
        buttonLayoutCount = source.buttonLayoutCount;
        buttonLayoutX = source.buttonLayoutX;
        buttonLayoutY = source.buttonLayoutY;
        buttonLayoutW = source.buttonLayoutW;
        buttonLayoutH = source.buttonLayoutH;
    }

    FormRow(EditorField field, String path, FormControl control) {
        this.field = field;
        this.path = path;
        this.control = control;
    }
}
