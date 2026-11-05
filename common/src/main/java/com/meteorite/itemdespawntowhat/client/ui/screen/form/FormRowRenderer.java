package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 字段行渲染与命中提示：标签、说明行与悬停提示。
 *
 * <p>只读几何：全部参数由 {@link FormView} 的布局结果给出（行矩形、视口与滚动偏移），
 * 本类不改行状态、不分发输入，因此测量与命中共用同一份坐标。</p>
 */
final class FormRowRenderer {

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
        int color = issues.isEmpty() ? theme.palette().surfaces().text() : theme.palette().controls().danger();
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
        graphics.drawString(font, trimmed, viewportX, screenY + 1, theme.palette().surfaces().secondaryText(), false);
    }

    // 已命中字段行的提示：说明行给完整说明，其它行优先显示校验问题，其次字段提示与完整标签
    static @Nullable Component tooltip(FormRow row, Font font, boolean childTooltip) {
        if (!row.tooltipEnabled) return null;
        if (row.field.isNote()) {
            Component note = ((NoteControl) row.control).note();
            return font.width(note) > row.controlWidth ? note : null;
        }
        Component issue = inlineMessage(row.control.issues(row.path));
        if (issue != null) return issue;
        if (childTooltip) return null;
        Component full = row.control.fullText();
        if (full != null && font.width(full) > row.controlWidth - TEXT_SLACK) return full;
        int labelRoom = row.narrow ? row.controlWidth : row.labelWidth;
        if (row.labelShown && labelRoom > 0 && font.width(row.control.label()) > labelRoom) return row.control.label();
        return row.hintTooltipEnabled ? row.control.hint() : null;
    }

    private static @Nullable Component inlineMessage(List<FormIssue> issues) {
        for (FormIssue issue : issues) {
            if (!issue.missingRequired()) return issue.message();
        }
        return null;
    }
}
