package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * 按结构版本构建的只读先序索引；折叠只派生可见行，不再读取业务树。
 * <p>单根以 ROOT 定位；多根以 ROOT.child(rootIndex) 定位，位置路径的稳定身份由宿主映射。</p>
 * <p>输入为无环树，children 返回非 null 列表；null 子槽保留位置但不生成行。索引保存节点引用，
 * 宿主修改可变节点的结构后须重新构建；各视图在客户端线程独立拥有视图状态。</p>
 * <pre>{@code
 * var projection = UiTreeProjection.single(root, Node::children);
 * var state = UiTreeViewState.EMPTY.fold(UiTreePath.ROOT, true);
 * var visible = projection.visible(state);
 * }</pre>
 */
public final class UiTreeProjection<N> {
    /** 结构行；多根的 parent 为 null，childCount 保留原始槽位数。 */
    public record Entry<N>(N node, UiTreePath path, int depth, @Nullable UiTreePath parent, int childCount) {}
    private final List<Entry<N>> entries;
    private final Map<UiTreePath, Entry<N>> byPath;
    private final Map<N, UiTreePath> byIdentity;

    private UiTreeProjection(List<Entry<N>> entries) {
        this.entries = List.copyOf(entries);
        byPath = new LinkedHashMap<>();
        byIdentity = new IdentityHashMap<>();
        for (Entry<N> entry : entries) {
            byPath.put(entry.path(), entry);
            byIdentity.putIfAbsent(entry.node(), entry.path());
        }
    }
    /** 单根结构快照；null 根产生空索引，调用方在结构变化后重新构建。 */
    public static <N> UiTreeProjection<N> single(@Nullable N root, Function<N, List<N>> children) {
        List<Entry<N>> rows = new ArrayList<>();
        if (root != null) append(root, UiTreePath.ROOT, 0, null, children, rows);
        return new UiTreeProjection<>(rows);
    }
    /** 多根结构快照，根的视觉深度均为 0。 */
    public static <N> UiTreeProjection<N> forest(List<N> roots, Function<N, List<N>> children) {
        List<Entry<N>> rows = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) append(roots.get(i), UiTreePath.ROOT.child(i), 0, null, children, rows);
        return new UiTreeProjection<>(rows);
    }
    private static <N> void append(N node, UiTreePath path, int depth, @Nullable UiTreePath parent,
                                   Function<N, List<N>> children, List<Entry<N>> rows) {
        List<N> descendants = children.apply(node);
        rows.add(new Entry<>(node, path, depth, parent, descendants.size()));
        for (int i = 0; i < descendants.size(); i++) {
            N child = descendants.get(i);
            if (child != null) append(child, path.child(i), depth + 1, path, children, rows);
        }
    }
    /** 完整的只读先序行。 */
    public List<Entry<N>> entries() { return entries; }
    /** 按位置路径查询，不存在时返回 null。 */
    public @Nullable Entry<N> entry(UiTreePath path) { return byPath.get(path); }
    /** 按节点身份找第一次出现的位置；不以 equals 混淆内容相同的节点。 */
    public @Nullable UiTreePath pathOf(N node) { return byIdentity.get(node); }
    /** 在已建索引上按折叠状态过滤；返回值可缓存至状态变化。 */
    public List<Entry<N>> visible(UiTreeViewState state) {
        List<Entry<N>> rows = new ArrayList<>();
        int hiddenBelow = Integer.MAX_VALUE;
        for (Entry<N> entry : entries) {
            if (entry.depth() > hiddenBelow) continue;
            hiddenBelow = Integer.MAX_VALUE;
            rows.add(entry);
            if (state.collapsed().contains(entry.path())) hiddenBelow = entry.depth();
        }
        return List.copyOf(rows);
    }
}
