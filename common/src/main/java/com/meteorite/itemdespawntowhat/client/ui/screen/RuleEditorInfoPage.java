package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
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
 * 基本信息页：只读规则 ID、显示名、启用、优先级与备注。
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
    }

    @Nullable FormView infoForm;

    @Nullable UiRect infoIdRect;

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
        infoIdRect = new UiRect(x, y, width, (int) Math.ceil(idLines * (pages.font.lineHeight + 1) * 0.8));
        y = pages.layoutForm(infoForm, x, infoIdRect.bottom() + 4, width);
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
        if (infoIdRect == null || pages.currentView() == null) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(infoIdRect.x(), infoIdRect.y(), 0);
            graphics.pose().scale(0.8F, 0.8F, 1);
            int lineY = 0;
            for (FormattedCharSequence line : idTextLines(infoIdRect.width())) {
                graphics.drawString(pages.font, line, 0, lineY, THEME.palette().textSecondary(), false);
                lineY += pages.font.lineHeight + 1;
            }
        } finally {
            graphics.pose().popPose();
        }
    }
}
