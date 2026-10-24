package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DebugTestRules;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑器「调试页」（DEBUG 模式）：批量生成与批量删除测试规则草稿。
 *
 * <p>页面只做两件事：按输入的数量填充测试规则、一次性删除全部测试规则；两者都只写草稿，
 * 规则是否落盘仍由管理页的「应用全部」决定，因此本页不提供保存入口。</p>
 *
 * <p>测试规则的身份前缀、分类归属与规则体由 {@link DebugTestRules} 统一约定，
 * 删除只按前缀匹配，不会影响任何非测试规则。调试页由 {@link RuleEditorScreen} 在
 * debug 模式下按 Ctrl+F8 从管理页进入，控件与视图状态全部留在本类。</p>
 */
final class RuleEditorDebugPage {

    /** 宿主能力：调试页只通过这些入口读写屏幕状态与跨模式动作。 */
    interface Host {

        Font font();

        // 弹窗栈
        UiModalStack modals();

        // 规则模型（快照、草稿会话与脏规则）
        RuleEditorModel model();

        // 编辑工作区只读视图
        EditorWorkspaceView workspace();

        // 屏幕宽高
        int screenWidth();

        int screenHeight();

        // 底部提示条
        void setNotice(@Nullable Component message, int color);

        // 重建管理页规则树（草稿改动后的统一刷新入口）
        void refreshList();

        void backToList();
    }

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 编辑器公共 i18n 前缀；调试页的文案挂在它下面的 debug. 命名空间
    private static final String EDIT_UI = TypeLabels.UI_PREFIX;
    private static final String UI = EDIT_UI + "debug.";
    // 布局常量
    private static final int INPUT_H = 18;
    private static final int COUNT_FIELD_MAX_WIDTH = 56;
    // 删除确认弹窗宽度区间
    private static final int MODAL_MIN_WIDTH = 180;
    private static final int MODAL_MAX_WIDTH = 300;
    // 数量输入长度上限与非法值哨兵
    private static final int COUNT_MAX_LENGTH = 4;
    private static final int INVALID_COUNT = -1;

    private final Host host;

    private @Nullable UiTextInput countField;
    private final RuleEditorScreenButtonBar bottomBar = new RuleEditorScreenButtonBar();
    private final List<UiButton> buttons = new ArrayList<>();
    private @Nullable UiButton fillButton;
    private @Nullable UiButton deleteButton;

    RuleEditorDebugPage(Host host) {
        this.host = host;
    }

    // ---- 初始化 ----

