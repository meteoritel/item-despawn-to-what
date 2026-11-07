package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreePath;
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
    public record Address(UiTreePath path, FieldPath nodePath, @Nullable FieldPath parameterSuffix) {}

    /** 完整段解析节点地址，参数字段仅允许从 .condition 段开始，空槽路径同样可表达。 */
    public static @Nullable Address address(@Nullable FieldPath jsonPath, FieldPath scope) {
        if (jsonPath == null || !jsonPath.isWithin(scope)) return null;
        List<FieldPath.Segment> suffix = jsonPath.relativeTo(scope).segments();
        List<Integer> indices = new ArrayList<>();
        int cursor = 0;
        while (cursor < suffix.size()) {
            if (!(suffix.get(cursor) instanceof FieldPath.Key(var name))) return null;
            if (RuleFields.CONDITION.equals(name)) {
                FieldPath node = jsonPath.prefix(scope.depth() + cursor);
                return new Address(new UiTreePath(indices), node, jsonPath.relativeTo(node));
            }
            if (RuleFields.TERMS.equals(name)) {
                if (++cursor == suffix.size() || !(suffix.get(cursor) instanceof FieldPath.Index(var index))) return null;
                indices.add(index);
            } else if (RuleFields.TERM.equals(name)) {
                indices.add(0);
            } else return null;
            cursor++;
        }
        return new Address(new UiTreePath(indices), jsonPath, null);
    }

    /** 树内纯节点路径；参数后缀通过 address 单独读取。 */
    public static @Nullable UiTreePath path(@Nullable FieldPath jsonPath) {
        Address address = address(jsonPath, FieldPath.key(RuleFields.CONDITIONS));
        return address == null || address.parameterSuffix() != null ? null : address.path();
    }

    /** 按模型节点形态把位置路径还原为 JSON 地址，缺失槽位保留可定位的结构段。 */
    public static FieldPath jsonPath(@Nullable ConditionNode root, UiTreePath path, FieldPath scope) {
        FieldPath result = scope;
        ConditionNode current = root;
        for (int index : path.indices()) {
            if (current instanceof ConditionNode.Inverted) result = result.child(RuleFields.TERM);
            else result = result.child(RuleFields.TERMS).index(index);
            List<ConditionNode> children = current == null ? List.of() : INSTANCE.children(current);
            current = index < children.size() ? children.get(index) : null;
        }
        return result;
    }

    /** 显式移动路径覆盖所有原节点；JSON 路径用各自的前后树转换，并与写入处于同一事务。 */
    public static void remapInputs(EditSession session, UiTreeEditor.Change<ConditionNode> change, FieldPath scope) {
        if (change.paths().isEmpty() && change.removed().isEmpty() && change.added().isEmpty()) {
            remapInputs(session, change.before(), change.after(), scope);
            return;
        }
        Set<FieldPath> removed = change.removed().stream().map(path -> jsonPath(change.before(), path, scope))
                .collect(Collectors.toSet());
        Set<FieldPath> added = change.added().stream().map(path -> jsonPath(change.after(), path, scope))
                .collect(Collectors.toSet());
        session.recordInputStructure(removed, added);
        removed.forEach(session::clearPendingInputs);
        Map<FieldPath, FieldPath> paths = new LinkedHashMap<>();
        change.paths().forEach((before, after) -> paths.put(jsonPath(change.before(), before, scope),
                jsonPath(change.after(), after, scope)));
        session.remapPendingInputs(paths);
    }

    /** 结构共享节点按对象身份迁移输入，内容相同的条件不会互相混淆。 */
    public static void remapInputs(EditSession session, @Nullable ConditionNode before, @Nullable ConditionNode after, FieldPath scope) {
        Map<ConditionNode, FieldPath> oldPaths = new IdentityHashMap<>();
        Map<ConditionNode, FieldPath> newPaths = new IdentityHashMap<>();
        collect(before, scope, oldPaths);
        collect(after, scope, newPaths);
        Map<FieldPath, FieldPath> moved = new LinkedHashMap<>();
        oldPaths.forEach((node, previous) -> {
            FieldPath next = newPaths.get(node);
            if (next != null && !previous.equals(next)) moved.put(previous, next);
            else if (next == null && node instanceof ConditionNode.Leaf) session.clearPendingInputs(previous);
        });
        if (after == null) session.clearPendingInputs(scope);
        session.remapPendingInputs(moved);
    }

    private static void collect(@Nullable ConditionNode node, FieldPath path, Map<ConditionNode, FieldPath> paths) {
        if (node == null) return;
        paths.put(node, path);
        List<ConditionNode> children = INSTANCE.children(node);
        for (int index = 0; index < children.size(); index++) {
            FieldPath childPath = node instanceof ConditionNode.Inverted ? path.child(RuleFields.TERM)
                    : path.child(RuleFields.TERMS).index(index);
            collect(children.get(index), childPath, paths);
        }
    }
}
