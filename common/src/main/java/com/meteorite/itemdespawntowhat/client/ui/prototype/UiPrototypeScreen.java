package com.meteorite.itemdespawntowhat.client.ui.prototype;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeNode;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/***
 * 开发用原型屏。
 * <p>在一屏内展示灰色像素主题与全部通用控件：按钮四种变体、单行文本输入框、
 * 可滚动列表、可展开条件树、模态弹窗与多层弹窗栈。
 * <p>布局按当前窗口尺寸自适应：窄屏（小于 460 逻辑像素）改为单列上下排布，
 * 宽屏改为左右两栏，用于验证 GUI 缩放 1x/2x/3x 与 320×240、1920×1080 下无重叠与文本溢出。
 * <p>本屏不注册任何快捷键，也不提供绕过独占锁的编辑器入口，只作为开发验证设施存在。
 */
public final class UiPrototypeScreen extends Screen {

    // 窄屏阈值（逻辑像素）
    private static final int NARROW_WIDTH = 460;
    // 分区标签高度
    private static final int LABEL_HEIGHT = 10;
    // 底部提示行高度
    private static final int HINT_HEIGHT = 10;

    // 弹窗栈
    private final UiModalStack modalStack = new UiModalStack();
    // 基础层焦点管理
    private final UiFocusManager baseFocus = new UiFocusManager();
    // 基础层控件（渲染与命中顺序：末尾在最上层）
    private final List<UiWidget> baseWidgets = new ArrayList<>();

    // 文本输入框
    private UiTextInput nameInput;
    // 四种按钮变体 + 弹窗按钮
    private UiButton primaryButton;
    private UiButton secondaryButton;
    private UiButton dangerButton;
    private UiButton disabledButton;
    private UiButton modalButton;
    // 列表与树
    private UiListView<Component> ruleList;
    private UiTreeView<String> conditionTree;
    // 状态行
    private Component statusLine = Component.empty();
    // 布局缓存
    private int listLabelY;
    private int treeLabelY;
    private int hintY;

    public UiPrototypeScreen() {
        super(Component.translatable("gui.itemdespawntowhat.prototype.title"));
        this.baseFocus.setEnterActivates(true);
        this.baseFocus.setSpaceActivates(true);
    }

    @Override
    protected void init() {
        this.baseWidgets.clear();
        this.baseFocus.clear();
        buildInput();
        buildButtons();
        buildList();
        buildTree();
        layout();
        refreshBaseFocus();
    }

    // ---- 控件构建 ----

