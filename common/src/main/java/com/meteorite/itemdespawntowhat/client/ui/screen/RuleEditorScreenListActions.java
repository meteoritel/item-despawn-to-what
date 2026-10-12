package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.RuleTemplateHooks;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.Suggestion;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 规则管理页的条目动作：新建（含模板）/ 复制 / 启用 / 屏蔽 / 恢复原始 / 删除，以及这些动作共用的
 * 规则 ID 命名与弹窗流程。
 *
 * <p>动作只通过 {@link RuleEditorScreenListPage.Host} 读取屏幕能力，选中项由构造时传入的选择器提供；
 * 每次改动草稿后调用宿主的列表刷新入口，由 {@link RuleEditorScreenListPage} 重建规则树。</p>
 */
final class RuleEditorScreenListActions {

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    private static final int RULE_ID_MAX_LENGTH = 128;
    private static final int ROW_H = 12;
    private static final String DEFAULT_RULE_NAME = "new_rule";
    private static final String COPY_SUFFIX = "_copy";
    private static final int TEMPLATE_ROW_MAX_H = 140;
    private static final String UI = "gui.itemdespawntowhat.edit.";

    private final RuleEditorScreenListPage.Host host;
    private final Supplier<@Nullable String> selection;

    RuleEditorScreenListActions(RuleEditorScreenListPage.Host host, Supplier<@Nullable String> selection) {
        this.host = host;
        this.selection = selection;
    }

    void promptNewRule() {
        promptNewRule(null);
    }

    void promptNewRule(@Nullable String templateId) {
        if (host.rejectWhenFrozen()) {
            return;
        }
        RuleNameInput content = new RuleNameInput(host.font());
        UiTextInput input = content.input();
        input.setMaxLength(RULE_ID_MAX_LENGTH);
        input.setValue(DEFAULT_RULE_NAME);
        UiModal modal = UiModal.create(THEME, host.font()).title(Component.translatable(UI + "prompt.new_title"))
                .preferredWidth(RuleNameInput.MODAL_WIDTH);
        modal.contentWidget(content, content.preferredHeight());
        UiButton confirmButton = new UiButton(THEME, host.font(), Component.translatable(UI + "button.confirm"),
                UiButtonVariant.PRIMARY, null);
        Runnable validate = () -> {
            Component problem = ruleNameProblem(input.value());
            content.setProblem(problem);
            confirmButton.setEnabled(problem == null);
        };
        Runnable submit = () -> {
            validate.run();
            if (ruleNameProblem(input.value()) == null && createRule(input.value(), templateId)) {
                host.modals().close(modal);
            }
        };
        confirmButton.setAction(submit::run);
        input.setOnValueChanged(validate);
        input.setOnCommit(value -> submit.run());
        modal.addButton(confirmButton).cancel(Component.translatable(UI + "button.cancel"));
        validate.run();
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
        // 登记暂时禁用的按钮，输入合法后仍可通过 Tab 到达确认按钮。
        UiFocusManager modalFocus = host.modals().focusManager();
        modalFocus.beginUpdate();
        modalFocus.add(content);
        for (UiButton button : modal.buttons()) {
            modalFocus.add(button);
        }
        modalFocus.endUpdate();
        modalFocus.focusOn(content);
        input.selectAll();
    }

    private @Nullable Component ruleNameProblem(String value) {
        String name = value.trim();
        ResourceLocation id = name.indexOf(':') >= 0 ? null : normalizeId(name);
        if (id == null) {
            return Component.translatable(UI + "prompt.invalid_name");
        }
        String key = id.toString();
        return host.model().entry(key) != null || host.model().isCreated(key)
                ? Component.translatable(UI + "notice.duplicate_id") : null;
    }

    private boolean createRule(String value, @Nullable String templateId) {
        if (host.rejectWhenFrozen()) {
            return false;
        }
        ResourceLocation id = normalizeId(value);
        if (id == null) {
            host.setNotice(Component.translatable(UI + "notice.invalid_id"), THEME.palette().danger());
            return false;
        }
        String key = id.toString();
        if (host.model().entry(key) != null || host.model().isCreated(key)) {
            host.setNotice(Component.translatable(UI + "notice.duplicate_id"), THEME.palette().danger());
            return false;
        }
        RuleValue body;
        if (templateId == null) {
            body = BuiltinEditorDefaults.ruleBody(key);
        } else {
            RuleValue loaded = RuleTemplateHooks.load(ResourceLocation.tryParse(templateId));
            if (loaded == null) {
                host.setNotice(Component.translatable(UI + "notice.template_failed"), THEME.palette().danger());
                return false;
            }
            body = BuiltinEditorDefaults.copyOf(key, loaded);
            // 模板里的 enabled=false 只用于样本演示，从模板新建的规则一律启用
            body = RuleValue.mapping(body).put(RuleFields.ENABLED, RuleValue.flag(true)).build();
        }
        host.model().createSession(key, body);
        host.refreshList();
        host.openEditor(key);
        if (templateId == null) {
            host.selectInputTab();
        }
        host.setNotice(null, THEME.palette().textSecondary());
        return true;
    }

