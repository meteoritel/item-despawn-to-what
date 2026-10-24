package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTidyTreeLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** 条件画布的布局与展示数据：集中维护身份索引、诊断、标签和卡片几何。 */
final class ConditionGraphLayout {
    private static final int CARD_MIN_WIDTH = 64;
    private static final int CARD_MAX_WIDTH = 176;
    // 卡片 1 像素描边：文本位置恒按它计算，选中时改为外环，避免内容位移
    static final int BORDER = 1;
    static final int CARD_PADDING_X = 5;
    // 卡片纵向内边距：标题带之上与内容区之下各留一段，文字不贴描边
    static final int CARD_PADDING_Y = 2;
    // 标题带的上内边距
    static final int TITLE_BAND_PADDING_Y = CARD_PADDING_Y;
    // 卡片底部内边距：内容行不贴下边框
    static final int CARD_BOTTOM_PADDING = 2;
    static final int CARD_ROW_HEIGHT = 11;
    // 卡片内部：标题带 + 固定两行内容区，叶与容器共用同一套纵向分区；标题带按放大后的标题字高留白
    static final int TITLE_BAND_HEIGHT = 16;
    static final int CONTENT_ROWS = 2;
    // 标题带与内容区之间的空隙：用底色分层之后不再需要 1 像素分隔线
    static final int BAND_CONTENT_GAP = 2;
    static final int CARD_HEIGHT = BORDER * 2 + TITLE_BAND_HEIGHT + BAND_CONTENT_GAP
            + CONTENT_ROWS * CARD_ROW_HEIGHT + CARD_BOTTOM_PADDING;
    // 卡片内部各分区的起点：相对卡片顶边，描边之后是标题带、空隙和内容区
    static final int TITLE_BAND_TOP = BORDER + TITLE_BAND_PADDING_Y;
    // 标题字号放大一号：位图字体没有字号参数，靠 pose 缩放绘制；测宽与绘制共用同一个比例
    static final float TITLE_TEXT_SCALE = 1.25F;
    static final int CONTENT_TOP = BORDER + TITLE_BAND_HEIGHT + BAND_CONTENT_GAP;
    private static final int LEVEL_PITCH = CARD_HEIGHT + 18;
    private static final int SIBLING_GAP = 12;
    static final int LOGIC_CUT_CORNER = 6;
    // 圆角半径：矩形卡统一 3 像素，宽卡（AnyOf）按高度对半但封顶，避免半圆裁掉两端的文字
    static final int CARD_CORNER_RADIUS = 3;
    static final int LOGIC_ROUND_MAX_RADIUS = 10;
    private static final int ENDPOINT_GAP = 32;
    static final int HOTSPOT_SIZE = 14;
    static final int HOTSPOT_LARGE_SIZE = 28;
    // 热区命中范围向外放宽的像素：chip 画得紧凑，但点击要够大（50% 缩放下仍有 12 屏幕像素）
    static final int HOTSPOT_HIT_PADDING = 5;
    // 标题带右上角结构标记的预留宽度：测宽时一并计入，标记出现时不挤掉标题文字
    static final int MARK_RESERVE = 8;
    static final String FLOW_HINT_KEY = TypeLabels.UI_PREFIX + "tree.flow_hint";
    // 流动提示文字与虚线之间的空隙：文字底下还要铺一块画布底色遮断虚线
    static final int FLOW_HINT_GAP = 2;

    // 布局用包装节点：节点身份即对象身份，不依赖 ConditionNode 的值相等。
    private abstract static class GraphNode {
        List<GraphNode> children = List.of();
    }

    // 真实条件节点
    private static final class NodeSlot extends GraphNode {
        private final ConditionNode node;

        NodeSlot(ConditionNode node) {
            this.node = node;
        }
    }

    // 容器末尾为 [+] 预留的槽位
    private static final class HotspotSlot extends GraphNode {
        private final @Nullable ConditionNode container;

        HotspotSlot(@Nullable ConditionNode container) {
            this.container = container;
        }
    }

    // 只读链路端点，不参与条件表达式、选择、结构操作与限额统计。
    private static final class EndpointSlot {
        private final Component title;
        private Component summary = Component.empty();

