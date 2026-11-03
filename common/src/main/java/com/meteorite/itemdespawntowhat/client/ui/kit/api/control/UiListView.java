package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiRowMetrics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.Nullable;

/**
 * 可滚动列表控件。
 * <p>提供选中、悬停、键盘导航（上下 / Home / End / PgUp / PgDn）、滚轮与滚动条拖拽；
 * 行内容由调用方通过 {@link RowRenderer} 决定，控件只负责背景、选中标记与命中判定。
 * <p>单击选中、双击激活；键盘 {@code Enter} 同样触发激活。
 */
public final class UiListView<T> implements UiWidget, UiFocusTarget {

    /**
     * 行渲染回调：背景、选中/悬停/焦点标记与命中由控件处理，行内容由调用方绘制；{@code row} 已为左侧选中标记预留空间。
     */
    @FunctionalInterface
    public interface RowRenderer<T> {
        // 渲染一行；row 已为左侧选中标记预留空间
        void renderRow(GuiGraphics graphics, Font font, T item, int index, UiRect row, boolean selected, boolean hovered, boolean focused);
    }

    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 250L;
    private static final int CHIP_ROW_GAP = 4;
    // 视口相对外框的内缩：四周留 1 像素边框，因此可用视口比外框窄高各两像素
    private static final int VIEWPORT_INSET = 2;

    // 行渲染器
    private final RowRenderer<T> rowRenderer;
    // 滚动与裁剪
    private final UiScrollView scrollView = new UiScrollView();
    // 数据源
    private final List<T> items = new ArrayList<>();
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 行高
    private int rowHeight;
    private boolean rowHeightOverride;
    private long themeRevision = -1;
    // 选中行下标，-1 表示无选中
    private int selectedIndex = -1;
    // 一屏可见行数
    private int visibleRowCount = 1;
    // 空列表提示
    private Component emptyMessage = Component.empty();
    // 选中变化回调
    private IntConsumer onSelectionChanged;
    // 激活回调
    private Consumer<T> onActivate;
    // 焦点变化回调
    private Consumer<Boolean> onFocusChanged;
    private boolean activateOnSingleClick;
    private boolean selectionMarkerVisible = true;
    private boolean chipRows;
    private final UiFocusState focusState = new UiFocusState();
    private boolean visible = true;
    // 上次点击时间与下标（双击判定）
    private long lastClickTime;
    private int lastClickIndex = -1;

    // 绘字字体由 render 接收，主题提供结构与行度量。
    private final UiStyleSource theme;

    /** 使用原版主题装配泛型行列表。 */
    public UiListView(RowRenderer<T> rowRenderer) { this(UiMinecraftTheme.INSTANCE, rowRenderer); }

