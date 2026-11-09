package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiOverlay;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiRowMetrics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_Y;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphSidePanel.SIDE_PANEL_HEADER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphSidePanel.SIDE_PANEL_PADDING;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 条件与行为共用的右侧类型选择面板：统一搜索、键盘选择、滚动与滑入外观。
 */
public final class UiTypePicker implements UiWidget, UiLayoutSource {
    /**
     * 一条可选择的类型。
     *
     * @param id    类型注册名，用于搜索与确认回调
     * @param label 玩家可读的本地化名称
     */
    public record Option(ResourceLocation id, Component label) {
    }

    private static final int PICKER_SEARCH_ROW = 14;
    private static final int PICKER_ROW_HEIGHT = 18;
    private static final int PICKER_FILTER_MAX = 32;
    private static final int SEARCH_PADDING = 2;
    private static final int CARET_WIDTH = 1;
    // 类型选择框状态
    private boolean open;
    private Runnable onOpenChanged = () -> {};

    void setOnOpenChanged(Runnable callback) {
        onOpenChanged = callback;
    }
    private int cursor;
    private final UiScrollView scroll = new UiScrollView();
    private final StringBuilder filterText = new StringBuilder();
    private UiRect panelRect = new UiRect(0, 0, 0, 0);
    private UiRect searchRect = new UiRect(0, 0, 0, 0);
    private UiRect listRect = new UiRect(0, 0, 0, 0);
    private List<Option> filteredOptions = List.of();
    private final ConditionGraphSidePanel panel;
    private boolean pointerKnown;
    private int lastMouseX;
    private int lastMouseY;

    private final UiHostTheme theme;
    private final Font font;
    private final Component title;
    private final Consumer<ResourceLocation> onConfirm;
    private List<Option> typeOptions = List.of();
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    /**
     * 创建共用类型选择面板。
     *
     * @param theme     面板配色与控件主题
     * @param font      文本测量与绘制字体
     * @param title     面板标题
     * @param onConfirm 用户确认类型后的回调
     */
    public UiTypePicker(UiHostTheme theme, Font font, Component title, Consumer<ResourceLocation> onConfirm) {
        this.title = title;
        this.theme = theme;
        this.font = font;
        this.onConfirm = onConfirm;
        panel = new ConditionGraphSidePanel(theme, font);
    }

    /**
     * @return 选择面板是否正在接收输入
     */
    public boolean isOpen() {
        return open;
    }

    private final UiOverlay overlay = new UiOverlay(this);
    private final UiInputScope inputScope = overlay.inputScope();
    private final UiLayer layer = new UiLayer(inputScope, this::renderLayer, this);

    /** 选择器的一份稳定呈现描述。 */
    public UiLayer layer() { return layer; }

    /** 每个可见绘制帧推进一次动画，事件重排只读取当前进度。 */
    public void advanceAnimation() {
        if (open) panel.tick();
    }

    /**
     * 类型选择面板的稳定输入作用域。
     */
    public UiInputScope inputScope() {
        return inputScope;
    }

    void setOptions(List<Option> options) {
        this.typeOptions = options;
    }

    void setBounds(UiRect bounds) {
        setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    void cancelCapture() {
        scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
    }

    /**
     * 在宿主可视区域右缘打开选择面板，并清空上次搜索与选择。
     *
     * @param options 可选择的类型列表
     * @param bounds  宿主可视区域
     */
    public void open(List<Option> options, UiRect bounds) {
        this.typeOptions = options;
        this.bounds = bounds;
        open = true;
        cursor = 0;
        scroll.setOffset(0);
        filterText.setLength(0);
        panel.reset();
        panel.start(1.0F);
        pointerKnown = false;
        refreshFilter();
        updateBounds();
        onOpenChanged.run();
    }

    /**
     * 按宿主边界绘制滑入面板。
     *
     * @param graphics   绘制上下文
     * @param renderFont 内容字体
     */
    @Override
    public void render(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer) {
        renderLayer(graphics, renderFont, incomingPointer, 0);
    }

    private void renderLayer(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer, int depth) {
        if (!open) return;
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        if (pointer.allowed()) updateHover(pointer);
        overlay.render(graphics, new UiRect(0, 0, graphics.guiWidth(), graphics.guiHeight()), depth, 0,
                () -> panel.render(graphics, bounds, title, () -> renderContents(graphics, renderFont)));
    }

    /**
     * 捕获面板打开时的点击；单击条目确认，点击面板外关闭。
     *
     * @return 面板是否消费本次点击
     */
    @Override
    public boolean mousePressed(UiInputContext context) {
        int button = context.button();
        if (!open) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) handleClick(context);
        return true;
    }

