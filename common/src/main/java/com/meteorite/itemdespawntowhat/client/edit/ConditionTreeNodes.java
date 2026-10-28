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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * 深拷贝节点树：组合节点逐层重建，叶节点重建为新对象、复用条件参数。
     *
     * <p>副本不与原树共享节点身份，可安全插入同一棵布局树；条件参数按不可变对象处理。</p>
     *
     * @param node 待复制的节点，可为 {@code null}
     * @return 结构相同的独立副本；入参为 {@code null} 时返回 {@code null}
     */
    public static @Nullable ConditionNode copy(@Nullable ConditionNode node) {
        if (node == null) {
            return null;
        }
        return switch (node) {
            case ConditionNode.AllOf all -> new ConditionNode.AllOf(copyTerms(all.terms()));
            case ConditionNode.AnyOf any -> new ConditionNode.AnyOf(copyTerms(any.terms()));
            case ConditionNode.Inverted not -> new ConditionNode.Inverted(copy(not.term()));
            case ConditionNode.Leaf leaf -> new ConditionNode.Leaf(leaf.condition());
        };
    }

    private static List<ConditionNode> copyTerms(List<ConditionNode> terms) {
        List<ConditionNode> copies = new ArrayList<>(terms.size());
        for (ConditionNode term : terms) {
            copies.add(Objects.requireNonNull(copy(term)));
        }
        return copies;
    }

    /** 节点结构地址与可选参数后缀；nodePath 使用调用方传入的作用域。 */
    public record Address(UiTreePath path, String nodePath, @Nullable String parameterSuffix) {}

    /** 完整段解析节点地址，参数字段仅允许从 .condition 段开始，空槽路径同样可表达。 */
    public static @Nullable Address address(@Nullable String jsonPath, String scope) {
        if (jsonPath == null || !(jsonPath.equals(scope) || jsonPath.startsWith(scope + "."))) return null;
        int cursor = scope.length();
        List<Integer> indices = new ArrayList<>();
        while (cursor < jsonPath.length()) {
            String condition = "." + RuleFields.CONDITION;
            if (jsonPath.startsWith(condition, cursor)) {
                int end = cursor + condition.length();
                if (end != jsonPath.length() && jsonPath.charAt(end) != '.' && jsonPath.charAt(end) != '[') return null;
                return new Address(new UiTreePath(indices), jsonPath.substring(0, cursor), jsonPath.substring(cursor));
            }
            String terms = "." + RuleFields.TERMS + "[";
            String term = "." + RuleFields.TERM;
            if (jsonPath.startsWith(terms, cursor)) {
                int begin = cursor + terms.length();
                int end = jsonPath.indexOf(']', begin);
                if (end <= begin) return null;
                for (int i = begin; i < end; i++) if (jsonPath.charAt(i) < '0' || jsonPath.charAt(i) > '9') return null;
                try { indices.add(Integer.parseInt(jsonPath.substring(begin, end))); }
                catch (NumberFormatException invalid) { return null; }
                cursor = end + 1;
            } else if (jsonPath.startsWith(term, cursor)) {
                cursor += term.length();
                if (cursor < jsonPath.length() && jsonPath.charAt(cursor) != '.') return null;
                indices.add(0);
            } else return null;
        }
        return new Address(new UiTreePath(indices), jsonPath, null);
    }

    /** 树内纯节点路径；参数后缀通过 address 单独读取。 */
    public static @Nullable UiTreePath path(@Nullable String jsonPath) {
        Address address = address(jsonPath, RuleFields.CONDITIONS);
        return address == null || address.parameterSuffix() != null ? null : address.path();
    }

    /** 按模型节点形态把位置路径还原为 JSON 地址，缺失槽位保留可定位的结构段。 */
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
        Set<String> removed = change.removed().stream().map(path -> jsonPath(change.before(), path, scope))
                .collect(Collectors.toSet());
        Set<String> added = change.added().stream().map(path -> jsonPath(change.after(), path, scope))
                .collect(Collectors.toSet());
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
            String childPath = node instanceof ConditionNode.Inverted ? DraftPaths.child(path, RuleFields.TERM)
                    : DraftPaths.indexPath(DraftPaths.child(path, RuleFields.TERMS), index);
            collect(children.get(index), childPath, paths);
        }
    }
}
