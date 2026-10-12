package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.List;
import net.minecraft.client.gui.Font;

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

    // 工具类，禁止实例化
    private FormRowLayout() {
    }

    // 行尾操作按钮宽度：文本宽度加内边距
    static int actionWidth(Font font, UiButton button) {
        return Math.max(16, font.width(button.label()) + 8);
    }

    static void syncBounds(List<FormRow> rows, int viewportX, int viewportY, int scrollOffset, Font font) {
        for (FormRow row : rows) {
            if (!row.visible) continue;
            int screenY = rowScreenY(viewportY, row, scrollOffset);
            row.control.setBounds(viewportX + row.controlRelX, screenY + row.controlRelY, row.controlWidth);
            int startX = viewportX + row.controlRelX;
            if (row.buttonsBelow) {
                // 换行按钮组：测量（layoutRows）与实际摆放共用同一次 wrap 结果
                int[] widths = new int[row.buttons.size()];
                for (int index = 0; index < row.buttons.size(); index++) {
                    widths[index] = Math.min(actionWidth(font, row.buttons.get(index)), row.buttonWrapWidth);
                }
                UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, row.buttonWrapWidth, 2, row.buttonHeight);
                int originY = screenY + row.controlRelY + row.control.layoutHeight() + 2;
                for (int index = 0; index < row.buttons.size(); index++) {
                    UiRect offset = flow.bounds(index);
                    row.buttons.get(index).setBounds(startX + offset.x(), originY + offset.y(), offset.width(), offset.height());
                }
                continue;
            }
            int cursor = startX + row.controlWidth + 2;
            int buttonY = screenY + row.controlRelY;
            for (var action : row.buttons) {
                int width = Math.min(actionWidth(font, action), row.buttonWrapWidth);
                action.setBounds(cursor, buttonY, width, row.buttonHeight);
                cursor += width + 2;
            }
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
            visibleRows++;
            boolean showLabel = row.labelVisible && (minimumControlHeight == 0 || row.control.usesLabelColumn());
            row.narrow = narrow && showLabel;
            row.labelWidth = showLabel ? effectiveLabelWidth : 0;
            int controlWidth = narrow || !showLabel ? availableWidth : Math.max(0, availableWidth - effectiveLabelWidth - LABEL_GAP);
            int reserved = 0;
            for (var pickerButton : row.pickers) reserved += actionWidth(font, pickerButton) + 2;
            for (com.meteorite.itemdespawntowhat.client.ui.widget.UiButton action : row.actions) {
                reserved += actionWidth(font, action) + 2;
            }
            row.buttonsBelow = controlWidth - reserved < 64 && reserved > 0;
            // 行尾按钮的换行口径：测量与实际摆放共用本值（见 syncBounds）
            row.buttonWrapWidth = controlWidth;
            row.controlWidth = Math.max(0, row.buttonsBelow ? controlWidth : controlWidth - reserved);
            row.controlWidth = row.control.preferredWidth(row.controlWidth);
            row.controlRelX = narrow || !showLabel ? 0 : effectiveLabelWidth + LABEL_GAP;
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
                    int[] widths = new int[row.buttons.size()];
                    for (int index = 0; index < row.buttons.size(); index++) {
                        widths[index] = Math.min(actionWidth(font, row.buttons.get(index)), controlWidth);
                    }
                    UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, controlWidth, 2, row.buttonHeight);
                    // 空按钮组沿用旧口径：按一行高度计
                    int lines = Math.max(1, flow.rowCount());
                    row.contentHeight += 2 + lines * row.buttonHeight + (lines - 1) * 2;
                }
            }
            row.contentY = cursor;
            cursor += row.contentHeight + rowGap;
        }
        return Math.max(0, cursor - (visibleRows == 0 ? 0 : rowGap));
    }
}