    void promptDuplicate() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        String sourceId = selection.get();
        if (sourceId == null) {
            noSelection();
            return;
        }
        EditSession session = host.model().openSession(sourceId);
        if (session == null) {
            noSelection();
            return;
        }
        RuleValue body = session.draft().root().deepCopy();
        String candidate = sourceId + COPY_SUFFIX;
        int suffix = 2;
        while (host.model().entry(candidate) != null || host.model().isCreated(candidate)) {
            candidate = sourceId + COPY_SUFFIX + suffix;
            suffix++;
        }
        promptDuplicateId(candidate, value -> {
            // 弹窗期间可能已被冻结（应用在途 / 会话结束），提交前再确认一次
            if (host.rejectWhenFrozen()) {
                return;
            }
            ResourceLocation id = normalizeId(value);
            if (id == null) {
                host.setNotice(Component.translatable(UI + "notice.invalid_id"), THEME.palette().danger());
                return;
            }
            String key = id.toString();
            if (host.model().entry(key) != null || host.model().isCreated(key)) {
                host.setNotice(Component.translatable(UI + "notice.duplicate_id"), THEME.palette().danger());
                return;
            }
            host.model().createSession(key, BuiltinEditorDefaults.copyOf(key, body));
            host.refreshList();
            host.openEditor(key);
            host.setNotice(null, THEME.palette().textSecondary());
        });
    }

    // 从内置模板新建
    void openTemplatePicker() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        List<Suggestion> templates = RuleTemplateHooks.list();
        if (templates.isEmpty()) {
            host.setNotice(Component.translatable(UI + "notice.template_empty"), THEME.palette().warning());
            return;
        }
        UiModal modal = UiModal.create(THEME, host.font());
        modal.title(Component.translatable(UI + "prompt.template_title"));
        UiListView<Suggestion> view = new UiListView<>(THEME, host.font(), this::renderTemplateRow);
        view.setItems(templates);
        view.setRowHeight(ROW_H);
        view.setEmptyMessage(Component.translatable(UI + "list.empty"));
        view.setOnActivate(suggestion -> {
            host.modals().closeTop();
            promptNewRule(suggestion.value());
        });
        int height = Math.clamp((long) templates.size() * ROW_H, ROW_H, TEMPLATE_ROW_MAX_H);
        modal.contentWidget(view, height);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    private void renderTemplateRow(GuiGraphics graphics, Font rowFont, Suggestion item, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        String label = TextScroll.trimToWidth(rowFont, item.label().getString(), Math.max(0, row.width() - 4));
        graphics.drawString(rowFont, label, row.x() + 2, row.y() + 2, THEME.palette().textPrimary(), false);
    }

    void toggleSelectedEnabled() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        EditSession session = openSelected();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        boolean enabled = !draft.getBoolean(RuleFields.ENABLED, true);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.ENABLED, enabled));
        host.refreshList();
        host.setNotice(Component.translatable(enabled ? UI + "notice.enabled" : UI + "notice.disabled"), THEME.palette().success());
    }

    void maskSelected() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        EditSession session = openSelected();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        boolean masked = !draft.getBoolean(RuleFields.DELETE, false);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.DELETE, masked));
        host.refreshList();
        host.setNotice(Component.translatable(masked ? UI + "notice.masked" : UI + "notice.unmasked"), THEME.palette().success());
    }

    void restoreSelected() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        String id = selection.get();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = host.model().openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        // 走统一门面：「恢复原始版本」也要能撤销
        session.apply(EditSession.OP_RESTORE_ORIGINAL, () -> host.model().markRestore(id));
        host.refreshList();
        host.setNotice(Component.translatable(UI + "notice.restored"), THEME.palette().success());
    }

    void deleteSelected() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        String id = selection.get();
        if (id == null) {
            noSelection();
            return;
        }
        if (host.model().isCreated(id)) {
            host.confirm(UI + "confirm.delete_title", UI + "confirm.delete_draft_message", () -> {
                if (host.rejectWhenFrozen()) {
                    return;
                }
                host.model().dropSession(id);
                host.refreshList();
                host.setNotice(Component.translatable(UI + "notice.deleted"), THEME.palette().success());
            });
            return;
        }
        host.confirm(UI + "confirm.delete_title", UI + "confirm.delete_message", () -> {
            if (host.rejectWhenFrozen()) {
                return;
            }
            host.model().openSession(id);
            host.model().markDeleted(id, true);
            host.refreshList();
            host.setNotice(Component.translatable(UI + "notice.deleted"), THEME.palette().success());
        });
    }

    // 打开选中项会话；无选中或会话不可用时给出提示并返回 null
    private @Nullable EditSession openSelected() {
        String id = selection.get();
        if (id == null) {
            noSelection();
            return null;
        }
        EditSession session = host.model().openSession(id);
        if (session == null) {
            noSelection();
        }
        return session;
    }

    void noSelection() {
        host.setNotice(Component.translatable(UI + "notice.no_selection"), THEME.palette().warning());
    }

    private @Nullable ResourceLocation normalizeId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation parsed = trimmed.indexOf(':') >= 0
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse(TypeLabels.OWN_NAMESPACE + ":" + trimmed);
        if (parsed == null || !TypeLabels.OWN_NAMESPACE.equals(parsed.getNamespace())) {
            return null;
        }
        return parsed;
    }

    // 复制规则的完整 ID 输入弹窗
    private void promptDuplicateId(String initial, Consumer<String> onValue) {
        UiTextInput input = new UiTextInput(THEME, host.font(), Component.translatable(UI + "prompt.id_hint"));
        input.setMaxLength(RULE_ID_MAX_LENGTH);
        input.setValue(initial);
        UiModal modal = UiModal.create(THEME, host.font());
        modal.title(Component.translatable(UI + "prompt.duplicate_title"));
        modal.contentWidget(input, ROW_H + 4);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> onValue.accept(input.value()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
        input.setFocused(true);
    }
}
