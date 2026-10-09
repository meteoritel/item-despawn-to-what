package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** 条件节点与通用 Kit 树的适配；参数仍由现有条件模型承载。 */
public final class ConditionTreeNodes implements UiTreeEditor.Adapter<ConditionNode> {
    public static final ConditionTreeNodes INSTANCE = new ConditionTreeNodes();
    private ConditionTreeNodes() {}

    @Override
    public List<ConditionNode> children(ConditionNode node) {
        return switch (node) {
            case ConditionNode.AllOf all -> all.terms();
            case ConditionNode.AnyOf any -> any.terms();
            case ConditionNode.Inverted not -> not.term() == null ? List.of() : List.of(not.term());
            case ConditionNode.Leaf ignored -> List.of();
        };
    }

    @Override
    public ConditionNode withChildren(ConditionNode node, List<ConditionNode> children) {
        if (!acceptsChildren(node, children.size())) throw new IllegalArgumentException("Invalid condition child count");
        return switch (node) {
            case ConditionNode.AllOf ignored -> new ConditionNode.AllOf(children);
            case ConditionNode.AnyOf ignored -> new ConditionNode.AnyOf(children);
            case ConditionNode.Inverted ignored -> new ConditionNode.Inverted(children.isEmpty() ? null : children.getFirst());
            case ConditionNode.Leaf ignored -> node;
        };
    }

    @Override
    public int childCapacity(ConditionNode node) {
        return node instanceof ConditionNode.Leaf ? 0 : node instanceof ConditionNode.Inverted ? 1 : UNLIMITED_CHILDREN;
    }

    public static @Nullable UiTreePath path(@Nullable String jsonPath) {
        if (jsonPath == null || !jsonPath.startsWith(RuleFields.CONDITIONS)) return null;
        String suffix = jsonPath.substring(RuleFields.CONDITIONS.length());
        List<Integer> indices = new ArrayList<>();
        while (!suffix.isEmpty()) {
            if (suffix.startsWith("." + RuleFields.TERM) && !suffix.startsWith("." + RuleFields.TERMS)) {
                indices.add(0);
                suffix = suffix.substring(RuleFields.TERM.length() + 1);
            } else if (suffix.startsWith("." + RuleFields.TERMS + "[")) {
                int end = suffix.indexOf(']');
                if (end < 0) return null;
                try { indices.add(Integer.parseInt(suffix.substring(RuleFields.TERMS.length() + 2, end))); }
                catch (IllegalArgumentException invalid) { return null; }
                suffix = suffix.substring(end + 1);
            } else return null;
        }
        return indices.stream().anyMatch(index -> index < 0) ? null : new UiTreePath(indices);
    }

    public static String jsonPath(@Nullable ConditionNode root, UiTreePath path, String scope) {
        StringBuilder result = new StringBuilder(scope);
        ConditionNode current = root;
        for (int index : path.indices()) {
            if (current instanceof ConditionNode.Inverted) result.append('.').append(RuleFields.TERM);
            else result.append('.').append(RuleFields.TERMS).append('[').append(index).append(']');
            List<ConditionNode> children = current == null ? List.of() : INSTANCE.children(current);
            current = index < children.size() ? children.get(index) : null;
        }
        return result.toString();
    }

    /** 显式移动路径覆盖所有原节点；JSON 路径用各自的前后树转换，并与写入处于同一事务。 */
    public static void remapInputs(EditSession session, UiTreeEditor.Change<ConditionNode> change, String scope) {
        if (change.paths().isEmpty() && change.removed().isEmpty() && change.added().isEmpty()) {
            remapInputs(session, change.before(), change.after(), scope);
            return;
        }
        java.util.Set<String> removed = change.removed().stream().map(path -> jsonPath(change.before(), path, scope))
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> added = change.added().stream().map(path -> jsonPath(change.after(), path, scope))
                .collect(java.util.stream.Collectors.toSet());
        session.recordInputStructure(removed, added);
        removed.forEach(session::clearPendingInputs);
        Map<String, String> paths = new LinkedHashMap<>();
        change.paths().forEach((before, after) -> paths.put(jsonPath(change.before(), before, scope),
                jsonPath(change.after(), after, scope)));
        session.remapPendingInputs(paths);
    }

    /** 结构共享节点按对象身份迁移输入，内容相同的条件不会互相混淆。 */
    public static void remapInputs(EditSession session, @Nullable ConditionNode before, @Nullable ConditionNode after, String scope) {
        Map<ConditionNode, String> oldPaths = new IdentityHashMap<>();
        Map<ConditionNode, String> newPaths = new IdentityHashMap<>();
        collect(before, scope, oldPaths);
        collect(after, scope, newPaths);
        Map<String, String> moved = new LinkedHashMap<>();
        oldPaths.forEach((node, previous) -> {
            String next = newPaths.get(node);
            if (next != null && !previous.equals(next)) moved.put(previous, next);
            else if (next == null && node instanceof ConditionNode.Leaf) session.clearPendingInputs(previous);
        });
        if (after == null) session.clearPendingInputs(scope);
        session.remapPendingInputs(moved);
    }

    private static void collect(@Nullable ConditionNode node, String path, Map<ConditionNode, String> paths) {
        if (node == null) return;
        paths.put(node, path);
        List<ConditionNode> children = INSTANCE.children(node);
        for (int index = 0; index < children.size(); index++) {
            String childPath = node instanceof ConditionNode.Inverted ? path + "." + RuleFields.TERM
                    : path + "." + RuleFields.TERMS + "[" + index + "]";
            collect(children.get(index), childPath, paths);
        }
    }
}
