package com.meteorite.itemdespawntowhat.client.ui.kit.api.layout;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * 表单的相对几何：标签列、控件自然高度与行尾动作共用一次测量结果。
 * 宿主先解释可见性、配对和标签政策，再提交实际参与排版的行。
 */
public final class UiFormLayout {
    private UiFormLayout() {
    }

    /** 宿主提供的布局度量；同一轮布局中的尺寸均使用 GUI 逻辑像素。 */
    public record Metrics(int narrowWidth, int labelGap, int labelLineHeight, int minimumLabelWidth,
                          int actionGap, int minimumActionWidth, int actionPadding,
                          int minimumControlWidth, int actionHeight, int rowGap) {
    }

    /** 控件先接收分配宽度，再测量该宽度下的自然高度。 */
    public interface Control {
        /** 返回分配宽度内控件实际使用的宽度。 */
        int preferredWidth(int availableWidth);

        /** 测量控件并返回其实际布局高度。 */
        int measureHeight(int width);
    }

    /**
     * 已解释的行输入；fixedHeight 非负时是固定正文行，保留原高且不布局动作。
     * stackedLabel 将标签置于控件上方，usesLabelColumn 决定是否使用统一标签列。
     */
    public record Row(Component label, boolean labelVisible, boolean usesLabelColumn, boolean stackedLabel,
                      int fixedHeight, Control control, List<Component> actions) {
    }

    /** 行结果；control 相对本行，buttons 相对控件左上角，y 相对整个内容顶部。 */
    public record RowGeometry(int y, int height, UiRect control, int labelWidth, boolean narrow,
                              boolean labelShown, List<UiRect> buttons) {
    }

    /** 本次排版结果，末行不附加行间距。 */
    public record Layout(List<RowGeometry> rows, int height) {
    }

    /** 解析统一标签列宽，供宿主把组内控件对齐到同一列。 */
    public static int labelWidth(int availableWidth, int preferredWidth, Metrics metrics) {
        return availableWidth < metrics.narrowWidth() ? 0
                : Math.clamp(availableWidth / 3, Math.min(metrics.minimumLabelWidth(), preferredWidth), preferredWidth);
    }

    /** 按可用宽度测量各行；测字与动作换行只发生在本次调用中。 */
    public static Layout layout(List<Row> rows, int availableWidth, int preferredLabelWidth,
                                Metrics metrics, TextMeasurer text) {
        int width = Math.max(0, availableWidth);
        boolean narrow = width < metrics.narrowWidth();
        int column = labelWidth(width, preferredLabelWidth, metrics);
        List<RowGeometry> result = new ArrayList<>(rows.size());
        int cursor = 0;
        for (Row row : rows) {
            int label = column;
            if (!row.usesLabelColumn() && row.fixedHeight() < 0) {
                label = Math.clamp(text.width(row.label()), Math.min(column, metrics.minimumLabelWidth()), column);
            }
            boolean sideLabel = row.labelVisible() && !row.stackedLabel();
            boolean stacked = row.stackedLabel() || (narrow && row.labelVisible());
            int controlRoom = sideLabel ? Math.max(0, width - label - metrics.labelGap()) : width;
            int[] actionWidths = new int[row.actions().size()];
            int reserved = 0;
            for (int index = 0; index < actionWidths.length; index++) {
                actionWidths[index] = Math.max(metrics.minimumActionWidth(),
                        text.width(row.actions().get(index)) + metrics.actionPadding());
                reserved += actionWidths[index] + metrics.actionGap();
            }
            boolean below = reserved > 0 && controlRoom - reserved < metrics.minimumControlWidth();
            int controlWidth = Math.max(0, row.control().preferredWidth(
                    Math.max(0, below ? controlRoom : controlRoom - reserved)));
            int controlHeight = row.fixedHeight() >= 0 ? row.fixedHeight() : row.control().measureHeight(controlWidth);
            int controlY = row.fixedHeight() >= 0 ? 0 : stacked ? metrics.labelLineHeight()
                    : Math.max(0, (Math.max(controlHeight, metrics.labelLineHeight()) - controlHeight) / 2);
            int height = row.fixedHeight() >= 0 ? controlHeight
                    : Math.max(controlHeight + controlY, metrics.labelLineHeight());
            List<UiRect> buttons = new ArrayList<>(actionWidths.length);
            if (row.fixedHeight() < 0) {
                for (int index = 0; index < actionWidths.length; index++) {
                    actionWidths[index] = Math.min(actionWidths[index], controlRoom);
                }
                if (below) {
                    UiFlowLayout.Wrap flow = UiFlowLayout.wrap(actionWidths, controlRoom,
                            metrics.actionGap(), metrics.actionHeight());
                    for (int index = 0; index < flow.size(); index++) {
                        UiRect rect = flow.bounds(index);
                        buttons.add(new UiRect(rect.x(), controlHeight + metrics.actionGap() + rect.y(),
                                rect.width(), rect.height()));
                    }
                    height += metrics.actionGap() + flow.height();
                } else {
                    int buttonX = controlWidth + metrics.actionGap();
                    for (int actionWidth : actionWidths) {
                        buttons.add(new UiRect(buttonX, 0, actionWidth, metrics.actionHeight()));
                        buttonX += actionWidth + metrics.actionGap();
                    }
                }
            }
            result.add(new RowGeometry(cursor, height,
                    new UiRect(sideLabel ? label + metrics.labelGap() : 0, controlY, controlWidth, controlHeight),
                    row.labelVisible() ? label : 0, stacked, row.labelVisible(), List.copyOf(buttons)));
            cursor += height + metrics.rowGap();
        }
        return new Layout(List.copyOf(result), Math.max(0, cursor - (rows.isEmpty() ? 0 : metrics.rowGap())));
    }
}
