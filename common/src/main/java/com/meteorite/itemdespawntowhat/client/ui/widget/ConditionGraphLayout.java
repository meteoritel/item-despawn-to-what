package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTidyTreeLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 条件画布的布局与展示数据：集中维护身份索引、诊断、标签和卡片几何。 */
final class ConditionGraphLayout {
    private static final int CARD_MIN_WIDTH = 60;
    private static final int CARD_MAX_WIDTH = 168;
    static final int CARD_PADDING_X = 4;
    static final int CARD_PADDING_Y = 2;
    static final int CARD_ROW_HEIGHT = 10;
    // 卡片内部：标题带 + 1 像素分隔线 + 固定两行内容区，叶与容器共用同一套纵向分区
    static final int TITLE_BAND_HEIGHT = CARD_ROW_HEIGHT;
    static final int CARD_SEPARATOR = 1;
    static final int CONTENT_ROWS = 2;
    static final int CARD_HEIGHT = CARD_PADDING_Y * 2 + TITLE_BAND_HEIGHT + CARD_SEPARATOR
            + CONTENT_ROWS * CARD_ROW_HEIGHT;
    static final int BORDER = 1;
    // 卡片内部各分区的起点：相对卡片顶边，描边与上内边距之后依次是标题带、分隔线和内容区
    static final int TITLE_BAND_TOP = BORDER + CARD_PADDING_Y;
    static final int CONTENT_TOP = TITLE_BAND_TOP + TITLE_BAND_HEIGHT + CARD_SEPARATOR;
    private static final int LEVEL_PITCH = CARD_HEIGHT + 18;
    private static final int SIBLING_GAP = 12;
    static final int LOGIC_CUT_CORNER = 6;
    private static final int ENDPOINT_GAP = 32;
    private static final int HOTSPOT_SIZE = 12;
    private static final int HOTSPOT_LARGE_SIZE = 22;
    static final String FLOW_HINT_KEY = "gui.itemdespawntowhat.edit.tree.flow_hint";

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

    record NodeInfo(boolean incomplete, boolean overflow, boolean deep,
                    boolean leafMissing, boolean subtreeIssue) {}

    private static final class NodeCounts {
        private int nodes;
        private int leaves;
    }

    private final Font font;
    private final UiTidyTreeLayout<GraphNode> layout = new UiTidyTreeLayout<>(LEVEL_PITCH, SIBLING_GAP);
    private final LayoutSource layoutSource = new LayoutSource(this);
    private final EndpointSlot sourceSlot = new EndpointSlot("gui.itemdespawntowhat.edit.tree.node.source");
    private final EndpointSlot resultSlot = new EndpointSlot("gui.itemdespawntowhat.edit.tree.node.result");
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

