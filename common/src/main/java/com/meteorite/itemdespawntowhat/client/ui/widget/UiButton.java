package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/***
 * 按钮控件。
 * <p>委托 kit 的 {@link UiControl} 处理状态与焦点，再叠加主题的像素立体边，
 * 因此同时兼容键盘（Tab / Enter）与鼠标（按下、拖出取消）操作。
 */
public final class UiButton implements UiWidget, UiFocusTarget {

    // 被委托的 kit 控件
    private final UiControl control = new UiControl();
    // 变体
    private final UiButtonVariant variant;
    // 字体（重新配置文本时需要）
    private final Font font;
    // 当前文本
    private Component label;
    // 当前图标，可为 null
    private UiIcon icon;
    // 提示行
    private final List<Component> tooltip = new ArrayList<>();
    // 点击动作，可为 null
    private UiAction action;
    // 是否启用
    private boolean enabled = true;
    // 是否可见
    private boolean visible = true;

    public UiButton(Font font, Component label, UiButtonVariant variant, UiAction action) {
        this.font = font;
        this.label = label;
        this.variant = variant;
        this.action = action;
        apply();
    }

    // 把当前字段重新写入 kit 控件
    private void apply() {
        control.configure(font, label, variant.textColor(), icon, tooltip, action);
        control.setStyle(variant.style());
        control.setEnabled(enabled && variant.interactive());
        control.setVisible(visible);
    }

    // 设置文本
    public UiButton setLabel(Component label) {
        this.label = label;
        apply();
        return this;
    }

    // 设置图标
    public UiButton setIcon(UiIcon icon) {
        this.icon = icon;
        apply();
        return this;
    }

    // 设置点击动作
    public UiButton setAction(UiAction action) {
        this.action = action;
        apply();
        return this;
    }

    // 设置提示行
    public UiButton setTooltip(Component... lines) {
        tooltip.clear();
        tooltip.addAll(List.of(lines));
        apply();
        return this;
    }

    // 设置是否启用
    public UiButton setEnabled(boolean enabled) {
        this.enabled = enabled;
        apply();
        return this;
    }

    // 设置是否可见
    public UiButton setVisible(boolean visible) {
        this.visible = visible;
        apply();
        return this;
    }

    // 当前文本
    public Component label() {
        return label;
    }

    // 当前变体
    public UiButtonVariant variant() {
        return variant;
    }

    // 是否处于按下状态
    public boolean isPressed() {
        return control.isPressed();
    }

    // 按文本宽度计算合适的按钮宽度（含左右内边距与图标）
    public int preferredWidth(int padding) {
        int width = font.width(label) + padding * 2;
        if (icon != null) {
            width += icon.width() + 3;
        }
        return width;
    }

    @Override
    public UiRect bounds() {
        return control.bounds();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        control.setBounds(x, y, width, height);
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
        control.render(graphics, font, mouseX, mouseY);
        UiTheme.drawBevel(graphics, control.bounds(), variant.bevelLight(), variant.bevelDark());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0 || !control.isHittable()) {
            return false;
        }
        if (!control.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        control.setPressed(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !control.isPressed()) {
            return false;
        }
        boolean inside = control.isHittable() && control.bounds().contains(mouseX, mouseY);
        control.setPressed(false);
        if (inside) {
            control.activate();
        }
        return true;
    }

    @Override
    public boolean canFocus() {
        return visible && control.canFocus();
    }

    @Override
    public void setFocused(boolean focused) {
        control.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return control.isFocused();
    }

    @Override
    public boolean activate() {
        if (!visible || !control.isHittable()) {
            return false;
        }
        return control.activate();
    }

    @Override
    public Component accessibleName() {
        return control.accessibleName();
    }

    // 设置无障碍名称
    public UiButton setAccessibleName(Component name) {
        control.setAccessibleName(name);
        return this;
    }
}
