package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.ui.Screen.BaseConfigEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FormList extends ContainerObjectSelectionList<FormList.Entry> {

    private static final int BASE_X = 20;
    private static final int LABEL_WIDTH = 80;
    private static final int GAP = 30;

    // 每个组件上下各留的边距
    private static final int ITEM_PADDING = 6;

    public FormList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void add(Component label, AbstractWidget widget) {
        addEntry(new Entry(minecraft.font, label, widget));
    }

    // 调整滑条的位置
    @Override
    public int getRowWidth() {
        return 340;
    }

    @Override
    protected int getRowBottom(int index) {
        List<Entry> entries = children();
        if (index < 0 || index >= entries.size()) {
            return getRowTop(index) + itemHeight;
        }
        return getRowTop(index) + getEntryHeight(entries.get(index));
    }

    // 根据widget实际高度来计算列表元素的行高
    private int getEntryHeight(Entry entry) {
        return Math.max(entry.widget.getHeight(), minecraft.font.lineHeight) + ITEM_PADDING * 2;
    }

    /*
     * 重写：返回所有 Entry 的总高度，父类用它计算 getMaxScroll()。
     * 父类实现是 itemHeight * getItemCount()，这里改为累加各自真实高度。
     */
    @Override
    protected int getMaxPosition() {
        int total = 0;
        for (Entry entry : children()) {
            total += getEntryHeight(entry);
        }
        return total;
    }

    /*
     * 重写：返回第 index 个 Entry 的顶部 Y 坐标（含滚动偏移）。
     * 父类实现是 top + 4 - scrollAmount + index * itemHeight，
     * 这里改为累加前面所有 Entry 的真实高度。
     */
    @Override
    protected int getRowTop(int index) {
        int y = this.getY() + 4 - (int) this.getScrollAmount();
        List<Entry> entries = children();
        for (int i = 0; i < index && i < entries.size(); i++) {
            y += getEntryHeight(entries.get(i));
        }
        return y;
    }

    // 用累加逻辑找到鼠标位置对应的 Entry，替代父类final类型的getEntryAtPosition
    @Nullable
    private Entry getEntryAtMouse(double mouseX, double mouseY) {
        int halfRowWidth = this.getRowWidth() / 2;
        int centerX = this.getX() + this.width / 2;
        if (mouseX < centerX - halfRowWidth || mouseX > centerX + halfRowWidth) {
            return null;
        }

        List<Entry> entries = children();
        int currentY = this.getY() + 4 - (int) this.getScrollAmount();
        for (Entry entry : entries) {
            int entryHeight = getEntryHeight(entry);
            if (mouseY >= currentY && mouseY < currentY + entryHeight) {
                return entry;
            }
            currentY += entryHeight;
        }
        return null;
    }

    // 重写 mouseClicked，绕过父类对 final getEntryAtPosition 的调用
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        // 让父类处理滚动条拖拽状态的更新
        this.updateScrollingState(mouseX, mouseY, button);

        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        Entry e = getEntryAtMouse(mouseX, mouseY);
        if (e != null) {
            if (e.mouseClicked(mouseX, mouseY, button)) {
                // 切换焦点时清除旧焦点
                Entry prevFocused = this.getFocused();
                if (prevFocused != e && prevFocused instanceof ContainerEventHandler ceh) {
                    ceh.setFocused(null);
                }
                this.setFocused(e);
                this.setDragging(true);
                return true;
            }
        }
        // 拖拽的时候还有问题，之后再解决
        return this.isScrollBarVisible() && isScrolling(mouseX, mouseY);
    }

    private boolean isScrolling(double mouseX, double mouseY) {
        int scrollBarX = this.getX() + this.getRowWidth() - 6;
        return mouseX >= scrollBarX && mouseX <= scrollBarX + 6
                && mouseY >= this.getY() && mouseY <= this.getBottom();
    }

    private boolean isScrollBarVisible() {
        return this.getMaxScroll() > 0;
    }


    // ===============================================================================
    public class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final Font font;
        private final Component label;
        private final AbstractWidget widget;


        public Entry(Font font, Component label, AbstractWidget widget) {
            this.font = font;
            this.label = label;
            this.widget = widget;
        }
        @Override
        public void render(GuiGraphics g, int index, int y, int x, int width, int height,
                           int mx, int my, boolean hovered, float pt) {
            int widgetX = BASE_X + LABEL_WIDTH + GAP;

            int labelY  = y + widget.getHeight() / 2 - font.lineHeight / 2;
            g.drawString(font, label, BASE_X, labelY, 0xE0E0E0, false);

            widget.setX(widgetX);
            widget.setY(y);
            widget.render(g, mx, my, pt);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean clicked = widget.mouseClicked(mouseX, mouseY, button);

            if (clicked
                    && minecraft.screen instanceof BaseConfigEditScreen<?> screen
                    && screen.shouldTakeFocus(widget)) {
                screen.setFocusedWidget(widget);
            }
            return clicked;
        }

        // 按钮组件绑定的是release事件
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return widget.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return false;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return false;
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
