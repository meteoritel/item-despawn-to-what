package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/***
 * 单行文本输入框。
 * <p>封装原版 {@link EditBox}：关闭原版边框，改由主题绘制内凹像素框；
 * 支持占位提示、长度限制、输入过滤、提交（Enter）与取消（Esc）。
 * <p>点击输入框本身不夺取焦点，焦点顺序由宿主的 {@code UiFocusManager} 统一维护。
 */
public final class UiTextInput implements UiWidget, UiFocusTarget {

    // 左右内边距
    private static final int PADDING_X = 3;
    // 上下内边距
    private static final int PADDING_Y = 2;
    // 最小宽度
    private static final int MIN_WIDTH = 24;

    // 原版输入框
    private final EditBox editBox;
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 提交回调（Enter）
    private Consumer<String> onCommit;
    // 取消回调（Esc）
    private Runnable onCancel;
    // 无障碍名称
    private Component accessibleName;
    // 是否可见
    private boolean visible = true;
    // 是否可编辑（原版 EditBox 未暴露读取方法，这里自行记录）
    private boolean editable = true;

    public UiTextInput(Font font, Component hint) {
        this.editBox = new EditBox(font, 0, 0, MIN_WIDTH, UiTheme.ROW_HEIGHT, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setHint(hint);
        this.editBox.setTextColor(UiPalette.TEXT_ON_DARK);
        this.editBox.setTextColorUneditable(UiPalette.TEXT_DISABLED);
        this.editBox.setMaxLength(256);
        this.editBox.setCanLoseFocus(true);
        this.accessibleName = hint;
    }

    // 当前文本
    public String value() {
        return editBox.getValue();
    }

    // 设置文本
    public UiTextInput setValue(String value) {
        editBox.setValue(value);
        return this;
    }

    // 清空文本
    public UiTextInput clear() {
        editBox.setValue("");
        return this;
    }

    // 设置最大长度
    public UiTextInput setMaxLength(int maxLength) {
        editBox.setMaxLength(maxLength);
        return this;
    }

    // 设置输入过滤规则
    public UiTextInput setFilter(Predicate<String> filter) {
        editBox.setFilter(filter);
        return this;
    }

    // 设置是否可编辑
    public UiTextInput setEditable(boolean editable) {
        this.editable = editable;
        editBox.setEditable(editable);
        return this;
    }

    // 设置占位提示
    public UiTextInput setHint(Component hint) {
        editBox.setHint(hint);
        return this;
    }

    // 设置提交回调
    public UiTextInput setOnCommit(Consumer<String> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    // 设置取消回调
    public UiTextInput setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    // 设置是否可见
    public UiTextInput setVisible(boolean visible) {
        this.visible = visible;
        editBox.setVisible(visible);
        return this;
    }

    // 全选当前文本
    public UiTextInput selectAll() {
        editBox.setHighlightPos(0);
        editBox.moveCursorToEnd(true);
        return this;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        editBox.setX(bounds.x() + PADDING_X);
        editBox.setY(bounds.y() + PADDING_Y);
        editBox.setWidth(Math.max(1, bounds.width() - PADDING_X * 2));
        editBox.setHeight(Math.max(1, bounds.height() - PADDING_Y * 2));
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        UiTheme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, UiPalette.SLOT_INNER);
        UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND,
                () -> editBox.renderWidget(graphics, mouseX, mouseY, 0.0F));
        if (editBox.isFocused()) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        return editBox.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return editBox.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (onCommit != null) {
                onCommit.accept(editBox.getValue());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (onCancel != null) {
                onCancel.run();
                return true;
            }
            return false;
        }
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) {
            return false;
        }
        return editBox.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean canFocus() {
        return visible && editable;
    }

    @Override
    public void setFocused(boolean focused) {
        editBox.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return editBox.isFocused();
    }

    @Override
    public boolean activate() {
        if (!visible || onCommit == null) {
            return false;
        }
        onCommit.accept(editBox.getValue());
        return true;
    }

    @Override
    public Component accessibleName() {
        return accessibleName;
    }

    // 设置无障碍名称
    public UiTextInput setAccessibleName(Component name) {
        this.accessibleName = name;
        return this;
    }
}