        EndpointSlot(String titleKey) {
            title = Component.translatable(titleKey);
        }
    }

    // 布局数据源：卡片宽度由文本测量得到，[+] 槽位按固定边长占位
    private record LayoutSource(ConditionGraphLayout graph) implements UiTidyTreeLayout.Source<GraphNode> {
        @Override
        public List<GraphNode> children(GraphNode node) {
            return node.children;
        }

        @Override
        public int width(GraphNode node) {
            if (node instanceof HotspotSlot hotspot) {
                return hotspot.container == null ? HOTSPOT_LARGE_SIZE : HOTSPOT_SIZE;
            }
            return graph.cardWidth(((NodeSlot) node).node);
        }
    }

    // 单个可添加位置：parent 为 null 表示空树（作为新根写入）
    record Hotspot(@Nullable UiTreePath parent, int index, UiRect rect) {}

    // 卡片文本快照：标题已按卡片宽裁剪、内容行已折行，绘制直接取用
    record CardText(String title, int titleWidth, boolean container, List<FormattedCharSequence> lines) {}

    record NodeInfo(boolean incomplete, boolean overflow, boolean deep,
                    boolean leafMissing, boolean subtreeIssue) {}

    private static final class NodeCounts {
        private int nodes;
        private int leaves;
    }

    private final Font font;
    private final UiTidyTreeLayout<GraphNode> layout = new UiTidyTreeLayout<>(LEVEL_PITCH, SIBLING_GAP);
    private final LayoutSource layoutSource = new LayoutSource(this);
    private final EndpointSlot sourceSlot = new EndpointSlot(TypeLabels.UI_PREFIX + "tree.node.source");
    private final EndpointSlot resultSlot = new EndpointSlot(TypeLabels.UI_PREFIX + "tree.node.result");
    private UiRect sourceRect = new UiRect(0, 0, 0, 0);
    private UiRect resultRect = new UiRect(0, 0, 0, 0);
    private int treeOriginX;
    private int treeOriginY;
    // 每轮重建得到的布局数据；键全部按对象身份匹配
    private final List<NodeSlot> graphNodes = new ArrayList<>();
    private final List<HotspotSlot> hotspotSlots = new ArrayList<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<ConditionNode, GraphNode> wrappers = new IdentityHashMap<>();
    private final Map<ConditionNode, UiTreePath> paths = new IdentityHashMap<>();
    // 路径 → 节点反查：内联框锚点与菜单定位使用
    private final Map<UiTreePath, ConditionNode> nodesByPath = new HashMap<>();
    private final Map<ConditionNode, NodeInfo> infos = new IdentityHashMap<>();
    private final Map<ConditionNode, Component> leafSummaries = new IdentityHashMap<>();
    private final Map<ConditionNode, Integer> cardWidths = new IdentityHashMap<>();
    // 树序（深度优先前序），Shift 范围选择与键盘导航使用
    private final List<ConditionNode> order = new ArrayList<>();
    private final List<ConditionTreeInspection.Issue> issues = new ArrayList<>();
    // 标题与内容行文案：本地化文本的构造与折行成本较高，按节点身份缓存到下一轮布局/语言变化
    private final Map<ConditionNode, Component> titleLabels = new IdentityHashMap<>();
    private final Map<ConditionNode, List<Component>> summaryRowCache = new IdentityHashMap<>();
    private final Map<ConditionNode, CardText> cardTexts = new IdentityHashMap<>();
    // 当前布局根：语言变化时用它就地重排，不重建表达式扫描结果
    private @Nullable GraphNode layoutRoot;
    // 文本快照依赖的字体与语言版本：变化后必须重排并重算文本
    private @Nullable Font textFont;
    private @Nullable Object textLanguage;
    private String flowHintText = "";
    private int flowHintTextWidth;
    private String sourceTitleText = "";
    private int sourceTitleWidth;
    private String resultTitleText = "";
    private int resultTitleWidth;
    private String sourceSummaryText = "";
    private String resultSummaryText = "";

    private ConditionExpression expression = ConditionExpression.EMPTY;
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    private @Nullable Function<ConditionNode.Leaf, Component> leafSummary;
    private final List<ConditionNode> nodeView = Collections.unmodifiableList(order);
    private final List<Hotspot> hotspotView = Collections.unmodifiableList(hotspots);

