package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_Y;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_ROW_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.FLOW_HINT_KEY;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.LOGIC_CUT_CORNER;
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
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.NodeInfo;
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
        int x = rect.x() + BORDER + CARD_PADDING_X;
        int y = rect.y() + BORDER + CARD_PADDING_Y;
        int width = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2);
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, title.getString(), width),
                x, y, theme.palette().canvasText(), false);
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, summary.getString(), width),
                x, y + CARD_ROW_HEIGHT, theme.palette().canvasTextMuted(), false);
    }

    private void drawCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                          boolean selected, boolean hovered) {
        if (isContainer(node)) {
            drawLogicCard(graphics, renderFont, node, rect, selected, hovered);
            return;
        }
        int fill = selected ? theme.palette().canvasCardSelectedFill()
                : theme.palette().canvasCardFill();
        if (hovered && !selected) {
            fill = theme.palette().canvasCardHoverFill();
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        drawBorder(graphics, rect, selected ? theme.palette().canvasCardBorderSelected() : theme.palette().canvasCardBorder());
        NodeInfo info = graph.info(node);
        int textX = rect.x() + BORDER + CARD_PADDING_X;
        int textWidth = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2);
        int row1Y = rect.y() + BORDER + CARD_PADDING_Y;
        int color = info != null && (info.overflow() || info.leafMissing() || info.subtreeIssue())
                ? theme.palette().canvasTextDanger() : theme.palette().canvasText();
        String row1 = TextScroll.trimToWidth(renderFont, graph.row1Label(node).getString(), textWidth);
        String row2 = TextScroll.trimToWidth(renderFont, graph.row2Label(node).getString(), textWidth);
        graphics.drawString(renderFont, row1, textX, row1Y, color, false);
        graphics.drawString(renderFont, row2, textX, row1Y + CARD_ROW_HEIGHT, theme.palette().canvasTextMuted(), false);
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
        NodeInfo info = graph.info(node);
        boolean invalid = info != null && info.subtreeIssue();
        int border = invalid ? theme.palette().canvasTextDanger()
                : selected ? theme.palette().canvasCardBorderSelected() : text;
        int thickness = selected || hovered ? BORDER * 2 : BORDER;
        drawLogicShape(graphics, node, rect, border);
        UiRect inner = new UiRect(rect.x() + thickness, rect.y() + thickness,
                rect.width() - thickness * 2, rect.height() - thickness * 2);
        drawLogicShape(graphics, node, inner, fill);
        String title = TextScroll.trimToWidth(renderFont, graph.operatorLabel(node).getString(), rect.width() - LOGIC_CUT_CORNER * 2);
        String detail = TextScroll.trimToWidth(renderFont, graph.row1Label(node).getString(), rect.width() - LOGIC_CUT_CORNER * 2);
        int y = rect.y() + BORDER + CARD_PADDING_Y;
        graphics.drawString(renderFont, title, rect.x() + (rect.width() - renderFont.width(title)) / 2, y,
                invalid ? theme.palette().canvasTextDanger() : text, false);
        graphics.drawString(renderFont, detail, rect.x() + (rect.width() - renderFont.width(detail)) / 2,
                y + CARD_ROW_HEIGHT, text, false);
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
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + BORDER, color);
        graphics.fill(rect.x(), rect.bottom() - BORDER, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y() + BORDER, rect.x() + BORDER, rect.bottom() - BORDER, color);
        graphics.fill(rect.right() - BORDER, rect.y() + BORDER, rect.right(), rect.bottom() - BORDER, color);
    }

}
