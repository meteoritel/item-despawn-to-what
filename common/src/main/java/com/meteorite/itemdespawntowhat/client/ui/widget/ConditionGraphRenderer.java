package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_ROW_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_SEPARATOR;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CONTENT_ROWS;
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
import net.minecraft.util.FormattedCharSequence;
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
    // 标题带右上角的结构问题标记：0 表示不画标记，其余为标记颜色
    private static final int NO_MARK = 0;
    private static final int MARK_RESERVE = 8;
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
        drawHotspotLinks(graphics, selection);
    }

    /**
     * 容器末尾的 [+] 热区也是它未来的子项位置：给热区画一段来自容器的连线，玩家才能看出这个
     * 加号长在哪个分支下面。热区不是条件节点，因此不画箭头，与子项连线区分。
     */
    private void drawHotspotLinks(GuiGraphics graphics, Set<UiTreePath> selection) {
        for (Hotspot hotspot : graph.hotspots()) {
            UiTreePath parentPath = hotspot.parent();
            if (parentPath == null) {
                continue;
            }
            ConditionNode container = graph.nodeAtPath(parentPath);
            UiRect parentRect = container == null ? null : graph.rect(container);
            if (parentRect == null) {
                continue;
            }
            UiRect hotspotRect = hotspot.rect();
            int color = selection.contains(parentPath)
                    ? theme.palette().canvasLinkSelected() : theme.palette().canvasLink();
            UiOrthoLink.drawDown(graphics, parentRect.x() + parentRect.width() / 2, parentRect.bottom(),
                    hotspotRect.x() + hotspotRect.width() / 2, hotspotRect.y(), LINK_THICKNESS, color);
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
        drawTitleBand(graphics, renderFont, title, rect, BORDER, theme.palette().canvasText(), NO_MARK);
        drawEndpointSummary(graphics, renderFont, summary, rect);
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
                issue ? theme.palette().canvasTextDanger() : theme.palette().canvasText(), markColor(node));
        drawSummaryRows(graphics, renderFont, node, rect, thickness, theme.palette().canvasText());
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
                issue ? theme.palette().canvasTextDanger() : text, markColor(node));
        drawSummaryRows(graphics, renderFont, node, rect, thickness, text);
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
     * 绘制卡片标题带：标题左对齐，与内容行共用同一条左轴；分隔线横跨描边内侧。结构问题只在
     * 右上角留一个颜色标记（具体原因看悬停提示），把整行内容区让给参数文本。
     */
    private void drawTitleBand(GuiGraphics graphics, Font renderFont, Component title, UiRect rect,
                               int thickness, int titleColor, int markColor) {
        int reserve = markColor == NO_MARK ? 0 : MARK_RESERVE;
        String text = TextScroll.trimToWidth(renderFont, title.getString(),
                Math.max(0, rect.width() - (thickness + CARD_PADDING_X) * 2 - reserve));
        graphics.drawString(renderFont, text, rect.x() + thickness + CARD_PADDING_X,
                rect.y() + TITLE_BAND_TOP, titleColor, false);
        int separatorY = rect.y() + TITLE_BAND_TOP + TITLE_BAND_HEIGHT;
        graphics.fill(rect.x() + thickness, separatorY, rect.right() - thickness, separatorY + CARD_SEPARATOR,
                theme.palette().canvasCardBorder());
        if (markColor != NO_MARK) {
            String mark = "!";
            graphics.drawString(renderFont, mark,
                    rect.right() - thickness - CARD_PADDING_X - renderFont.width(mark),
                    rect.y() + TITLE_BAND_TOP, markColor, false);
        }
    }

    // 标题带右上角的标记颜色：必须修的错误用警示色，只是提示（层级较深）用次要色，无问题不留标记
    private int markColor(ConditionNode node) {
        if (graph.statusLabel(node).getString().isEmpty()) {
            return NO_MARK;
        }
        return graph.statusSevere(node) ? theme.palette().canvasTextDanger() : theme.palette().canvasTextMuted();
    }

    /**
     * 绘制卡片内容区：把布局给出的文本行按内容宽折行，最多铺满两行——长列表（周围方块、气候
     * 区间）因此能完整读，不再被单行硬裁成半截。
     */
    private void drawSummaryRows(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                                 int thickness, int color) {
        int width = Math.max(0, rect.width() - (thickness + CARD_PADDING_X) * 2);
        if (width == 0) {
            return;
        }
        int row = 0;
        for (Component content : graph.summaryRows(node)) {
            if (content.getString().isEmpty()) {
                continue;
            }
            for (FormattedCharSequence line : renderFont.split(content, width)) {
                if (row >= CONTENT_ROWS) {
                    return;
                }
                graphics.drawString(renderFont, line, rect.x() + thickness + CARD_PADDING_X,
                        rect.y() + CONTENT_TOP + row * CARD_ROW_HEIGHT, color, false);
                row++;
            }
        }
    }

    // 端点卡片只有一行摘要：左对齐绘制，超出内容宽按宽度裁剪
    private void drawEndpointSummary(GuiGraphics graphics, Font renderFont, Component summary, UiRect rect) {
        String text = TextScroll.trimToWidth(renderFont, summary.getString(),
                Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2));
        graphics.drawString(renderFont, text, rect.x() + BORDER + CARD_PADDING_X,
                rect.y() + CONTENT_TOP, theme.palette().canvasTextMuted(), false);
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

    // 热区常显在每张容器卡片下方，静息态只留一个低调的「+」；悬停才用强调色填充，避免整页加号抢走卡片正文的注意力
    void drawHotspots(GuiGraphics graphics, Font renderFont, @Nullable Hotspot hoveredHotspot) {
        for (Hotspot hotspot : graph.hotspots()) {
            UiRect rect = hotspot.rect();
            boolean hovered = hotspot == hoveredHotspot;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                    hovered ? theme.palette().canvasAccentHover() : theme.palette().canvasCardFill());
            drawBorder(graphics, rect, theme.palette().canvasHotspotBorder());
            String mark = "+";
            graphics.drawString(renderFont, mark, rect.x() + (rect.width() - renderFont.width(mark)) / 2,
                    rect.y() + (rect.height() - renderFont.lineHeight) / 2 + 1,
                    hovered ? theme.palette().canvasTextOnAccent() : theme.palette().canvasAccent(), false);
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
