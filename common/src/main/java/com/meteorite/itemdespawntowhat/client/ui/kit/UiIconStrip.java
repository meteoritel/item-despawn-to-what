package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 通用图标条：水平功能栏与竖直工具列共用同一实现，每格是一个只有图标与提示的 {@link UiControl}。
 *
 * <p>本类只负责布局与每格状态：格子背景、悬停/按下/选中/禁用/焦点绘制、tooltip 载体、命中与
 * {@code activate} 全部复用 {@link UiControl}；启用与禁用只决定使用常态还是禁用字形。
 * 输入契约走 kit 的 {@link UiInputTarget} 与 {@link UiFocusTarget}，宿主用一层薄适配接入既有控件分发。</p>
 *
 * <p>{@link #setBounds} 后立即重排，不需要再调用布局方法；不可见的格子不占槽位。
 * 所有格子共用统一尺寸（取可见项图标的最大宽高），因此切换字形不会让图标错位。</p>
 */
public final class UiIconStrip implements UiInputTarget, UiFocusTarget {

    /** 排列方向：水平功能栏沿 x 排布，竖直工具列沿 y 排布。 */
    public enum Orientation { HORIZONTAL, VERTICAL }

    /** 单格四周留白：图标与格子边缘的最小距离。 */
    private static final int CELL_PADDING = 2;
    /** 空标签占位色：格子只有图标，标签恒为空，该颜色不参与画面。 */
    private static final int EMPTY_LABEL_COLOR = 0xFFFFFFFF;

    private final Font font;
    private final List<Item> cells = new ArrayList<>();
    private Orientation orientation;
    private UiControlStyle style = UiControlStyle.DARK;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private int focusedIndex = -1;
    private int pressedIndex = -1;
    private boolean focused;

    public UiIconStrip(Font font, Orientation orientation) {
        this.font = Objects.requireNonNull(font);
        this.orientation = Objects.requireNonNull(orientation);
    }

    /** 加一格：常态与禁用共用同一字形。 */
    public Item add(UiIcon icon, Component tooltip, UiAction action) {
        return add(icon, icon, tooltip, action);
    }

    /** 加一格：禁用时切换到 {@code disabled} 字形。 */
    public Item add(UiIcon normal, UiIcon disabled, Component tooltip, UiAction action) {
        Item item = new Item(this, Objects.requireNonNull(normal, "Normal icon"),
                Objects.requireNonNull(disabled, "Disabled icon"), tooltip, action);
        cells.add(item);
        item.applyIcon();
        relayout();
        return item;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation);
        relayout();
    }

    /** 皮肤注入：只影响格子结构色与焦点轮廓。 */
    public void setStyle(UiControlStyle style) {
        this.style = Objects.requireNonNull(style);
        for (Item item : cells) item.control.setStyle(style);
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        relayout();
    }

    @Override
    public UiRect bounds() { return bounds; }

    /**
     * 量取一组格子横向覆盖的宽度：取可见格子的最右边缘减最左边缘，零可见格子时为 0。
     *
     * <p>只读度量，不改变布局与命中判定；同一图标条内的格子沿排列方向紧邻摆放，因此水平条
     * 用它得到各格宽度之和，竖直条得到单格宽度。宿主借此把底板收窄到最后一格之后，把右侧
     * 空白交还画布，而命中仍按格子矩形处理。</p>
     *
     * @param items 同一图标条内的格子句柄，{@code null} 视为空集合
     * @return 可见格子横向覆盖的宽度
     */
    public static int cellsWidth(@Nullable List<Item> items) {
        if (items == null) {
            return 0;
        }
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        for (Item item : items) {
            if (item == null || !item.isVisible()) {
                continue;
            }
            UiRect rect = item.bounds();
            left = Math.min(left, rect.x());
            right = Math.max(right, rect.right());
        }
        return left == Integer.MAX_VALUE ? 0 : Math.max(0, right - left);
    }

    /** 绘制全部可见格子；{@code mouseX/mouseY} 为宿主 GUI 逻辑坐标。 */
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < cells.size(); i++) {
            UiControl control = cells.get(i).control;
            if (!control.isVisible()) continue;
            control.setFocused(focused && i == focusedIndex);
            control.render(graphics, font, mouseX, mouseY);
        }
    }

    /** 命中的格子目标（含 tooltip 与动作）；供宿主显示提示或做光标反馈。 */
    @Nullable
    public UiTarget hit(double x, double y) {
        int index = indexAt(x, y);
        return index < 0 ? null : cells.get(index).control.hit(x, y);
    }

    // ---------- UiInputTarget ----------

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.button() != 0) return false;
        int index = indexAt(context.pointerX(), context.pointerY());
        if (index < 0) return false;
        UiControl control = cells.get(index).control;
        if (!control.isHittable()) return false;
        control.setPressed(true);
        pressedIndex = index;
        focusedIndex = index;
        return true;
    }

    // 拖动期间保持独占：指针离开格子后仍继续消费，直到释放。
    @Override
    public boolean mouseDragged(UiInputContext context) {
        return pressedIndex >= 0;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (context.button() != 0 || pressedIndex < 0) return false;
        int pressed = pressedIndex;
        pressedIndex = -1;
        for (Item item : cells) item.control.setPressed(false);
        // 指针移回同一格才激活；无论是否激活都消费这次释放，避免同一次点击落到画布上。
        if (indexAt(context.pointerX(), context.pointerY()) == pressed) {
            cells.get(pressed).control.activate();
        }
        return true;
    }

    // 方向键在可聚焦格之间移动（横向条用左右，纵向条用上下）；Enter / Space 由焦点管理器走 activate()。
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int delta = 0;
        if (orientation == Orientation.HORIZONTAL) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) delta = -1;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT) delta = 1;
        } else {
            if (keyCode == GLFW.GLFW_KEY_UP) delta = -1;
            else if (keyCode == GLFW.GLFW_KEY_DOWN) delta = 1;
        }
        return delta != 0 && moveFocus(delta);
    }

    // ---------- UiFocusTarget ----------

    @Override
    public boolean canFocus() { return firstFocusableIndex() >= 0; }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused && canFocus();
        if (this.focused && focusedIndex < 0) focusedIndex = firstFocusableIndex();
        if (!this.focused) {
            for (Item item : cells) item.control.setFocused(false);
        }
    }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public boolean activate() {
        int index = focusedIndex >= 0 ? focusedIndex : firstFocusableIndex();
        return index >= 0 && cells.get(index).control.activate();
    }

    @Override
    public @Nullable Component accessibleName() {
        int index = focusedIndex >= 0 ? focusedIndex : firstFocusableIndex();
        return index < 0 ? null : cells.get(index).control.accessibleName();
    }

    // ---------- 内部 ----------

    private int indexAt(double x, double y) {
        for (int i = 0; i < cells.size(); i++) {
            UiControl control = cells.get(i).control;
            if (control.isVisible() && control.bounds().contains(x, y)) return i;
        }
        return -1;
    }

    private int firstFocusableIndex() {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).control.isFocusable()) return i;
        }
        return -1;
    }

    // 在可聚焦格之间环绕移动；当前焦点不在列表里（尚未聚焦或该格被禁用）时从首/末格进入。
    private boolean moveFocus(int delta) {
        List<Integer> focusable = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).control.isFocusable()) focusable.add(i);
        }
        if (focusable.isEmpty()) return false;
        int position = focusable.indexOf(focusedIndex);
        int next = position < 0
                ? (delta < 0 ? focusable.size() - 1 : 0)
                : Math.floorMod(position + delta, focusable.size());
        focusedIndex = focusable.get(next);
        focused = true;
        return true;
    }

    // 统一格子尺寸：取可见项两种字形里的最大宽高，再补四周留白。
    private void relayout() {
        boolean anyVisible = false;
        int cellWidth = 0;
        int cellHeight = 0;
        for (Item item : cells) {
            if (!item.control.isVisible()) continue;
            anyVisible = true;
            cellWidth = Math.max(cellWidth, Math.max(item.normal.width(), item.disabled.width()));
            cellHeight = Math.max(cellHeight, Math.max(item.normal.height(), item.disabled.height()));
        }
        if (!anyVisible) {
            for (Item item : cells) item.control.setBounds(0, 0, 0, 0);
            return;
        }
        cellWidth += CELL_PADDING * 2;
        cellHeight += CELL_PADDING * 2;
        if (orientation == Orientation.HORIZONTAL) {
            int y = bounds.y() + Math.max(0, (bounds.height() - cellHeight) / 2);
            int x = bounds.x();
            for (Item item : cells) {
                if (!item.control.isVisible()) {
                    item.control.setBounds(0, 0, 0, 0);
                    continue;
                }
                item.control.setBounds(x, y, cellWidth, cellHeight);
                x += cellWidth;
            }
        } else {
            int x = bounds.x() + Math.max(0, (bounds.width() - cellWidth) / 2);
            int y = bounds.y();
            for (Item item : cells) {
                if (!item.control.isVisible()) {
                    item.control.setBounds(0, 0, 0, 0);
                    continue;
                }
                item.control.setBounds(x, y, cellWidth, cellHeight);
                y += cellHeight;
            }
        }
    }

    /** 图标条的格子句柄：状态变化只影响该格绘制，不重建图标条。 */
    public static final class Item {
        private final UiIconStrip strip;
        private final UiControl control = new UiControl();
        private final Component tooltipOrNull;
        @Nullable private final UiAction action;
        private UiIcon normal;
        private UiIcon disabled;
        private boolean enabled = true;
        private boolean visible = true;
        private boolean selected;

        private Item(UiIconStrip strip, UiIcon normal, UiIcon disabled,
                     @Nullable Component tooltip, @Nullable UiAction action) {
            this.strip = strip;
            this.normal = normal;
            this.disabled = disabled;
            this.tooltipOrNull = tooltip == null ? null : tooltip.copy();
            this.action = action;
            this.control.setStyle(strip.style);
        }

        public void setEnabled(boolean value) {
            if (enabled == value) return;
            enabled = value;
            control.setEnabled(value);
            applyIcon();
        }

        public void setVisible(boolean value) {
            if (visible == value) return;
            visible = value;
            control.setVisible(value);
            strip.relayout();
        }

        public void setSelected(boolean value) {
            selected = value;
            control.setSelected(value);
        }

        /** 切换图标对（例如 JSON 入口在树视图与外置 JSON 视图之间切换）；尺寸变化时重排。 */
        public void setIcon(UiIcon normal, UiIcon disabled) {
            this.normal = Objects.requireNonNull(normal, "Normal icon");
            this.disabled = Objects.requireNonNull(disabled, "Disabled icon");
            applyIcon();
            strip.relayout();
        }

        public boolean isEnabled() { return enabled; }

        public boolean isVisible() { return visible; }

        public boolean isSelected() { return selected; }

        public UiRect bounds() { return control.bounds(); }

        // 禁用时换成禁用字形；提示与动作保持不变，因此只需重配一次 UiControl。
        private void applyIcon() {
            control.configure(strip.font, Component.empty(), EMPTY_LABEL_COLOR,
                    enabled ? normal : disabled,
                    tooltipOrNull == null ? List.of() : List.of(tooltipOrNull),
                    action);
        }
    }
}