    void mouseDragged(double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) scroll.mouseDragged(mouseY);
    }

    void mouseReleased(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
    }

    void mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (listRect.contains(mouseX, mouseY)) scroll.scrollBy(scrollY);
    }

    boolean charTyped(char codePoint) {
        if (codePoint < ' ') return false;
        if (filterText.length() < PICKER_FILTER_MAX) filterText.append(codePoint);
        refreshFilter();
        return true;
    }

    private void confirm() {
        if (cursor < 0 || cursor >= filteredOptions.size()) {
            close();
            return;
        }
        ResourceLocation type = filteredOptions.get(cursor).id();
        close();
        onConfirm.accept(type);
    }

    /**
     * 关闭面板并释放滚动条捕获；不提交类型。
     */
    public void close() {
        boolean wasOpen = open;
        open = false;
        filterText.setLength(0);
        filteredOptions = List.of();
        cursor = 0;
        scroll.setOffset(0);
        scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
        panel.reset();
        pointerKnown = false;
        if (wasOpen) onOpenChanged.run();
    }

    /**
     * 导出选择面板的只读排版快照：标题一行、搜索行、以及每个真正画得出来的候选项一行。
     *
     * <p>选项行只报落在列表视口内的那些：面板是滚动视口，视口外的行既不可见也点不到，
     * 把它们的矩形报出去会让探针照着一个屏幕外的坐标点击。行矩形按「列表左上 + 行序号 × 行高 −
     * 当前偏移」换算成 GUI 坐标，与 {@code indexAt} 的命中口径同源；探针点行中心即命中同一项。
     * 面板关闭或几何尚未就绪时返回空列表。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!open || panelRect.width() <= 0 || panelRect.height() <= 0) {
            return List.of();
        }
        List<UiLayoutNode> nodes = new ArrayList<>();
        nodes.add(row(new UiRect(panelRect.x() + SIDE_PANEL_PADDING, panelRect.y(),
                panelRect.width() - SIDE_PANEL_PADDING * 2, SIDE_PANEL_HEADER), title));
        String filter = filterText.toString();
        Component search = filter.isEmpty()
                ? Component.translatable(TypeLabels.UI_PREFIX + "picker.filter") : Component.literal(filter);
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.TEXT_INPUT, searchRect, search, font.width(search),
                Math.max(0, searchRect.width() - SEARCH_PADDING * 2), true, true, List.of()));
        if (filteredOptions.isEmpty()) {
            nodes.add(row(new UiRect(listRect.x(), listRect.y(), listRect.width(),
                            Math.min(PICKER_ROW_HEIGHT, listRect.height())),
                    Component.translatable(TypeLabels.UI_PREFIX + "picker.empty")));
            return List.of(new UiLayoutNode(UiLayoutNode.Kind.MODAL, panelRect, title, font.width(title), panelRect.width(), true, true, List.copyOf(nodes)));
        }
        int rowWidth = rowContentWidth();
        int first = scroll.offset() / PICKER_ROW_HEIGHT;
        int end = Math.min(filteredOptions.size(),
                (scroll.offset() + listRect.height() + PICKER_ROW_HEIGHT - 1) / PICKER_ROW_HEIGHT);
        for (int optionIndex = first; optionIndex < end; optionIndex++) {
            int rowY = listRect.y() + optionIndex * PICKER_ROW_HEIGHT - scroll.offset();
            // 最后一行可能只露出一半：矩形按剩余高度截断，中心点仍落在视口内
            int height = Math.min(PICKER_ROW_HEIGHT, listRect.bottom() - rowY);
            if (height <= 0) continue;
            Component label = filteredOptions.get(optionIndex).label();
            nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL,
                    new UiRect(listRect.x(), rowY, Math.max(0, rowWidth), height),
                    label, font.width(label), Math.max(0, rowWidth - CARD_PADDING_X * 2), true, true, List.of()));
        }
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.MODAL, panelRect, title, font.width(title), panelRect.width(), true, true, List.copyOf(nodes)));
    }

    // 只读文本行：带宽取矩形宽度，不参与命中也不改变面板状态
    private UiLayoutNode row(UiRect rect, Component text) {
        return new UiLayoutNode(UiLayoutNode.Kind.ROW, rect, text, font.width(text),
                rect.width(), true, true, List.of());
    }

    private void refreshFilter() {
        String needle = filterText.toString().trim().toLowerCase(Locale.ROOT);
        filteredOptions = needle.isEmpty() ? List.copyOf(typeOptions) : typeOptions.stream()
                .filter(option -> option.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || option.id().toString().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        cursor = Math.clamp(cursor, 0, Math.max(0, filteredOptions.size() - 1));
        scroll.setOffset(0);
        scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
        updateBounds();
        if (open) revealCursor();
    }

    private void updateBounds() {
        panelRect = panel.bounds(bounds);
        int width = panelRect.width();
        int listHeight = bounds.height() - SIDE_PANEL_HEADER - PICKER_SEARCH_ROW
                - SIDE_PANEL_PADDING * 2;
        if (width <= SIDE_PANEL_PADDING * 2 || listHeight < PICKER_ROW_HEIGHT) {
            close();
            panelRect = new UiRect(0, 0, 0, 0);
            return;
        }
        int x = panelRect.x();
        int y = panelRect.y();
        searchRect = new UiRect(x + SIDE_PANEL_PADDING, y + SIDE_PANEL_HEADER + SIDE_PANEL_PADDING,
                width - SIDE_PANEL_PADDING * 2, PICKER_SEARCH_ROW - BORDER);
        listRect = new UiRect(x + SIDE_PANEL_PADDING, searchRect.bottom() + BORDER,
                width - SIDE_PANEL_PADDING * 2, listHeight);
        scroll.setViewport(listRect.x(), listRect.y(), listRect.width(), listHeight);
        scroll.setContentHeight(filteredOptions.size() * PICKER_ROW_HEIGHT);
        scroll.setStep(PICKER_ROW_HEIGHT);
        scroll.setScrollbarVisible(scroll.maxOffset() > 0);
    }

    private void revealCursor() {
        scroll.ensureVisible(new UiRect(0, cursor * PICKER_ROW_HEIGHT,
                listRect.width(), PICKER_ROW_HEIGHT));
    }

    private void updateHover(UiPointer pointer) {
        int mouseX = (int) pointer.x();
        int mouseY = (int) pointer.y();
        // 静止指针不覆盖键盘选择；内容滚动也不改变当前高亮。
        boolean moved = !pointerKnown || mouseX != lastMouseX || mouseY != lastMouseY;
        pointerKnown = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (moved) {
            int index = pointer.hit() == null ? -1 : pointer.hit().rowIndex(this);
            if (index >= 0) cursor = index;
        }
    }

    /** 面板使用当前动画几何解析，轨道保留自己的捕获身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!open || !panelRect.contains(x, y)) return null;
        UiPointerHit hit = scroll.hitTest(x, y);
        if (hit != null) return hit.within(this);
        hit = UiPointerHit.leaf(this, x, y);
        int index = indexAt(x, y);
        if (index >= 0) {
            UiRect row = new UiRect(listRect.x(), scroll.toScreenY(index * PICKER_ROW_HEIGHT),
                    rowContentWidth(), PICKER_ROW_HEIGHT);
            hit = hit.withRow(this, index, filteredOptions.get(index), row);
        }
        return hit;
    }

    private void renderContents(GuiGraphics graphics, Font renderFont) {
        if (panelRect.width() <= 0 || panelRect.height() <= 0) {
            return;
        }
        renderSearch(graphics, renderFont);
        renderList(graphics, renderFont);
    }

    private void renderSearch(GuiGraphics graphics, Font renderFont) {
        graphics.fill(searchRect.x(), searchRect.y(), searchRect.right(),
                searchRect.bottom(), theme.palette().canvas().canvasPickerInputFill());
        drawBorder(graphics, searchRect, theme.palette().canvas().canvasPickerInputBorder());
        String filter = filterText.toString();
        String searchText = filter.isEmpty()
                ? Component.translatable(TypeLabels.UI_PREFIX + "picker.filter").getString() : filter;
        int searchColor = filter.isEmpty() ? theme.palette().canvas().canvasPickerTextDisabled() : theme.palette().canvas().canvasTextOnAccent();
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, searchText, searchRect.width() - SEARCH_PADDING * 2 - CARET_WIDTH * 2),
                searchRect.x() + SEARCH_PADDING, searchRect.y() + SEARCH_PADDING, searchColor, false);
        int caretX = searchRect.x() + SEARCH_PADDING + Math.min(renderFont.width(filter), searchRect.width() - SEARCH_PADDING * 2);
        graphics.fill(caretX, searchRect.y() + SEARCH_PADDING, caretX + CARET_WIDTH, searchRect.bottom() - SEARCH_PADDING,
                theme.palette().canvas().canvasTextOnAccent());

    }

    private int rowContentWidth() {
        return Math.max(0, scroll.contentWidth() - (scroll.isScrollbarVisible() ? BORDER : 0));
    }

    private void renderList(GuiGraphics graphics, Font renderFont) {
        int rowWidth = rowContentWidth();
        scroll.push(graphics);
        try {
            if (filteredOptions.isEmpty()) {
                graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                                Component.translatable(TypeLabels.UI_PREFIX + "picker.empty").getString(),
                                rowWidth - CARD_PADDING_X),
                        CARD_PADDING_X, CARD_PADDING_Y, theme.palette().canvas().canvasPickerTextMuted(), false);
            } else {
                int first = scroll.offset() / PICKER_ROW_HEIGHT;
                int end = Math.min(filteredOptions.size(),
                        (scroll.offset() + listRect.height() + PICKER_ROW_HEIGHT - 1) / PICKER_ROW_HEIGHT);
                for (int optionIndex = first; optionIndex < end; optionIndex++) {
                    int rowY = optionIndex * PICKER_ROW_HEIGHT;
                    boolean current = optionIndex == cursor;
                    if (current) {
                        graphics.fill(0, rowY, rowWidth, rowY + PICKER_ROW_HEIGHT,
                                theme.palette().canvas().canvasPickerRowSelected());
                    }
                    graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                                    filteredOptions.get(optionIndex).label().getString(), rowWidth - CARD_PADDING_X * 2),
                            CARD_PADDING_X, rowY + (PICKER_ROW_HEIGHT - renderFont.lineHeight) / 2,
                            current ? theme.palette().canvas().canvasPickerText() : theme.palette().canvas().canvasPickerTextMuted(), false);
                }
            }
        } finally {
            scroll.pop(graphics);
        }
        scroll.renderScrollbar(graphics, theme);
    }

    private void handleClick(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (bounds.contains(mouseX, mouseY)
                && scroll.mousePressed(context)) return;
        int index = context.hit() == null ? indexAt(mouseX, mouseY) : context.hit().rowIndex(this);
        if (index >= 0) {
            cursor = index;
            confirm();
            return;
        }
        if (!panelRect.contains(mouseX, mouseY)) {
            close();
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!open || !bounds.contains(mouseX, mouseY)
                || filteredOptions.isEmpty()) {
            return -1;
        }
        var hit = scroll.hitContentY(mouseX, mouseY);
        if (hit.isEmpty()) return -1;
        if (mouseX >= listRect.x() + rowContentWidth()) return -1;
        return UiRowMetrics.fixedRowIndex(hit.getAsDouble(),
                PICKER_ROW_HEIGHT, PICKER_ROW_HEIGHT, filteredOptions.size());
    }

    void keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!filterText.isEmpty()) {
                filterText.setLength(filterText.length() - 1);
                refreshFilter();
            }
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            cursor = Math.max(0, cursor - 1);
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            cursor = Math.clamp(cursor + 1, 0, Math.max(0, filteredOptions.size() - 1));
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            cursor = Math.max(0, filteredOptions.size() - 1);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!filteredOptions.isEmpty()) {
                confirm();
            }
        }
        if (open && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END)) {
            updateBounds();
            revealCursor();
        }
    }

    /**
     * @return 宿主可视区域
     */
    @Override
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 更新宿主区域，面板始终沿右缘定位。
     *
     * @param width  可视区域宽度
     * @param height 可视区域高度
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        if (open) updateBounds();
    }

    /**
     * @return 是否显示选择面板
     */
    @Override
    public boolean isVisible() {
        return open;
    }

    /**
     * 转发滚动条拖动，阻止底层控件接收同一事件。
     *
     * @param dragX 水平移动量
     * @param dragY 垂直移动量
     * @return 面板是否消费本次拖动
     */
    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        double y = context.pointerY();
        int button = context.button();
        if (!open) return false;
        mouseDragged(y, button);
        return true;
    }

    /**
     * 释放选择面板的滚动条捕获。
     *
     * @return 面板是否消费本次释放
     */
    @Override
    public boolean mouseReleased(UiInputContext context) {
        int button = context.button();
        if (!open) return false;
        mouseReleased(button);
        return true;
    }

    /**
     * 在类型列表上滚动，面板外的滚轮不传到底层页面。
     *
     * @param scrollX 水平滚动量
     * @param scrollY 垂直滚动量
     * @return 面板是否消费本次滚动
     */
    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double x = context.pointerX();
        double y = context.pointerY();
        if (!open) return false;
        mouseScrolled(x, y, scrollY);
        return true;
    }

    /**
     * 搜索、方向键与确认均由同一个选择器处理。
     *
     * @param keyCode   GLFW 按键码
     * @param scanCode  键盘扫描码
     * @param modifiers 修饰键标记
     * @return 面板是否消费本次按键
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        keyPressed(keyCode);
        return true;
    }

    /**
     * 捕获面板打开时的按键释放。
     *
     * @param keyCode   GLFW 按键码
     * @param scanCode  键盘扫描码
     * @param modifiers 修饰键标记
     * @return 面板是否消费本次释放
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return open;
    }

    /**
     * 把输入字符追加到类型搜索词。
     *
     * @param codePoint 输入字符
     * @param modifiers 修饰键标记
     * @return 面板是否消费本次字符输入
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return open && charTyped(codePoint);
    }

}
