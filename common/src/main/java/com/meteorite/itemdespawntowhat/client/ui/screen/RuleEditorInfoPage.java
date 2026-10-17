package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategorySkeleton;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 基本信息页：只读规则 ID、分类及其来源，并编辑显示名、启用、优先级与备注。
 */
final class RuleEditorInfoPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;

    RuleEditorInfoPage(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        infoForm = null;
        infoIdRect = null;
        infoCategoryRect = null;
        infoCategoryLines = null;
    }

    @Nullable FormView infoForm;

    @Nullable UiRect infoIdRect;

    @Nullable UiRect infoCategoryRect;

    // 分类行文本：随布局重新派生，避免每帧重复解码效果
    @Nullable List<FormattedCharSequence> infoCategoryLines;

    void buildInfo(EditSession session) {
        // 自上而下：只读规则 ID、显示名、启用、加高的优先级控件、多行备注；
        // 页头只保留当前实际生效名称，页内不再重复说明文案
        TypeEditorDescriptor descriptor = TypeEditorDescriptor.of(RuleEditorPageSupport.INFO_PAGE, Component.translatable(RuleEditorPageSupport.UI + "tab.info"), List.of(
                EditorField.optionalText(RuleFields.DISPLAY_NAME, RuleEditorPageSupport.UI + "rule.display_name").withHint(RuleEditorPageSupport.UI + "rule.display_name_hint"),
                EditorField.bool(RuleFields.ENABLED, RuleEditorPageSupport.UI + "rule.enabled"),
                EditorField.integerSlider(RuleFields.PRIORITY, RuleEditorPageSupport.UI + "rule.priority", Integer.MIN_VALUE, Integer.MAX_VALUE, -100, 100).optional().withHint(RuleEditorPageSupport.UI + "rule.priority.hint"),
                EditorField.longText(RuleFields.NOTES, RuleEditorPageSupport.UI + "rule.notes").withHint(RuleEditorPageSupport.UI + "rule.notes_hint")));
        infoForm = pages.newForm(session, "", descriptor);
        infoForm.useCompactSpacing();
        // 滑条与数字输入同高，备注保留三行可见高度。
        infoForm.setRowHeight(RuleFields.PRIORITY, RuleEditorEditPages.PRIORITY_ROW_HEIGHT);
        infoForm.setRowHeight(RuleFields.NOTES, 36);
        infoForm.suppressRowTooltip(RuleFields.ENABLED);
        pages.liveForms.add(infoForm);
        pages.liveWidgets.add(infoForm);
    }

    int layoutInfo(UiRect area) {
        int padding = 6;
        int x = area.x() + padding;
        int width = Math.max(0, area.width() - padding * 2);
        int y = area.y() + padding;
        int idLines = Math.max(1, idTextLines(width).size());
        infoIdRect = new UiRect(x, y, width, scaledHeight(idLines));
        // 分类只做只读展示：不进入表单，避免把展示意图变成可随意改动的字段
        infoCategoryLines = categoryTextLines(width);
        infoCategoryRect = new UiRect(x, infoIdRect.bottom() + 2, width, scaledHeight(Math.max(1, infoCategoryLines.size())));
        y = pages.layoutForm(infoForm, x, infoCategoryRect.bottom() + 4, width);
        int bottom = y + padding;
        pages.cards.add(new RuleEditorEditPages.PageCard(new UiRect(area.x(), area.y(), area.width(), bottom - area.y()), null));
        return bottom;
    }

    List<FormattedCharSequence> idTextLines(int width) {
        RuleValue view = pages.currentView();
        String id = view == null ? "" : RuleEditorPageSupport.stringOf(view);
        return pages.font.split(Component.literal(id), Math.max(8, (int) (width / 0.8)));
    }

    void renderInfo(GuiGraphics graphics) {
        if (pages.currentView() == null) {
            return;
        }
        renderScaled(graphics, infoIdRect, infoIdRect == null ? List.of() : idTextLines(infoIdRect.width()));
        renderScaled(graphics, infoCategoryRect, infoCategoryLines == null ? List.of() : infoCategoryLines);
    }

    // 顶部只读行按 0.8 缩放绘制，宽高换算与缩放比例保持一致
    private void renderScaled(GuiGraphics graphics, @Nullable UiRect rect, List<FormattedCharSequence> lines) {
        if (rect == null || lines.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(rect.x(), rect.y(), 0);
            graphics.pose().scale(0.8F, 0.8F, 1);
            int lineY = 0;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(pages.font, line, 0, lineY, THEME.palette().textSecondary(), false);
                lineY += pages.font.lineHeight + 1;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    // 缩放后的文本块高度
    private int scaledHeight(int lines) {
        return (int) Math.ceil(lines * (pages.font.lineHeight + 1) * 0.8);
    }

    // 分类行：当前分类与来源（显式 / 自动）只读展示
    List<FormattedCharSequence> categoryTextLines(int width) {
        RuleValue view = pages.currentView();
        return pages.font.split(categoryText(view), Math.max(8, (int) (width / 0.8)));
    }

    // 分类文案：未声明分类时展示当前派生值，来源标记区分作者手写与自动派生
    private static Component categoryText(@Nullable RuleValue view) {
        String category = RuleCategoryAssignment.declared(view);
        if (category == null) {
            category = RuleCategoryAssignment.derive(view);
        }
        String labelKey = RuleCategorySkeleton.knownLabelKey(category);
        Component label = labelKey == null ? Component.literal(category) : Component.translatable(labelKey);
        Component source = Component.translatable(RuleEditorPageSupport.UI + "info.category.source."
                + RuleCategoryAssignment.source(view));
        return Component.translatable(RuleEditorPageSupport.UI + "info.category", label, source);
    }
}
