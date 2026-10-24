package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
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
    // 行内问题文案与标签之间的留白
    private static final int ISSUE_GAP = 4;
    // 有校验问题时标签至少保留的宽度，保证字段名仍可辨认
    private static final int MIN_LABEL_WIDTH = 24;
    // 必填角标：标签右上角的星号
    private static final String REQUIRED_MARK = "*";
    // 角标相对标签文字上移的像素：读作右上角标记，而不是行内追加的文字
    private static final int REQUIRED_MARK_RISE = 2;
    // 角标与标签之间的留白
    private static final int REQUIRED_MARK_GAP = 1;

    // 工具类，禁止实例化
    private FormRowRenderer() {
    }

    // 字段标签；必填字段在标签右上角加 * 角标
    static void drawLabel(GuiGraphics graphics, Font font, FormRow row, int screenY, int viewportX,
                          int minimumControlHeight, UiHostTheme theme) {
        int width = row.narrow ? row.controlWidth : row.labelWidth;
        String text = row.control.label().getString();
        if (minimumControlHeight == 0 && row.defaultDisplayed && !row.control.isEdited()) {
            text = Component.translatable(TypeLabels.UI_PREFIX + "default_marker").getString() + text;
        }
        List<FormIssue> issues = row.control.issues(row.path);
        // 角标宽度先扣掉：标签被裁剪时角标仍要画得下
        int markRoom = row.field.required() ? font.width(REQUIRED_MARK) + REQUIRED_MARK_GAP : 0;
        // 有校验问题时除标签改用警示色外，标签后还要补一段可读文案：颜色不是唯一线索；
        // 必填缺值已由角标与红色警示表达，不在行内重复文案
        Component inline = inlineMessage(issues);
        int issueRoom = Math.max(0, width - MIN_LABEL_WIDTH - markRoom);
        int reserve = inline == null ? 0 : Math.min(issueRoom, font.width(inline) + ISSUE_GAP);
        String trimmed = TextScroll.trimToWidth(font, text, Math.max(4, width - markRoom - reserve));
        int color = issues.isEmpty() ? theme.palette().textPrimary() : theme.palette().danger();
        int labelY = row.narrow ? screenY + 1 : screenY + row.controlRelY
                + Math.max(0, (Math.min(row.control.layoutHeight(),
                        minimumControlHeight > 0 ? minimumControlHeight : theme.rowHeight()) - font.lineHeight) / 2);
        graphics.drawString(font, trimmed, viewportX, labelY, color, false);
        int labelEnd = viewportX + font.width(trimmed);
        if (markRoom > 0) {
            graphics.drawString(font, REQUIRED_MARK, labelEnd + REQUIRED_MARK_GAP, labelY - REQUIRED_MARK_RISE, color, false);
            labelEnd += markRoom;
        }
        if (inline != null) {
            String issue = TextScroll.trimToWidth(font, inline.getString(),
                    Math.max(0, width - font.width(trimmed) - markRoom - ISSUE_GAP));
            if (!issue.isEmpty()) {
                graphics.drawString(font, issue, labelEnd + ISSUE_GAP, labelY, color, false);
            }
        }
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
        theme.drawScrollThumb(graphics, new UiRect(x, thumbY, scrollbarWidth, thumbHeight));
    }

    // 鼠标位置下的提示：说明行给完整说明，其它行优先显示校验问题，其次字段提示与完整标签
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
            int screenY = viewportY + row.contentY - scrollOffset;
            UiRect rect = new UiRect(viewportX, screenY, row.controlWidth + row.controlRelX, row.contentHeight);
            if (!rect.contains(mouseX, mouseY)) {
                continue;
            }
            if (row.field.type() == EditorFieldType.NOTE) {
                // 说明行按整行宽绘制，被裁剪时给出完整说明；它不参与校验问题与字段提示
                Component note = ((NoteControl) row.control).note();
                return font.width(note) > row.controlWidth ? note : null;
            }
            Component inline = inlineMessage(row.control.issues(row.path));
            if (inline != null) {
                return inline;
            }
            Component itemTip = row.control.tooltipAt(mouseX, mouseY);
            if (itemTip != null) return itemTip;
            Component full = row.control.fullText();
            if (full != null && font.width(full) > row.controlWidth - TEXT_SLACK) {
                return full;
            }
            // 标签被裁剪时的通道：行内控件与窄布局同样适用，可用宽度按实际绘制宽度取
            int labelRoom = row.narrow ? row.controlWidth : row.labelWidth;
            if (row.labelShown && labelRoom > 0 && font.width(row.control.label()) > labelRoom) {
                return row.control.label();
            }
            return row.hintTooltipEnabled ? row.control.hint() : null;
        }
        return null;
    }

    // 行内可重复文案的问题文案：必填缺值已由红色警示与角标表达，不进入行内文案与悬停提示
    private static @Nullable Component inlineMessage(List<FormIssue> issues) {
        for (FormIssue issue : issues) {
            if (!issue.missingRequired()) return issue.message();
        }
        return null;
    }
}
