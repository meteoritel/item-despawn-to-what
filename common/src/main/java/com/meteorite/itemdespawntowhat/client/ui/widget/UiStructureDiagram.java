package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 结构图解（宿主侧控件，主计划 §4 结果与：253 图解验收）。
 * <p>只做槽位与连接原语：节点是「源物品 / 催化剂 / 候选 / 效果」这类配置槽位，
 * 连线只表达宿主给定的关系；不预测收益、不模拟运行、不声称知道实时容量、位置或游标。
 * <p>宽空间横向排布并换行，窄空间单列纵向；只绘制可见行；节点点击/Enter 触发宿主导航回调。
 */
public final class UiStructureDiagram implements UiWidget, UiFocusTarget {

    // 节点：id 供宿主映射到页面/候选/效果
    public record Node(String id, Component title, @Nullable Component detail) {
    }

    // 连接：只表达宿主给定的关系
    public record Edge(String fromId, String toId) {
    }

    // 节点激活回调（导航由宿主提供）
    public interface Listener {

        void onNodeActivated(String id);
    }

    // 布局常量
    private static final int NODE_HEIGHT = 16;
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 4;
    private static final int MIN_NODE_WIDTH = 96;
    private static final int DETAIL_MIN_WIDTH = 132;
    private static final int ARROW_WIDTH = 10;

    private final Font font;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private @Nullable Listener listener;
    private Component emptyMessage = Component.empty();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private int topRow;
    private int cursor = -1;

    public UiStructureDiagram(Font font, @Nullable Listener listener) {
        this.font = Objects.requireNonNull(font, "font");
        this.listener = listener;
    }

    public UiStructureDiagram setNodes(List<Node> next) {
        nodes.clear();
        if (next != null) {
            nodes.addAll(next);
        }
        cursor = nodes.isEmpty() ? -1 : Math.clamp(cursor < 0 ? 0 : cursor, 0, nodes.size() - 1);
        return this;
    }

    public UiStructureDiagram setEdges(List<Edge> next) {
        edges.clear();
        if (next != null) {
            edges.addAll(next);
        }
        return this;
    }

    public UiStructureDiagram setEmptyMessage(Component message) {
        this.emptyMessage = message == null ? Component.empty() : message;
        return this;
    }