    // 控件只创建一次；窗口缩放不重建，输入缓冲与焦点因此保持不变。
    void init() {
        Font font = host.font();
        countField = new UiTextInput(THEME, font, Component.translatable(UI + "count_hint"));
        countField.setMaxLength(COUNT_MAX_LENGTH);
        // 只接受数字：非法字符整体拒绝，避免把非数字交给数量解析
        countField.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        countField.setValue(Integer.toString(DebugTestRules.DEFAULT_COUNT));
        countField.setOnCommit(value -> fillTestRules());
        fillButton = button(UI + "button.fill", UiButtonVariant.PRIMARY, this::fillTestRules);
        deleteButton = button(UI + "button.delete_all", UiButtonVariant.DANGER, this::confirmDeleteAll);
        UiButton back = button(EDIT_UI + "button.back", UiButtonVariant.SECONDARY, host::backToList);
        bottomBar.set(List.of(fillButton, deleteButton, back));
        buttons.clear();
        buttons.addAll(List.of(fillButton, deleteButton, back));
        updateButtons();
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, host.font(), Component.translatable(labelKey), variant, action::run).setChipStyle(true);
    }

    // ---- 动作 ----

    // 数量输入按整数解析；空值与非法字符用调用方给的哨兵值表示
    private static int intOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    // 读取输入框数量；空值与越界一律视为非法
    private int requestedCount() {
        if (countField == null) {
            return INVALID_COUNT;
        }
        int parsed = intOr(countField.value(), INVALID_COUNT);
        return parsed < DebugTestRules.MIN_COUNT || parsed > DebugTestRules.MAX_COUNT ? INVALID_COUNT : parsed;
    }

    // 填充测试规则草稿：数量非法时不改动模型，只给出提示
    private void fillTestRules() {
        int count = requestedCount();
        if (count == INVALID_COUNT) {
            host.setNotice(Component.translatable(UI + "notice.count_invalid",
                    DebugTestRules.MIN_COUNT, DebugTestRules.MAX_COUNT), RuleEditorScreen.noticeColor(RuleEditorScreen.NoticeLevel.WARNING));
            return;
        }
        int created = host.model().createTestRules(count);
        host.refreshList();
        updateButtons();
        host.setNotice(created > 0
                ? Component.translatable(UI + "notice.filled", created, host.model().testRuleCount())
                : Component.translatable(UI + "notice.fill_noop", count),
                RuleEditorScreen.noticeColor(created > 0
                        ? RuleEditorScreen.NoticeLevel.INFO : RuleEditorScreen.NoticeLevel.WARNING));
    }

    // 删除全部测试规则草稿前二次确认，数量为 0 时直接提示
    private void confirmDeleteAll() {
        int total = host.model().testRuleCount();
        if (total <= 0) {
            host.setNotice(Component.translatable(UI + "notice.delete_noop"),
                    RuleEditorScreen.noticeColor(RuleEditorScreen.NoticeLevel.WARNING));
            return;
        }
        UiModal modal = UiModal.create(THEME, host.font())
                .title(Component.translatable(UI + "delete.title"))
                .message(Component.translatable(UI + "delete.message", total))
                .confirm(Component.translatable(EDIT_UI + "button.confirm"), this::deleteTestRules)
                .cancel(Component.translatable(EDIT_UI + "button.cancel"));
        modal.preferredWidth(Math.clamp(host.screenWidth() - 40, MODAL_MIN_WIDTH, MODAL_MAX_WIDTH));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 删除全部测试规则草稿；只按 id 前缀匹配，非测试规则不受影响
    private void deleteTestRules() {
        int affected = host.model().deleteTestRules();
        host.refreshList();
        updateButtons();
        host.setNotice(Component.translatable(UI + "notice.deleted", affected, host.model().testRuleCount()),
                RuleEditorScreen.noticeColor(RuleEditorScreen.NoticeLevel.INFO));
    }

    // ---- 渲染 ----

    int footerHeight(int width) {
        return bottomBar.preferredHeight(width);
    }

    void renderFooter(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int x, int y, int width) {
        bottomBar.layout(x, y, width);
        bottomBar.render(graphics, renderFont, mouseX, mouseY);
    }

    // 调试页布局：数量摘要、数量输入与只写草稿的说明
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect area) {
        updateButtons();
        int y = area.y();
        graphics.drawString(renderFont, summary(), area.x(), y, THEME.palette().textPrimary(), false);
        y += renderFont.lineHeight + RuleEditorPageSupport.PAD;
        Component label = Component.translatable(UI + "count_label");
        int labelWidth = renderFont.width(label);
        graphics.drawString(renderFont, label, area.x(),
                y + (INPUT_H - renderFont.lineHeight) / 2, THEME.palette().textSecondary(), false);
        int fieldWidth = Math.clamp(area.width() - labelWidth - RuleEditorPageSupport.PAD, 0, COUNT_FIELD_MAX_WIDTH);
        if (countField != null) {
            countField.setBounds(area.x() + labelWidth + RuleEditorPageSupport.PAD, y, fieldWidth, INPUT_H);
            countField.render(graphics, renderFont, mouseX, mouseY);
        }
        y += INPUT_H + RuleEditorPageSupport.PAD;
        int hintColor = THEME.palette().textSecondary();
        y = drawHint(graphics, renderFont, area, y, hintColor, Component.translatable(UI + "hint.draft"));
        drawHint(graphics, renderFont, area, y, hintColor, Component.translatable(UI + "hint.prefix", DebugTestRules.QUALIFIED_ID_PREFIX));
    }

    // 提示按行高逐行绘制，返回下一行的 y
    private int drawHint(GuiGraphics graphics, Font renderFont, UiRect area, int y, int color, Component text) {
        graphics.drawString(renderFont, text, area.x(), y, color, false);
        return y + renderFont.lineHeight + RuleEditorPageSupport.PAD;
    }

    // 数量反馈：测试规则 / 全部规则 / 待应用分别计数
    private Component summary() {
        return Component.translatable(UI + "summary", host.model().testRuleCount(),
                host.model().ruleIds().size(), host.model().dirtyRuleIds().size());
    }

    // ---- 指针与键盘委托 ----

    void onScopeChanged() {
        if (countField != null) {
            countField.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (countField != null && countField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return bottomBar.mouseClicked(mouseX, mouseY, button);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = countField != null && countField.mouseReleased(mouseX, mouseY, button);
        consumed |= bottomBar.mouseReleased(mouseX, mouseY, button);
        return consumed;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return countField != null && countField.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return countField != null && countField.isFocused() && countField.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        return countField != null && countField.isFocused() && countField.charTyped(codePoint, modifiers);
    }

    // ---- 状态同步 ----

    // 每帧同步按钮可用状态：冻结时全部禁用，没有测试规则时删除按钮禁用
    void updateButtons() {
        boolean editable = host.workspace().active();
        boolean hasTestRules = host.model().testRuleCount() > 0;
        if (fillButton != null) {
            fillButton.setEnabled(editable);
        }
        if (deleteButton != null) {
            deleteButton.setEnabled(editable && hasTestRules);
        }
    }

    // 冻结切换时由屏幕统一刷新使能状态
    void setButtonsEnabled(boolean editable) {
        for (UiButton button : buttons) {
            button.setEnabled(editable);
        }
        updateButtons();
    }

    void addFocusTargets(UiFocusManager focus) {
        if (countField != null) {
            focus.add(countField);
        }
        for (UiButton button : buttons) {
            focus.add(button);
        }
    }
}
