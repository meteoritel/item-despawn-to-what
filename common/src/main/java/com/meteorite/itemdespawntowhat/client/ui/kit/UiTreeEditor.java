package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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

    public enum Kind { CREATE, DELETE, UPDATE, MOVE }
    public record Change<N>(@Nullable N before, @Nullable N after, Kind kind, Map<UiTreePath, UiTreePath> paths) {
        public Change { paths = Map.copyOf(paths); }
        public Change(@Nullable N before, @Nullable N after, Kind kind) { this(before, after, kind, Map.of()); }
    }
    public enum DropPosition { BEFORE, INSIDE, AFTER }
    public record MoveErrors(Component cycle, Component rootSibling, Component noChildren, Component fullParent, Component stale) {}
    public record MovePreview<N>(@Nullable N before, @Nullable N after, UiTreePath source, UiTreePath target,
                                 DropPosition position, @Nullable UiTreePath movedTo,
                                 Map<UiTreePath, UiTreePath> paths, Outcome outcome) {
        public MovePreview { paths = Map.copyOf(paths); }
    }
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
    private MoveErrors moveErrors;

    public UiTreeEditor(Adapter<N> adapter, Supplier<N> root, Consumer<Change<N>> commit,
                        Function<N, Component> validator, Component invalidPath, Component invalidParent) {
        this.adapter = Objects.requireNonNull(adapter);
        this.root = Objects.requireNonNull(root);
        this.commit = Objects.requireNonNull(commit);
        this.validator = Objects.requireNonNull(validator);
        this.invalidPath = invalidPath;
        this.invalidParent = invalidParent;
        this.moveErrors = new MoveErrors(invalidParent, invalidParent, invalidParent, invalidParent, invalidPath);
    }

    public void setMoveErrors(MoveErrors errors) { moveErrors = Objects.requireNonNull(errors); }

    public Adapter<N> adapter() { return adapter; }
    public @Nullable N root() { return root.get(); }
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
        return accept(before, after, kind, Map.of());
    }

    private Outcome accept(@Nullable N before, @Nullable N after, Kind kind, Map<UiTreePath, UiTreePath> paths) {
        if (Objects.equals(before, after)) return new Outcome(false, null);
        Component error = validator.apply(after);
        if (error != null) return new Outcome(false, error);
        commit.accept(new Change<>(before, after, kind, paths));
        return new Outcome(true, null);
    }

    /** 开始移动前完成宿主字段，之后才读取拖拽基准；预览本身不触发该回调。 */
    public boolean beginMove(UiTreePath source) {
        beforeEdit.run();
        return query(source) != null;
    }

    /** 只构造候选与所有原节点路径映射，不修改绑定或视图状态。 */
    public MovePreview<N> previewMove(@Nullable N before, UiTreePath source, UiTreePath target, DropPosition position) {
        N node = query(before, source);
        N anchor = query(before, target);
        if (node == null || anchor == null) return rejectedMove(before, source, target, position, invalidPath);
        if (source.indices().isEmpty()) return rejectedMove(before, source, target, position, moveErrors.cycle());
        if (position != DropPosition.INSIDE && target.indices().isEmpty()) {
            return rejectedMove(before, source, target, position, moveErrors.rootSibling());
        }
        UiTreePath parent = position == DropPosition.INSIDE ? target : target.parent();
        if (source.equals(parent) || source.isAncestorOf(parent)) {
            return rejectedMove(before, source, target, position, moveErrors.cycle());
        }
        N oldParent = query(before, parent);
        int sourceIndex = lastIndex(source);
        int boundary = position == DropPosition.INSIDE ? adapter.children(Objects.requireNonNull(oldParent)).size()
                : lastIndex(target) + (position == DropPosition.AFTER ? 1 : 0);
        N removed = replace(before, source.indices(), 0, null);
        UiTreePath nextParent = removedPath(parent, source);
        N destination = query(removed, nextParent);
        if (destination == null) return rejectedMove(before, source, target, position, invalidPath);
        List<N> children = new ArrayList<>(adapter.children(destination));
        if (source.parent().equals(parent) && sourceIndex < boundary) boundary--;
        if (!adapter.acceptsChildren(destination, children.size() + 1)) {
            Component error = adapter.childCapacity(destination) == 0 ? moveErrors.noChildren() : moveErrors.fullParent();
            return rejectedMove(before, source, target, position, error);
        }
        if (boundary < 0 || boundary > children.size()) return rejectedMove(before, source, target, position, invalidPath);
        children.add(boundary, node);
        N after = replace(removed, nextParent.indices(), 0, adapter.withChildren(destination, children));
        Component error = validator.apply(after);
        if (error != null) return rejectedMove(before, source, target, position, error);
        UiTreePath movedTo = nextParent.child(boundary);
        Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
        collectMovePaths(before, UiTreePath.ROOT, source, movedTo, nextParent, boundary, paths);
        return new MovePreview<>(before, after, source, target, position, movedTo, paths,
                new Outcome(!Objects.equals(before, after), null));
    }

    private MovePreview<N> rejectedMove(@Nullable N before, UiTreePath source, UiTreePath target,
                                        DropPosition position, Component error) {
        return new MovePreview<>(before, before, source, target, position, null, Map.of(), new Outcome(false, error));
    }

    /** 接受已展示的同一候选；基准改变、非法或无变化均不提交。 */
    public Outcome commitMove(MovePreview<N> preview) {
        if (!Objects.equals(root.get(), preview.before())) return new Outcome(false, moveErrors.stale());
        if (!preview.outcome().changed()) return preview.outcome();
        Outcome result = accept(preview.before(), preview.after(), Kind.MOVE, preview.paths());
        if (result.changed()) {
            Set<UiTreePath> nextCollapsed = new HashSet<>();
            collapsed.forEach(path -> {
                UiTreePath next = preview.paths().get(path);
                if (next != null) nextCollapsed.add(next);
            });
            collapsed.clear();
            collapsed.addAll(nextCollapsed);
            selected = preview.movedTo();
            notifyView();
        }
        return result;
    }

    private void collectMovePaths(@Nullable N node, UiTreePath path, UiTreePath source, UiTreePath movedTo,
                                   UiTreePath parent, int boundary, Map<UiTreePath, UiTreePath> paths) {
        if (node == null) return;
        UiTreePath next;
        if (path.equals(source) || source.isAncestorOf(path)) {
            List<Integer> indices = new ArrayList<>(movedTo.indices());
            indices.addAll(path.indices().subList(source.indices().size(), path.indices().size()));
            next = new UiTreePath(indices);
        } else next = insertedPath(removedPath(path, source), parent, boundary);
        paths.put(path, next);
        List<N> children = adapter.children(node);
        for (int index = 0; index < children.size(); index++) {
            collectMovePaths(children.get(index), path.child(index), source, movedTo, parent, boundary, paths);
        }
    }

    private static UiTreePath removedPath(UiTreePath path, UiTreePath source) {
        UiTreePath parent = source.parent();
        if (!parent.isAncestorOf(path)) return path;
        int depth = parent.indices().size();
        if (path.indices().get(depth) <= lastIndex(source)) return path;
        List<Integer> indices = new ArrayList<>(path.indices());
        indices.set(depth, indices.get(depth) - 1);
        return new UiTreePath(indices);
    }

    private static UiTreePath insertedPath(UiTreePath path, UiTreePath parent, int boundary) {
        if (!parent.isAncestorOf(path)) return path;
        int depth = parent.indices().size();
        if (path.indices().get(depth) < boundary) return path;
        List<Integer> indices = new ArrayList<>(path.indices());
        indices.set(depth, indices.get(depth) + 1);
        return new UiTreePath(indices);
    }

    private static int lastIndex(UiTreePath path) { return path.indices().getLast(); }

    private @Nullable N replace(@Nullable N node, List<Integer> path, int depth, @Nullable N replacement) {
        if (depth == path.size()) return replacement;
        List<N> children = new ArrayList<>(adapter.children(Objects.requireNonNull(node)));
        int index = path.get(depth);
        N next = replace(children.get(index), path, depth + 1, replacement);
        if (next == null) children.remove(index); else children.set(index, next);
        return adapter.withChildren(node, children);
    }
}
