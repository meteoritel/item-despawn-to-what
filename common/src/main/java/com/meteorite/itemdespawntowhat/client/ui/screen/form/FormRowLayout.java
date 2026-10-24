package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.List;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;

/**
 * 表单行布局与滚动几何：按可用宽度排布每一行（标签列 / 控件 / 行尾按钮），并把结果同步到屏幕坐标。
 *
 * <p>测量与实际摆放共用同一份行状态（{@link FormRow} 的 controlRelX/controlWidth/buttonWrapWidth 等），
 * 因此不会出现「测量一套、命中另一套」的漂移；本类不持有草稿与输入状态。</p>
 */
final class FormRowLayout {

    // 窄于此宽度时标签换到控件上方（320x240 小尺寸用单列）
    private static final int NARROW_WIDTH = 180;
    // 标签列与控件之间的间隙
    private static final int LABEL_GAP = 4;
    // 标签行高（标签在上方时使用）
    private static final int LABEL_LINE_HEIGHT = 10;
    // 行内自带文字的控件至少保留的标签列宽度
    private static final int MIN_INLINE_LABEL_WIDTH = 32;

    // 工具类，禁止实例化
    private FormRowLayout() {
    }

    // 行尾操作按钮宽度：文本宽度加内边距
    static int actionWidth(Font font, UiButton button) {
        return Math.max(16, font.width(button.label()) + 8);
    }

    /**
     * 该行标签列实际预留的宽度。
     *
     * <p>行内自带文字的控件（复选框、条件树）把标签排在控件内部，整列留给控件也读得出，
     * 统一列宽只会在窄窗口下白占空间，因此这类行按标签文本宽度收窄；其余行保持统一列宽，
     * 控件左边缘仍对齐。说明行整行显示，不参与标签列对齐。物品计数行的滑条列由
     * {@code ItemCountsControl.setSliderColumnOffset} 取本行控件列，与其余行共用同一列。</p>
     *
     * @param row                 当前行
     * @param effectiveLabelWidth 表单统一标签列宽（已按可用宽度收紧）
     * @param font                文本测量字体
     * @return 该行预留的标签列宽度
     */
    private static int rowLabelWidth(FormRow row, int effectiveLabelWidth, Font font) {
        if (row.control.usesLabelColumn() || row.field.type() == EditorFieldType.NOTE) {
            return effectiveLabelWidth;
        }
        int inlineSpan = Math.max(MIN_INLINE_LABEL_WIDTH, font.width(row.control.label()));
        return Math.min(effectiveLabelWidth, inlineSpan);
    }

    /**
     * 把测量好的行几何同步到屏幕坐标：只叠加视口原点与滚动偏移。
     *
     * <p>按钮换行结果与逐按钮内容坐标在 {@link #layoutRows} 测量时已写入行状态，这里不再测量文本宽度、
     * 不再分配换行数组与临时矩形，因此静止帧与滚动帧的同步都不触发字体测量。</p>
     *
     * @param rows    表单行
     * @param originX 表单内容原点屏幕 X（控件列左缘基于该值）
     * @param originY 表单内容原点屏幕 Y（已含视口位置与滚动偏移）
     */
    static void syncBounds(List<FormRow> rows, int originX, int originY) {
        for (FormRow row : rows) {
            if (!row.visible) continue;
            int screenY = originY + row.contentY;
            row.control.setBounds(originX + row.controlRelX, screenY + row.controlRelY, row.controlWidth);
            int buttonLeft = originX + row.controlRelX;
            int buttonTop = screenY + row.controlRelY;
            for (int index = 0; index < row.buttonLayoutCount; index++) {
                row.buttons.get(index).setBounds(buttonLeft + row.buttonLayoutX[index],
                        buttonTop + row.buttonLayoutY[index],
                        row.buttonLayoutW[index], row.buttonLayoutH[index]);
            }
        }
    }

