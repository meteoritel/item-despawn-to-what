package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_CORNER_RADIUS;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_ROW_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CONTENT_ROWS;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CONTENT_TOP;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.FLOW_HINT_GAP;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.LOGIC_CUT_CORNER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.LOGIC_ROUND_MAX_RADIUS;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.MARK_RESERVE;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.TITLE_BAND_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.TITLE_BAND_TOP;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.TITLE_TEXT_SCALE;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.isContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiDotGrid;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CardText;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** 条件画布绘制：读取布局与选择状态，绘制卡片、连线和手势反馈。 */
final class ConditionGraphRenderer {
    // 点阵间距：画布放大到 50% 时仍是轻纹理，比卡片正文更弱
    private static final int DOT_BASE_STEP = 20;
    // 连线线宽：50% 缩放下正好落到 1 个屏幕像素
    private static final int LINK_THICKNESS = 2;
    private static final int FLOW_DASH_LENGTH = 4;
    private static final int FLOW_DASH_GAP = 4;
    private static final int EMPTY_HINT_GAP = 4;
    private static final int DROP_MARKER_THICKNESS = 2;
    private static final int DRAG_GHOST_GAP = 6;
    // 标题带右上角的结构问题标记：0 表示不画标记，其余为标记颜色
    private static final int NO_MARK = 0;
    // 结构标记字形：宽度随字体固定，构造时量一次即可
    private static final String MARK_TEXT = "!";
    // 选中环：画在卡片外侧，卡片内容与文本位置不因选中而位移
    private static final int SELECTION_RING = 1;
    private final UiHostTheme theme;
    private final Font font;
    private final ConditionGraphLayout graph;
    private final int markWidth;

    ConditionGraphRenderer(UiHostTheme theme, Font font, ConditionGraphLayout graph) {
        this.theme = theme;
        this.font = font;
        this.graph = graph;
        this.markWidth = font.width(MARK_TEXT);
    }

