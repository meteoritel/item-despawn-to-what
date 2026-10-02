package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 复选框：左侧像素勾选框，右侧标签文本（颜色状态一律辅以文字，不只靠颜色区分）。
 * <p>选中标记当前用 ASCII 字符占位（美术资源 TODO）。
 */
public final class UiCheckBox implements UiWidget, UiFocusTarget {

    // 勾选框边长
    public static final int BOX_SIZE = 9;
    // 勾选框与标签的水平间距
    public static final int LABEL_GAP = 3;

    private final Font font;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private Component label;
    private boolean checked;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private @Nullable Consumer<Boolean> onChanged;

    public UiCheckBox(Font font, Component label, boolean checked) {
        this.font = Objects.requireNonNull(font, "font");
        this.label = label == null ? Component.empty() : label;
        this.checked = checked;
    }

    // 附带内边距的首选宽度
    public int preferredWidth(int padding) {
        return BOX_SIZE + LABEL_GAP + font.width(label) + padding * 2;
    }

    public Component label() {
        return label;
    }

    public UiCheckBox setLabel(Component next) {
        this.label = next == null ? Component.empty() : next;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    // 直接设置状态，不触发回调（用于服务器快照回填）
    public UiCheckBox setChecked(boolean next) {
        this.checked = next;
        return this;
    }

    // 用户触发的切换：改变状态并回调
    public boolean toggle() {
        if (!enabled) {
            return false;
        }
        checked = !checked;
        if (onChanged != null) {
            onChanged.accept(checked);
        }
        return true;
    }

    public UiCheckBox setOnChanged(@Nullable Consumer<Boolean> listener) {
        this.onChanged = listener;
        return this;
    }

    public UiCheckBox setEnabled(boolean next) {
        this.enabled = next;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiCheckBox setVisible(boolean next) {
        this.visible = next;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int boxY = bounds.y() + Math.max(0, (bounds.height() - BOX_SIZE) / 2);
        UiRect box = new UiRect(bounds.x(), boxY, BOX_SIZE, BOX_SIZE);
        UiTheme.drawInset(graphics, box);
        if (checked) {
            graphics.fill(box.x() + 2, box.y() + 2, box.right() - 2, box.bottom() - 2,
                    enabled ? UiPalette.ACCENT : UiPalette.CONTROL_DISABLED);
            // TODO 美术：勾选图标贴图（当前用 ASCII 字符占位）
            graphics.drawString(renderFont, "X", box.x() + 2, box.y() + 1, UiPalette.TEXT_ON_DARK, false);
        }
        int textX = box.right() + LABEL_GAP;
        int maxWidth = Math.max(0, bounds.right() - textX);
        String text = TextScroll.trimToWidth(renderFont, label.getString(), maxWidth);
        int color = !enabled ? UiPalette.TEXT_DISABLED : (hovered ? UiPalette.TEXT_PRIMARY : UiTheme.textColor(true));
        int textY = bounds.y() + Math.max(0, (bounds.height() - renderFont.lineHeight) / 2);
        graphics.drawString(renderFont, text, textX, textY, color, false);
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0 || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        return toggle();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            return toggle();
        }
        return false;
    }

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean next) {
        this.focused = next;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        return toggle();
    }

    @Override
    public @Nullable Component accessibleName() {
        return label;
    }
}