    public UiStructureDiagram setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public UiStructureDiagram setVisible(boolean visible) {
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
        clampCursor();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    // ---- 布局 ----

    private int perRow() {
        int usable = Math.max(MIN_NODE_WIDTH, bounds.width());
        int count = Math.max(1, usable / (MIN_NODE_WIDTH + GAP + ARROW_WIDTH));
        return Math.max(1, Math.min(count, Math.max(1, nodes.size())));
    }

    private int nodeWidth() {
        int perRow = perRow();
        int usable = Math.max(0, bounds.width() - perRow * (ARROW_WIDTH + GAP) - GAP);
        return Math.max(24, usable / perRow);
    }

    private int rowCount() {
        int perRow = perRow();
        return (nodes.size() + perRow - 1) / perRow;
    }

    private int rowHeight() {
        return (showsDetail() ? NODE_HEIGHT + LINE_HEIGHT : NODE_HEIGHT) + GAP;
    }

    private boolean showsDetail() {
        return nodeWidth() >= DETAIL_MIN_WIDTH && nodes.stream().anyMatch(node -> node.detail() != null);
    }

    private int visibleRows() {
        return Math.max(1, bounds.height() / Math.max(1, rowHeight()));
    }

    private void clampCursor() {
        int maxRow = Math.max(0, rowCount() - visibleRows());
        if (cursor < 0) {
            topRow = Math.clamp(topRow, 0, maxRow);
            return;
        }
        int row = cursor / perRow();
        if (row < topRow) {
            topRow = row;
        } else if (row >= topRow + visibleRows()) {
            topRow = row - visibleRows() + 1;
        }
        topRow = Math.clamp(topRow, 0, maxRow);
    }

    private @Nullable UiRect nodeRect(int index) {
        int perRow = perRow();
        int row = index / perRow;
        if (row < topRow || row >= topRow + visibleRows()) {
            return null;
        }
        int column = index % perRow;
        int x = bounds.x() + column * (nodeWidth() + ARROW_WIDTH + GAP);
        int y = bounds.y() + (row - topRow) * rowHeight();
        return new UiRect(x, y, nodeWidth(), rowHeight() - GAP);
    }

    private int indexAt(double mouseX, double mouseY) {
        for (int index = 0; index < nodes.size(); index++) {
            UiRect rect = nodeRect(index);
            if (rect != null && rect.contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (nodes.isEmpty()) {
            String text = TextScroll.trimToWidth(font, emptyMessage.getString(), Math.max(1, bounds.width()));
            graphics.drawString(font, text, bounds.x(), bounds.y(), UiPalette.TEXT_DISABLED, false);
            return;
        }
        int lastRow = Math.min(rowCount(), topRow + visibleRows());
        for (int row = topRow; row < lastRow; row++) {
            int first = row * perRow();
            int rowEnd = Math.min(nodes.size(), first + perRow());
            for (int index = first; index < rowEnd; index++) {
                UiRect rect = nodeRect(index);
                if (rect == null) {
                    continue;
                }
                drawNode(graphics, index, rect, mouseX, mouseY);
                if (index + 1 < rowEnd) {
                    drawArrow(graphics, hasEdge(nodes.get(index).id(), nodes.get(index + 1).id()), rect);
                }
            }
        }
    }

    private void drawNode(GuiGraphics graphics, int index, UiRect rect, int mouseX, int mouseY) {
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill = index == cursor && focused ? UiPalette.CONTROL_SELECTED
                : (hovered ? UiPalette.CONTROL_HOVER : UiPalette.CONTROL_FILL);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), enabled ? fill : UiPalette.CONTROL_DISABLED);
        UiTheme.drawBevel(graphics, rect, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK);
        Node node = nodes.get(index);
        String title = TextScroll.trimToWidth(font, node.title().getString(), Math.max(1, rect.width() - 4));
        graphics.drawString(font, title, rect.x() + 2, rect.y() + 4,
                enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
        if (showsDetail() && node.detail() != null) {
            String detail = TextScroll.trimToWidth(font, node.detail().getString(), Math.max(1, rect.width() - 4));
            graphics.drawString(font, detail, rect.x() + 2, rect.y() + NODE_HEIGHT - 2, UiPalette.TEXT_SECONDARY,
                    false);
        }
        if (index == cursor && focused) {
            UiTheme.drawFocusOutline(graphics, rect);
        }
    }

    private void drawArrow(GuiGraphics graphics, boolean connected, UiRect rect) {
        int x = rect.right() + 1;
        int y = rect.y() + rect.height() / 2 - 3;
        graphics.drawString(font, connected ? "->" : "  ", x, y,
                connected ? UiPalette.ACCENT : UiPalette.TEXT_DISABLED, false);
    }

    // 是否存在给定连线（相邻节点之间才画箭头，其余关系不猜测）
    private boolean hasEdge(String fromId, String toId) {
        for (Edge edge : edges) {
            if (edge.fromId().equals(fromId) && edge.toId().equals(toId)) {
                return true;
            }
        }
        return false;
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return bounds.contains(mouseX, mouseY);
        }
        cursor = index;
        activateNode(index);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        topRow -= (int) Math.signum(scrollY);
        topRow = Math.clamp(topRow, 0, Math.max(0, rowCount() - visibleRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled || nodes.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveCursor(-1);
            case GLFW.GLFW_KEY_RIGHT -> moveCursor(1);
            case GLFW.GLFW_KEY_UP -> moveCursor(-perRow());
            case GLFW.GLFW_KEY_DOWN -> moveCursor(perRow());
            case GLFW.GLFW_KEY_HOME -> setCursor(0);
            case GLFW.GLFW_KEY_END -> setCursor(nodes.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activateNode(cursor);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveCursor(int delta) {
        setCursor((cursor < 0 ? 0 : cursor) + delta);
    }

    private void setCursor(int index) {
        if (nodes.isEmpty()) {
            cursor = -1;
            return;
        }
        cursor = Math.clamp(index, 0, nodes.size() - 1);
        clampCursor();
    }

    private void activateNode(int index) {
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        Listener current = listener;
        if (current != null) {
            current.onNodeActivated(nodes.get(index).id());
        }
    }

    @Override
    public boolean canFocus() {
        return visible && enabled && !nodes.isEmpty();
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
        if (!enabled || cursor < 0 || cursor >= nodes.size()) {
            return false;
        }
        activateNode(cursor);
        return true;
    }

    @Override
    public Component accessibleName() {
        return Component.empty().append(nodes.isEmpty() ? emptyMessage : nodes.get(Math.max(0, cursor)).title());
    }

    // 鼠标所指节点的完整标题与细节（长名称 tooltip 用）
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        Node node = nodes.get(index);
        return node.detail() == null ? node.title() : node.title().copy().append(" ").append(node.detail());
    }
}
