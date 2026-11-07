package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiFormLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;

/** 表单业务行适配：解释数量配对与标签政策，将 kit 的测量结果缓存到行状态。 */
final class FormRowLayout {
    private static final int NARROW_WIDTH = 180;
    private static final int LABEL_GAP = 4;
    private static final int LABEL_LINE_HEIGHT = 10;

    private FormRowLayout() {
    }

    // 同步只平移已测量几何，滚动帧不测字或分配换行结果。
    static void syncBounds(List<FormRow> rows, int originX, int originY) {
        for (FormRow row : rows) {
            if (!row.visible) continue;
            int screenY = originY + row.contentY;
            row.control.setBounds(originX + row.controlRelX, screenY + row.controlRelY, row.controlWidth);
            int buttonLeft = originX + row.controlRelX;
            int buttonTop = screenY + row.controlRelY;
            for (int index = 0; index < row.buttonLayoutCount; index++) {
                row.buttons.get(index).setBounds(buttonLeft + row.buttonLayoutX[index],
                        buttonTop + row.buttonLayoutY[index], row.buttonLayoutW[index], row.buttonLayoutH[index]);
            }
        }
    }

    static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    static int rowScreenY(int viewportY, FormRow row, int scrollOffset) {
        return viewportY + row.contentY - scrollOffset;
    }

    /** 标签列与控件之间的统一留白；页面把卡片外的行对齐到控件列时用它。 */
    static int labelGap() {
        return LABEL_GAP;
    }

    /**
     * 表单行度量：行高、动作按钮与标签列共用同一组参数。
     * <p>页面在卡片外另排一行（例如产物档位）时也必须用这份度量，否则标签列与行高会各算一套。
     */
    static UiFormLayout.Metrics metrics(int minimumControlHeight, int rowGap, UiHostTheme theme) {
        return new UiFormLayout.Metrics(NARROW_WIDTH, LABEL_GAP, LABEL_LINE_HEIGHT,
                32, 2, 16, 8, 64, Math.max(theme.rowHeight(), minimumControlHeight), rowGap);
    }

    /** 指定可用宽度下解析出的标签列宽；窄宽度返回 0，表示标签叠到控件上方。 */
    static int labelColumnWidth(int availableWidth, int preferredLabelWidth, int minimumControlHeight,
                                int rowGap, UiHostTheme theme) {
        return UiFormLayout.labelWidth(availableWidth, preferredLabelWidth,
                metrics(minimumControlHeight, rowGap, theme));
    }

    static int layoutRows(List<FormRow> rows, int availableWidth, int labelWidth, int rowGap,
                          int minimumControlHeight, Font font, UiHostTheme theme) {
        UiFormLayout.Metrics metrics = metrics(minimumControlHeight, rowGap, theme);
        boolean narrow = availableWidth < NARROW_WIDTH;
        int column = UiFormLayout.labelWidth(availableWidth, labelWidth, metrics);
        List<FormRow> active = new ArrayList<>();
        List<UiFormLayout.Row> inputs = new ArrayList<>();
        for (FormRow row : rows) {
            if (!row.visible || (row.pairedAbove != null && row.pairedAbove.visible)) continue;
            boolean stackedCounts = false;
            if (row.control instanceof ItemCountsControl counts) {
                boolean paired = row.pairedBelow != null && row.pairedBelow.visible;
                counts.setPairRole(paired ? ItemCountsControl.PairRole.UPPER : ItemCountsControl.PairRole.NONE);
                boolean sharesLabelColumn = narrow || !row.labelVisible || minimumControlHeight == 0;
                counts.setSliderColumnOffset(sharesLabelColumn ? 0 : column + LABEL_GAP);
                stackedCounts = !sharesLabelColumn && !paired;
                if (paired && row.pairedBelow.control instanceof ItemCountsControl lower) {
                    counts.setSecondRowLabel(lower.label());
                }
            }
            boolean showLabel = row.labelVisible
                    && (minimumControlHeight == 0 || row.control.usesLabelColumn() || stackedCounts);
            active.add(row);
            inputs.add(new UiFormLayout.Row(row.control.label(), showLabel, row.control.usesLabelColumn(),
                    stackedCounts, row.field.isNote() ? row.control.height() : -1, new UiFormLayout.Control() {
                        @Override
                        public int preferredWidth(int available) {
                            return row.control.preferredWidth(available);
                        }

                        @Override
                        public int measureHeight(int width) {
                            row.control.measure(width);
                            return row.control.layoutHeight();
                        }
                    }, row.buttons.stream().map(UiButton::label).toList()));
        }
        UiFormLayout.Layout layout = UiFormLayout.layout(inputs, availableWidth, labelWidth, metrics, TextMeasurer.of(font));
        for (int index = 0; index < active.size(); index++) {
            FormRow row = active.get(index);
            UiFormLayout.RowGeometry geometry = layout.rows().get(index);
            UiRect control = geometry.control();
            row.contentY = geometry.y();
            row.contentHeight = geometry.height();
            row.controlRelX = control.x();
            row.controlRelY = control.y();
            row.controlWidth = control.width();
            row.labelWidth = geometry.labelWidth();
            row.narrow = geometry.narrow();
            row.labelShown = geometry.labelShown();
            row.ensureButtonLayout(geometry.buttons().size());
            for (int button = 0; button < row.buttonLayoutCount; button++) {
                UiRect rect = geometry.buttons().get(button);
                row.buttonLayoutX[button] = rect.x();
                row.buttonLayoutY[button] = rect.y();
                row.buttonLayoutW[button] = rect.width();
                row.buttonLayoutH[button] = rect.height();
            }
            if (row.pairedBelow != null && row.pairedBelow.visible
                    && row.control instanceof ItemCountsControl upper
                    && row.pairedBelow.control instanceof ItemCountsControl lower) {
                FormRow paired = row.pairedBelow;
                lower.setPairRole(ItemCountsControl.PairRole.LOWER);
                lower.setSecondRowLabel(null);
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
        }
        return layout.height();
    }
}
