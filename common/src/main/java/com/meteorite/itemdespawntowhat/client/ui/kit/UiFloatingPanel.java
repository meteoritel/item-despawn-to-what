package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 通用浮动调板：标题栏可拖动、可折叠，支持贴边吸附与贴底抽屉，内容由宿主注入。
 *
 * <p>内容契约 {@link Content} 继承 kit 的 {@link UiInputTarget}，只额外要求绘制与边界；
 * 宿主把既有控件包一层薄适配即可接入。面板自身的输入契约同样是 {@link UiInputTarget} 与
 * {@link UiFocusTarget}：内容实现 {@link UiFocusTarget} 时，面板的焦点、激活与可读名称全部委派给它。</p>
 *
 * <p>结构色来自注入的 {@link UiControlStyle}：背景取 {@code background}、标题栏取
 * {@code hoverBackground}、1 像素描边取 {@code scrollbarThumb}；文字色按 {@code UiControlStyle}
 * 的既有分工由调用方给出（{@link #setTitleColor(int)}），本类不另设颜色容器。
 * 折叠按钮是一个 {@link UiControl}，复用悬停/按下/命中/激活，字形按折叠状态实时绘制。</p>
 */
public final class UiFloatingPanel implements UiInteractionSession.Target, UiFocusTarget {

    /**
     * 停靠方式：自由拖动、吸附右上角、贴底抽屉。
     */
    public enum Dock {FREE, TOP_RIGHT, BOTTOM_SHEET}

    /**
     * 内容契约：绘制与边界之外的输入方法全部来自 {@link UiInputTarget}。
     */
    public interface Content extends UiInputTarget {
        UiRect bounds();

        void setBounds(int x, int y, int width, int height);

        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

        default boolean isVisible() {
            return true;
        }
    }

    /**
     * 标题文字与标题栏边缘的水平留白。
     */
    private static final int TITLE_INSET = 4;
    /**
     * 标题栏在字体行高之外补的上下留白。
     */
    private static final int TITLE_PADDING = 2;
    /**
     * 折叠按钮相对标题栏的内缩量。
     */
    private static final int COLLAPSE_INSET = 2;
    private static final int DEFAULT_SNAP_MARGIN = 8;
    private static final int DEFAULT_TITLE_COLOR = 0xFFE6E6E6;

    private final Font font;
    private final Component title;
    private final int titleHeight;
    private final UiControl collapseButton = new UiControl();

    private UiControlStyle style = UiControlStyle.DARK;
    private int titleColor = DEFAULT_TITLE_COLOR;
    private UiRect workArea = new UiRect(0, 0, 0, 0);
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    @Nullable
    private Content content;
    private Dock dock = Dock.FREE;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int snapMargin = DEFAULT_SNAP_MARGIN;
    private boolean collapsed;
    private @Nullable UiInteractionSession session;
    private double dragOffsetX;
    private double dragOffsetY;
    private final UiFocusState focusState = new UiFocusState();

    public UiFloatingPanel(Font font, Component title) {
        this.font = Objects.requireNonNull(font);
        this.title = Objects.requireNonNull(title).copy();
        this.titleHeight = font.lineHeight + TITLE_PADDING * 2;
        collapseButton.setStyle(style);
        collapseButton.configure(font, Component.empty(), titleColor, collapseIcon(), List.of(),
                () -> setCollapsed(!collapsed));
    }

    public void setContent(@Nullable Content content) {
        this.content = content;
        layoutContent();
    }

    /**
     * 结构色注入；默认暗色主题。
     */
    public void setStyle(UiControlStyle style) {
        this.style = Objects.requireNonNull(style);
        collapseButton.setStyle(style);
    }

    /**
     * 标题与折叠字形颜色：{@code UiControlStyle} 只承载结构色，文字色由调用方给出。
     */
    public void setTitleColor(int color) {
        this.titleColor = color;
    }

    public void setDock(Dock dock) {
        this.dock = Objects.requireNonNull(dock);
        rebuild();
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        rebuild();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setSnapMargin(int pixels) {
        this.snapMargin = Math.max(0, pixels);
    }

    /**
     * 拖动夹取、贴边吸附与停靠共用的可用区域；宽或高为 0 表示不限制。
     */
    public void setWorkArea(UiRect area) {
        this.workArea = Objects.requireNonNull(area);
        rebuild();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.panelX = x;
        this.panelY = y;
        this.panelWidth = Math.max(0, width);
        this.panelHeight = Math.max(0, height);
        rebuild();
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 绘制调板：背景、标题栏、描边、标题、折叠按钮，最后绘制内容。
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), style.background());
        UiRect bar = titleBar();
        graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), style.hoverBackground());
        outline(graphics, bounds, style.scrollbarThumb());
        String label = TextScroll.trimToWidth(font, title.getString(), Math.max(0, bar.width() - titleInset()));
        graphics.drawString(font, label, bar.x() + TITLE_INSET, bar.y() + TITLE_PADDING, titleColor, false);
        collapseButton.render(graphics, font, mouseX, mouseY);
        Content target = content;
        if (target != null && !collapsed && target.isVisible() && contentRect().height() > 0) {
            target.render(graphics, font, mouseX, mouseY);
        }
    }

    // ---------- UiInputTarget ----------

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.button() != 0) return false;
        double x = context.pointerX();
        double y = context.pointerY();
        if (collapseButton.isHittable() && collapseButton.bounds().contains(x, y)) {
            return collapseButton.mousePressed(context);
        }
        if (titleBar().contains(x, y)) {
            context.scope().pointerHit(null);
            session = context.scope().interaction();
            session.begin(this, UiInteractionSession.Source.POINTER, context.button());
            dragOffsetX = x - bounds.x();
            dragOffsetY = y - bounds.y();
            // 从停靠状态拖动即转为自由摆放；当前坐标已经是停靠后的位置。
            dock = Dock.FREE;
            return true;
        }
        Content target = content;
        if (target == null || collapsed || !target.isVisible()) return false;
        return target.mousePressed(context);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (session != null && session.owns(this)) {
            panelX = (int) Math.round(context.pointerX() - dragOffsetX);
            panelY = (int) Math.round(context.pointerY() - dragOffsetY);
            rebuild();
            return true;
        }
        Content target = content;
        if (target == null || collapsed || !target.isVisible()) return false;
        return target.mouseDragged(context, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (session != null && session.owns(this)) {
            snapToEdges();
            return true;
        }
        if (collapseButton.isPressed()) {
            collapseButton.setPressed(false);
            if (collapseButton.bounds().contains(context.pointerX(), context.pointerY())) {
                collapseButton.activate();
            }
            return true;
        }
        Content target = content;
        if (target == null || collapsed || !target.isVisible()) return false;
        return target.mouseReleased(context);
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        Content target = content;
        if (target == null || collapsed || !target.isVisible()) return false;
        if (!bounds.contains(context.pointerX(), context.pointerY())) return false;
        return target.mouseScrolled(context, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Content target = content;
        if (target == null || collapsed) return false;
        return isFocused() && target.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        Content target = content;
        if (target == null || collapsed) return false;
        return isFocused() && target.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Content target = content;
        if (target == null || collapsed) return false;
        return isFocused() && target.charTyped(codePoint, modifiers);
    }

    // ---------- UiFocusTarget ----------

    @Override
    public UiFocusState focusState() {
        return content instanceof UiFocusTarget target ? target.focusState() : focusState;
    }

    @Override
    public boolean canFocus() {
        Content target = content;
        return !collapsed && target instanceof UiFocusTarget focusTarget && focusTarget.canFocus();
    }

    @Override
    public void focusChanged(boolean focused) {
        Content target = content;
        if (target instanceof UiFocusTarget focusTarget) focusTarget.focusChanged(focused);
    }

    @Override
    public boolean isFocused() {
        return focusState().isFocused();
    }

    @Override
    public boolean activate() {
        Content target = content;
        return isFocused() && target instanceof UiFocusTarget focusTarget && focusTarget.activate();
    }

    @Override
    public @Nullable Component accessibleName() {
        return title;
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
    }

    // ---------- 内部 ----------

    // 统一重算位置与可视高度：折叠只影响高度，停靠只影响位置与抽屉宽度。
    private void rebuild() {
        int width = Math.max(0, panelWidth);
        int height = collapsed ? Math.min(titleHeight, panelHeight) : Math.max(0, panelHeight);
        int x = panelX;
        int y = panelY;
        if (hasWorkArea()) {
            switch (dock) {
                case TOP_RIGHT -> {
                    x = workArea.right() - width;
                    y = workArea.y();
                }
                case BOTTOM_SHEET -> {
                    width = workArea.width();
                    x = workArea.x();
                    y = workArea.bottom() - height;
                }
                case FREE -> {
                    x = clampX(x, width);
                    y = clampY(y, height);
                }
            }
        }
        panelX = x;
        panelY = y;
        bounds = new UiRect(x, y, width, height);
        layoutContent();
        layoutCollapseButton();
    }

    // 释放时贴边吸附：靠近哪条边就贴齐哪条边，仍保持自由摆放。
    private void snapToEdges() {
        if (!hasWorkArea() || dock != Dock.FREE) return;
        int width = bounds.width();
        int height = bounds.height();
        if (Math.abs(panelX - workArea.x()) <= snapMargin) panelX = workArea.x();
        else if (Math.abs(workArea.right() - (panelX + width)) <= snapMargin) panelX = workArea.right() - width;
        if (Math.abs(panelY - workArea.y()) <= snapMargin) panelY = workArea.y();
        else if (Math.abs(workArea.bottom() - (panelY + height)) <= snapMargin) panelY = workArea.bottom() - height;
        rebuild();
    }

    private boolean hasWorkArea() {
        return workArea.width() > 0 && workArea.height() > 0;
    }

    private int clampX(int x, int width) {
        return Math.clamp(x, workArea.x(), Math.max(workArea.x(), workArea.right() - width));
    }

    private int clampY(int y, int height) {
        return Math.clamp(y, workArea.y(), Math.max(workArea.y(), workArea.bottom() - height));
    }

    private UiRect titleBar() {
        return new UiRect(bounds.x(), bounds.y(), bounds.width(), Math.min(titleHeight, bounds.height()));
    }

    private UiRect contentRect() {
        int top = bounds.y() + Math.min(titleHeight, bounds.height());
        return new UiRect(bounds.x(), top, bounds.width(), Math.max(0, bounds.bottom() - top));
    }

    private void layoutContent() {
        Content target = content;
        if (target == null) return;
        UiRect inner = contentRect();
        target.setBounds(inner.x(), inner.y(), inner.width(), inner.height());
    }

    private void layoutCollapseButton() {
        int size = Math.min(titleHeight - COLLAPSE_INSET * 2, bounds.width());
        if (size <= 0) {
            collapseButton.setBounds(0, 0, 0, 0);
            return;
        }
        collapseButton.setBounds(bounds.right() - COLLAPSE_INSET - size,
                bounds.y() + (Math.min(titleHeight, bounds.height()) - size) / 2, size, size);
    }

    // 标题可用宽度要避让折叠按钮。
    private int titleInset() {
        int buttonWidth = collapseButton.bounds().width();
        return TITLE_INSET + (buttonWidth > 0 ? buttonWidth + COLLAPSE_INSET : 0);
    }

    private UiIcon collapseIcon() {
        int size = Math.max(3, titleHeight - COLLAPSE_INSET * 2);
        return new UiIcon.Rendered(size, size, this::drawCollapseGlyph);
    }

    // 展开画一横、折叠再多一竖：纯像素字形，不引入文本与语言键。
    private void drawCollapseGlyph(GuiGraphics graphics, UiRect box) {
        int centerY = box.y() + box.height() / 2;
        graphics.fill(box.x(), centerY, box.right(), centerY + 1, titleColor);
        if (collapsed) {
            int centerX = box.x() + box.width() / 2;
            graphics.fill(centerX, box.y(), centerX + 1, box.bottom(), titleColor);
        }
    }

    private static void outline(GuiGraphics graphics, UiRect rect, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, color);
        graphics.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, color);
    }
}
