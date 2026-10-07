package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 分段选择控件：把少量互斥取值渲染成一排等宽按钮（枚举取值用）。
 * <p>选中项同时用底色与文字颜色区分，颜色不是唯一线索。
 */
public final class UiSegmentedControl implements UiWidget, UiFocusTarget {

    /*** 单个分段选项 */
    public record Option(String value, Component label) {
        public Option {
            Objects.requireNonNull(value, "value");
            if (label == null) {
                label = Component.literal(value);
            }
        }
    }

    private final Font font;
    private List<Option> options = List.of();
    private String selected = "";
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private @Nullable Consumer<String> onChanged;

    public UiSegmentedControl(Font font, List<Option> options) {
        this.font = Objects.requireNonNull(font, "font");
        setOptions(options);
    }

    // 设置候选项；若当前选中值不在候选中，则回退到第一项
    public UiSegmentedControl setOptions(List<Option> next) {
        List<Option> copy = new ArrayList<>();
        if (next != null) {
            for (Option option : next) {
                if (option != null) {
                    copy.add(option);
                }
            }
        }
        this.options = List.copyOf(copy);
        boolean present = false;
        for (Option option : this.options) {
            if (option.value().equals(selected)) {
                present = true;
                break;
            }
        }
        if (!present) {
            this.selected = this.options.isEmpty() ? "" : this.options.getFirst().value();
        }
        return this;
    }

    public List<Option> options() {
        return options;
    }

    public String selected() {
        return selected;
    }

    // 选中下标，无选中返回 -1
    public int selectedIndex() {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    // 直接设置选中值（不回调），用于从草稿回填
    public UiSegmentedControl setSelected(@Nullable String value) {
        this.selected = "";
        if (value == null) {
            return this;
        }
        for (Option option : options) {
            if (option.value().equals(value)) {
                this.selected = value;
                return this;
            }
        }
        return this;
    }

    public UiSegmentedControl setOnChanged(@Nullable Consumer<String> listener) {
        this.onChanged = listener;
        return this;
    }

    public UiSegmentedControl setEnabled(boolean next) {
        this.enabled = next;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiSegmentedControl setVisible(boolean next) {
        this.visible = next;
        return this;
    }

    public int preferredWidth(int padding) {
        int total = 0;
        for (Option option : options) {
            total += font.width(option.label()) + padding * 2;
        }
        return total;
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

    // 第 index 段的矩形（等分，最后一段吃掉余数）
    private UiRect segmentRect(int index) {
        int count = options.size();
        if (count <= 0) {
            return new UiRect(bounds.x(), bounds.y(), 0, 0);
        }
        int left = bounds.x() + (int) ((long) bounds.width() * index / count);
        int right = bounds.x() + (int) ((long) bounds.width() * (index + 1) / count);
        return new UiRect(left, bounds.y(), Math.max(0, right - left), bounds.height());
    }

    // 鼠标位置落在哪一段，未命中返回 -1
    private int indexAt(double mouseX, double mouseY) {
        if (options.isEmpty() || !bounds.contains(mouseX, mouseY)) {
            return -1;
        }
        for (int i = 0; i < options.size(); i++) {
            if (segmentRect(i).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible || options.isEmpty()) {
            return;
        }
        int hovered = enabled ? indexAt(mouseX, mouseY) : -1;
        int chosen = selectedIndex();
        UiControlStyle style = UiTheme.secondaryStyle();
        for (int i = 0; i < options.size(); i++) {
            UiRect segment = segmentRect(i);
            UiControlStyle.State state;
            if (!enabled) {
                state = UiControlStyle.State.DISABLED;
            } else if (i == chosen) {
                state = UiControlStyle.State.SELECTED;
            } else if (i == hovered) {
                state = UiControlStyle.State.HOVER;
            } else {
                state = UiControlStyle.State.NORMAL;
            }
            graphics.fill(segment.x(), segment.y(), segment.right(), segment.bottom(), style.background(state));
            int color = !enabled ? UiPalette.TEXT_DISABLED : UiPalette.TEXT_PRIMARY;
            String label = TextScroll.trimToWidth(renderFont, options.get(i).label().getString(),
                    Math.max(0, segment.width() - 4));
            graphics.drawString(renderFont, label, segment.x() + Math.max(0, (segment.width() - renderFont.width(label)) / 2),
                    segment.y() + Math.max(0, (segment.height() - renderFont.lineHeight) / 2), color, false);
        }
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    // 选中指定下标并回调
    private boolean select(int index) {
        if (index < 0 || index >= options.size()) {
            return false;
        }
        String next = options.get(index).value();
        if (next.equals(selected)) {
            return true;
        }
        selected = next;
        if (onChanged != null) {
            onChanged.accept(selected);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        return index >= 0 && select(index);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled || options.isEmpty()) {
            return false;
        }
        int current = Math.max(0, selectedIndex());
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> {
                return select(Math.floorMod(current - 1, options.size()));
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> {
                return select((current + 1) % options.size());
            }
            case GLFW.GLFW_KEY_HOME -> {
                return select(0);
            }
            case GLFW.GLFW_KEY_END -> {
                return select(options.size() - 1);
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean canFocus() {
        return visible && enabled && !options.isEmpty();
    }

    @Override
    public void setFocused(boolean next) {
        this.focused = next;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    // Enter 激活：循环切到下一项
    @Override
    public boolean activate() {
        if (!visible || !enabled || options.isEmpty()) {
            return false;
        }
        int current = Math.max(0, selectedIndex());
        return select((current + 1) % options.size());
    }

    @Override
    public @Nullable Component accessibleName() {
        int index = selectedIndex();
        if (index < 0) {
            return Component.empty();
        }
        return options.get(index).label();
    }
}
