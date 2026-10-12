package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 字段行渲染与命中提示：标签、说明行、滚动条与悬停提示。
 *
 * <p>只读几何：全部参数由 {@link FormView} 的布局结果给出（行矩形、视口与滚动偏移），
 * 本类不改行状态、不分发输入，因此测量与命中共用同一份坐标。</p>
 */
final class FormRowRenderer {

    // 滚动条最小高度
    private static final int MIN_THUMB_HEIGHT = 8;
    // 字段名与控件之间的留白（提示换行判定用）
    private static final int TEXT_SLACK = 6;

    // 工具类，禁止实例化
    private FormRowRenderer() {
    }

    // 字段标签；必填字段加 * 提示
    static void drawLabel(GuiGraphics graphics, Font font, FormRow row, int screenY, int viewportX,
                          int minimumControlHeight, UiHostTheme theme) {
        int width = row.narrow ? row.controlWidth : row.labelWidth;
        String text = row.control.label().getString();
        if (minimumControlHeight == 0 && row.defaultDisplayed && !row.control.isEdited()) {
            text = Component.translatable("gui.itemdespawntowhat.edit.default_marker").getString() + text;
        }
        if (row.field.required()) {
            text = text + " *";
        }
        String trimmed = TextScroll.trimToWidth(font, text, Math.max(4, width));
        // 该行有本地校验问题（如输入超限被拒）时标签改用警示色，悬停可看到具体文案
        int color = row.control.issues(row.path).isEmpty() ? theme.palette().textPrimary() : theme.palette().danger();
        int labelY = row.narrow ? screenY + 1 : screenY + row.controlRelY
                + Math.max(0, (Math.min(row.control.layoutHeight(),
                        minimumControlHeight > 0 ? minimumControlHeight : theme.rowHeight()) - font.lineHeight) / 2);
        graphics.drawString(font, trimmed, viewportX, labelY, color, false);
    }

    // 只读说明行
    static void drawNote(GuiGraphics graphics, Font font, FormRow row, int screenY, int viewportX, UiHostTheme theme) {
        String text = ((NoteControl) row.control).note().getString();
        String trimmed = TextScroll.trimToWidth(font, text, Math.max(4, row.controlWidth));
        graphics.drawString(font, trimmed, viewportX, screenY + 1, theme.palette().textSecondary(), false);
    }

    // 滚动条：轨道固定，滑块按内容比高
    static void renderScrollbar(GuiGraphics graphics, int viewportX, int viewportY, int viewportWidth,
                                int viewportHeight, int contentHeight, int scrollOffset, int maxScroll,
                                int scrollbarWidth, UiHostTheme theme) {
        int x = viewportX + viewportWidth - scrollbarWidth;
        theme.drawScrollTrack(graphics, new UiRect(x, viewportY, scrollbarWidth, viewportHeight));
        if (maxScroll <= 0) {
            return;
        }
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, viewportHeight * viewportHeight / Math.max(1, contentHeight));
        int thumbY = viewportY + (viewportHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x, thumbY, x + scrollbarWidth, thumbY + thumbHeight, theme.palette().scrollThumb());
    }

    // 鼠标位置下的提示：优先显示该字段的校验问题，其次显示字段提示
    static @Nullable Component tooltipAt(double mouseX, double mouseY, Font font, List<FormRow> rows,
                                         int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                         int scrollOffset) {
        boolean inside = mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
        if (!inside) {
            return null;
        }
        for (FormRow row : rows) {
            if (!row.visible || !row.tooltipEnabled) continue;
            if (row.field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.NOTE) {
                continue;
            }
            int screenY = viewportY + row.contentY - scrollOffset;
            UiRect rect = new UiRect(viewportX, screenY, row.controlWidth + row.controlRelX, row.contentHeight);
            if (!rect.contains(mouseX, mouseY)) {
                continue;
            }
            List<FormIssue> list = row.control.issues(row.path);
            if (!list.isEmpty()) {
                return list.getFirst().message();
            }
            Component itemTip = row.control.tooltipAt(mouseX, mouseY);
            if (itemTip != null) return itemTip;
            Component full = row.control.fullText();
            if (full != null && font.width(full) > row.controlWidth - TEXT_SLACK) {
                return full;
            }
            if (row.labelVisible && row.control.usesLabelColumn()
                    && font.width(row.control.label()) > (row.narrow ? row.controlWidth : row.labelWidth)) {
                return row.control.label();
            }
            return row.control.hint();
        }
        return null;
    }
}
