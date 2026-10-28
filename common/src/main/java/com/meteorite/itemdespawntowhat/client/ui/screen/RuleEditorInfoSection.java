package com.meteorite.itemdespawntowhat.client.ui.screen;

import net.minecraft.client.gui.Font;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategorySkeleton;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSourceView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 基本信息页：只读规则 ID、分类及其来源、来源摘要，以及显示名、默认启用、优先级与备注表单。
 * 分类与来源摘要都只读展示；来源摘要只读取已取回的来源视图，请求失败时改为提示来源对照不可用，
 * 不影响本页表单编辑。
 *
 * <p>「默认启用」是内容字段，改它属于沙盒内的内容改动；右侧另挂本服两态开关与「恢复默认」，
 * 它们改的是本服启用状态，两者互不污染：开关与按钮不进入表单字段，因此不会给内容字段加改动标注。</p>
 */
final class RuleEditorInfoSection {

    private final RuleEditorModel model;
    final EditorSectionContent content;
    private final EditorShell shell;
    private final Font font;
    private final EditorFormFactory forms;
    private final EditorSectionActions commands;

    // 主题：与容器共用宿主默认主题
    private final UiHostTheme THEME;

    // 页面文案前缀
    private static final String UI = RuleEditorPageSupport.UI;

    // 只读文本块的缩放比例（与 ID 行一致，便于各段文字共用行高口径）
    private static final float TEXT_SCALE = 0.8F;