    void drawDotGrid(GuiGraphics graphics, UiTransform transform, UiRect viewport) {
        int step = Math.max(1, (int) Math.round(DOT_BASE_STEP * transform.scale()));
        int gridX = alignToGrid(transform.toScreenX(0), viewport.x(), step);
        int gridY = alignToGrid(transform.toScreenY(0), viewport.y(), step);
        // 点阵纹理背景透明，混合状态由绘制方按 alpha 贴图口径负责：先提交批次，结束后提交本层并恢复默认关闭
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            UiDotGrid.draw(graphics, transform.scale(), gridX, gridY, viewport.right(), viewport.bottom(),
                    DOT_BASE_STEP, theme.palette().canvasGridDot());
        } finally {
            graphics.flush();
            RenderSystem.disableBlend();
        }
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
        // 语言/字体变化后需要先重排再取几何，否则本轮绘制会用到上一套文本测宽
        graph.ensureText();
        ConditionNode root = graph.root();
        if (root == null) {
            UiRect hotspot = graph.hotspots().getFirst().rect();
            drawEndpointLink(graphics, graph.sourceRect(), hotspot);
            drawEndpointLink(graphics, hotspot, graph.resultRect());
            return;
        }
        UiRect rootRect = graph.rect(root);
        if (rootRect != null) {
            drawEndpointLink(graphics, graph.sourceRect(), rootRect);
            drawResultFlow(graphics, rootRect);
        }
        for (ConditionNode node : graph.nodes()) {
            UiRect parentRect = graph.rect(node);
            if (parentRect == null) {
                continue;
            }
            // 空容器没有子项，但它末尾的 [+] 仍要连到卡片上，否则加号看起来凭空浮在右侧
            drawChildBus(graphics, node, parentRect, ConditionTreeNodes.INSTANCE.children(node), selection);
        }
    }

    /**
     * 一个容器只画一条总线：竖干从卡片底部下到总线，总线横穿所有落点，再逐个垂直落到子项卡片顶部。
     * 子项与末尾的 [+] 热区共用同一条总线，因此不会出现两条平行横线；树天然自上而下，也不再画箭头。
     */
    private void drawChildBus(GuiGraphics graphics, ConditionNode node, UiRect parentRect,
                              List<ConditionNode> children, Set<UiTreePath> selection) {
        UiTreePath parentPath = graph.path(node);
        boolean highlighted = parentPath != null && selection.contains(parentPath);
        List<int[]> drops = new ArrayList<>(children.size() + 1);
        for (ConditionNode child : children) {
            UiRect childRect = graph.rect(child);
            if (childRect == null) {
                continue;
            }
            drops.add(new int[]{centerX(childRect), childRect.y()});
            UiTreePath childPath = graph.path(child);
            highlighted |= childPath != null && selection.contains(childPath);
        }
        // 容器末尾的 [+] 槽位也挂在同一条总线上：它本就是「下一个子项」的位置
        UiRect hotspotRect = hotspotRectOf(parentPath);
        if (hotspotRect != null) {
            drops.add(new int[]{centerX(hotspotRect), hotspotRect.y()});
        }
        if (drops.isEmpty()) {
            return;
        }
        int color = highlighted ? theme.palette().canvasLinkSelected() : theme.palette().canvasLink();
        int parentCenterX = centerX(parentRect);
        int busY = (parentRect.bottom() + drops.getFirst()[1]) / 2;
        int left = parentCenterX;
        int right = parentCenterX;
        for (int[] drop : drops) {
            left = Math.min(left, drop[0]);
            right = Math.max(right, drop[0]);
        }
        // 竖干落到总线所在的像素行，横竖转角处不会缺一格
        linkColumn(graphics, parentCenterX, parentRect.bottom(), busY + LINK_THICKNESS, color);
        int half = LINK_THICKNESS / 2;
        graphics.fill(left - half, busY, right + half, busY + LINK_THICKNESS, color);
        for (int[] drop : drops) {
            linkColumn(graphics, drop[0], busY, drop[1], color);
        }
    }

    // 某个容器末尾热区的矩形：它参与该容器的总线，需要与子项一起排进落点
    @Nullable
    private UiRect hotspotRectOf(@Nullable UiTreePath parentPath) {
        if (parentPath == null) {
            return null;
        }
        for (Hotspot hotspot : graph.hotspots()) {
            if (parentPath.equals(hotspot.parent())) {
                return hotspot.rect();
            }
        }
        return null;
    }

    private static int centerX(UiRect rect) {
        return rect.x() + rect.width() / 2;
    }

    // 纵向连线段：以 centerX 为轴，线宽恒为 LINK_THICKNESS
    private static void linkColumn(GuiGraphics graphics, int centerX, int top, int bottom, int color) {
        if (bottom <= top) {
            return;
        }
        int left = centerX - LINK_THICKNESS / 2;
        graphics.fill(left, top, left + LINK_THICKNESS, bottom, color);
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
    }

    private void drawResultFlow(GuiGraphics graphics, UiRect root) {
        int fromY = root.y() + root.height() / 2;
        int color = theme.palette().canvasFlowLink();
        drawDashedLine(graphics, root.right(), fromY, graph.resultRect().x(), fromY, color);
        // 提示文字压在虚线上会互相干扰：先垫一块画布底色遮断虚线，再在留白里写字
        String text = graph.flowHintText();
        int textWidth = graph.flowHintTextWidth();
        int hintX = root.right() + (graph.resultRect().x() - root.right() - textWidth) / 2;
        int hintY = fromY - font.lineHeight - FLOW_HINT_GAP * 2;
        // 底板一直铺到虚线所在行：虚线在提示文字下方断开，读起来才不是「文字压在虚线上」
        graphics.fill(hintX - FLOW_HINT_GAP * 2, hintY - FLOW_HINT_GAP,
                hintX + textWidth + FLOW_HINT_GAP * 2, fromY + LINK_THICKNESS,
                theme.palette().canvasFill());
        graphics.drawString(font, text, hintX, hintY, color, false);
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

    private static UiRect selectionRing(UiRect rect) {
        return new UiRect(rect.x() - SELECTION_RING, rect.y() - SELECTION_RING,
                rect.width() + SELECTION_RING * 2, rect.height() + SELECTION_RING * 2);
    }

    void drawCards(GuiGraphics graphics, Font renderFont, Set<UiTreePath> selection,
                   @Nullable ConditionNode hoveredNode) {
        graph.ensureText();
        drawEndpoint(graphics, renderFont, graph.sourceTitleText(), graph.sourceTitleWidth(),
                graph.sourceSummaryText(), graph.sourceRect());
        drawEndpoint(graphics, renderFont, graph.resultTitleText(), graph.resultTitleWidth(),
                graph.resultSummaryText(), graph.resultRect());
        for (ConditionNode node : graph.nodes()) {
            UiRect rect = graph.rect(node);
            if (rect == null || rect.right() < 0 || rect.bottom() < 0) {
                continue;
            }
            boolean selected = selection.contains(graph.path(node));
            drawCard(graphics, renderFont, node, rect, selected, node == hoveredNode);
        }
    }

    private void drawEndpoint(GuiGraphics graphics, Font renderFont, String title, int titleWidth,
                              String summary, UiRect rect) {
        drawCardBody(graphics, null, rect, theme.palette().canvasEndpointBand(),
                theme.palette().canvasEndpointFill(), theme.palette().canvasCardBorder());
        drawCardTitle(graphics, renderFont, title, titleWidth, rect, theme.palette().canvasTextMuted(), NO_MARK);
        drawEndpointSummary(graphics, renderFont, summary, rect);
    }

    private void drawCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                          boolean selected, boolean hovered) {
        if (isContainer(node)) {
            drawLogicCard(graphics, renderFont, node, rect, selected, hovered);
            return;
        }
        boolean issue = graph.hasIssue(node);
        if (selected) {
            drawShape(graphics, node, selectionRing(rect), 0, rect.height() + SELECTION_RING * 2,
                    theme.palette().canvasCardBorderSelected());
        }
        int fill = selected ? theme.palette().canvasCardSelectedFill()
                : hovered ? theme.palette().canvasCardHoverFill() : theme.palette().canvasCardFill();
        drawCardBody(graphics, node, rect, theme.palette().canvasCardBand(), fill,
                borderColor(selected, issue, hovered));
        CardText cardText = graph.cardText(node);
        drawCardTitle(graphics, renderFont, cardText.title(), cardText.titleWidth(), rect,
                issue ? theme.palette().canvasTextDanger() : theme.palette().canvasText(), markColor(node));
        drawSummaryRows(graphics, renderFont, cardText, rect,
                theme.palette().canvasText(), theme.palette().canvasTextMuted());
    }

    private void drawLogicCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                               boolean selected, boolean hovered) {
        int fill;
        int band;
        int text;
        if (node instanceof ConditionNode.AllOf) {
            fill = theme.palette().canvasAllFill();
            band = theme.palette().canvasAllBand();
            text = theme.palette().canvasAllText();
        } else if (node instanceof ConditionNode.AnyOf) {
            fill = theme.palette().canvasAnyFill();
            band = theme.palette().canvasAnyBand();
            text = theme.palette().canvasAnyText();
        } else {
            fill = theme.palette().canvasNotFill();
            band = theme.palette().canvasNotBand();
            text = theme.palette().canvasNotText();
        }
        // 容器保留形状与运算符盖色，强调色只作用于描边和外环，避免覆盖语义色
        boolean issue = graph.hasIssue(node);
        if (selected) {
            drawShape(graphics, node, selectionRing(rect), 0, rect.height() + SELECTION_RING * 2,
                    theme.palette().canvasCardBorderSelected());
        }
        drawCardBody(graphics, node, rect, band, fill, borderColor(selected, issue, hovered));
        CardText cardText = graph.cardText(node);
        drawCardTitle(graphics, renderFont, cardText.title(), cardText.titleWidth(), rect,
                issue ? theme.palette().canvasTextDanger() : text, markColor(node));
        drawSummaryRows(graphics, renderFont, cardText, rect, text, theme.palette().canvasTextMuted());
    }

    /**
     * 卡片主体：先按整张卡片形状描边，再在内侧把标题带与正文分两块填色。标题带用同色相更深一档
     * 的底色分层，不再画横贯分隔线，圆角卡片也就不会出现扎出轮廓的直线。
     */
    private static void drawCardBody(GuiGraphics graphics, @Nullable ConditionNode node, UiRect rect,
                                     int bandColor, int bodyColor, int borderColor) {
        drawShape(graphics, node, rect, 0, rect.height(), borderColor);
        UiRect inner = new UiRect(rect.x() + BORDER, rect.y() + BORDER,
                rect.width() - BORDER * 2, rect.height() - BORDER * 2);
        int bandRows = Math.min(inner.height(), TITLE_BAND_HEIGHT);
        drawShape(graphics, node, inner, 0, bandRows, bandColor);
        drawShape(graphics, node, inner, bandRows, inner.height(), bodyColor);
    }

    /**
     * 按行扫描绘制卡片剪影，只填 [fromRow, toRow) 这段高度：整卡描边、标题带切片和正文切片都走
     * 同一个函数，圆角与斜切在各层之间保持一致。
     */
    private static void drawShape(GuiGraphics graphics, @Nullable ConditionNode node, UiRect rect,
                                  int fromRow, int toRow, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        int maxRadius = Math.min(rect.width(), rect.height()) / 2;
        boolean chamfer = node instanceof ConditionNode.Inverted;
        int radius;
        if (chamfer) {
            radius = Math.min(LOGIC_CUT_CORNER, maxRadius);
        } else if (node instanceof ConditionNode.AnyOf) {
            radius = Math.min(LOGIC_ROUND_MAX_RADIUS, maxRadius);
        } else {
            radius = Math.min(CARD_CORNER_RADIUS, maxRadius);
        }
        int top = Math.clamp(fromRow, 0, rect.height());
        int bottom = Math.clamp(toRow, 0, rect.height());
        int runTop = top;
        int runInset = shapeInset(top, rect.height(), radius, chamfer);
        for (int row = top + 1; row <= bottom; row++) {
            int inset = row < bottom ? shapeInset(row, rect.height(), radius, chamfer) : -1;
            if (inset != runInset) {
                if (runInset >= 0) {
                    graphics.fill(rect.x() + runInset, rect.y() + runTop,
                            rect.right() - runInset, rect.y() + row, color);
                }
                runTop = row;
                runInset = inset;
            }
        }
    }

    // 某一扫描行的左右缩进：斜切卡走直线，圆角卡走圆弧
    private static int shapeInset(int row, int height, int radius, boolean chamfer) {
        int edge = Math.min(row, height - row - 1);
        if (edge >= radius) {
            return 0;
        }
        if (chamfer) {
            return radius - edge;
        }
        double dy = radius - edge - 0.5D;
        return (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
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
     * 绘制卡片标题带文字：标题水平居中并按 TITLE_TEXT_SCALE 放大一号，结构问题只在右上角留一个
     * 颜色标记（具体原因看悬停提示），两行内容区都留给参数文本。
     */
    private void drawCardTitle(GuiGraphics graphics, Font renderFont, String text, int textWidth, UiRect rect,
                               int titleColor, int markColor) {
        int reserve = markColor == NO_MARK ? 0 : MARK_RESERVE;
        int available = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2 - reserve);
        int left = rect.x() + BORDER + CARD_PADDING_X;
        int x = left + (available - textWidth) / 2;
        int y = rect.y() + TITLE_BAND_TOP;
        // 位图字体没有字号参数：平移到位后整体缩放，屏幕像素按最近邻取样
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 0F);
        graphics.pose().scale(TITLE_TEXT_SCALE, TITLE_TEXT_SCALE, 1F);
        graphics.drawString(renderFont, text, 0, 0, titleColor, false);
        graphics.pose().popPose();
        if (markColor != NO_MARK) {
            graphics.drawString(renderFont, MARK_TEXT,
                    rect.right() - BORDER - CARD_PADDING_X - markWidth,
                    y, markColor, false);
        }
    }

    // 标题带右上角的标记颜色：必须修的错误用警示色，只是提示（层级较深）用次要色，无问题不留标记
    private int markColor(ConditionNode node) {
        if (!graph.hasStatus(node)) {
            return NO_MARK;
        }
        return graph.statusSevere(node) ? theme.palette().canvasTextDanger() : theme.palette().canvasTextMuted();
    }

    /**
     * 绘制卡片内容区：把布局给出的文本行按内容宽折行，最多铺满两行——长列表（周围方块、气候
     * 区间）因此能完整读，不再被单行硬裁成半截。容器的运算符释义走次要色，与子项计数分层。
     */
    private void drawSummaryRows(GuiGraphics graphics, Font renderFont, CardText cardText, UiRect rect,
                                 int color, int mutedColor) {
        List<FormattedCharSequence> lines = cardText.lines();
        boolean container = cardText.container();
        for (int row = 0; row < lines.size(); row++) {
            int rowColor = container && row > 0 ? mutedColor : color;
            graphics.drawString(renderFont, lines.get(row), rect.x() + BORDER + CARD_PADDING_X,
                    rect.y() + CONTENT_TOP + row * CARD_ROW_HEIGHT, rowColor, false);
        }
    }

    // 端点卡片只有一行摘要：正文区垂直居中，主色绘制，让槽位里的值成为主角
    private void drawEndpointSummary(GuiGraphics graphics, Font renderFont, String text, UiRect rect) {
        int y = rect.y() + CONTENT_TOP + (CONTENT_ROWS * CARD_ROW_HEIGHT - renderFont.lineHeight) / 2;
        graphics.drawString(renderFont, text, rect.x() + BORDER + CARD_PADDING_X, y,
                theme.palette().canvasText(), false);
    }

    /**
     * 热区是方形加号 chip：静息态与卡片同族、只留一个强调色加号；悬停才整块换成强调色，
     * 避免整页加号抢走卡片正文的注意力。加号用填色画而不是字形，缩放到 50% 时也不会糊。
     */
    void drawHotspots(GuiGraphics graphics, @Nullable Hotspot hoveredHotspot) {
        for (Hotspot hotspot : graph.hotspots()) {
            UiRect rect = hotspot.rect();
            boolean hovered = hotspot == hoveredHotspot;
            drawChip(graphics, rect,
                    hovered ? theme.palette().canvasAccentHover() : theme.palette().canvasHotspotFill(),
                    hovered ? theme.palette().canvasAccentHover() : theme.palette().canvasHotspotBorder());
            int markColor = hovered ? theme.palette().canvasTextOnAccent() : theme.palette().canvasAccent();
            int arm = Math.max(2, rect.width() / 4);
            int thickness = Math.max(1, rect.width() / 9);
            int centerX = rect.x() + rect.width() / 2;
            int centerY = rect.y() + rect.height() / 2;
            graphics.fill(centerX - arm, centerY - thickness, centerX + arm + 1, centerY + thickness, markColor);
            graphics.fill(centerX - thickness, centerY - arm, centerX + thickness, centerY + arm + 1, markColor);
        }
    }

    // 圆角方形按钮底板：外圈描边色铺满，内圈再铺填充色，留出 1 像素边框
    private static void drawChip(GuiGraphics graphics, UiRect rect, int fill, int border) {
        drawShape(graphics, null, rect, 0, rect.height(), border);
        drawShape(graphics, null, new UiRect(rect.x() + BORDER, rect.y() + BORDER,
                rect.width() - BORDER * 2, rect.height() - BORDER * 2), 0, rect.height() - BORDER * 2, fill);
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
