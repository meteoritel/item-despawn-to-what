package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

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

    // 字体由 render 接收；保留构造参数以兼容现有组件 API。
    // 注入的宿主主题
    private final UiHostTheme theme;

    /**
     * 使用注入主题的实例度量初始化行布局。
     */
    public UiListView(UiHostTheme theme, @SuppressWarnings("unused") Font font, RowRenderer<T> rowRenderer) {
        this.theme = UiHostThemes.require(theme);
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
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        scrollView.setViewport(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2));
        scrollView.setStep(Math.max(1, rowHeight * 2));
        updateContentSize();
    }

    // 根据数据量与视口更新滚动范围
    private void updateContentSize() {
        int contentHeight = items.isEmpty() ? 0 : (items.size() - 1) * rowHeight + rowContentHeight();
        scrollView.setContentHeight(contentHeight);
        int viewportHeight = scrollView.viewport().height();
        visibleRowCount = Math.max(1, viewportHeight / Math.max(1, rowHeight));
        scrollView.setScrollbarVisible(contentHeight > viewportHeight);
    }

    private int rowContentHeight() {
        return chipRows ? Math.max(1, rowHeight - CHIP_ROW_GAP) : rowHeight;
    }

    // 屏幕坐标换算为内容坐标下的行下标；chip 行的行内容比行距矮，行间留白不算命中
    private int indexAt(double mouseX, double mouseY) {
        if (!scrollView.canHoverContent(mouseX, mouseY)) {
            return -1;
        }
        return RowGeometry.fixedRowIndex(RowGeometry.contentY(scrollView, mouseY),
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
                RowGeometry.contentWidth(scrollView), rowContentHeight());
    }

    // 供宿主绘制行级覆盖层；返回与实际视口相交的可见矩形。
    public @Nullable UiRect visibleRowBounds(int index) {
        UiRect row = rowBounds(index);
        if (row == null) return null;
        UiRect viewport = scrollView.viewport();
        int start = Math.max(row.y(), viewport.y());
        int end = Math.min(row.bottom(), viewport.bottom());
        return end > start ? new UiRect(viewport.x(), start, RowGeometry.contentWidth(scrollView), end - start) : null;
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

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (!chipRows) theme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        if (items.isEmpty()) {
            // 空文案用主要文本色：在内凹底 SLOT_FILL 上 textSecondary 的对比度不足
            UiFlatText.drawCentered(graphics, font, emptyMessage, viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), theme.palette().textPrimary());
            return;
        }
        int hoveredIndex = itemIndexAt(mouseX, mouseY);
        int rowWidth = RowGeometry.contentWidth(scrollView);
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
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), theme.palette().controlHover());
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
        scrollView.renderScrollbar(graphics, theme.controlStyle(UiControlRole.SECONDARY));
    }

    // ---- 输入 ----

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (canFocus() && bounds.contains(context.pointerX(), context.pointerY())) context.scope().pointerHit(this);
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollView.mousePressed(context)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
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
    public boolean canFocus() {
        return visible;
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
}
