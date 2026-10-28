package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeProjection;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 条件展示的结构与诊断快照；画布和摘要消费同一结构版本，几何占位不计入节点。 */
public final class ConditionTreeProjection {
    /** 单节点结构诊断与子树汇总。 */
    public record Info(boolean incomplete, boolean overflow, boolean deep, boolean leafMissing, boolean subtreeIssue) {}
    private final ConditionExpression expression;
    private final UiTreeProjection<ConditionNode> structure;
    private final Map<UiTreePath, Info> infos = new HashMap<>();
    private final Map<UiTreePath, String> jsonPaths = new HashMap<>();
    private final List<ConditionTreeInspection.Issue> issues;

    /** 为不可变表达式构建一次完整结构、地址和诊断索引。 */
    public ConditionTreeProjection(ConditionExpression expression) {
        this.expression = expression;
        structure = UiTreeProjection.single(expression.root(), ConditionTreeNodes.INSTANCE::children);
        List<ConditionTreeInspection.Issue> found = new ArrayList<>();
        int ordinal = 0;
        int leaves = 0;
        for (var entry : structure.entries()) {
            ConditionNode node = entry.node();
            int leaf = node instanceof ConditionNode.Leaf ? ++leaves : -1;
            int depth = entry.depth() + 1;
            String json = ConditionTreeNodes.jsonPath(expression.root(), entry.path(), ConditionTreeContract.ROOT_PATH);
            jsonPaths.put(entry.path(), json);
            boolean incomplete = ConditionTreeInspection.isIncomplete(node);
            boolean missing = ConditionTreeInspection.isLeafMissing(node);
            boolean overflow = ConditionTreeInspection.isOverflow(depth, ++ordinal, leaf);
            infos.put(entry.path(), new Info(incomplete, overflow, ConditionTreeInspection.isDeep(depth), missing,
                    incomplete || missing || overflow));
            found.addAll(ConditionTreeInspection.nodeIssues(node, json, depth, ordinal, leaf));
        }
        for (var entry : structure.entries().reversed()) {
            Info info = infos.get(entry.path());
            if (entry.parent() != null && info.subtreeIssue()) {
                infos.computeIfPresent(entry.parent(), (path, parent) -> new Info(parent.incomplete(), parent.overflow(),
                        parent.deep(), parent.leafMissing(), true));
            }
        }
        issues = List.copyOf(found);
    }
    /** 当前表达式版本。 */
    public ConditionExpression expression() { return expression; }
    /** 通用结构索引，空槽与画布端点不在其中。 */
    public UiTreeProjection<ConditionNode> structure() { return structure; }
    /** 节点诊断；仅接受本投影内的真实节点路径。 */
    public Info info(UiTreePath path) { return infos.get(path); }
    /** 节点的树内 JSON 地址。 */
    public String jsonPath(UiTreePath path) { return jsonPaths.get(path); }
    /** 全树诊断，与折叠状态无关。 */
    public List<ConditionTreeInspection.Issue> issues() { return issues; }
}
