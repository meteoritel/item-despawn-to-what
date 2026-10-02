package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 数值滑杆：左侧标签，右侧当前值，中间像素轨道与滑块。
 * <p>数值一律同时显示为文本，颜色不是唯一线索。
 */
public final class UiSlider implements UiWidget, UiFocusTarget {

    // 轨道高度
    public static final int TRACK_HEIGHT = 4;
    // 滑块宽度
    public static final int THUMB_WIDTH = 3;

    private final Font font;
    private double min;
    private double max;
    private double step;
    private double value;
    private Component label = Component.empty();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean dragging;
    private @Nullable DoubleConsumer onChanged;
    private @Nullable Function<Double, Component> formatter;

    public UiSlider(Font font, double min, double max, double step) {
        this.font = Objects.requireNonNull(font, "font");
        setRange(min, max, step);
    }

    // 设置取值域与步长（自动钳制当前值）
    public UiSlider setRange(double nextMin, double nextMax, double nextStep) {
        this.min = nextMin;
        this.max = nextMax > nextMin ? nextMax : nextMin + 1.0D;
        this.step = nextStep;
        setValue(this.value);
        return this;
    }

    public double value() {
        return value;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    // 直接设置取值（吸附到步长并钳制，不回调），用于从草稿回填
    public UiSlider setValue(double next) {
        this.value = snap(next);
        return this;
    }

    public UiSlider setLabel(Component next) {
        this.label = next == null ? Component.empty() : next;
        return this;
    }

    public UiSlider setFormatter(@Nullable Function<Double, Component> next) {
        this.formatter = next;
        return this;
    }

    public UiSlider setOnChanged(@Nullable DoubleConsumer listener) {
        this.onChanged = listener;
        return this;
    }

    public UiSlider setEnabled(boolean next) {
        this.enabled = next;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiSlider setVisible(boolean next) {
        this.visible = next;
        return this;
    }

    // 当前值的显示文本
    public Component formatValue() {
        if (formatter != null) {
            Component custom = formatter.apply(value);
            if (custom != null) {
                return custom;
            }
        }
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return Component.literal(Long.toString(Math.round(value)));
        }
        return Component.literal(String.format(java.util.Locale.ROOT, "%.2f", value));
    }

    // 吸附到步长并钳制到取值域
    private double snap(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        if (step > 0.0D) {
            double steps = Math.round((clamped - min) / step);
            clamped = Math.max(min, Math.min(max, min + steps * step));
        }
        return clamped;
    }

    private int trackLeft() {
        return bounds.x() + THUMB_WIDTH;
    }

    private int trackRight() {
        return Math.max(trackLeft() + 1, bounds.right() - THUMB_WIDTH);
    }

    private int trackWidth() {
        return Math.max(1, trackRight() - trackLeft());
    }

    private double fraction() {
        return max > min ? (value - min) / (max - min) : 0.0D;
    }

    private int thumbX() {
        int x = trackLeft() + (int) Math.round(fraction() * trackWidth()) - THUMB_WIDTH / 2;
        return Math.max(bounds.x(), Math.min(bounds.right() - THUMB_WIDTH, x));
    }

    // 由鼠标横坐标更新取值
    private void valueFromMouse(double mouseX) {
        double t = (mouseX - trackLeft()) / (double) trackWidth();
        t = Math.max(0.0D, Math.min(1.0D, t));
        double next = snap(min + t * (max - min));
        if (Math.abs(next - value) < 1.0E-9D) {
            return;
        }
        value = next;
        if (onChanged != null) {
            onChanged.accept(value);
        }
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
        int textY = bounds.y() + Math.max(0, (bounds.height() - renderFont.lineHeight) / 2);
        String labelText = TextScroll.trimToWidth(renderFont, label.getString(), Math.max(0, bounds.width() / 2));
        if (!labelText.isEmpty()) {
            graphics.drawString(renderFont, labelText, bounds.x(), textY,
                    enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
        }
        int trackY = bounds.y() + Math.max(0, (bounds.height() - TRACK_HEIGHT) / 2);
        graphics.fill(trackLeft(), trackY, trackRight(), trackY + TRACK_HEIGHT, UiPalette.SCROLL_TRACK);
        int filled = trackLeft() + (int) Math.round(fraction() * trackWidth());
        graphics.fill(trackLeft(), trackY, filled, trackY + TRACK_HEIGHT,
                enabled ? UiPalette.ACCENT : UiPalette.CONTROL_DISABLED);
        int thumbX = thumbX();
        graphics.fill(thumbX, trackY - 2, thumbX + THUMB_WIDTH, trackY + TRACK_HEIGHT + 2,
                enabled ? UiPalette.CONTROL_FILL : UiPalette.CONTROL_DISABLED);
        String valueText = formatValue().getString();
        int valueWidth = renderFont.width(valueText);
        int valueX = Math.max(bounds.x(), bounds.right() - valueWidth);
        graphics.drawString(renderFont, valueText, valueX, textY,
                enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0 || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        valueFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        valueFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        double stepSize = step > 0.0D ? step : Math.max(1.0E-4D, (max - min) / 100.0D);
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> nudge(-stepSize);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> nudge(stepSize);
            case GLFW.GLFW_KEY_PAGE_DOWN -> nudge(-stepSize * 10.0D);
            case GLFW.GLFW_KEY_PAGE_UP -> nudge(stepSize * 10.0D);
            case GLFW.GLFW_KEY_HOME -> setFromUser(min);
            case GLFW.GLFW_KEY_END -> setFromUser(max);
            default -> false;
        };
    }

    private boolean nudge(double delta) {
        return setFromUser(value + delta);
    }

    // 用户操作的统一落点：吸附、比较、写值与回调
    private boolean setFromUser(double raw) {
        double next = snap(raw);
        if (Math.abs(next - value) < 1.0E-9D) {
            return true;
        }
        value = next;
        if (onChanged != null) {
            onChanged.accept(value);
        }
        return true;
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
        return false;
    }

    @Override
    public @Nullable Component accessibleName() {
        return label;
    }
}
