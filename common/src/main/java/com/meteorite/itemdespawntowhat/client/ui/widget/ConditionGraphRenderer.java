package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_ROW_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_SEPARATOR;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CONTENT_TOP;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.FLOW_HINT_KEY;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.LOGIC_CUT_CORNER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.TITLE_BAND_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.TITLE_BAND_TOP;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.isContainer;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiDotGrid;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiOrthoLink;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 条件画布绘制：读取布局与选择状态，绘制卡片、连线和手势反馈。 */
final class ConditionGraphRenderer {
    private static final int DOT_BASE_STEP = 16;
    private static final int LINK_THICKNESS = 1;
    private static final int LOGIC_ROUND_RADIUS = 4;
    private static final int LINK_ARROW_SIZE = 3;
    private static final int FLOW_DASH_LENGTH = 4;
    private static final int FLOW_DASH_GAP = 3;
    private static final int EMPTY_HINT_GAP = 4;
    private static final int DROP_MARKER_THICKNESS = 2;
    private static final int DRAG_GHOST_GAP = 6;
    private final UiHostTheme theme;
    private final Font font;
    private final ConditionGraphLayout graph;

    ConditionGraphRenderer(UiHostTheme theme, Font font, ConditionGraphLayout graph) {
        this.theme = theme;
        this.font = font;
        this.graph = graph;
    }

    void drawDotGrid(GuiGraphics graphics, UiTransform transform, UiRect viewport) {
        int step = Math.max(1, (int) Math.round(DOT_BASE_STEP * transform.scale()));
        int gridX = alignToGrid(transform.toScreenX(0), viewport.x(), step);
        int gridY = alignToGrid(transform.toScreenY(0), viewport.y(), step);
        UiDotGrid.draw(graphics, transform.scale(), gridX, gridY, viewport.right(), viewport.bottom(),
                DOT_BASE_STEP, theme.palette().canvasGridDot());
    }

    private static int alignToGrid(double originScreen, int start, int step) {
        long offset = Math.floorMod((long) Math.floor(originScreen) - start, (long) step);
        return start + (int) offset;
    }

