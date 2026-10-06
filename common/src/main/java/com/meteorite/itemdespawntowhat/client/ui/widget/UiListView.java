package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.Nullable;

/***
 * 可滚动列表控件。
 * <p>提供选中、悬停、键盘导航（上下 / Home / End / PgUp / PgDn）、滚轮与滚动条拖拽；
 * 行内容由调用方通过 {@link RowRenderer} 决定，控件只负责背景、选中标记与命中判定。
 * <p>单击选中、双击激活；键盘 {@code Enter} 同样触发激活。
 */
public final class UiListView<T> implements UiWidget, UiFocusTarget {

    // 行渲染回调
    @FunctionalInterface
    public interface RowRenderer<T> {
        // 渲染一行；row 已为左侧选中标记预留空间
        void renderRow(GuiGraphics graphics, Font font, T item, int index, UiRect row, boolean selected, boolean hovered, boolean focused);
    }

    // 默认行高
    public static final int DEFAULT_ROW_HEIGHT = UiTheme.ROW_HEIGHT;
    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 250L;
    // 滚动条预留宽度（与 kit 的滚动条宽度保持一致）
    private static final int SCROLLBAR_RESERVE = 4;

    // 行渲染器
    private final RowRenderer<T> rowRenderer;
    // 滚动与裁剪
    private final UiScrollView scrollView = new UiScrollView();
    // 数据源
    private final List<T> items = new ArrayList<>();
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 行高
    private int rowHeight = DEFAULT_ROW_HEIGHT;
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
    // 是否持有键盘焦点
    private boolean focused;
    // 是否可见
    private boolean visible = true;
    // 上次点击时间与下标（双击判定）
    private long lastClickTime;
    private int lastClickIndex = -1;

    // 字体由 render 接收；保留构造参数以兼容现有组件 API。
    public UiListView(@SuppressWarnings("unused") Font font, RowRenderer<T> rowRenderer) {
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

    // 设置选中行
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

    // 设置行高
    public UiListView<T> setRowHeight(int rowHeight) {
        this.rowHeight = Math.max(9, rowHeight);
        scrollView.setStep(Math.max(1, this.rowHeight * 2));
        updateContentSize();
        return this;
    }

    // 设置空列表提示
    public UiListView<T> setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    // 设置选中变化回调
    public UiListView<T> setOnSelectionChanged(IntConsumer callback) {
        this.onSelectionChanged = callback;
        return this;
    }

    // 设置激活回调
    public UiListView<T> setOnActivate(Consumer<T> callback) {
        this.onActivate = callback;
        return this;
    }

    // 设置是否可见
    public UiListView<T> setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    // 滚动到指定行
    public void ensureVisible(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        scrollView.ensureVisible(new UiRect(0, index * rowHeight, Math.max(0, scrollView.viewport().width()), rowHeight));
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
        scrollView.setContentHeight(items.size() * rowHeight);
        int viewportHeight = scrollView.viewport().height();
        visibleRowCount = Math.max(1, viewportHeight / Math.max(1, rowHeight));
        scrollView.setScrollbarVisible(items.size() * rowHeight > viewportHeight);
    }

    // 内容区可用宽度（扣除滚动条）
    private int contentWidth() {
        int width = scrollView.viewport().width();
        if (scrollView.isScrollbarVisible()) {
            width -= SCROLLBAR_RESERVE;
        }
        return Math.max(0, width);
    }

    // 屏幕坐标换算为内容坐标下的行下标
    private int indexAt(double mouseX, double mouseY) {
        UiRect viewport = scrollView.viewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return -1;
        }
        int contentY = (int) (mouseY - viewport.y()) + scrollView.offset();
        int index = contentY / Math.max(1, rowHeight);
        return index >= 0 && index < items.size() ? index : -1;
    }

    // 命中数据行时排除滚动条，空白区域返回无条目。
    public int itemIndexAt(double mouseX, double mouseY) {
        return scrollView.hitScrollbar(mouseX, mouseY) ? -1 : indexAt(mouseX, mouseY);
    }

    // 供宿主绘制行级覆盖层；返回与实际视口相交的可见矩形。
    public @Nullable UiRect visibleRowBounds(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        UiRect viewport = scrollView.viewport();
        int top = viewport.y() + index * rowHeight - scrollView.offset();
        int start = Math.max(top, viewport.y());
        int end = Math.min(top + rowHeight, viewport.bottom());
        return end > start ? new UiRect(viewport.x(), start, contentWidth(), end - start) : null;
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
        UiTheme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        if (items.isEmpty()) {
            graphics.drawCenteredString(font, emptyMessage, viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), UiPalette.TEXT_SECONDARY);
            return;
        }
        int hoveredIndex = indexAt(mouseX, mouseY);
        int rowWidth = contentWidth();
        scrollView.push(graphics);
        for (int i = 0; i < items.size(); i++) {
            int rowY = i * rowHeight;
            if (rowY + rowHeight < scrollView.offset() || rowY > scrollView.offset() + viewport.height()) {
                continue;
            }
            UiRect row = new UiRect(0, rowY, rowWidth, rowHeight);
            if (i == selectedIndex) {
                UiTheme.drawSelection(graphics, row);
                UiTheme.drawSelectMarker(graphics, row);
            } else if (i == hoveredIndex) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), UiPalette.CONTROL_HOVER);
            }
            UiRect textRow = new UiRect(row.x() + UiTheme.SELECT_MARKER_WIDTH, row.y(),
                    Math.max(0, row.width() - UiTheme.SELECT_MARKER_WIDTH), row.height());
            rowRenderer.renderRow(graphics, font, items.get(i), i, textRow, i == selectedIndex, i == hoveredIndex, focused);
        }
        scrollView.pop(graphics);
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
        scrollView.renderScrollbar(graphics, UiTheme.secondaryStyle());
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollView.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return true;
        }
        long now = Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickIndex = index;
        lastClickTime = now;
        setSelectedIndex(index);
        if (doubleClick && onActivate != null) {
            onActivate.accept(items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollView.isDragging()) {
            scrollView.mouseDragged(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollView.mouseReleased();
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
        setSelectedIndex(base + delta);
    }

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
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
