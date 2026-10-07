package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 目录图标网格面板（宿主侧通用控件，主计划 §8「kit、目录与视觉」）。
 * <p>面板不知道任何目录协议类型：条目、图标与全部本地化文本由宿主提供，
 * 面板只负责可见项绘制、指针/键盘导航、选择状态以及加载/空/错误/分页状态的展示。
 * <p>搜索与翻页只发出意图回调（宿主据此走既有目录请求链路），因此快速切换时由宿主丢弃过时响应。
 * <p>只绘制可见行；条目源通过 {@link #setEntrySource(Supplier)} 注入，宿主返回同一缓存实例时不会重建格子。
 */
public final class UiCatalogGrid implements UiWidget, UiFocusTarget {

    // 交互意图回调（默认实现有意留空：宿主只覆写需要的意图）
    @SuppressWarnings("unused")
    public interface Listener {

        // 搜索意图（回车提交或清空时发出）
        default void onSearch(String filter) {
        }

        // 翻页意图：-1 上一页 / +1 下一页
        default void onPageChange(int delta) {
        }

        // 选择变化
        default void onSelectionChanged(List<String> selection) {
        }

        // 激活条目（Enter 或双击）
        default void onActivate(String id) {
        }
    }

    // 一条目录条目：图标可为空（回落文字槽）
    public record Entry(String id, Component label, @Nullable Component subLabel, @Nullable UiIcon icon) {
    }

    // 宿主提供的本地化文本
    public record Texts(Component searchHint, Component empty, Component loading, Component error,
                        Component prevPage, Component nextPage, Component pageInfo, Component selection) {
    }

    // 计划规定的分页上限
    public static final int PAGE_SIZE = 200;

    // 网格几何
    private static final int CELL_WIDTH = 22;
    private static final int CELL_HEIGHT = 24;
    private static final int STATUS_HEIGHT = 10;
    private static final int HEADER_HEIGHT = UiTheme.ROW_HEIGHT;
    private static final int FOOTER_HEIGHT = UiTheme.ROW_HEIGHT;
    private static final int PAGER_WIDTH = 46;

    private final Font font;
    private final boolean multiSelect;
    private final Texts texts;
    private final Listener listener;
    private final UiTextInput search;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> selection = new ArrayList<>();

    private @Nullable Supplier<@Nullable List<Entry>> entrySource;
    private @Nullable List<Entry> sourceCache;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect searchRect = new UiRect(0, 0, 0, 0);
    private UiRect gridRect = new UiRect(0, 0, 0, 0);
    private UiRect statusRect = new UiRect(0, 0, 0, 0);
    private UiRect prevRect = new UiRect(0, 0, 0, 0);
    private UiRect nextRect = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean loading;
    private boolean explicitLoading;
    private @Nullable Runnable onRetry;
    private java.util.function.Function<String, Component> tooltipSuffix = ignored -> null;
    private boolean searchActive;
    private @Nullable Component error;
    private int page;
    private int pageCount;
    private boolean canPrev = true;
    private boolean canNext = true;
    private int scrollRow;
    private int cursor = -1;

    public UiCatalogGrid(Font font, boolean multiSelect, Texts texts, Listener listener) {
        this.font = Objects.requireNonNull(font, "font");
        this.multiSelect = multiSelect;
        this.texts = Objects.requireNonNull(texts, "texts");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.search = new UiTextInput(font, texts.searchHint());
        this.search.setMaxLength(128);
        this.search.setAccessibleName(texts.searchHint());
        this.search.setOnCommit(value -> {
            searchActive = false;
            search.setFocused(false);
            listener.onSearch(value == null ? "" : value.trim());
        });
        this.search.setOnCancel(() -> {
            searchActive = false;
            search.setFocused(false);
            search.clear();
            listener.onSearch("");
        });
    }

    // 注入条目源：宿主返回同一缓存实例时只在内容变化后重建格子
    public UiCatalogGrid setEntrySource(@Nullable Supplier<@Nullable List<Entry>> source) {
        this.entrySource = source;
        this.sourceCache = null;
        syncSource();
        return this;
    }

    // 直接设置条目（测试或宿主已就绪的缓存）
    public UiCatalogGrid setEntries(List<Entry> next) {
        this.entrySource = null;
        this.sourceCache = next;
        this.entries.clear();
        if (next != null) {
            this.entries.addAll(next);
        }
        clampCursor();
        return this;
    }

    // 条目列表（当前已同步的视图）
    public List<Entry> entries() {
        syncSource();
        return List.copyOf(entries);
    }

    public int size() {
        syncSource();
        return entries.size();
    }

    public List<String> selection() {
        return List.copyOf(selection);
    }

    public boolean isSelected(String id) {
        return id != null && selection.contains(id);
    }

    public UiCatalogGrid setFilter(String filter) {
        search.setValue(filter == null ? "" : filter);
        return this;
    }

    public String filter() {
        return search.value();
    }

    public UiCatalogGrid setError(@Nullable Component error) {
        this.error = error;
        return this;
    }

    public UiCatalogGrid setLoading(boolean loading) { explicitLoading = loading; return this; }
    public UiCatalogGrid setOnRetry(Runnable retry) { onRetry = retry; return this; }
    public UiCatalogGrid setTooltipSuffix(java.util.function.Function<String, Component> suffix) { tooltipSuffix = suffix; return this; }

    // 页码状态：page 从 0 开始
    // 页码状态：page 从 0 开始；pageCount <= 0 表示未知（只显示当前页号）
    public UiCatalogGrid setPage(int page, int pageCount) {
        this.page = Math.max(0, page);
        this.pageCount = Math.max(0, pageCount);
        return this;
    }

    // 分页边界：由宿主按目录缓存的 lastPage / 返回条数判定，边界外不响应翻页
    public UiCatalogGrid setPageLimits(boolean canPrev, boolean canNext) {
        this.canPrev = canPrev;
        this.canNext = canNext;
        return this;
    }

    public UiCatalogGrid setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && searchActive) {
            searchActive = false;
            search.setFocused(false);
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiCatalogGrid setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int w = bounds.width();
        int h = bounds.height();
        this.searchRect = new UiRect(x, y, w, Math.min(HEADER_HEIGHT, h));
        this.statusRect = new UiRect(x, searchRect.bottom(), w, Math.clamp(h - HEADER_HEIGHT, 0, STATUS_HEIGHT));
        int footerY = Math.max(statusRect.bottom(), y + h - FOOTER_HEIGHT);
        int footerHeight = Math.max(0, y + h - footerY);
        int pagerWidth = Math.clamp(w / 2, 0, PAGER_WIDTH);
        this.prevRect = new UiRect(x, footerY, pagerWidth, footerHeight);
        this.nextRect = new UiRect(x + Math.max(0, w - pagerWidth), footerY, pagerWidth, footerHeight);
        this.gridRect = new UiRect(x, statusRect.bottom(), w, Math.max(0, footerY - statusRect.bottom()));
        search.setBounds(searchRect.x() + 1, searchRect.y(), Math.max(1, searchRect.width() - 2), searchRect.height());
        clampScroll();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    private void syncSource() {
        Supplier<@Nullable List<Entry>> source = entrySource;
        if (source == null) {
            return;
        }
        List<Entry> next = source.get();
        this.loading = next == null;
        if (next == sourceCache) {
            return;
        }
        sourceCache = next;
        entries.clear();
        if (next != null) {
            entries.addAll(next);
        }
        clampCursor();
    }

    private int columns() {
        return Math.max(1, gridRect.width() / CELL_WIDTH);
    }

    private int visibleRows() {
        return Math.max(1, gridRect.height() / CELL_HEIGHT);
    }

    private int rowCount() {
        return (entries.size() + columns() - 1) / columns();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, rowCount() - visibleRows());
        scrollRow = Math.clamp(scrollRow, 0, maxScroll);
    }

    private void clampCursor() {
        if (entries.isEmpty()) {
            cursor = -1;
        } else {
            cursor = Math.clamp(Math.max(0, cursor), 0, entries.size() - 1);
        }
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        if (cursor < 0) {
            return;
        }
        int row = cursor / columns();
        int maxScroll = Math.max(0, rowCount() - visibleRows());
        if (row < scrollRow) {
            scrollRow = row;
        } else if (row >= scrollRow + visibleRows()) {
            scrollRow = Math.min(maxScroll, row - visibleRows() + 1);
        }
        clampScroll();
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!gridRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int col = (int) Math.floor((mouseX - gridRect.x()) / CELL_WIDTH);
        int row = scrollRow + (int) Math.floor((mouseY - gridRect.y()) / CELL_HEIGHT);
        if (col < 0 || col >= columns()) {
            return -1;
        }
        int index = row * columns() + col;
        return index >= 0 && index < entries.size() ? index : -1;
    }

    private @Nullable UiRect cellRect(int index) {
        int cols = columns();
        int row = index / cols;
        if (row < scrollRow || row >= scrollRow + visibleRows()) {
            return null;
        }
        int col = index % cols;
        return new UiRect(gridRect.x() + col * CELL_WIDTH, gridRect.y() + (row - scrollRow) * CELL_HEIGHT,
                CELL_WIDTH, CELL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        syncSource();
        search.render(graphics, renderFont, mouseX, mouseY);
        drawStatus(graphics);
        drawGrid(graphics);
        drawPager(graphics, mouseX, mouseY);
        Component tooltip = tooltipAt(mouseX, mouseY);
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private void drawStatus(GuiGraphics graphics) {
        Component text;
        int color = UiPalette.TEXT_SECONDARY;
        if (error != null) {
            text = texts.error().copy().append(" ").append(error);
            color = UiPalette.DANGER;
        } else if (loading || explicitLoading) {
            text = texts.loading();
        } else if (entries.isEmpty()) {
            text = texts.empty();
            color = UiPalette.TEXT_DISABLED;
        } else {
            String pages = (page + 1) + (pageCount > 0 ? "/" + pageCount : "");
            text = Component.empty().append(texts.pageInfo()).append(" " + pages)
                    .append("  " + texts.selection() + " " + selection.size());
        }
        String trimmed = TextScroll.trimToWidth(font, text.getString(), Math.max(1, statusRect.width()));
        int offset = (loading || explicitLoading) && error == null ? 14 : 0;
        if (offset > 0) com.meteorite.itemdespawntowhat.client.ui.kit.UiSpinner.render(graphics, statusRect.x(), statusRect.y(), net.minecraft.Util.getMillis(), color);
        graphics.drawString(font, TextScroll.trimToWidth(font, trimmed, Math.max(1, statusRect.width() - offset)), statusRect.x() + offset, statusRect.y(), color, false);
    }

    private void drawGrid(GuiGraphics graphics) {
        int cols = columns();
        int rows = visibleRows();
        int lastRow = Math.min(rowCount(), scrollRow + rows);
        for (int row = scrollRow; row < lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= entries.size()) {
                    break;
                }
                UiRect cell = cellRect(index);
                if (cell == null) {
                    continue;
                }
                Entry entry = entries.get(index);
                graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), UiPalette.SLOT_FILL);
                UiTheme.drawBevel(graphics, cell, UiPalette.SLOT_BORDER_LIGHT, UiPalette.SLOT_BORDER_DARK);
                if (isSelected(entry.id())) {
                    UiTheme.drawSelection(graphics, cell);
                }
                if (entry.icon() != null) {
                    UiIcon icon = entry.icon();
                    icon.render(graphics, cell.x() + Math.max(0, (CELL_WIDTH - icon.width()) / 2),
                            cell.y() + Math.max(0, (CELL_HEIGHT - icon.height()) / 2));
                } else {
                    String initial = entry.label() == null ? "?" : TextScroll.trimToWidth(font, entry.label().getString(), CELL_WIDTH - 4);
                    graphics.drawString(font, initial, cell.x() + 7, cell.y() + 8, UiPalette.TEXT_PRIMARY, false);
                }
                if (index == cursor && focused) {
                    UiTheme.drawFocusOutline(graphics, cell);
                }
            }
        }
    }

    private void drawPager(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPagerButton(graphics, prevRect, texts.prevPage(), canPrev, mouseX, mouseY);
        drawPagerButton(graphics, nextRect, texts.nextPage(), canNext, mouseX, mouseY);
    }

    private void drawPagerButton(GuiGraphics graphics, UiRect rect, Component label, boolean allowed, int mouseX,
                                 int mouseY) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = enabled && allowed && rect.contains(mouseX, mouseY);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                !allowed ? UiPalette.CONTROL_DISABLED : (hovered ? UiPalette.CONTROL_HOVER : UiPalette.CONTROL_FILL));
        UiTheme.drawBevel(graphics, rect, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK);
        String trimmed = TextScroll.trimToWidth(font, label.getString(), Math.max(1, rect.width() - 4));
        graphics.drawString(font, trimmed, rect.x() + Math.max(0, (rect.width() - font.width(trimmed)) / 2),
                rect.y() + Math.max(0, (rect.height() - 8) / 2),
                allowed ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (error != null && statusRect.contains(mouseX, mouseY) && onRetry != null) { onRetry.run(); return true; }
        if (searchRect.contains(mouseX, mouseY) || search.isFocused()) {
            searchActive = true;
            search.setFocused(true);
            if (search.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (canPrev && prevRect.contains(mouseX, mouseY)) {
            searchActive = false;
            listener.onPageChange(-1);
            return true;
        }
        if (canNext && nextRect.contains(mouseX, mouseY)) {
            searchActive = false;
            listener.onPageChange(1);
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            searchActive = false;
            cursor = index;
            toggle(entries.get(index).id());
            return true;
        }
        return bounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return search.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return search.isFocused() && search.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        scrollRow -= (int) Math.signum(scrollY);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (searchActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchActive = false;
                search.setFocused(false);
                search.clear();
                listener.onSearch("");
                return true;
            }
            return search.keyPressed(keyCode, scanCode, modifiers);
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveCursor(-1);
            case GLFW.GLFW_KEY_RIGHT -> moveCursor(1);
            case GLFW.GLFW_KEY_UP -> moveCursor(-columns());
            case GLFW.GLFW_KEY_DOWN -> moveCursor(columns());
            case GLFW.GLFW_KEY_HOME -> setCursor(0);
            case GLFW.GLFW_KEY_END -> setCursor(entries.size() - 1);
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (!canPrev) {
                    return false;
                }
                listener.onPageChange(-1);
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                if (!canNext) {
                    return false;
                }
                listener.onPageChange(1);
            }
            case GLFW.GLFW_KEY_SPACE -> toggleCursor();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateCursor();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return searchActive && search.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (!searchActive) {
            searchActive = true;
            search.setFocused(true);
            search.clear();
        }
        return search.charTyped(codePoint, modifiers);
    }

    private void moveCursor(int delta) {
        if (entries.isEmpty()) {
            return;
        }
        setCursor(Math.max(0, cursor) + delta);
    }

    private void setCursor(int index) {
        if (entries.isEmpty()) {
            cursor = -1;
            return;
        }
        cursor = Math.clamp(index, 0, entries.size() - 1);
        ensureCursorVisible();
    }

    private void toggleCursor() {
        if (cursor < 0 || cursor >= entries.size()) {
            return;
        }
        toggle(entries.get(cursor).id());
    }

    private void activateCursor() {
        if (cursor < 0 || cursor >= entries.size()) {
            return;
        }
        listener.onActivate(entries.get(cursor).id());
    }

    // 选择切换：单选模式替换旧选择，多选模式切换单项
    private void toggle(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (multiSelect) {
            if (!selection.remove(id)) {
                selection.add(id);
            }
        } else {
            selection.clear();
            selection.add(id);
        }
        listener.onSelectionChanged(selection());
    }

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused && searchActive) {
            searchActive = false;
            search.setFocused(false);
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        if (!enabled || cursor < 0 || cursor >= entries.size()) {
            return false;
        }
        activateCursor();
        return true;
    }

    @Override
    public Component accessibleName() {
        return Component.empty().append(texts.selection()).append(" " + selection.size() + "/" + entries.size());
    }

    // 鼠标所指格子的完整名称（长名称 tooltip 用）
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        Entry entry = entries.get(index);
        Component label = entry.label() == null ? Component.literal(entry.id()) : entry.label();
        Component suffix = tooltipSuffix.apply(entry.id());
        if (suffix != null) label = label.copy().append("\n").append(suffix);
        return entry.subLabel() == null ? label : label.copy().append(" ").append(entry.subLabel());
    }
}
