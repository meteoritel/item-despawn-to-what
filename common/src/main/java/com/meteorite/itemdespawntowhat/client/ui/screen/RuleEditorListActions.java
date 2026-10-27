package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 规则管理页的条目动作：新建 / 复制 / 启用 / 删除，以及这些动作共用的
 * 规则 ID 命名与弹窗流程。
 *
 * <p>动作只通过 {@link EditorShell} 的命令改状态，选中项由构造时传入的选择器提供；
 * 每次改动草稿后调用外壳的列表刷新入口，由 {@link RuleEditorListPage} 重建规则树。</p>
 */
final class RuleEditorListActions {

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    private static final String DEFAULT_RULE_NAME = "new_rule";
    private static final String COPY_SUFFIX = "_copy";
    private static final int COPY_BASE_MAX_LENGTH = RuleNameInput.MAX_NAME_LENGTH - COPY_SUFFIX.length()
            - Integer.toString(Integer.MAX_VALUE).length();
    private static final String UI = TypeLabels.UI_PREFIX;

    private final EditorShell shell;
    private final RuleEditorModel model;
    private final Font font;
    private final UiModalStack modals;
    private final Supplier<@Nullable String> selection;

    RuleEditorListActions(EditorShell shell, RuleEditorModel model, Font font, UiModalStack modals,
            Supplier<@Nullable String> selection) {
        this.shell = shell;
        this.model = model;
        this.font = font;
        this.modals = modals;
        this.selection = selection;
    }

    void promptNewRule() {
        if (!shell.editable()) {
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
        return model.entry(key) != null || model.isCreated(key);
    }

    private void promptRuleName(String titleKey, String initial, Predicate<String> onSubmit) {
        RuleNameInput content = new RuleNameInput(font);
        UiTextInput input = content.input();
        input.setMaxLength(RuleNameInput.MAX_NAME_LENGTH);
        input.setValue(initial);
        UiModal modal = UiModal.create(THEME, font).title(Component.translatable(UI + titleKey))
                .preferredWidth(RuleNameInput.MODAL_WIDTH);
        modal.contentWidget(content, content.preferredHeight());
        UiButton confirmButton = new UiButton(THEME, font, Component.translatable(UI + "button.confirm"),
                UiButtonVariant.PRIMARY, null);
        Runnable validate = () -> {
            Component problem = ruleNameProblem(input.value());
            content.setProblem(problem);
            confirmButton.setEnabled(problem == null);
        };
        Runnable submit = () -> {
            validate.run();
            if (ruleNameProblem(input.value()) == null && onSubmit.test(input.value())) {
                modals.close(modal);
            }
        };
        confirmButton.setAction(submit::run);
        input.setOnValueChanged(validate);
        input.setOnCommit(value -> submit.run());
        modal.addButton(confirmButton).cancel(Component.translatable(UI + "button.cancel"));
        validate.run();
        modal.layoutCentered(shell.screenWidth(), shell.screenHeight());
        modals.push(modal);
        // 登记暂时禁用的按钮，输入合法后仍可通过 Tab 到达确认按钮。
        UiFocusManager modalFocus = modals.focusManager();
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
        if (!shell.editable()) {
            return false;
        }
        ResourceLocation id = RuleNameInput.parseId(value);
        if (id == null) {
            shell.setNotice(Component.translatable(UI + "notice.invalid_id"), EditorShell.NoticeLevel.DANGER);
            return false;
        }
        String key = id.toString();
        if (model.entry(key) != null || model.isCreated(key)) {
            shell.setNotice(Component.translatable(UI + "notice.duplicate_id"), EditorShell.NoticeLevel.DANGER);
            return false;
        }
        model.createSession(key, BuiltinEditorDefaults.ruleBody(key));
        shell.refreshList();
        shell.openEditor(key);
        shell.openInputTab();
        shell.setNotice(null, EditorShell.NoticeLevel.INFO);
        return true;
    }

    void promptDuplicate() {
        if (!shell.editable()) {
            return;
        }
        String sourceId = selection.get();
        if (sourceId == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(sourceId);
        if (session == null) {
            noSelection();
            return;
        }
        RuleValue body = session.root().deepCopy();
        // 源条目的生效启停状态在复制时一并带走：副本内容可能与源不同，只带意图才不会丢覆盖
        boolean sourceEnabled = model.effectiveEnabled(sourceId);
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
            if (!shell.editable()) {
                return false;
            }
            ResourceLocation id = RuleNameInput.parseId(value);
            if (id == null) {
                shell.setNotice(Component.translatable(UI + "notice.invalid_id"), EditorShell.NoticeLevel.DANGER);
                return false;
            }
            String key = id.toString();
            if (model.entry(key) != null || model.isCreated(key)) {
                shell.setNotice(Component.translatable(UI + "notice.duplicate_id"), EditorShell.NoticeLevel.DANGER);
                return false;
            }
            model.createSession(key, BuiltinEditorDefaults.copyOf(key, body));
            model.setEnableIntent(key, sourceEnabled);
            shell.refreshList();
            shell.openEditor(key);
            shell.setNotice(null, EditorShell.NoticeLevel.INFO);
            return true;
        });
    }

    /**
     * 切换选中条目的本服启停状态。
     *
     * <p>内容只读的条目同样可切：只读约束的是内容，不是启停。目标值等于默认启用时改用
     * reset 消除冗余覆盖，因此提示按模型返回的动作给出。</p>
     */
    void toggleSelectedEnabled() {
        if (!shell.editable()) {
            return;
        }
        String id = selection.get();
        if (id == null) {
            noSelection();
            return;
        }
        RuleEdit.Action action = model.toggleEnabled(id);
        if (action == null) {
            noSelection();
            return;
        }
        shell.refreshList();
        // 新建规则在服务端还没有条目：这时切换的是内容里的默认启用，不该用「（本服）」反馈
        if (model.isCreated(id)) {
            return;
        }
        String notice = action == RuleEdit.Action.ENABLE ? "notice.enabled"
                : action == RuleEdit.Action.DISABLE ? "notice.disabled" : "notice.reset_default";
        shell.setNotice(Component.translatable(UI + notice), EditorShell.NoticeLevel.SUCCESS);
    }

    void deleteSelected() {
        if (!shell.editable()) {
            return;
        }
        String id = selection.get();
        if (id == null) {
            noSelection();
            return;
        }
        if (model.isReadOnly(id)) {
            shell.setNotice(Component.translatable(UI + "notice.readonly_delete"), EditorShell.NoticeLevel.WARNING);
            return;
        }
        if (model.isCreated(id)) {
            shell.confirm(Component.translatable(UI + "confirm.delete_title"),
                Component.translatable(UI + "confirm.delete_draft_message"), () -> {
                if (!shell.editable()) {
                    return;
                }
                model.dropSession(id);
                shell.refreshList();
                shell.setNotice(Component.translatable(UI + "notice.deleted"), EditorShell.NoticeLevel.SUCCESS);
            });
            return;
        }
        shell.confirm(Component.translatable(UI + "confirm.delete_title"),
                Component.translatable(UI + "confirm.delete_message"), () -> {
            if (!shell.editable()) {
                return;
            }
            model.openSession(id);
            model.markDeleted(id, true);
            shell.refreshList();
            shell.setNotice(Component.translatable(UI + "notice.deleted"), EditorShell.NoticeLevel.SUCCESS);
        });
    }

    void noSelection() {
        shell.setNotice(Component.translatable(UI + "notice.no_selection"), EditorShell.NoticeLevel.WARNING);
    }

}
