package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 规则管理页的条目动作：新建 / 复制 / 启用 / 删除，以及这些动作共用的
 * 规则 ID 命名与弹窗流程。
 *
 * <p>动作只通过 {@link RuleEditorListPage.Host} 读取屏幕能力，选中项由构造时传入的选择器提供；
 * 每次改动草稿后调用宿主的列表刷新入口，由 {@link RuleEditorListPage} 重建规则树。</p>
 */
final class RuleEditorListActions {

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    private static final String DEFAULT_RULE_NAME = "new_rule";
    private static final String COPY_SUFFIX = "_copy";
    private static final int COPY_BASE_MAX_LENGTH = RuleNameInput.MAX_NAME_LENGTH - COPY_SUFFIX.length()
            - Integer.toString(Integer.MAX_VALUE).length();
    private static final String UI = TypeLabels.UI_PREFIX;

    private final RuleEditorListPage.Host host;
    private final Supplier<@Nullable String> selection;

    RuleEditorListActions(RuleEditorListPage.Host host, Supplier<@Nullable String> selection) {
        this.host = host;
        this.selection = selection;
    }

    void promptNewRule() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        promptRuleName("prompt.new_title", nextDefaultRuleName(), this::createRule);
    }

    // 默认规则名的递推：new_rule 已存在时依次尝试 new_rule_1、new_rule_2，取未被占用的最小序号
    private String nextDefaultRuleName() {
        String candidate = DEFAULT_RULE_NAME;
        int suffix = 1;
        while (ruleNameTaken(candidate)) {
            candidate = DEFAULT_RULE_NAME + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    // 规则名（可为完整 id）对应到 id 后是否已被快照条目或本次新建占用
    private boolean ruleNameTaken(String value) {
        ResourceLocation id = RuleNameInput.parseId(value);
        if (id == null) {
            return false;
        }
        String key = id.toString();
        return host.model().entry(key) != null || host.model().isCreated(key);
    }

    private void promptRuleName(String titleKey, String initial, Predicate<String> onSubmit) {
        RuleNameInput content = new RuleNameInput(host.font());
        UiTextInput input = content.input();
        input.setMaxLength(RuleNameInput.MAX_NAME_LENGTH);
        input.setValue(initial);
        UiModal modal = UiModal.create(THEME, host.font()).title(Component.translatable(UI + titleKey))
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
            if (ruleNameProblem(input.value()) == null && onSubmit.test(input.value())) {
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
        if (RuleNameInput.parseId(value) == null) {
            return Component.translatable(UI + "prompt.invalid_name");
        }
        return ruleNameTaken(value) ? Component.translatable(UI + "notice.duplicate_id") : null;
    }

    private boolean createRule(String value) {
        if (host.rejectWhenFrozen()) {
            return false;
        }
        ResourceLocation id = RuleNameInput.parseId(value);
        if (id == null) {
            host.setNotice(Component.translatable(UI + "notice.invalid_id"), THEME.palette().danger());
            return false;
        }
        String key = id.toString();
        if (host.model().entry(key) != null || host.model().isCreated(key)) {
            host.setNotice(Component.translatable(UI + "notice.duplicate_id"), THEME.palette().danger());
            return false;
        }
        host.model().createSession(key, BuiltinEditorDefaults.ruleBody(key));
        host.refreshList();
        host.openEditor(key);
        host.selectInputTab();
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
        String sourceName = ResourceLocation.parse(sourceId).getPath();
        String copyBase = sourceName.replaceAll("[^a-z0-9_]", "_");
        copyBase = copyBase.substring(0, Math.min(copyBase.length(), COPY_BASE_MAX_LENGTH));
        String candidate = copyBase + COPY_SUFFIX;
        int suffix = 2;
        while (ruleNameProblem(candidate) != null) {
            candidate = copyBase + COPY_SUFFIX + suffix;
            suffix++;
        }
        promptRuleName("prompt.duplicate_title", candidate, value -> {
            // 弹窗期间可能已被冻结（应用在途 / 会话结束），提交前再确认一次
            if (host.rejectWhenFrozen()) {
                return false;
            }
            ResourceLocation id = RuleNameInput.parseId(value);
            if (id == null) {
                host.setNotice(Component.translatable(UI + "notice.invalid_id"), THEME.palette().danger());
                return false;
            }
            String key = id.toString();
            if (host.model().entry(key) != null || host.model().isCreated(key)) {
                host.setNotice(Component.translatable(UI + "notice.duplicate_id"), THEME.palette().danger());
                return false;
            }
            host.model().createSession(key, BuiltinEditorDefaults.copyOf(key, body));
            host.refreshList();
            host.openEditor(key);
            host.setNotice(null, THEME.palette().textSecondary());
            return true;
        });
    }

    void toggleSelectedEnabled() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        if (rejectWhenReadOnly()) {
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

    void deleteSelected() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        String id = selection.get();
        if (id == null) {
            noSelection();
            return;
        }
        if (host.model().isReadOnly(id)) {
            rejectWhenReadOnly();
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

    // 只读数据包条目没有可写入的覆盖层副本：列表按钮已禁用，这里兜底拒绝并给出原因
    private boolean rejectWhenReadOnly() {
        String id = selection.get();
        if (id == null || !host.model().isReadOnly(id)) {
            return false;
        }
        host.setNotice(Component.translatable(UI + "notice.readonly_rule"), THEME.palette().warning());
        return true;
    }

}
