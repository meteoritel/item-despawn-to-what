package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 绑定宿主不可变节点的通用树操作；配置变更与选择、折叠事件分开。 */
public final class UiTreeEditor<N> {
    public interface Adapter<N> {
        int UNLIMITED_CHILDREN = Integer.MAX_VALUE;
        List<N> children(N node);
        N withChildren(N node, List<N> children);
        int childCapacity(N node);
        default boolean acceptsChildren(N node, int childCount) {
            return childCount >= 0 && childCount <= childCapacity(node);
        }
    }

    public enum Kind { CREATE, DELETE, UPDATE }
    public record Change<N>(@Nullable N before, @Nullable N after, Kind kind) {}
    public record Outcome(boolean changed, @Nullable Component error) {}
    public record ViewChange(@Nullable UiTreePath selected, Set<UiTreePath> collapsed) {
        public ViewChange { collapsed = Set.copyOf(collapsed); }
    }

    private final Adapter<N> adapter;
    private final Supplier<N> root;
    private final Consumer<Change<N>> commit;
    private final Function<N, Component> validator;
    private final Component invalidPath;
    private final Component invalidParent;
    private final Set<UiTreePath> collapsed = new HashSet<>();
    private @Nullable UiTreePath selected;
    private Consumer<ViewChange> onViewChanged = change -> {};
    private Runnable beforeEdit = () -> {};

    public UiTreeEditor(Adapter<N> adapter, Supplier<N> root, Consumer<Change<N>> commit,
                        Function<N, Component> validator, Component invalidPath, Component invalidParent) {
        this.adapter = Objects.requireNonNull(adapter);
        this.root = Objects.requireNonNull(root);
        this.commit = Objects.requireNonNull(commit);
        this.validator = Objects.requireNonNull(validator);
        this.invalidPath = invalidPath;
        this.invalidParent = invalidParent;
    }

    public Adapter<N> adapter() { return adapter; }
    public @Nullable N root() { return root.get(); }
    /** 读取外部表示；回填根由宿主执行，不产生配置操作事件。 */
    public <S> UiTreeCodec.DecodeResult<N> decode(UiTreeCodec<N, S> codec, @Nullable S source) {
        return codec.decode(source);
    }
    public <S> @Nullable S encode(UiTreeCodec<N, S> codec) { return codec.encode(root.get()); }
    public void setBeforeEdit(Runnable handler) { beforeEdit = Objects.requireNonNull(handler); }
    public void setOnViewChanged(Consumer<ViewChange> listener) { onViewChanged = Objects.requireNonNull(listener); }
    public @Nullable UiTreePath selected() { return selected; }
    public Set<UiTreePath> collapsed() { return Set.copyOf(collapsed); }

    public void select(@Nullable UiTreePath path) {
        if (path != null && query(path) == null) return;
        if (Objects.equals(path, selected)) return;
        selected = path;
        notifyView();
    }

    public void setCollapsed(UiTreePath path, boolean value) {
        if (query(path) == null) return;
        if (value ? collapsed.add(path) : collapsed.remove(path)) notifyView();
    }

    public void expandAll() {
        if (collapsed.isEmpty()) return;
        collapsed.clear();
        notifyView();
    }

    private void notifyView() { onViewChanged.accept(new ViewChange(selected, collapsed)); }

    public @Nullable N query(UiTreePath path) { return query(root.get(), path); }

    private @Nullable N query(@Nullable N node, UiTreePath path) {
        for (int index : path.indices()) {
            if (node == null) return null;
            List<N> children = adapter.children(node);
            if (index >= children.size()) return null;
            node = children.get(index);
        }
        return node;
    }

    public Outcome create(@Nullable UiTreePath parent, int index, N node) {
        beforeEdit.run();
        N before = root.get();
        if (parent == null) {
            if (before != null) return new Outcome(false, invalidParent);
            return accept(before, node, Kind.CREATE);
        }
        N target = query(before, parent);
        if (target == null) return new Outcome(false, invalidPath);
        List<N> children = new ArrayList<>(adapter.children(target));
        if (index < 0 || index > children.size()) return new Outcome(false, invalidPath);
        if (!adapter.acceptsChildren(target, children.size() + 1)) return new Outcome(false, invalidParent);
        children.add(index, node);
        return accept(before, replace(before, parent.indices(), 0, adapter.withChildren(target, children)), Kind.CREATE);
    }

    public Outcome delete(UiTreePath path) {
        return update(path, node -> null, Kind.DELETE);
    }

    public Outcome update(UiTreePath path, UnaryOperator<N> updater) {
        return update(path, updater, Kind.UPDATE);
    }

    private Outcome update(UiTreePath path, UnaryOperator<N> updater, Kind kind) {
        beforeEdit.run();
        N before = root.get();
        N node = query(before, path);
        if (node == null) return new Outcome(false, invalidPath);
        return accept(before, replace(before, path.indices(), 0, updater.apply(node)), kind);
    }

    /** 接受候选根；合法性由宿主判断，错误与无变化不写绑定也不产生操作事件。 */
    public Outcome accept(@Nullable N before, @Nullable N after, Kind kind) {
        if (Objects.equals(before, after)) return new Outcome(false, null);
        Component error = validator.apply(after);
        if (error != null) return new Outcome(false, error);
        commit.accept(new Change<>(before, after, kind));
        return new Outcome(true, null);
    }

    private @Nullable N replace(@Nullable N node, List<Integer> path, int depth, @Nullable N replacement) {
        if (depth == path.size()) return replacement;
        List<N> children = new ArrayList<>(adapter.children(Objects.requireNonNull(node)));
        int index = path.get(depth);
        N next = replace(children.get(index), path, depth + 1, replacement);
        if (next == null) children.remove(index); else children.set(index, next);
        return adapter.withChildren(node, children);
    }
}
