package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DebugTestRules;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
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
 * 删除只按前缀匹配，不会影响任何非测试规则。调试页由 {@link EditorShell} 在
 * debug 模式下按 Ctrl+F8 从管理页进入，控件与视图状态全部留在本类。</p>
 */
final class RuleEditorDebugPage implements EditorPage {

    // ---- 页面度量与标题状态：屏幕按这些声明统一排版，页不自己算窗口几何 ----

    public int headerHeight() {
        return UiTheme.HEADER_HEIGHT;
    }

    public int pagePadding() {
        return RuleEditorPageSupport.PAD;
    }

    public String title() {
        return Component.translatable(UI + "title").getString();
    }

    public String stateLabel() {
        return Component.translatable(EDIT_UI + (shell.editable() ? "state.active" : "state.freezing")).getString();
    }

    private final UiHostTheme THEME;
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

    private final EditorShell shell;
    private final RuleEditorModel model;
    private final Font font;
    private final UiModalStack modals;

    private @Nullable UiTextInput countField;
    private final RuleEditorScreenButtonBar bottomBar = new RuleEditorScreenButtonBar();
    private final List<UiButton> buttons = new ArrayList<>();
    private @Nullable UiButton fillButton;
    private @Nullable UiButton deleteButton;

    RuleEditorDebugPage(EditorShell shell, RuleEditorModel model, Font font,
            UiModalStack modals) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.model = model;
        this.font = font;
        this.modals = modals;
    }

    // ---- 初始化 ----

    // 控件只创建一次；窗口缩放不重建，输入缓冲与焦点因此保持不变。
    public void init() {
        Font font = this.font;
        countField = new UiTextInput(THEME, font, Component.translatable(UI + "count_hint"));
        countField.setMaxLength(COUNT_MAX_LENGTH);
        // 只接受数字：非法字符整体拒绝，避免把非数字交给数量解析
        countField.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        countField.setValue(Integer.toString(DebugTestRules.DEFAULT_COUNT));
        countField.setOnCommit(value -> fillTestRules());
        fillButton = button(UI + "button.fill", UiButtonVariant.PRIMARY, this::fillTestRules);
        deleteButton = button(UI + "button.delete_all", UiButtonVariant.DANGER, this::confirmDeleteAll);
        UiButton back = button(EDIT_UI + "button.back", UiButtonVariant.SECONDARY, shell::backToList);
        bottomBar.set(List.of(fillButton, deleteButton, back));
        buttons.clear();
        buttons.addAll(List.of(fillButton, deleteButton, back));
    updateButtons();
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, font, Component.translatable(labelKey), variant, action::run).setChipStyle(true);
    }

    // ---- 动作 ----

    // 数量输入按整数解析；空值与非法字符用调用方给的哨兵值表示
    private static int intOr(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return INVALID_COUNT;
        }
    }

    // 读取输入框数量；空值与越界一律视为非法
    private int requestedCount() {
        if (countField == null) {
            return INVALID_COUNT;
        }
        int parsed = intOr(countField.value());
        return parsed < DebugTestRules.MIN_COUNT || parsed > DebugTestRules.MAX_COUNT ? INVALID_COUNT : parsed;
    }

    // 填充测试规则草稿：数量非法时不改动模型，只给出提示
    private void fillTestRules() {
        int count = requestedCount();
        if (count == INVALID_COUNT) {
            shell.setNotice(Component.translatable(UI + "notice.count_invalid",
                    DebugTestRules.MIN_COUNT, DebugTestRules.MAX_COUNT),EditorShell.NoticeLevel.WARNING);
            return;
        }
        int created = model.createTestRules(count);
        shell.refreshList();
    updateButtons();
        shell.setNotice(created > 0
                ? Component.translatable(UI + "notice.filled", created, model.testRuleCount())
                : Component.translatable(UI + "notice.fill_noop", count),
created > 0
                        ? EditorShell.NoticeLevel.INFO : EditorShell.NoticeLevel.WARNING);
    }

    // 删除全部测试规则草稿前二次确认，数量为 0 时直接提示
    private void confirmDeleteAll() {
        int total = model.testRuleCount();
        if (total <= 0) {
            shell.setNotice(Component.translatable(UI + "notice.delete_noop"),
EditorShell.NoticeLevel.WARNING);
            return;
        }
        UiModal modal = UiModal.create(THEME, font)
    .title(Component.translatable(UI + "delete.title"))
                .message(Component.translatable(UI + "delete.message", total))
                .confirm(Component.translatable(EDIT_UI + "button.confirm"), this::deleteTestRules)
                .cancel(Component.translatable(EDIT_UI + "button.cancel"));
        modal.preferredWidth(Math.clamp(shell.screenWidth() - 40, MODAL_MIN_WIDTH, MODAL_MAX_WIDTH));
        modal.layoutCentered(shell.screenWidth(), shell.screenHeight());
        modals.push(modal);
    }

    // 删除全部测试规则草稿；只按 id 前缀匹配，非测试规则不受影响
    private void deleteTestRules() {
        int affected = model.deleteTestRules();
        shell.refreshList();
    updateButtons();
        shell.setNotice(Component.translatable(UI + "notice.deleted", affected, model.testRuleCount()),
EditorShell.NoticeLevel.INFO);
    }

    // ---- 渲染 ----

    /**
     * 导出调试页当前可见控件的快照。
     *
     * <p>页内摘要与说明是直接绘制的文字，不在控件树里，快照只含数量输入框与底排按钮。</p>
     */
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        if (countField != null) {
            nodes.addAll(countField.layoutNodes());
        }
        nodes.addAll(bottomBar.layoutNodes());
        return nodes;
    }

    public int footerHeight(int width) {
        return bottomBar.preferredHeight(width);
    }

    public void renderFooter(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect footer) {
        bottomBar.layout(footer.x(), footer.y(), footer.width());
    bottomBar.render(graphics, renderFont, mouseX, mouseY);
    }

    // 调试页布局：数量摘要、数量输入与只写草稿的说明
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect area) {
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
        return Component.translatable(UI + "summary", model.testRuleCount(),
                model.ruleIds().size(), model.dirtyRuleIds().size());
    }

    // ---- 指针与键盘委托 ----

    public void onScopeChanged() {
        if (countField != null) {
            countField.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (countField != null && countField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
    return bottomBar.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = countField != null && countField.mouseReleased(mouseX, mouseY, button);
        consumed |= bottomBar.mouseReleased(mouseX, mouseY, button);
        return consumed;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return countField != null && countField.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return countField != null && countField.isFocused() && countField.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return countField != null && countField.isFocused() && countField.charTyped(codePoint, modifiers);
    }

    // ---- 状态同步 ----

    // 每帧同步按钮可用状态：冻结时全部禁用，没有测试规则时删除按钮禁用
    public void updateButtons() {
        boolean editable = shell.editable();
        buttons.forEach(button -> button.setEnabled(editable));
        boolean hasTestRules = model.testRuleCount() > 0;
        if (fillButton != null) {
    fillButton.setEnabled(editable);
        }
        if (deleteButton != null) {
    deleteButton.setEnabled(editable && hasTestRules);
        }
    }

    public void addFocusTargets(UiFocusManager focus) {
        if (countField != null) {
            focus.add(countField);
        }
        for (UiButton button : buttons) {
            focus.add(button);
        }
    }
}
