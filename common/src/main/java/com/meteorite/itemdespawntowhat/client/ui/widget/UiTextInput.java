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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
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
    private final Font font;
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
    // 本次按下是否由本控件承接：只有承接了按下的控件才消费释放，避免吞掉后面控件的释放
    private boolean pressed;
    // 码点上限：原版 EditBox 的上限按 UTF-16 单元截断，不能直接用，这里自己按 Unicode 码点判定
    private int maxLength = 256;
    // 最近一次被接受的文本（构造函数里初始化），超限时整体回退到它
    private String lastAccepted;
    // 业务过滤规则
    private Predicate<String> filter = text -> true;
    // 输入因超上限被拒绝时的回调
    private @Nullable Runnable onOverflow;
    // 文本被接受发生变化时的回调（宿主据此清除超限提示）
    private @Nullable Runnable onValueChanged;

    public UiTextInput(Font font, Component hint) {
        this.font = font;
        this.editBox = new EditBox(font, 0, 0, MIN_WIDTH, UiTheme.ROW_HEIGHT, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setHint(hint);
        this.editBox.setTextColor(UiPalette.TEXT_ON_DARK);
        this.editBox.setTextColorUneditable(UiPalette.TEXT_DISABLED);
        // 给 EditBox 留 2 倍 UTF-16 单元冗余，保证码点上限先起作用、补充平面字符不被单元数提前拒
        this.editBox.setMaxLength(maxLength * 2);
        this.editBox.setCanLoseFocus(true);
        this.editBox.setResponder(text -> onValueAccepted());
        this.lastAccepted = editBox.getValue();
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

    // 设置长度上限（Unicode 码点）：超出上限的输入整体被拒绝，不截断，并触发 onOverflow
    public UiTextInput setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        editBox.setMaxLength(this.maxLength * 2);
        installFilter();
        return this;
    }

    // 设置输入过滤规则：与码点上限共同构成 EditBox 的过滤器
    public UiTextInput setFilter(Predicate<String> filter) {
        this.filter = filter == null ? text -> true : filter;
        installFilter();
        return this;
    }

    // 安装过滤器：先判码点上限（超限整体拒绝并上报），再判业务规则
    private void installFilter() {
        editBox.setFilter(candidate -> {
            if (codePoints(candidate) > maxLength) {
                reportOverflow();
                return false;
            }
            return filter.test(candidate);
        });
    }

    // 文本被接受：更新回退基线并清除超限提示；超限兜底回退（正常已被过滤器拦住）
    private void onValueAccepted() {
        String value = editBox.getValue();
        if (codePoints(value) > maxLength) {
            editBox.setValue(lastAccepted);
            return;
        }
        this.lastAccepted = value;
        if (onValueChanged != null) {
            onValueChanged.run();
        }
    }

    // Unicode 码点计数：补充平面字符（emoji）按 1 个码点计
    private static int codePoints(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    // 上报一次「输入超出码点上限被拒绝」
    private void reportOverflow() {
        if (onOverflow != null) {
            onOverflow.run();
        }
    }

    // 设置超限回调
    public UiTextInput setOnOverflow(@Nullable Runnable onOverflow) {
        this.onOverflow = onOverflow;
        return this;
    }

    // 设置「文本被接受并变化」回调
    public UiTextInput setOnValueChanged(@Nullable Runnable onValueChanged) {
        this.onValueChanged = onValueChanged;
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

    // 把光标与水平滚动复位到开头；回填后调用一次，编辑期间不调用
    public UiTextInput moveCursorToStart() {
        editBox.moveCursorToStart(false);
        editBox.setHighlightPos(0);
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
        editBox.setY(bounds.y() + Math.max(PADDING_Y, (bounds.height() - font.lineHeight) / 2));
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
        this.pressed = true;
        return editBox.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed) {
            return false;
        }
        this.pressed = false;
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
        String before = editBox.getValue();
        boolean handled = editBox.keyPressed(keyCode, scanCode, modifiers);
        // 已达上限时的粘贴同样被静默丢弃，给出与键入一致的超限提示
        boolean paste = Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V
                || Screen.hasShiftDown() && keyCode == GLFW.GLFW_KEY_INSERT;
        if (handled && editBox.getValue().equals(before) && paste && codePoints(before) >= maxLength) {
            reportOverflow();
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) {
            return false;
        }
        String before = editBox.getValue();
        boolean handled = editBox.charTyped(codePoint, modifiers);
        // 已达码点上限时 EditBox 单元上限会先耗尽、字符被静默丢弃（不经过过滤器），这里补一次提示
        if (handled && editBox.getValue().equals(before)
                && codePoints(before) + Character.charCount(codePoint) > maxLength) {
            reportOverflow();
        }
        return handled;
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