    /**
     * 把测量阶段的按钮排布固化成行内容坐标，供后续同步与命中直接读取。
     *
     * <p>只在真实几何输入变化（宽度/行高/按钮文本/可见性）触发的测量里执行，因此这里剩余的
     * 文本测量与换行数组分配不会进入帧循环。</p>
     *
     * @param row     目标行
     * @param font    文本测量字体
     * @param flow    换行结果；行尾横排按钮为 {@code null}
     * @param widths  换行按钮宽度；与 {@code flow} 同时给出
     */
    private static void storeButtonLayout(FormRow row, Font font, @Nullable UiFlowLayout.Wrap flow, int[] widths) {
        int count = row.buttons.size();
        row.ensureButtonLayout(count);
        if (flow != null) {
            for (int index = 0; index < count; index++) {
                UiRect offset = flow.bounds(index);
                row.buttonLayoutX[index] = offset.x();
                // y 相对控件行顶端：换行按钮组从控件底部下方 2px 起排
                row.buttonLayoutY[index] = row.control.layoutHeight() + 2 + offset.y();
                row.buttonLayoutW[index] = offset.width();
                row.buttonLayoutH[index] = offset.height();
            }
            return;
        }
        int cursor = row.controlWidth + 2;
        for (int index = 0; index < count; index++) {
            row.buttonLayoutX[index] = cursor;
            row.buttonLayoutY[index] = 0;
            row.buttonLayoutW[index] = widths == null
                    ? Math.min(actionWidth(font, row.buttons.get(index)), row.buttonWrapWidth) : widths[index];
            row.buttonLayoutH[index] = row.buttonHeight;
            cursor += row.buttonLayoutW[index] + 2;
        }
    }

    static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    static int rowScreenY(int viewportY, FormRow row, int scrollOffset) {
        return viewportY + row.contentY - scrollOffset;
    }