    void drawCanvasBackground(GuiGraphics graphics, UiRect bounds) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), theme.palette().canvasFill());
        drawBorder(graphics, bounds, theme.palette().canvasBorder());
    }

    void drawEmptyMessage(GuiGraphics graphics, Font renderFont, UiRect viewport,
                          UiTransform transform, Component emptyMessage) {
        String text = TextScroll.trimToWidth(renderFont, emptyMessage.getString(), Math.max(0, viewport.width() - 8));
        int textWidth = renderFont.width(text);
        int centerX = viewport.x() + viewport.width() / 2;
        int textY = viewport.y() + viewport.height() / 2 - renderFont.lineHeight - EMPTY_HINT_GAP;
        if (!graph.hotspots().isEmpty()) {
            UiRect hotspot = graph.hotspots().getFirst().rect();
            centerX = (int) Math.round(transform.toScreenX(hotspot.x() + hotspot.width() / 2.0));
            textY = (int) Math.round(transform.toScreenY(hotspot.y())) - renderFont.lineHeight - EMPTY_HINT_GAP;
        }
        int textX = Math.clamp(centerX - textWidth / 2, viewport.x() + EMPTY_HINT_GAP,
                Math.max(viewport.x() + EMPTY_HINT_GAP, viewport.right() - textWidth - EMPTY_HINT_GAP));
        int clampedY = Math.clamp(textY, viewport.y() + EMPTY_HINT_GAP,
                Math.max(viewport.y() + EMPTY_HINT_GAP, viewport.bottom() - renderFont.lineHeight - EMPTY_HINT_GAP));
        graphics.drawString(renderFont, text, textX, clampedY, theme.palette().canvasHintText(), false);
    }

    void drawLinks(GuiGraphics graphics, Set<UiTreePath> selection) {
        ConditionNode root = graph.root();
        if (root == null) {
            UiRect hotspot = graph.hotspots().getFirst().rect();
            drawEndpointLink(graphics, graph.sourceRect(), hotspot);
            drawEndpointLink(graphics, hotspot, graph.resultRect());
        } else {
            UiRect rootRect = graph.rect(root);
            if (rootRect != null) {
                drawEndpointLink(graphics, graph.sourceRect(), rootRect);
                drawResultFlow(graphics, rootRect);
            }
        }
        for (ConditionNode node : graph.nodes()) {
            List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
            if (children.isEmpty()) {
                continue;
            }
            UiRect parentRect = graph.rect(node);
            if (parentRect == null) continue;
            for (ConditionNode child : children) {
                UiRect childRect = graph.rect(child);
                if (childRect == null) {
                    continue;
                }
                int color = selection.contains(graph.path(node)) || selection.contains(graph.path(child))
                        ? theme.palette().canvasLinkSelected() : theme.palette().canvasLink();
                int childCenterX = childRect.x() + childRect.width() / 2;
                UiOrthoLink.drawDown(graphics, parentRect.x() + parentRect.width() / 2, parentRect.bottom(),
                        childCenterX, childRect.y(), LINK_THICKNESS, color);
                drawDownArrow(graphics, childCenterX, childRect.y(), color);
            }
        }
    }

    private void drawEndpointLink(GuiGraphics graphics, UiRect from, UiRect to) {
        int fromY = from.y() + from.height() / 2;
        int toY = to.y() + to.height() / 2;
        int middleX = (from.right() + to.x()) / 2;
        int color = theme.palette().canvasLink();
        graphics.fill(from.right(), fromY, middleX + LINK_THICKNESS, fromY + LINK_THICKNESS, color);
        graphics.fill(middleX, Math.min(fromY, toY), middleX + LINK_THICKNESS,
                Math.max(fromY, toY) + LINK_THICKNESS, color);
        graphics.fill(middleX, toY, to.x(), toY + LINK_THICKNESS, color);
        drawRightArrow(graphics, to.x(), toY, color);
    }

    private static void drawDownArrow(GuiGraphics graphics, int centerX, int tipY, int color) {
        for (int row = 0; row < LINK_ARROW_SIZE; row++) {
            graphics.fill(centerX - row, tipY - row - BORDER, centerX + row + BORDER, tipY - row, color);
        }
    }

    private static void drawRightArrow(GuiGraphics graphics, int tipX, int centerY, int color) {
        for (int row = 0; row < LINK_ARROW_SIZE; row++) {
            graphics.fill(tipX - row - BORDER, centerY - row, tipX - row, centerY + row + BORDER, color);
        }
    }

    private void drawResultFlow(GuiGraphics graphics, UiRect root) {
        int fromY = root.y() + root.height() / 2;
        int color = theme.palette().canvasFlowLink();
        drawDashedLine(graphics, root.right(), fromY, graph.resultRect().x(), fromY, color);
        drawRightArrow(graphics, graph.resultRect().x(), fromY, color);
        Component hint = Component.translatable(FLOW_HINT_KEY);
        int hintX = root.right() + (graph.resultRect().x() - root.right() - font.width(hint)) / 2;
        graphics.drawString(font, hint, hintX, fromY - font.lineHeight - CARD_PADDING_X, color, false);
    }

    private static void drawDashedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int distance = Math.max(x2 - x1, y2 - y1);
        boolean horizontal = y1 == y2;
        for (int offset = 0; offset < distance; offset += FLOW_DASH_LENGTH + FLOW_DASH_GAP) {
            int length = Math.min(FLOW_DASH_LENGTH, distance - offset);
            int x = horizontal ? x1 + offset : x1;
            int y = horizontal ? y1 : y1 + offset;
            graphics.fill(x, y, x + (horizontal ? length : LINK_THICKNESS),
                    y + (horizontal ? LINK_THICKNESS : length), color);
        }
    }

    void drawCards(GuiGraphics graphics, Font renderFont, Set<UiTreePath> selection,
                   @Nullable ConditionNode hoveredNode) {
        drawEndpoint(graphics, renderFont, graph.sourceTitle(), graph.sourceSummary(), graph.sourceRect());
        drawEndpoint(graphics, renderFont, graph.resultTitle(), graph.resultSummary(), graph.resultRect());
        for (ConditionNode node : graph.nodes()) {
            UiRect rect = graph.rect(node);
            if (rect == null || rect.right() < 0 || rect.bottom() < 0) {
                continue;
            }
            boolean selected = selection.contains(graph.path(node));
            drawCard(graphics, renderFont, node, rect, selected, node == hoveredNode);
        }
    }

    private void drawEndpoint(GuiGraphics graphics, Font renderFont, Component title, Component summary, UiRect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), theme.palette().canvasGroupFill());
        drawBorder(graphics, rect, theme.palette().canvasCardBorder());
        drawTitleBand(graphics, renderFont, title, rect, BORDER, theme.palette().canvasText());
        drawContentRow(graphics, renderFont, summary, theme.palette().canvasTextMuted(), rect, BORDER, 0);
    }

    private void drawCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                          boolean selected, boolean hovered) {
        if (isContainer(node)) {
            drawLogicCard(graphics, renderFont, node, rect, selected, hovered);
            return;
        }
        int fill = selected ? theme.palette().canvasCardSelectedFill()
                : hovered ? theme.palette().canvasCardHoverFill() : theme.palette().canvasCardFill();
        boolean issue = graph.hasIssue(node);
        int thickness = selected ? BORDER * 2 : BORDER;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        drawBorder(graphics, rect, borderColor(selected, issue, hovered), thickness);
        drawTitleBand(graphics, renderFont, graph.titleLabel(node), rect, thickness,
                issue ? theme.palette().canvasTextDanger() : theme.palette().canvasText());
        drawContentRow(graphics, renderFont, graph.summaryLabel(node), theme.palette().canvasText(), rect, thickness, 0);
        drawContentRow(graphics, renderFont, graph.statusLabel(node), theme.palette().canvasTextDanger(),
                rect, thickness, 1);
    }

    private void drawLogicCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                               boolean selected, boolean hovered) {
        int fill;
        int text;
        if (node instanceof ConditionNode.AllOf) {
            fill = theme.palette().canvasAllFill();
            text = theme.palette().canvasAllText();
        } else if (node instanceof ConditionNode.AnyOf) {
            fill = theme.palette().canvasAnyFill();
            text = theme.palette().canvasAnyText();
        } else {
            fill = theme.palette().canvasNotFill();
            text = theme.palette().canvasNotText();
        }
        // 容器保留形状与运算符盖色，强调色只作用于描边，避免覆盖语义色
        boolean issue = graph.hasIssue(node);
        int thickness = selected ? BORDER * 2 : BORDER;
        drawLogicShape(graphics, node, rect, borderColor(selected, issue, hovered));
        UiRect inner = new UiRect(rect.x() + thickness, rect.y() + thickness,
                rect.width() - thickness * 2, rect.height() - thickness * 2);
        drawLogicShape(graphics, node, inner, fill);
        drawTitleBand(graphics, renderFont, graph.titleLabel(node), rect, thickness,
                issue ? theme.palette().canvasTextDanger() : text);
        drawContentRow(graphics, renderFont, graph.summaryLabel(node), text, rect, thickness, 0);
        drawContentRow(graphics, renderFont, graph.row2Label(node), theme.palette().canvasTextMuted(),
                rect, thickness, 1);
    }

    /**
     * 卡片描边取色，优先级为 选中 &gt; 结构问题 &gt; 悬停，叶与容器共用同一规则。
     */
    private int borderColor(boolean selected, boolean issue, boolean hovered) {
        if (selected) {
            return theme.palette().canvasCardBorderSelected();
        }
        if (issue) {
            return theme.palette().canvasTextDanger();
        }
        return hovered ? theme.palette().canvasCardBorderSelected() : theme.palette().canvasCardBorder();
    }

    /**
     * 绘制卡片外壳的标题带与分隔线：标题整行居中，分隔线横跨描边内侧。
     * 端点、条件叶与容器共用同一套内部纵向分区，超长标题按可用宽度裁剪。
     */
    private void drawTitleBand(GuiGraphics graphics, Font renderFont, Component title, UiRect rect,
                               int thickness, int titleColor) {
        String text = TextScroll.trimToWidth(renderFont, title.getString(),
                Math.max(0, rect.width() - (thickness + CARD_PADDING_X) * 2));
        graphics.drawString(renderFont, text, rect.x() + (rect.width() - renderFont.width(text)) / 2,
                rect.y() + TITLE_BAND_TOP, titleColor, false);
        int separatorY = rect.y() + TITLE_BAND_TOP + TITLE_BAND_HEIGHT;
        graphics.fill(rect.x() + thickness, separatorY, rect.right() - thickness, separatorY + CARD_SEPARATOR,
                theme.palette().canvasCardBorder());
    }

    /**
     * 绘制内容区第 row 行（0 起）的左对齐文本，超出内容宽按宽度裁剪。
     */
    private void drawContentRow(GuiGraphics graphics, Font renderFont, Component content, int color,
                                UiRect rect, int thickness, int row) {
        String text = TextScroll.trimToWidth(renderFont, content.getString(),
                Math.max(0, rect.width() - (thickness + CARD_PADDING_X) * 2));
        graphics.drawString(renderFont, text, rect.x() + thickness + CARD_PADDING_X,
                rect.y() + CONTENT_TOP + row * CARD_ROW_HEIGHT, color, false);
    }

    private static void drawLogicShape(GuiGraphics graphics, ConditionNode node, UiRect rect, int color) {
        int radius = node instanceof ConditionNode.AnyOf ? rect.height() / 2
                : node instanceof ConditionNode.Inverted ? LOGIC_CUT_CORNER : LOGIC_ROUND_RADIUS;
        radius = Math.clamp(radius, 0, Math.min(rect.width(), rect.height()) / 2);
        int bandTop = 0;
        int bandInset = -1;
        for (int row = 0; row <= rect.height(); row++) {
            int edge = Math.min(row, rect.height() - row - BORDER);
            int inset = 0;
            if (row == rect.height()) inset = -1;
            else if (edge < radius) {
                if (node instanceof ConditionNode.Inverted) inset = radius - edge;
                else {
                    double dy = radius - edge - 0.5D;
                    inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
                }
            }
            // 相同缩进的连续扫描行合并绘制，长直边只提交一个矩形。
            if (inset != bandInset) {
                if (row > bandTop) {
                    graphics.fill(rect.x() + bandInset, rect.y() + bandTop, rect.right() - bandInset,
                            rect.y() + row, color);
                }
                bandTop = row;
                bandInset = inset;
            }
        }
    }

    void drawHotspots(GuiGraphics graphics, Font renderFont, @Nullable Hotspot hoveredHotspot) {
        for (Hotspot hotspot : graph.hotspots()) {
            UiRect rect = hotspot.rect();
            boolean hovered = hotspot == hoveredHotspot;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                    hovered ? theme.palette().canvasAccentHover() : theme.palette().canvasAccent());
            drawBorder(graphics, rect, theme.palette().canvasHotspotBorder());
            String mark = "+";
            graphics.drawString(renderFont, mark, rect.x() + (rect.width() - renderFont.width(mark)) / 2,
                    rect.y() + (rect.height() - renderFont.lineHeight) / 2 + 1, theme.palette().canvasTextOnAccent(), false);
        }
    }

    void drawDropMarker(GuiGraphics graphics, @Nullable UiTreeEditor.MovePreview<ConditionNode> preview,
                        @Nullable ConditionNode dropTarget) {
        if (preview == null || dropTarget == null) {
            return;
        }
        UiRect rect = graph.rect(dropTarget);
        if (rect == null) {
            return;
        }
        int color = preview.outcome().error() == null ? theme.palette().canvasAccent() : theme.palette().canvasTextDanger();
        if (preview.position() == UiTreeEditor.DropPosition.INSIDE) {
            drawBorder(graphics, rect, color);
            drawBorder(graphics, new UiRect(rect.x() - BORDER, rect.y() - BORDER,
                    rect.width() + BORDER * 2, rect.height() + BORDER * 2), color);
            return;
        }
        // 同层节点水平排列，BEFORE / AFTER 用目标卡片左右两侧的竖直短线表示
        int x = preview.position() == UiTreeEditor.DropPosition.BEFORE
                ? rect.x() - DROP_MARKER_THICKNESS : rect.right();
        graphics.fill(x, rect.y(), x + DROP_MARKER_THICKNESS, rect.bottom(), color);
    }

    void drawDragGhost(GuiGraphics graphics, Font renderFont, UiRect bounds,
                       @Nullable String dragLabel, int mouseX, int mouseY) {
        String label = dragLabel == null ? "" : dragLabel;
        int width = Math.min(bounds.width(), renderFont.width(label) + DRAG_GHOST_GAP * 2);
        int height = renderFont.lineHeight + 2;
        int x = Math.clamp(mouseX + DRAG_GHOST_GAP, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
        int y = Math.clamp(mouseY + DRAG_GHOST_GAP, bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
        graphics.fill(x, y, x + width, y + height, theme.palette().canvasDragGhostFill());
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, label, Math.max(0, width - DRAG_GHOST_GAP)),
                x + DRAG_GHOST_GAP / 2, y + 1, theme.palette().canvasText(), false);
    }

    static void drawBorder(GuiGraphics graphics, UiRect rect, int color) {
        drawBorder(graphics, rect, color, BORDER);
    }

    /**
     * 逐圈内缩绘制描边，厚度大于 1 时得到整圈加粗的同心边框（选中态）。
     */
    static void drawBorder(GuiGraphics graphics, UiRect rect, int color, int thickness) {
        for (int inset = 0; inset < thickness; inset++) {
            int left = rect.x() + inset;
            int top = rect.y() + inset;
            int right = rect.right() - inset;
            int bottom = rect.bottom() - inset;
            graphics.fill(left, top, right, top + BORDER, color);
            graphics.fill(left, bottom - BORDER, right, bottom, color);
            graphics.fill(left, top + BORDER, left + BORDER, bottom - BORDER, color);
            graphics.fill(right - BORDER, top + BORDER, right, bottom - BORDER, color);
        }
    }

}