    // 构建输入框
    private void buildInput() {
        this.nameInput = new UiTextInput(this.font, Component.translatable("gui.itemdespawntowhat.prototype.input.hint"));
        this.nameInput.setMaxLength(64);
        this.nameInput.setOnCommit(value -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.committed", value));
        this.baseWidgets.add(this.nameInput);
    }

    // 构建按钮
    private void buildButtons() {
        this.primaryButton = new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.button.primary"),
                UiButtonVariant.PRIMARY, () -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.primary"));
        this.secondaryButton = new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.button.secondary"),
                UiButtonVariant.SECONDARY, () -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.secondary"));
        this.dangerButton = new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.button.danger"),
                UiButtonVariant.DANGER, () -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.danger"));
        this.disabledButton = new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.button.disabled"),
                UiButtonVariant.SECONDARY, null);
        this.disabledButton.setEnabled(false);
        this.modalButton = new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.button.modal"),
                UiButtonVariant.SECONDARY, this::openDemoModal);
        this.baseWidgets.add(this.primaryButton);
        this.baseWidgets.add(this.secondaryButton);
        this.baseWidgets.add(this.dangerButton);
        this.baseWidgets.add(this.disabledButton);
        this.baseWidgets.add(this.modalButton);
    }

    // 构建列表
    private void buildList() {
        List<Component> rules = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            rules.add(Component.translatable("gui.itemdespawntowhat.prototype.list.item", i));
        }
        this.ruleList = new UiListView<>(this.font,
                (graphics, font, item, index, row, selected, hovered, focused) -> graphics.drawString(font, item,
                        row.x(), row.y() + UiTheme.TEXT_OFFSET, UiTheme.textColor(selected), false));
        this.ruleList.setEmptyMessage(Component.translatable("gui.itemdespawntowhat.prototype.list.empty"));
        this.ruleList.setItems(rules);
        this.ruleList.setOnSelectionChanged(index -> this.statusLine = this.ruleList.selectedItem());
        this.baseWidgets.add(this.ruleList);
    }

    // 构建条件树示例
    private void buildTree() {
        this.conditionTree = new UiTreeView<>(this.font,
                (graphics, font, node, row, depth, selected, hovered, focused) -> graphics.drawString(font, node.label(),
                        row.x(), row.y() + UiTheme.TEXT_OFFSET, UiTheme.textColor(selected), false));
        this.conditionTree.setEmptyMessage(Component.translatable("gui.itemdespawntowhat.prototype.tree.empty"));
        UiTreeNode<String> root = new UiTreeNode<>("root", Component.translatable("gui.itemdespawntowhat.prototype.tree.root"));
        UiTreeNode<String> all = root.addChild("all", Component.translatable("gui.itemdespawntowhat.prototype.tree.all"));
        UiTreeNode<String> any = root.addChild("any", Component.translatable("gui.itemdespawntowhat.prototype.tree.any"));
        for (int i = 1; i <= 2; i++) {
            all.addChild("all-" + i, Component.translatable("gui.itemdespawntowhat.prototype.tree.leaf", i));
        }
        for (int i = 3; i <= 5; i++) {
            any.addChild("any-" + i, Component.translatable("gui.itemdespawntowhat.prototype.tree.leaf", i));
        }
        root.setExpanded(true);
        all.setExpanded(true);
        List<UiTreeNode<String>> roots = new ArrayList<>();
        roots.add(root);
        this.conditionTree.setRoots(roots);
        this.conditionTree.setOnSelectionChanged(node -> this.statusLine = node.label());
        this.baseWidgets.add(this.conditionTree);
    }

    // 登记基础层可聚焦控件（顺序即 Tab 顺序）
    private void refreshBaseFocus() {
        this.baseFocus.clear();
        for (UiWidget widget : this.baseWidgets) {
            if (widget instanceof UiFocusTarget target && target.canFocus()) {
                this.baseFocus.add(target);
            }
        }
        this.baseFocus.focusFirst();
    }

    // ---- 布局 ----

    // 按当前窗口尺寸自适应布局
    private void layout() {
        int width = this.width;
        int height = this.height;
        int pad = UiTheme.PADDING;
        int inner = pad + 4;
        int headerHeight = UiTheme.HEADER_HEIGHT;
        int toolY = pad + headerHeight + 4;
        int inputHeight = 18;
        int buttonHeight = UiModal.BUTTON_HEIGHT;
        int gap = 4;
        boolean narrow = width < NARROW_WIDTH;
        int contentTop;
        if (narrow) {
            this.nameInput.setBounds(inner, toolY, Math.max(0, width - inner * 2), inputHeight);
            List<UiButton> buttons = List.of(this.primaryButton, this.secondaryButton, this.dangerButton, this.disabledButton, this.modalButton);
            int usable = Math.max(0, width - inner * 2);
            int cell = Math.max(0, (usable - gap) / 2);
            int startY = toolY + inputHeight + gap;
            for (int i = 0; i < buttons.size(); i++) {
                int x = inner + (i % 2) * (cell + gap);
                int y = startY + (i / 2) * (buttonHeight + gap);
                buttons.get(i).setBounds(x, y, cell, buttonHeight);
            }
            contentTop = startY + 3 * (buttonHeight + gap) + 2;
        } else {
            int inputWidth = Math.max(120, (width - inner * 2 - gap * 5) / 3);
            int inputY = toolY + (buttonHeight - inputHeight) / 2;
            this.nameInput.setBounds(inner, inputY, inputWidth, inputHeight);
            List<UiButton> buttons = List.of(this.primaryButton, this.secondaryButton, this.dangerButton, this.disabledButton, this.modalButton);
            int x = width - inner;
            for (int i = buttons.size() - 1; i >= 0; i--) {
                UiButton button = buttons.get(i);
                int buttonWidth = Math.max(40, button.preferredWidth(UiTheme.PADDING));
                x -= buttonWidth;
                button.setBounds(x, toolY, buttonWidth, buttonHeight);
                x -= gap;
            }
            contentTop = toolY + buttonHeight + 6;
        }
        int contentBottom = height - pad - HINT_HEIGHT;
        this.hintY = height - pad - HINT_HEIGHT + 1;
        if (narrow) {
            this.listLabelY = contentTop;
            int listTop = contentTop + LABEL_HEIGHT;
            int available = Math.max(20, contentBottom - listTop - gap);
            int listHeight = Math.max(24, available * 2 / 5);
            this.ruleList.setBounds(pad, listTop, Math.max(0, width - pad * 2), listHeight);
            this.treeLabelY = listTop + listHeight + gap;
            int treeTop = this.treeLabelY + LABEL_HEIGHT;
            this.conditionTree.setBounds(pad, treeTop, Math.max(0, width - pad * 2), Math.max(16, contentBottom - treeTop));
        } else {
            this.listLabelY = contentTop;
            this.treeLabelY = contentTop;
            int top = contentTop + LABEL_HEIGHT;
            int available = Math.max(20, contentBottom - top);
            int listWidth = Math.max(140, (width - pad * 2 - 6) * 2 / 5);
            this.ruleList.setBounds(pad, top, listWidth, available);
            int treeX = pad + listWidth + 6;
            this.conditionTree.setBounds(treeX, top, Math.max(60, width - pad - treeX), available);
        }
        this.modalStack.setBounds(0, 0, width, height);
    }

    // ---- 渲染 ----

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.drawWindow(graphics, new UiRect(0, 0, this.width, this.height));
        UiRect header = new UiRect(pad(), pad(), Math.max(0, this.width - pad() * 2), UiTheme.HEADER_HEIGHT);
        UiTheme.drawHeader(graphics, header);
        graphics.drawString(this.font, this.getTitle(), header.x() + UiTheme.PADDING, header.y() + UiTheme.TEXT_OFFSET, UiPalette.HEADER_TEXT, false);
        String status = this.statusLine.getString();
        if (!status.isEmpty()) {
            graphics.drawString(this.font, status, this.width - pad() - 4 - this.font.width(status), pad() + 4, UiPalette.TEXT_SECONDARY, false);
        }
        graphics.drawString(this.font, Component.translatable("gui.itemdespawntowhat.prototype.section.list"), pad() + 2, this.listLabelY, UiPalette.TEXT_SECONDARY, false);
        graphics.drawString(this.font, Component.translatable("gui.itemdespawntowhat.prototype.section.tree"), (this.treeLabelY == this.listLabelY ? this.conditionTree.bounds().x() : pad()) + 2, this.treeLabelY, UiPalette.TEXT_SECONDARY, false);
        var pointer = com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer.gated(this.modalStack.isEmpty(), mouseX, mouseY);
        int visibleMouseX = pointer.x();
        int visibleMouseY = pointer.y();
        for (UiWidget widget : this.baseWidgets) {
            if (widget.isVisible()) {
                widget.render(graphics, this.font, visibleMouseX, visibleMouseY);
            }
        }
        this.modalStack.render(graphics, this.font, mouseX, mouseY);
        if (this.modalStack.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.itemdespawntowhat.prototype.hint"), pad() + 2, this.hintY, UiPalette.TEXT_SECONDARY, false);
        }
    }

    // 内边距
    private int pad() {
        return UiTheme.PADDING;
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.modalStack.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = this.baseWidgets.size() - 1; i >= 0; i--) {
            UiWidget widget = this.baseWidgets.get(i);
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) {
                // 鼠标点击夺取焦点，保证输入框点击后可以直接输入
                if (widget instanceof UiFocusTarget target && target.canFocus()) {
                    this.baseFocus.focusOn(target);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.modalStack.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        for (UiWidget widget : this.baseWidgets) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.modalStack.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        for (UiWidget widget : this.baseWidgets) {
            if (widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.modalStack.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        for (UiWidget widget : this.baseWidgets) {
            if (widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.modalStack.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (this.baseFocus.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        UiFocusTarget focused = this.baseFocus.focused();
        if (focused instanceof UiWidget widget && widget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.modalStack.charTyped(codePoint, modifiers)) {
            return true;
        }
        UiFocusTarget focused = this.baseFocus.focused();
        if (focused instanceof UiWidget widget && widget.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ---- 弹窗示例 ----

    // 打开演示弹窗
    private void openDemoModal() {
        UiTextInput modalInput = new UiTextInput(this.font, Component.translatable("gui.itemdespawntowhat.prototype.modal.hint"));
        UiModal modal = UiModal.create(this.font)
                .title(Component.translatable("gui.itemdespawntowhat.prototype.modal.title"))
                .contentWidget(modalInput, 20)
                .preferredWidth(240)
                .addButton(new UiButton(this.font, Component.translatable("gui.itemdespawntowhat.prototype.modal.stack"),
                        UiButtonVariant.SECONDARY, this::openNestedModal))
                .confirm(Component.translatable("gui.itemdespawntowhat.prototype.modal.confirm"),
                        () -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.modal"))
                .cancel(Component.translatable("gui.itemdespawntowhat.prototype.modal.cancel"));
        this.modalStack.push(modal);
    }

    // 在弹窗之上再叠一层，验证弹窗栈
    private void openNestedModal() {
        UiModal nested = UiModal.create(this.font)
                .title(Component.translatable("gui.itemdespawntowhat.prototype.modal.stack.title"))
                .message(Component.translatable("gui.itemdespawntowhat.prototype.modal.stack.message"))
                .preferredWidth(200)
                .confirm(Component.translatable("gui.itemdespawntowhat.prototype.modal.confirm"),
                        () -> this.statusLine = Component.translatable("gui.itemdespawntowhat.prototype.status.modal"))
                .cancel(Component.translatable("gui.itemdespawntowhat.prototype.modal.cancel"));
        this.modalStack.push(nested);
    }

    // ---- 界面属性 ----

    @Override
    public void tick() {
        this.modalStack.tick();
    }

    // 原型屏不暂停游戏，方便对照世界状态
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