    ConditionGraphLayout(Font font) { this.font = font; }

    List<ConditionNode> nodes() { return nodeView; }
    List<Hotspot> hotspots() { return hotspotView; }
    List<ConditionTreeInspection.Issue> issues() { return List.copyOf(issues); }
    boolean hasIssues() { return !issues.isEmpty(); }
    @Nullable UiTreePath path(ConditionNode node) { return paths.get(node); }
    @Nullable ConditionNode nodeAtPath(UiTreePath path) { return nodesByPath.get(path); }
    @Nullable ConditionNode root() { return expression.root(); }
    UiRect sourceRect() { return sourceRect; }
    UiRect resultRect() { return resultRect; }
    int contentWidth() { return resultRect.right(); }
    int contentHeight() { return Math.max(treeOriginY + layout.contentHeight(), resultRect.bottom()); }

    UiRect rootRect() {
        return expression.root() == null ? hotspots.getFirst().rect() : rect(expression.root());
    }

    @Nullable UiRect rect(ConditionNode node) {
        GraphNode wrapper = wrappers.get(node);
        return wrapper == null ? null : cardRect(wrapper);
    }

    boolean setEndpoints(Component sourceSummary, Component resultSummary) {
        if (sourceSlot.summary.equals(sourceSummary) && resultSlot.summary.equals(resultSummary)) return false;
        sourceSlot.summary = sourceSummary;
        resultSlot.summary = resultSummary;
        // 端点宽度与摘要文本都取自 summary：先按新值重排端点，再刷新文本快照
        if (layoutRoot != null) {
            layoutEndpoints(layoutRoot);
            hotspots.clear();
            collectHotspots();
        }
        refreshTexts();
        return true;
    }

    // 端点标题/摘要与流动提示的裁剪结果：绘制路径只读这段文本，不再每帧构造 Component 并裁剪
    String sourceTitleText() { return sourceTitleText; }
    int sourceTitleWidth() { return sourceTitleWidth; }
    String resultTitleText() { return resultTitleText; }
    int resultTitleWidth() { return resultTitleWidth; }
    String sourceSummaryText() { return sourceSummaryText; }
    String resultSummaryText() { return resultSummaryText; }
    String flowHintText() { return flowHintText; }
    int flowHintTextWidth() { return flowHintTextWidth; }

    /**
     * 语言或字体变化会改变本地化文案与测宽结果：此时就地重排并刷新全部文本快照。
     * 绘制路径每帧都会调用，但只有依赖真正变化时才付出重排成本。
     */
    void ensureText() {
        if (Language.getInstance() == textLanguage && font == textFont) {
            return;
        }
        textLanguage = Language.getInstance();
        textFont = font;
        titleLabels.clear();
        summaryRowCache.clear();
        cardWidths.clear();
        refillLeafSummaries();
        GraphNode root = layoutRoot;
        if (root != null) {
            layout.layout(root, layoutSource);
            layoutEndpoints(root);
            hotspots.clear();
            collectHotspots();
        }
        refreshTexts();
    }

    // 叶参数摘要由宿主回调生成，语言变化后需要重跑一遍；节点身份与树序不变
    private void refillLeafSummaries() {
        leafSummaries.clear();
        if (leafSummary == null) {
            return;
        }
        for (ConditionNode node : order) {
            if (node instanceof ConditionNode.Leaf leaf && leaf.condition() != null) {
                leafSummaries.put(node, leafSummary.apply(leaf));
            }
        }
    }

    // 文本快照统一在布局完成后重算：卡片文本按需惰性补算，端点与流动提示一次算完
    private void refreshTexts() {
        cardTexts.clear();
        flowHintText = "";
        flowHintTextWidth = 0;
        GraphNode root = layoutRoot;
        if (expression.root() != null && root != null) {
            int width = Math.max(0, resultRect.x() - cardRect(root).right() - FLOW_HINT_GAP * 6);
            flowHintText = TextScroll.trimToWidth(font, Component.translatable(FLOW_HINT_KEY).getString(), width);
            flowHintTextWidth = font.width(flowHintText);
        }
        sourceTitleText = endpointTitleText(sourceSlot, sourceRect);
        sourceTitleWidth = Math.round(font.width(sourceTitleText) * TITLE_TEXT_SCALE);
        resultTitleText = endpointTitleText(resultSlot, resultRect);
        resultTitleWidth = Math.round(font.width(resultTitleText) * TITLE_TEXT_SCALE);
        sourceSummaryText = endpointSummaryText(sourceSlot, sourceRect);
        resultSummaryText = endpointSummaryText(resultSlot, resultRect);
    }

