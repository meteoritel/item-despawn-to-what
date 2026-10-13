package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/***
 * 按钮控件。
 * <p>委托 kit 的 {@link UiControl} 处理状态与焦点，按需绘制主题的像素立体边或胶囊底图，
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
    private boolean chipStyle;
    private int hoverTicks;
    private static final int CHIP_PADDING = 4;
    private static final int ICON_GAP = 3;

    // 注入的宿主主题
    private final UiHostTheme theme;
    // 兼容入口：未迁移模块沿用默认宿主主题
    public UiButton(Font font, Component label, UiButtonVariant variant, UiAction action) {
        this(UiHostThemes.defaultTheme(), font, label, variant, action);
    }

    public UiButton(UiHostTheme theme, Font font, Component label, UiButtonVariant variant, UiAction action) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        this.label = label;
        this.variant = variant;
        this.action = action;
        apply();
    }

    // 把当前字段重新写入 kit 控件
    private void apply() {
        control.configure(font, label, theme.controlTextColor(variant.role()), icon, tooltip, action);
        control.setStyle(theme.controlStyle(variant.role()));
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

    /**
     * 选择胶囊底图，沿用按钮的点击、禁用与键盘焦点行为。
     *
     * @param chipStyle 是否使用宿主主题的胶囊样式
     * @return 当前按钮
     */
    public UiButton setChipStyle(boolean chipStyle) {
        this.chipStyle = chipStyle;
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
            width += icon.width() + ICON_GAP;
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
        if (chipStyle) {
            renderChip(graphics, font, mouseX, mouseY);
            return;
        }
        control.render(graphics, font, mouseX, mouseY);
        theme.drawBevel(graphics, control.bounds(), theme.palette().controlBevelLight(), theme.palette().controlBevelDark());
    }

    private void renderChip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        boolean hovered = control.isHittable() && rect.contains(mouseX, mouseY);
        if (control.isEnabled()) theme.drawChip(graphics, rect, isPressed(), hovered);
        else theme.drawSegment(graphics, rect, false, false, false);
        int iconWidth = icon == null ? 0 : icon.width() + ICON_GAP;
        int available = Math.max(0, rect.width() - CHIP_PADDING * 2);
        int contentWidth = Math.min(available, font.width(label) + iconWidth);
        int x = rect.x() + (rect.width() - contentWidth) / 2;
        // 胶囊按钮在窄布局中仍须把图标与文字裁剪到自身范围。
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        try {
            if (icon != null) icon.render(graphics, x, rect.y() + (rect.height() - icon.height()) / 2);
            hoverTicks = hovered ? hoverTicks + 1 : 0;
            int color = control.isEnabled() ? theme.controlTextColor(variant.role()) : theme.palette().textDisabled();
            TextScroll.draw(graphics, font, label.getString(), x + iconWidth,
                    rect.y() + (rect.height() - font.lineHeight) / 2,
                    Math.max(0, contentWidth - iconWidth), color, hovered, hoverTicks, false);
        } finally {
            graphics.disableScissor();
        }
        if (control.isFocused()) theme.drawFocusOutline(graphics, rect);
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