    private ConditionExpression expression = ConditionExpression.EMPTY;
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    private @Nullable Function<ConditionNode.Leaf, Component> leafSummary;
    private final List<ConditionNode> nodeView = java.util.Collections.unmodifiableList(order);
    private final List<Hotspot> hotspotView = java.util.Collections.unmodifiableList(hotspots);

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
    Component sourceTitle() { return sourceSlot.title; }
    Component sourceSummary() { return sourceSlot.summary; }
    Component resultTitle() { return resultSlot.title; }
    Component resultSummary() { return resultSlot.summary; }
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
        return true;
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
        layout.layout(layoutRoot, layoutSource);
        layoutEndpoints(layoutRoot);
        collectHotspots();
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
        if (isContainer(node) && !children.isEmpty()
                && ConditionTreeNodes.INSTANCE.childCapacity(node) > children.size()) {
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
            if (container == null) {
                hotspots.add(new Hotspot(null, 0, centered(cardRect(slot))));
                continue;
            }
            UiTreePath parent = paths.get(container);
            if (parent == null) {
                continue;
            }
            hotspots.add(new Hotspot(parent, ConditionTreeNodes.INSTANCE.children(container).size(), cardRect(slot)));
        }
        for (NodeSlot wrapper : graphNodes) {
            ConditionNode node = wrapper.node;
            if (!isContainer(node) || !ConditionTreeNodes.INSTANCE.children(node).isEmpty()) {
                continue;
            }
            UiTreePath parent = paths.get(node);
            if (parent == null) {
                continue;
            }
            hotspots.add(new Hotspot(parent, 0, centered(cardRect(wrapper))));
        }
    }

    private static UiRect centered(UiRect card) {
        return new UiRect(card.x() + (card.width() - HOTSPOT_LARGE_SIZE) / 2,
                card.y() + (card.height() - HOTSPOT_LARGE_SIZE) / 2, HOTSPOT_LARGE_SIZE, HOTSPOT_LARGE_SIZE);
    }

    private int cardWidth(ConditionNode node) {
        Integer cached = cardWidths.get(node);
        if (cached != null) {
            return cached;
        }
        int measured = Math.max(Math.max(font.width(titleLabel(node).getString()),
                        font.width(summaryLabel(node).getString())), font.width(statusLabel(node).getString()))
                + (CARD_PADDING_X + BORDER) * 2;
        if (isContainer(node)) {
            measured = Math.max(measured, font.width(operatorLabel(node)) + LOGIC_CUT_CORNER * 2);
        }
        int width = Math.clamp(measured, CARD_MIN_WIDTH, CARD_MAX_WIDTH);
        cardWidths.put(node, width);
        return width;
    }

    private int endpointWidth(EndpointSlot endpoint) {
        int measured = Math.max(font.width(endpoint.title), font.width(endpoint.summary))
                + (CARD_PADDING_X + BORDER) * 2;
        return Math.clamp(measured, CARD_MIN_WIDTH, CARD_MAX_WIDTH);
    }

    private UiRect cardRect(GraphNode wrapper) {
        return new UiRect(treeOriginX + layout.x(wrapper), treeOriginY + layout.y(wrapper),
                layout.widthOf(wrapper), CARD_HEIGHT);
    }

    /**
     * 第一行文案：叶为类型名（只读参数另附标记），容器为容器语义；结构标记已由
     * {@link #statusLabel(ConditionNode)} 独立成行，不再拼进标题，标题带才能整体居中。
     */
    Component row1Label(ConditionNode node) {
        Component label = switch (node) {
            case ConditionNode.AllOf ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.all");
            case ConditionNode.AnyOf ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.any");
            case ConditionNode.Inverted ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.not");
            case ConditionNode.Leaf(var condition) -> condition == null
                    ? Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing")
                    : typeLabel(condition.type());
        };
        if (node instanceof ConditionNode.Leaf(var condition) && condition instanceof OpaqueCondition) {
            label = label.copy().append(" ").append(Component.translatable(
                    "gui.itemdespawntowhat.edit.tree.parameters_readonly"));
        }
        return label;
    }

    // 标题带文案：容器用运算符与本地化含义，叶沿用第一行
    Component titleLabel(ConditionNode node) {
        return isContainer(node) ? operatorLabel(node) : row1Label(node);
    }

    // 内容行 1：叶为参数摘要，容器为容器语义（全部满足 / 任一满足 / 取反）
    Component summaryLabel(ConditionNode node) {
        if (isContainer(node)) {
            return row1Label(node);
        }
        if (node instanceof ConditionNode.Leaf(var condition)) {
            if (condition == null) {
                return Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing");
            }
            return leafSummaries.getOrDefault(node, typeLabel(condition.type()));
        }
        return Component.empty();
    }

    // 内容行 2：只服务容器的分支计数，叶没有第二行内容
    Component row2Label(ConditionNode node) {
        if (!isContainer(node)) {
            return Component.empty();
        }
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        if (children.isEmpty()) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.empty");
        }
        return Component.translatable("gui.itemdespawntowhat.edit.tree.drag_many", children.size());
    }

    // 内容行 2（叶）：结构标记，无问题时为空。超限优先于层级较深，避免同一行堆叠两个标记
    Component statusLabel(ConditionNode node) {
        NodeInfo info = infos.get(node);
        if (info == null) {
            return Component.empty();
        }
        if (info.incomplete()) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.incomplete");
        }
        if (info.overflow()) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.overflow_mark");
        }
        if (info.deep()) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.deep_mark");
        }
        return Component.empty();
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
            return Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing");
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
            case ConditionNode.AllOf ignored -> "gui.itemdespawntowhat.edit.tree.operator.all";
            case ConditionNode.AnyOf ignored -> "gui.itemdespawntowhat.edit.tree.operator.any";
            case ConditionNode.Inverted ignored -> "gui.itemdespawntowhat.edit.tree.operator.not";
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
            if (hotspot.rect().contains(localX, localY)) {
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