    RuleEditorInfoSection(EditorShell shell, RuleEditorModel model, Font font, EditorFormFactory forms, EditorSectionActions actions) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.font = font;
        this.forms = forms;
        this.commands = actions;
        this.content = new EditorSectionContent(font);
        this.model = model;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        infoForm = null;
        serverToggle = null;
        resetButton = null;
        infoIdRect = null;
        infoCategoryRect = null;
        infoCategoryLines = null;
        infoSourceRect = null;
    }

    @Nullable FormView infoForm;

    // 本服两态开关：标签直接显示生效状态，点击切换
    @Nullable UiButton serverToggle;

    // 恢复默认：仅当条目带本服启用覆盖时可点
    @Nullable UiButton resetButton;

    @Nullable UiRect infoIdRect;

    @Nullable UiRect infoCategoryRect;

    // 分类行文本：随布局重新派生，避免每帧重复解码效果
    @Nullable List<FormattedCharSequence> infoCategoryLines;

    // 来源摘要（所属层 / 数据包 / 路径，或加载中 / 失败提示）
    @Nullable UiRect infoSourceRect;

    void buildInfo(EditSession session) {
        // 自上而下：只读规则 ID、分类、来源摘要、显示名、启用、加高的优先级控件、多行备注；
        // 页头只保留当前实际生效名称，页内不再重复说明文案
        TypeEditorDescriptor descriptor = BuiltinEditorDescriptors.infoDescriptor();
        infoForm = forms.create(session, "", descriptor);
        infoForm.useCompactSpacing();
        // 滑条与数字输入同高，备注保留三行可见高度。
        infoForm.field(RuleFields.PRIORITY).setHeight(RuleEditorEditSections.PRIORITY_ROW_HEIGHT);
        infoForm.field(RuleFields.NOTES).setHeight(36);
        infoForm.field(RuleFields.ENABLED).suppressTooltip();
        // 显示名与备注的提示已作为占位符写在输入框内，悬停不再重复弹出
        infoForm.field(RuleFields.DISPLAY_NAME).suppressHintTooltip();
        infoForm.field(RuleFields.NOTES).suppressHintTooltip();
        // 默认启用行右侧并排放本服开关与「恢复默认」：它们改的是本服状态，不属于表单内容字段
        serverToggle = new UiButton(THEME, font, Component.empty(), UiButtonVariant.SECONDARY,
                this::toggleServerEnabled);
        resetButton = new UiButton(THEME, font, Component.translatable(UI + "button.reset_default"),
                UiButtonVariant.SECONDARY, this::resetServerEnabled);
        infoForm.field(RuleFields.ENABLED).addAction(serverToggle);
        infoForm.field(RuleFields.ENABLED).addAction(resetButton);
        content.forms.add(infoForm);
        content.widgets.add(infoForm);
    }

    /**
     * 同步本服开关与「恢复默认」按钮的文案与可用状态。
     *
     * <p>页面统一使能会把行尾动作一并置为可用，所以页面集在 applyEnabled 末尾再调一次本方法，
     * 让「恢复默认」只在确有本服覆盖时可点。</p>
     *
     * @param editable 当前页面是否可写（会话冻结时为 false）
     */
    void applyServerState(boolean editable) {
        if (serverToggle == null || resetButton == null) {
            return;
        }
        EditSession session = shell.editingSession();
        if (session == null) {
            serverToggle.setEnabled(false);
            resetButton.setEnabled(false);
            return;
        }
        String id = session.targetId();
        boolean effective = model.effectiveEnabled(id);
        serverToggle.setLabel(Component.translatable(UI + (effective ? "button.server_enabled" : "button.server_disabled")));
        serverToggle.setEnabled(editable);
        resetButton.setEnabled(editable && model.hasEnabledOverride(id));
    }

    // 本服两态开关：切换后重建页面（新建规则改的是内容字段），提示按实际动作给出
    private void toggleServerEnabled() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleEdit.Action action = model.toggleEnabled(session.targetId());
        if (action == null) {
            return;
        }
        shell.draftChanged();
        commands.requestRebuild();
        // 新建规则切的是内容里的默认启用（内容动作），不套用本服启停的反馈文案
        if (model.isCreated(session.targetId())) {
            return;
        }
        String notice = action == RuleEdit.Action.ENABLE ? "notice.enabled"
                : action == RuleEdit.Action.DISABLE ? "notice.disabled" : "notice.reset_default";
        shell.setNotice(Component.translatable(UI + notice),EditorShell.NoticeLevel.SUCCESS);
    }

    // 恢复默认：清除本服启用覆盖
    private void resetServerEnabled() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        model.resetEnabled(session.targetId());
        shell.draftChanged();
        commands.requestRebuild();
        shell.setNotice(Component.translatable(UI + "notice.reset_default"),EditorShell.NoticeLevel.SUCCESS);
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
        infoCategoryRect = new UiRect(x, infoIdRect.bottom() + 2, width,
                scaledHeight(Math.max(1, infoCategoryLines.size())));
        List<FormattedCharSequence> sourceLines = sourceTextLines(width);
        if (sourceLines.isEmpty()) {
            infoSourceRect = null;
        } else {
            infoSourceRect = new UiRect(x, infoCategoryRect.bottom() + 2, width, scaledHeight(sourceLines.size()));
        }
        y = EditorSectionContent.layoutForm(infoForm, x,
                (infoSourceRect == null ? infoCategoryRect : infoSourceRect).bottom() + 4, width);
        int bottom = y + padding;
        content.cards.add(new EditorSectionContent.PageCard(new UiRect(area.x(), area.y(), area.width(), bottom - area.y()), null));
        return bottom;
    }

    List<FormattedCharSequence> idTextLines(int width) {
        RuleValue view = shell.editingValue();
        String id = view == null ? "" : RuleValue.text(view, RuleFields.ID);
        return font.split(Component.literal(id), Math.max(8, (int) (width / TEXT_SCALE)));
    }

    // 分类行：当前分类与来源（显式 / 自动）只读展示
    List<FormattedCharSequence> categoryTextLines(int width) {
        RuleValue view = shell.editingValue();
        return font.split(categoryText(view), Math.max(8, (int) (width / TEXT_SCALE)));
    }

    // 分类文案：未声明分类时展示当前派生值，来源标记区分作者手写与自动派生
    private static Component categoryText(@Nullable RuleValue view) {
        String category = RuleCategoryAssignment.declared(view);
        if (category == null) {
            category = RuleCategoryAssignment.derive(view);
        }
        String labelKey = RuleCategorySkeleton.knownLabelKey(category);
        Component label = labelKey == null ? Component.literal(category) : Component.translatable(labelKey);
        Component source = Component.translatable(UI + "info.category.source." + RuleCategoryAssignment.source(view));
        return Component.translatable(UI + "info.category", label, source);
    }

    // 来源摘要：已取回时逐层展示层名与文件路径；在途与失败各有提示；新建草稿没有来源
    private List<FormattedCharSequence> sourceTextLines(int width) {
        Component summary = sourceSummary();
        if (summary == null || summary.getString().isBlank()) {
            return List.of();
        }
        return font.split(summary, Math.max(8, (int) (width / TEXT_SCALE)));
    }

    private @Nullable Component sourceSummary() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return null;
        }
        String id = session.targetId();
        RuleEditClientWorkspace workspace = shell.workspace();
        RuleSourceView view = workspace.sourceView(id).value();
        if (view != null) {
            return describeSource(view);
        }
        if (workspace.sourceView(id).failed()) {
            return Component.translatable(UI + "source.failed");
        }
        if (workspace.sourceView(id).loading()) {
            return Component.translatable(UI + "source.loading");
        }
        // 本次新建的草稿在服务端还没有来源；已有条目等待请求时也落到这里
        return Component.translatable(UI + "source.none");
    }

    // 来源摘要文本：每层一行「层名 [数据包] · 路径」，省略不存在的层
    private static Component describeSource(RuleSourceView view) {
        if (view.isEmpty()) {
            return Component.translatable(UI + "source.empty");
        }
        Component text = Component.empty();
        if (view.base() != null) {
            text = describeLayer(view.base());
        }
        if (view.overlay() != null) {
            Component overlay = describeLayer(view.overlay());
            text = text.getString().isEmpty() ? overlay : text.copy().append("\n").append(overlay);
        }
        return text;
    }

    // 单层来源：层与文件位置平铺成一行
    private static Component describeLayer(RuleSourceView.View view) {
        return Component.translatable(UI + "source.line", layerLabel(view), view.path());
    }

    // 层的显示名：层标识走本地化，未知标识按原文兜底；数据包 id 跟在层名后
    private static Component layerLabel(RuleSourceView.View view) {
        Component layer = Component.translatableWithFallback(UI + "source.layer." + view.layer(), view.layer());
        return view.packId() == null ? layer : layer.copy().append(" [" + view.packId() + "]");
    }

    // 缩放后的文本块高度
    private int scaledHeight(int lines) {
        return (int) Math.ceil(lines * (font.lineHeight + 1) * TEXT_SCALE);
    }

    void renderInfo(GuiGraphics graphics) {
        if (shell.editingValue() == null) {
            return;
        }
        renderTextBlock(graphics, infoIdRect, idTextLines(infoIdRect == null ? 8 : infoIdRect.width()));
        renderTextBlock(graphics, infoCategoryRect, infoCategoryLines == null ? List.of() : infoCategoryLines);
        if (infoSourceRect != null) {
            renderTextBlock(graphics, infoSourceRect, sourceTextLines(infoSourceRect.width()));
        }
    }

    // 只读文本块：按 TEXT_SCALE 缩放绘制，行高与块高度使用同一口径
    private void renderTextBlock(GuiGraphics graphics, @Nullable UiRect rect, List<FormattedCharSequence> lines) {
        if (rect == null || lines.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(rect.x(), rect.y(), 0);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
            int lineY = 0;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, 0, lineY, THEME.palette().textSecondary(), false);
                lineY += font.lineHeight + 1;
            }
        } finally {
            graphics.pose().popPose();
        }
    }
}