    private String endpointTitleText(EndpointSlot endpoint, UiRect rect) {
        int available = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2);
        return TextScroll.trimToWidth(font, endpoint.title.getString(),
                Math.max(0, (int) (available / TITLE_TEXT_SCALE)));
    }

    private String endpointSummaryText(EndpointSlot endpoint, UiRect rect) {
        return TextScroll.trimToWidth(font, endpoint.summary.getString(),
                Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2));
    }

    // 卡片标题裁剪结果与内容行折行结果：同一轮布局内不变，按节点身份缓存
    CardText cardText(ConditionNode node) {
        CardText cached = cardTexts.get(node);
        if (cached != null) {
            return cached;
        }
        UiRect rect = rect(node);
        if (rect == null) {
            return new CardText("", 0, isContainer(node), List.of());
        }
        int reserve = hasStatus(node) ? MARK_RESERVE : 0;
        int available = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2 - reserve);
        String title = TextScroll.trimToWidth(font, titleLabel(node).getString(),
                Math.max(0, (int) (available / TITLE_TEXT_SCALE)));
        int titleWidth = Math.round(font.width(title) * TITLE_TEXT_SCALE);
        int contentWidth = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2);
        CardText text = new CardText(title, titleWidth, isContainer(node), splitRows(node, contentWidth));
        cardTexts.put(node, text);
        return text;
    }

    // 内容行按卡片内容宽折行，最多两行；空文本行跳过，与绘制时的可见行计数一致
    private List<FormattedCharSequence> splitRows(ConditionNode node, int width) {
        if (width == 0) {
            return List.of();
        }
        List<FormattedCharSequence> lines = new ArrayList<>(CONTENT_ROWS);
        for (Component content : summaryRows(node)) {
            if (content.getString().isEmpty()) {
                continue;
            }
            for (FormattedCharSequence line : font.split(content, width)) {
                if (lines.size() >= CONTENT_ROWS) {
                    return List.copyOf(lines);
                }
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    // 标题带是否要留结构标记：与 statusLabel 的非空判定保持一致
    boolean hasStatus(ConditionNode node) {
        NodeInfo info = infos.get(node);
        return info != null && (info.incomplete() || info.overflow() || info.deep());
    }

    void rebuild(ConditionExpression expression, List<ConditionTreeContract.TypeOption> options,
                 @Nullable Function<ConditionNode.Leaf, Component> leafSummary) {
        this.expression = expression;
        this.typeOptions = options;
        this.leafSummary = leafSummary;
        clearLayout();
        ConditionNode root = expression.root();
        GraphNode layoutRoot;
        if (root == null) {
            HotspotSlot empty = new HotspotSlot(null);
            hotspotSlots.add(empty);
            layoutRoot = empty;
        } else {
            inspect(root, UiTreePath.ROOT, 1, new NodeCounts());
            layoutRoot = wrap(root);
        }
        this.layoutRoot = layoutRoot;
        layout.layout(layoutRoot, layoutSource);
        layoutEndpoints(layoutRoot);
        collectHotspots();
        refreshTexts();
    }

    private void clearLayout() {
        graphNodes.clear();
        hotspotSlots.clear();
        hotspots.clear();
        wrappers.clear();
        paths.clear();
        nodesByPath.clear();
        infos.clear();
        leafSummaries.clear();
        cardWidths.clear();
        order.clear();
        issues.clear();
        titleLabels.clear();
        summaryRowCache.clear();
        cardTexts.clear();
        layoutRoot = null;
    }

    private void layoutEndpoints(GraphNode layoutRoot) {
        treeOriginX = endpointWidth(sourceSlot) + ENDPOINT_GAP;
        treeOriginY = font.lineHeight + CARD_PADDING_Y * 2;
        UiRect rootRect = cardRect(layoutRoot);
        sourceRect = new UiRect(0, rootRect.y(), endpointWidth(sourceSlot), CARD_HEIGHT);
        int resultX = treeOriginX + layout.contentWidth() + ENDPOINT_GAP;
        if (expression.root() != null) {
            resultX = Math.max(resultX, rootRect.right()
                    + font.width(Component.translatable(FLOW_HINT_KEY)) + CARD_PADDING_X * 2);
        }
        resultRect = new UiRect(resultX, rootRect.y(), endpointWidth(resultSlot), CARD_HEIGHT);
    }

    private boolean inspect(ConditionNode node, UiTreePath path, int depth, NodeCounts counters) {
        int nodeOrdinal = ++counters.nodes;
        int leafOrdinal = -1;
        if (node instanceof ConditionNode.Leaf) {
            leafOrdinal = ++counters.leaves;
        }
        paths.put(node, path);
        nodesByPath.put(path, node);
        order.add(node);
        if (node instanceof ConditionNode.Leaf leaf && leaf.condition() != null && leafSummary != null) {
            leafSummaries.put(node, leafSummary.apply(leaf));
        }
        boolean incomplete = ConditionTreeInspection.isIncomplete(node);
        boolean overflow = ConditionTreeInspection.isOverflow(depth, nodeOrdinal, leafOrdinal);
        boolean deep = ConditionTreeInspection.isDeep(depth);
        boolean leafMissing = ConditionTreeInspection.isLeafMissing(node);
        // 违规项由共用校验生成：缩进摘要与画布只有一份判定与一套文案
        String jsonPath = ConditionTreeNodes.jsonPath(expression.root(), path, ConditionTreeContract.ROOT_PATH);
        issues.addAll(ConditionTreeInspection.nodeIssues(node, jsonPath, depth, nodeOrdinal, leafOrdinal));
        boolean subtreeIssue = incomplete || leafMissing || overflow;
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        for (int index = 0; index < children.size(); index++) {
            subtreeIssue |= inspect(children.get(index), path.child(index), depth + 1, counters);
        }
        infos.put(node, new NodeInfo(incomplete, overflow, deep, leafMissing, subtreeIssue));
        return subtreeIssue;
    }

    private NodeSlot wrap(ConditionNode node) {
        NodeSlot wrapper = new NodeSlot(node);
        wrappers.put(node, wrapper);
        graphNodes.add(wrapper);
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        List<GraphNode> wrapped = new ArrayList<>(children.size() + 1);
        for (ConditionNode child : children) {
            wrapped.add(wrap(child));
        }
        if (isContainer(node) && ConditionTreeNodes.INSTANCE.childCapacity(node) > children.size()) {
            HotspotSlot hotspot = new HotspotSlot(node);
            hotspotSlots.add(hotspot);
            wrapped.add(hotspot);
        }
        wrapper.children = List.copyOf(wrapped);
        return wrapper;
    }

    private void collectHotspots() {
        for (HotspotSlot slot : hotspotSlots) {
            ConditionNode container = slot.container;
            UiRect card = cardRect(slot);
            if (container == null) {
                hotspots.add(new Hotspot(null, 0, centered(card, HOTSPOT_LARGE_SIZE)));
                continue;
            }
            UiTreePath parent = paths.get(container);
            if (parent == null) {
                continue;
            }
            hotspots.add(new Hotspot(parent, ConditionTreeNodes.INSTANCE.children(container).size(),
                    centered(card, HOTSPOT_SIZE)));
        }
    }

    // 热区按方形边长在槽位卡片里居中：槽位是窄条，只有居中才像按钮而不是竖白条
    private static UiRect centered(UiRect card, int size) {
        return new UiRect(card.x() + (card.width() - size) / 2,
                card.y() + (card.height() - size) / 2, size, size);
    }

    private int cardWidth(ConditionNode node) {
        Integer cached = cardWidths.get(node);
        if (cached != null) {
            return cached;
        }
        int measured = scaledTitleWidth(titleLabel(node));
        for (Component row : summaryRows(node)) {
            measured = Math.max(measured, font.width(row.getString()));
        }
        measured += (CARD_PADDING_X + BORDER) * 2 + MARK_RESERVE;
        if (isContainer(node)) {
            // 斜切卡的两端尖角会削掉标题带边缘，容器再多留一段斜切宽度
            measured = Math.max(measured, scaledTitleWidth(operatorLabel(node)) + LOGIC_CUT_CORNER * 2);
        }
        int width = Math.clamp(measured, CARD_MIN_WIDTH, CARD_MAX_WIDTH);
        cardWidths.put(node, width);
        return width;
    }

    // 标题按放大后的宽度测：只量原字号会在放大绘制后被裁掉尾部
    private int scaledTitleWidth(Component title) {
        return Math.round(font.width(title.getString()) * TITLE_TEXT_SCALE);
    }

    private int endpointWidth(EndpointSlot endpoint) {
        int measured = Math.max(scaledTitleWidth(endpoint.title), font.width(endpoint.summary))
                + (CARD_PADDING_X + BORDER) * 2;
        return Math.clamp(measured, CARD_MIN_WIDTH, CARD_MAX_WIDTH);
    }

    private UiRect cardRect(GraphNode wrapper) {
        return new UiRect(treeOriginX + layout.x(wrapper), treeOriginY + layout.y(wrapper),
                layout.widthOf(wrapper), CARD_HEIGHT);
    }

    /**
     * 标题带文案：容器为运算符，叶为类型名（只读参数另附标记）。卡片只显示运算符本身，
     * 它的本地化含义不再另起一行，避免同一张卡片把类型名或运算符说两遍。
     */
    Component titleLabel(ConditionNode node) {
        return titleLabels.computeIfAbsent(node,
                key -> isContainer(key) ? operatorLabel(key) : leafTitleLabel(key));
    }

    // 叶标题：类型名是该行唯一内容，参数摘要与结构标记分别占内容行 1 与内容行 2
    private Component leafTitleLabel(ConditionNode node) {
        // 容器走标题带的运算符，不会进到这里
        if (!(node instanceof ConditionNode.Leaf(var condition))) {
            return Component.empty();
        }
        Component label = condition == null
                ? Component.translatable(TypeLabels.UI_PREFIX + "tree.leaf_missing")
                : typeLabel(condition.type());
        if (condition instanceof OpaqueCondition) {
            label = label.copy().append(" ").append(Component.translatable(
                    TypeLabels.UI_PREFIX + "tree.parameters_readonly"));
        }
        return label;
    }

    /** 卡片内容区第一行文本：叶为参数摘要，容器为子项计数；卡片宽度测量与悬停提示共用这一段。 */
    Component summaryLabel(ConditionNode node) {
        if (isContainer(node)) {
            return branchCountLabel(node);
        }
        if (node instanceof ConditionNode.Leaf(var condition)) {
            if (condition == null) {
                return Component.empty();
            }
            return leafSummaries.getOrDefault(node, typeLabel(condition.type()));
        }
        return Component.empty();
    }

    /**
     * 卡片内容区要绘制的文本行（最多两行）：叶把参数摘要按内容宽折行铺满两行；容器第一行给子项
     * 计数，第二行说明运算符自身的意思，让「且 / 或 / 非」不必只靠一个字去猜。
     */
    List<Component> summaryRows(ConditionNode node) {
        return summaryRowCache.computeIfAbsent(node, key -> {
            if (isContainer(key)) {
                return List.of(branchCountLabel(key), operatorHintLabel(key));
            }
            Component summary = summaryLabel(key);
            return summary.getString().isEmpty() ? List.of() : List.of(summary);
        });
    }

    // 容器内容行 1：子项计数，空容器显示「暂无条件」
    private Component branchCountLabel(ConditionNode node) {
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        if (children.isEmpty()) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.empty");
        }
        String key = children.size() == 1
                ? TypeLabels.UI_PREFIX + "tree.card.count.one"
                : TypeLabels.UI_PREFIX + "tree.card.count";
        return key.endsWith(".one")
                ? Component.translatable(key)
                : Component.translatable(key, children.size());
    }

    // 容器内容行 2：运算符的展开说法，与标题带里的单字运算符互为注解
    private Component operatorHintLabel(ConditionNode node) {
        String key = switch (node) {
            case ConditionNode.AllOf ignored -> TypeLabels.UI_PREFIX + "tree.card.hint.all";
            case ConditionNode.AnyOf ignored -> TypeLabels.UI_PREFIX + "tree.card.hint.any";
            default -> TypeLabels.UI_PREFIX + "tree.card.hint.not";
        };
        return Component.translatable(key);
    }

    // 结构问题文案：卡片标题带右上角的标记、悬停提示与缩进摘要共用一套判定。超限优先于层级较深，避免同一节点堆叠两个标记
    Component statusLabel(ConditionNode node) {
        NodeInfo info = infos.get(node);
        if (info == null) {
            return Component.empty();
        }
        if (info.incomplete()) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.incomplete");
        }
        if (info.overflow()) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.overflow_mark");
        }
        if (info.deep()) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.deep_mark");
        }
        return Component.empty();
    }

    /** 结构问题是否属于必须修的错误（不完整 / 超限）；「层级较深」只是提示，按提示色画。 */
    boolean statusSevere(ConditionNode node) {
        NodeInfo info = infos.get(node);
        return info != null && (info.incomplete() || info.overflow());
    }

    /**
     * 节点自身或子树是否存在结构问题：描边取色与悬停提示标题行色共用同一判定。
     */
    boolean hasIssue(ConditionNode node) {
        NodeInfo info = infos.get(node);
        return info != null && info.subtreeIssue();
    }

    private Component typeLabel(@Nullable ResourceLocation type) {
        if (type == null) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.leaf_missing");
        }
        for (ConditionTreeContract.TypeOption option : typeOptions) {
            if (option.id().equals(type)) {
                return option.label();
            }
        }
        return TypeLabels.conditionLabel(type);
    }

    Component operatorLabel(ConditionNode node) {
        String key = switch (node) {
            case ConditionNode.AllOf ignored -> TypeLabels.UI_PREFIX + "tree.operator.all";
            case ConditionNode.AnyOf ignored -> TypeLabels.UI_PREFIX + "tree.operator.any";
            case ConditionNode.Inverted ignored -> TypeLabels.UI_PREFIX + "tree.operator.not";
            default -> throw new IllegalArgumentException("Not a logic node");
        };
        return Component.translatable(key);
    }

    @Nullable ConditionNode nodeAt(double localX, double localY) {
        for (int index = graphNodes.size() - 1; index >= 0; index--) {
            NodeSlot wrapper = graphNodes.get(index);
            if (cardRect(wrapper).contains(localX, localY)) {
                return wrapper.node;
            }
        }
        return null;
    }

    @Nullable Hotspot hotspotAt(double localX, double localY) {
        for (Hotspot hotspot : hotspots) {
            UiRect rect = hotspot.rect();
            if (localX >= rect.x() - HOTSPOT_HIT_PADDING && localX < rect.right() + HOTSPOT_HIT_PADDING
                    && localY >= rect.y() - HOTSPOT_HIT_PADDING && localY < rect.bottom() + HOTSPOT_HIT_PADDING) {
                return hotspot;
            }
        }
        return null;
    }

    @Nullable ConditionNode nodeForJsonPath(String path) {
        ConditionNode best = null;
        int bestLength = -1;
        for (Map.Entry<ConditionNode, UiTreePath> entry : paths.entrySet()) {
            String candidate = ConditionTreeNodes.jsonPath(expression.root(), entry.getValue(),
                    ConditionTreeContract.ROOT_PATH);
            if (!path.equals(candidate) && !path.startsWith(candidate + ".")) {
                continue;
            }
            if (candidate.length() > bestLength) {
                bestLength = candidate.length();
                best = entry.getKey();
            }
        }
        return best;
    }

    int indexOfNode(ConditionNode node) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index) == node) {
                return index;
            }
        }
        return -1;
    }

    static boolean isContainer(@Nullable ConditionNode node) {
        return node instanceof ConditionNode.AllOf || node instanceof ConditionNode.AnyOf
                || node instanceof ConditionNode.Inverted;
    }
}