    static int layoutRows(List<FormRow> rows, int availableWidth, int labelWidth, int rowGap, int minimumControlHeight, Font font, UiHostTheme theme) {
        boolean narrow = availableWidth < NARROW_WIDTH;
        int effectiveLabelWidth = narrow ? 0 : Math.min(labelWidth, Math.max(32, availableWidth / 3));
        int cursor = 0;
        int visibleRows = 0;
        for (FormRow row : rows) {
            if (!row.visible) continue;
            if (row.pairedAbove != null && row.pairedAbove.visible) continue;
            // 本行按钮排布：测量阶段算一次，随内容坐标固化到行上，滚动/同步不再重算
            UiFlowLayout.Wrap buttonFlow = null;
            int[] buttonWidths = null;
            boolean stackedCounts = false;
            if (row.control instanceof ItemCountsControl counts) {
                boolean paired = row.pairedBelow != null && row.pairedBelow.visible;
                counts.setPairRole(paired ? ItemCountsControl.PairRole.UPPER : ItemCountsControl.PairRole.NONE);
                // 滑条列取普通字段行的控件列：滑条与其它行左对齐，组内含义文案留在标签列。
                // 窄列或紧凑密度下本行自己已经占着标签列，控件内部不再叠加缩进
                boolean sharesLabelColumn = narrow || !row.labelVisible || minimumControlHeight == 0;
                counts.setSliderColumnOffset(sharesLabelColumn ? 0 : effectiveLabelWidth + LABEL_GAP);
                // 独立数量行在卡片密度下把字段名叠到控件上方：标签列整列留给物品图标与滑条
                stackedCounts = !sharesLabelColumn && !paired;
                if (paired && row.pairedBelow.control instanceof ItemCountsControl lower) {
                    // 第二行文案取下行字段标签（例如「消耗数量」），由上行统一绘制在物品名下方
                    counts.setSecondRowLabel(lower.label());
                }
            }
            visibleRows++;
            boolean showLabel = row.labelVisible && (minimumControlHeight == 0 || row.control.usesLabelColumn() || stackedCounts);
            // 叠排行只把字段名放到上方，控件占满整行；其余行按标签列让出控件列
            boolean sideLabel = showLabel && !stackedCounts;
            row.labelShown = showLabel;
            row.narrow = stackedCounts || (narrow && showLabel);
            int rowLabelWidth = rowLabelWidth(row, effectiveLabelWidth, font);
            row.labelWidth = showLabel ? rowLabelWidth : 0;
            int controlWidth = sideLabel ? Math.max(0, availableWidth - rowLabelWidth - LABEL_GAP) : availableWidth;
            int reserved = 0;
            for (var pickerButton : row.pickers) reserved += actionWidth(font, pickerButton) + 2;
            for (UiButton action : row.actions) {
                reserved += actionWidth(font, action) + 2;
            }
            row.buttonsBelow = controlWidth - reserved < 64 && reserved > 0;
            // 行尾按钮的换行口径：测量与实际摆放共用本值（见 syncBounds）
            row.buttonWrapWidth = controlWidth;
            row.controlWidth = Math.max(0, row.buttonsBelow ? controlWidth : controlWidth - reserved);
            row.controlWidth = row.control.preferredWidth(row.controlWidth);
            row.controlRelX = sideLabel ? rowLabelWidth + LABEL_GAP : 0;
            if (row.field.type() == EditorFieldType.NOTE) {
                row.controlRelY = 0;
                row.contentHeight = row.control.height();
            } else {
                row.control.measure(row.controlWidth);
                // 布局高度：控件自然高度，或被 setRowHeight 显式覆盖（加高的优先级控件）
                int controlHeight = row.control.layoutHeight();
                row.controlRelY = row.narrow ? LABEL_LINE_HEIGHT : Math.max(0, (Math.max(controlHeight, LABEL_LINE_HEIGHT) - controlHeight) / 2);
                row.contentHeight = Math.max(controlHeight + row.controlRelY, LABEL_LINE_HEIGHT);
                row.buttonHeight = Math.max(theme.rowHeight(), minimumControlHeight);
                if (row.buttonsBelow) {
                    buttonWidths = new int[row.buttons.size()];
                    for (int index = 0; index < row.buttons.size(); index++) {
                        buttonWidths[index] = Math.min(actionWidth(font, row.buttons.get(index)), controlWidth);
                    }
                    buttonFlow = UiFlowLayout.wrap(buttonWidths, controlWidth, 2, row.buttonHeight);
                    // 空按钮组沿用旧口径：按一行高度计
                    int lines = Math.max(1, buttonFlow.rowCount());
                    row.contentHeight += 2 + lines * row.buttonHeight + (lines - 1) * 2;
                }
            }
            row.contentY = cursor;
            if (row.field.type() == EditorFieldType.NOTE) {
                // 说明行不摆行尾按钮，清空缓存避免沿用上一版结构的坐标
                row.ensureButtonLayout(0);
            } else {
                storeButtonLayout(row, font, buttonFlow, buttonWidths);
            }
            if (row.pairedBelow != null && row.pairedBelow.visible
                    && row.control instanceof ItemCountsControl upper
                    && row.pairedBelow.control instanceof ItemCountsControl lower) {
                FormRow paired = row.pairedBelow;
                lower.setPairRole(ItemCountsControl.PairRole.LOWER);
                lower.setSecondRowLabel(null);
                // 两条滑条必须从同一横坐标开始：下行沿用上行实际使用的名称列宽度
                lower.setLabelColumnWidth(upper.resolvedLabelColumnWidth(row.controlWidth));
                paired.contentY = row.contentY;
                paired.contentHeight = row.contentHeight;
                paired.controlRelX = row.controlRelX;
                paired.controlRelY = row.controlRelY + upper.pairOffset();
                paired.controlWidth = row.controlWidth;
                paired.labelWidth = 0;
                paired.narrow = false;
                paired.labelVisible = false;
                paired.copyButtonLayoutFrom(row);
            }
            cursor += row.contentHeight + rowGap;
        }
        return Math.max(0, cursor - (visibleRows == 0 ? 0 : rowGap));
    }
}