    /** 行模型与绘制由调用方提供，视口、选择与焦点由列表维护。 */
    public UiListView(UiStyleSource theme, RowRenderer<T> rowRenderer) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.rowHeight = this.theme.rowHeight();
        scrollView.setStep(Math.max(1, rowHeight * 2));
        this.rowRenderer = rowRenderer;
    }

    // ---- 数据 ----

    // 替换全部数据
    public UiListView<T> setItems(List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        if (selectedIndex >= items.size()) {
            selectedIndex = items.isEmpty() ? -1 : items.size() - 1;
        }
        updateContentSize();
        return this;
    }

    // 只读数据视图
    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    // 数据条数
    public int size() {
        return items.size();
    }

    // 选中行下标，无选中时为 -1
    public int selectedIndex() {
        return selectedIndex;
    }

    // 选中的数据，无选中时为 null
    public T selectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : null;
    }

    public UiListView<T> setSelectedIndex(int index) {
        int clamped = index < 0 || index >= items.size() ? -1 : index;
        if (clamped == selectedIndex) {
            ensureVisible(clamped);
            return this;
        }
        selectedIndex = clamped;
        ensureVisible(clamped);
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(selectedIndex);
        }
        return this;
    }

    public UiListView<T> setRowHeight(int rowHeight) {
        rowHeightOverride = true;
        this.rowHeight = Math.max(9, rowHeight);
        scrollView.setStep(Math.max(1, this.rowHeight * 2));
        updateContentSize();
        return this;
    }

    public UiListView<T> setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    public UiListView<T> setOnSelectionChanged(IntConsumer callback) {
        this.onSelectionChanged = callback;
        return this;
    }

    // 导航清单与菜单可显式使用单击激活，普通可编辑列表仍保持双击。
    public void setActivateOnSingleClick(boolean enabled) {
        activateOnSingleClick = enabled;
    }

    /**
     * 设置是否绘制并预留左侧选中标记；隐藏时仍保留选中背景和键盘导航。
     *
     * @param visible 是否显示选中标记
     */
    public void setSelectionMarkerVisible(boolean visible) {
        selectionMarkerVisible = visible;
    }

    /**
     * 使用独立的圆角气泡行，列表底色透明，行间留白不参与条目命中。
     *
     * @param enabled 是否启用气泡样式；启用时不绘制左侧选中标记
     */
    public void setChipRows(boolean enabled) {
        chipRows = enabled;
        updateContentSize();
    }

    public UiListView<T> setOnActivate(Consumer<T> callback) {
        this.onActivate = callback;
        return this;
    }

    /**
     * 设置焦点变化回调；宿主据此在列表失焦时收起选中态等。
     *
     * @param callback 接收最新焦点状态的消费者
     */
    public UiListView<T> setOnFocusChanged(Consumer<Boolean> callback) {
        this.onFocusChanged = callback;
        return this;
    }

    public UiListView<T> setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    // 滚动到指定行
    public void ensureVisible(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        scrollView.ensureVisible(new UiRect(0, index * rowHeight, Math.max(0, scrollView.viewport().width()), rowContentHeight()));
    }

    // ---- 布局 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        refreshTheme();
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        scrollView.setViewport(x + VIEWPORT_INSET / 2, y + VIEWPORT_INSET / 2,
                Math.max(0, width - VIEWPORT_INSET), Math.max(0, height - VIEWPORT_INSET));
        scrollView.setStep(Math.max(1, rowHeight * 2));
        updateContentSize();
    }

    // 根据数据量与视口更新滚动范围
    private void refreshTheme() {
        if (themeRevision == theme.revision()) return;
        if (!rowHeightOverride) rowHeight = theme.rowHeight();
        updateContentSize();
        themeRevision = theme.revision();
    }

    private void updateContentSize() {
        int height = contentHeight();
        scrollView.setContentHeight(height);
        int viewportHeight = scrollView.viewport().height();
        visibleRowCount = Math.max(1, viewportHeight / Math.max(1, rowHeight));
        scrollView.setScrollbarVisible(height > viewportHeight);
    }

    /**
     * 全部条目所需的滚动内容高度：首行到末行内容底边，行间留白已计入。
     * <p>宿主需要按实际条目数收紧列表高度时读它，滚动范围也由同一口径派生，两者不会出现一像素级偏差。
     *
     * @return 内容高度；空列表返回 0
     */
    public int contentHeight() {
        return items.isEmpty() ? 0 : (items.size() - 1) * rowHeight + rowContentHeight();
    }

    /**
     * 视口相对列表外框的内缩边长：宿主按内容高收紧列表外框时必须加上它，
     * 否则可用视口比内容矮两像素，最后一行被裁掉并触发滚动条。
     *
     * @return 宽高各自的内缩总量
     */
    public int viewportInset() {
        return VIEWPORT_INSET;
    }

    /**
     * 单行内容高：气泡行比行距矮一条行间留白，因此实际行矩形小于步进距离。
     *
     * @return 行内容高度，即渲染与命中使用的行矩形高
     */
    public int rowContentHeight() {
        return chipRows ? Math.max(1, rowHeight - CHIP_ROW_GAP) : rowHeight;
    }

    // 屏幕坐标换算为内容坐标下的行下标；chip 行的行内容比行距矮，行间留白不算命中
    private int indexAt(double mouseX, double mouseY) {
        var hit = scrollView.hitContentY(mouseX, mouseY);
        if (hit.isEmpty()) {
            return -1;
        }
        return UiRowMetrics.fixedRowIndex(hit.getAsDouble(),
                rowHeight, rowContentHeight(), items.size());
    }

    // 命中数据行时排除滚动条，空白区域返回无条目。
    public int itemIndexAt(double mouseX, double mouseY) {
        return indexAt(mouseX, mouseY);
    }

    public boolean isDraggingScrollbar() {
        return scrollView.isDragging();
    }

    /**
     * 返回数据行的完整屏幕矩形，供宿主统一行内操作的绘制与命中坐标。
     *
     * @param index 数据行下标
     * @return 未裁剪的行矩形，下标无效时返回 null
     */
    public @Nullable UiRect rowBounds(int index) {
        if (index < 0 || index >= items.size()) return null;
        UiRect viewport = scrollView.viewport();
        return new UiRect(viewport.x(), viewport.y() + index * rowHeight - scrollView.offset(),
                scrollView.contentWidth(), rowContentHeight());
    }

    // 供宿主绘制行级覆盖层；返回与实际视口相交的可见矩形。
    public @Nullable UiRect visibleRowBounds(int index) {
        UiRect row = rowBounds(index);
        if (row == null) return null;
        UiRect viewport = scrollView.viewport();
        int start = Math.max(row.y(), viewport.y());
        int end = Math.min(row.bottom(), viewport.bottom());
        return end > start ? new UiRect(viewport.x(), start, scrollView.contentWidth(), end - start) : null;
    }

    // 拖动排序使用视口偏移；指针越出列表时钳制到首尾条目。
    public int dragIndexAt(double mouseY) {
        if (items.isEmpty()) {
            return -1;
        }
        int contentY = (int) Math.floor(mouseY - scrollView.viewport().y()) + scrollView.offset();
        return Math.clamp(Math.floorDiv(contentY, Math.max(1, rowHeight)), 0, items.size() - 1);
    }

    // ---- 渲染 ----

    @Override
    public boolean isVisible() {
        return visible;
    }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        refreshTheme();
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        if (!visible) {
            return;
        }
        if (!chipRows) theme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        if (items.isEmpty()) {
            // 空文案用主要文本色：在内凹底 SLOT_FILL 上 textSecondary 的对比度不足
            UiFlatText.drawCentered(graphics, font, emptyMessage, viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), theme.surfaces().text());
            return;
        }
        int hoveredIndex = pointer.allowed() && pointer.hit() != null ? pointer.hit().rowIndex(this) : -1;
        int rowWidth = scrollView.contentWidth();
        scrollView.push(graphics);
        for (int i = 0; i < items.size(); i++) {
            int rowY = i * rowHeight;
            if (rowY + rowHeight < scrollView.offset() || rowY > scrollView.offset() + viewport.height()) {
                continue;
            }
            UiRect row = new UiRect(0, rowY, rowWidth, rowContentHeight());
            if (chipRows) {
                theme.drawChip(graphics, row, i == selectedIndex, i == hoveredIndex);
            } else if (i == selectedIndex) {
                theme.drawSelection(graphics, row);
                if (selectionMarkerVisible) theme.drawSelectMarker(graphics, row);
            } else if (i == hoveredIndex) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), theme.controlStyle(UiControlRole.SECONDARY).hoverBackground());
            }
            int markerWidth = !chipRows && selectionMarkerVisible ? theme.selectMarkerWidth() : 0;
            UiRect textRow = new UiRect(row.x() + markerWidth, row.y(),
                    Math.max(0, row.width() - markerWidth), row.height());
            rowRenderer.renderRow(graphics, font, items.get(i), i, textRow, i == selectedIndex, i == hoveredIndex, isFocused());
        }
        scrollView.pop(graphics);
        if (isFocusVisible()) {
            UiRect focusBounds = chipRows ? visibleRowBounds(selectedIndex) : bounds;
            if (focusBounds != null) theme.drawFocusOutline(graphics, focusBounds);
        }
        scrollView.renderScrollbar(graphics, theme);
    }

    // ---- 输入 ----

    @Override
    public boolean mousePressed(UiInputContext context) {

        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollView.mousePressed(context)) {
            return true;
        }
        int index = context.hit() == null ? indexAt(mouseX, mouseY) : context.hit().rowIndex(this);
        if (index < 0) {
            return !chipRows;
        }
        long now = Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickIndex = index;
        lastClickTime = now;
        setSelectedIndex(index);
        if ((activateOnSingleClick || doubleClick) && onActivate != null) {
            onActivate.accept(items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        double mouseY = context.pointerY();
        if (scrollView.isDragging()) {
            scrollView.mouseDragged(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        scrollView.endInteraction(UiInteractionSession.EndReason.CANCEL);
        return false;
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        return scrollView.scrollBy(scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || items.isEmpty()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            moveSelection(-visibleRowCount);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            moveSelection(visibleRowCount);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setSelectedIndex(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            setSelectedIndex(items.size() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return activate();
        }
        return false;
    }

    // 相对移动选中行
    private void moveSelection(int delta) {
        int base = selectedIndex < 0 ? (delta > 0 ? -1 : items.size()) : selectedIndex;
        setSelectedIndex(Mth.clamp(base + delta, 0, items.size() - 1));
    }

    // ---- 焦点 ----

    @Override
    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public void focusChanged(boolean focused) {
        if (onFocusChanged != null) {
            onFocusChanged.accept(focused);
        }
    }

    @Override
    public boolean isFocused() {
        return focusState.isFocused();
    }

    @Override
    public boolean activate() {
        T item = selectedItem();
        if (!visible || item == null || onActivate == null) {
            return false;
        }
        onActivate.accept(item);
        return true;
    }

    @Override
    public Component accessibleName() {
        return emptyMessage;
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!isVisible() || !bounds().contains(x, y)) return null;
        UiPointerHit hit = scrollView.hitTest(x, y);
        if (hit != null) return hit.within(this);
        int index = indexAt(x, y);
        UiPointerHit leaf = UiPointerHit.leaf(this, x, y);
        return index < 0 ? leaf : leaf.withRow(this, index, items.get(index), rowBounds(index));
    }

}
