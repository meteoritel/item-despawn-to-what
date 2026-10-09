package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    public record Change<N>(@Nullable N before, @Nullable N after, Kind kind, Map<UiTreePath, UiTreePath> paths,
                            Set<UiTreePath> removed, Set<UiTreePath> added) {
        public Change { paths = Map.copyOf(paths); removed = Set.copyOf(removed); added = Set.copyOf(added); }
        public Change(@Nullable N before, @Nullable N after, Kind kind, Map<UiTreePath, UiTreePath> paths, Set<UiTreePath> removed) {
            this(before, after, kind, paths, removed, Set.of());
        }
        public Change(@Nullable N before, @Nullable N after, Kind kind, Map<UiTreePath, UiTreePath> paths) {
            this(before, after, kind, paths, Set.of());
        }
        public Change(@Nullable N before, @Nullable N after, Kind kind) { this(before, after, kind, Map.of()); }
    }
    public enum DropPosition { BEFORE, INSIDE, AFTER }
    public record MoveErrors(Component cycle, Component rootSibling, Component noChildren, Component fullParent, Component stale) {}
    public record MovePreview<N>(@Nullable N before, @Nullable N after, UiTreePath source, UiTreePath target,
                                 DropPosition position, @Nullable UiTreePath movedTo,
                                 Map<UiTreePath, UiTreePath> paths, Outcome outcome, List<UiTreePath> sources) {
        public MovePreview { paths = Map.copyOf(paths); sources = List.copyOf(sources); }
        public MovePreview(@Nullable N before, @Nullable N after, UiTreePath source, UiTreePath target,
                           DropPosition position, @Nullable UiTreePath movedTo,
                           Map<UiTreePath, UiTreePath> paths, Outcome outcome) {
            this(before, after, source, target, position, movedTo, paths, outcome, List.of(source));
        }
    }
    public record Outcome(boolean changed, @Nullable Component error) {}
    public record ViewChange(@Nullable UiTreePath selected, Set<UiTreePath> collapsed, Set<UiTreePath> selection) {
        public ViewChange { collapsed = Set.copyOf(collapsed); selection = Set.copyOf(selection); }
        public ViewChange(@Nullable UiTreePath selected, Set<UiTreePath> collapsed) {
            this(selected, collapsed, selected == null ? Set.of() : Set.of(selected));
        }
    }

    private final Adapter<N> adapter;
    private final Supplier<N> root;
    private final Consumer<Change<N>> commit;
    private final Function<N, Component> validator;
    private final Component invalidPath;
    private final Component invalidParent;
    private final Set<UiTreePath> collapsed = new HashSet<>();
    private @Nullable UiTreePath selected;
    private final Set<UiTreePath> selection = new LinkedHashSet<>();
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
    /** 读取外部表示；回填根由宿主执行，不产生配置操作事件。 */
    public <S> UiTreeCodec.DecodeResult<N> decode(UiTreeCodec<N, S> codec, @Nullable S source) {
        return codec.decode(source);
    }
    public <S> @Nullable S encode(UiTreeCodec<N, S> codec) { return codec.encode(root.get()); }
    public void setBeforeEdit(Runnable handler) { beforeEdit = Objects.requireNonNull(handler); }
    public void setOnViewChanged(Consumer<ViewChange> listener) { onViewChanged = Objects.requireNonNull(listener); }
    public @Nullable UiTreePath selected() { return selected; }
    public Set<UiTreePath> collapsed() { return Set.copyOf(collapsed); }

    public Set<UiTreePath> selection() { return Set.copyOf(selection); }

    public void select(@Nullable UiTreePath path) {
        if (path != null && query(path) == null) return;
        setSelection(path == null ? List.of() : List.of(path), path);
    }

    /** 选择集合与主节点仅产生视图事件；主节点用于逐节点参数编辑。 */
    public void setSelection(Collection<UiTreePath> paths, @Nullable UiTreePath primary) {
        Set<UiTreePath> next = new LinkedHashSet<>();
        paths.stream().filter(path -> query(path) != null).sorted(PATH_ORDER).forEach(next::add);
        UiTreePath nextPrimary = primary != null && next.contains(primary) ? primary : next.stream().findFirst().orElse(null);
        if (selection.equals(next) && Objects.equals(selected, nextPrimary)) return;
        selection.clear();
        selection.addAll(next);
        selected = nextPrimary;
        notifyView();
    }

    public void toggleSelection(UiTreePath path) {
        if (query(path) == null) return;
        Set<UiTreePath> next = new LinkedHashSet<>(selection);
        boolean added = next.add(path);
        if (!added) next.remove(path);
        setSelection(next, added ? path : selected);
    }

    /** 按树顺序排序、去重并去掉已选祖先覆盖的后代，结构操作只处理这些子树。 */
    public List<UiTreePath> normalizedSelection() { return normalize(selection); }

    public static List<UiTreePath> normalize(Collection<UiTreePath> paths) {
        List<UiTreePath> result = new ArrayList<>();
        for (UiTreePath path : paths.stream().distinct().sorted(PATH_ORDER).toList()) {
            if (result.stream().noneMatch(ancestor -> ancestor.equals(path) || ancestor.isAncestorOf(path))) result.add(path);
        }
        return List.copyOf(result);
    }

    private static final Comparator<UiTreePath> PATH_ORDER = (left, right) -> {
        for (int depth = 0; depth < Math.min(left.indices().size(), right.indices().size()); depth++) {
            int compared = Integer.compare(left.indices().get(depth), right.indices().get(depth));
            if (compared != 0) return compared;
        }
        return Integer.compare(left.indices().size(), right.indices().size());
    };

    public void setCollapsed(UiTreePath path, boolean value) {
        if (query(path) == null) return;
        if (value ? collapsed.add(path) : collapsed.remove(path)) notifyView();
    }

    public void expandAll() {
        if (collapsed.isEmpty()) return;
        collapsed.clear();
        notifyView();
    }

    private void notifyView() { onViewChanged.accept(new ViewChange(selected, collapsed, selection)); }

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
        return createMany(parent, index, List.of(node));
    }

    public Outcome delete(UiTreePath path) { return deleteMany(List.of(path)); }

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
        return accept(before, after, kind, paths, Set.of());
    }

    private Outcome accept(@Nullable N before, @Nullable N after, Kind kind,
                           Map<UiTreePath, UiTreePath> paths, Set<UiTreePath> removed) {
        return accept(before, after, kind, paths, removed, Set.of());
    }

    private Outcome accept(@Nullable N before, @Nullable N after, Kind kind,
                           Map<UiTreePath, UiTreePath> paths, Set<UiTreePath> removed, Set<UiTreePath> added) {
        if (Objects.equals(before, after)) return new Outcome(false, null);
        Component error = validator.apply(after);
        if (error != null) return new Outcome(false, error);
        commit.accept(new Change<>(before, after, kind, paths, removed, added));
        return new Outcome(true, null);
    }

    /** 开始移动前完成宿主字段，之后才读取拖拽基准；预览本身不触发该回调。 */
    public boolean beginMove(UiTreePath source) { return beginMove(List.of(source)); }
    public boolean beginMove(Collection<UiTreePath> sources) {
        beforeEdit.run();
        List<UiTreePath> paths = normalize(sources);
        return !paths.isEmpty() && paths.stream().allMatch(path -> query(path) != null);
    }

    public MovePreview<N> previewMove(@Nullable N before, UiTreePath source, UiTreePath target, DropPosition position) {
        return previewMove(before, List.of(source), target, position);
    }

    /** 批量候选保留原树顺序；移除全部源后再检查目标容量，任何错误完整拒绝。 */
    public MovePreview<N> previewMove(@Nullable N before, Collection<UiTreePath> requested, UiTreePath target, DropPosition position) {
        List<UiTreePath> sources = normalize(requested);
        UiTreePath source = sources.isEmpty() ? UiTreePath.ROOT : sources.getFirst();
        if (sources.isEmpty() || sources.stream().anyMatch(path -> query(before, path) == null) || query(before, target) == null) {
            return rejectedMove(before, sources, source, target, position, invalidPath);
        }
        if (sources.contains(UiTreePath.ROOT)) return rejectedMove(before, sources, source, target, position, moveErrors.cycle());
        if (position != DropPosition.INSIDE && target.indices().isEmpty()) {
            return rejectedMove(before, sources, source, target, position, moveErrors.rootSibling());
        }
        UiTreePath parent = position == DropPosition.INSIDE ? target : target.parent();
        if (sources.stream().anyMatch(path -> path.equals(parent) || path.isAncestorOf(parent))) {
            return rejectedMove(before, sources, source, target, position, moveErrors.cycle());
        }
        N oldParent = query(before, parent);
        int boundary = position == DropPosition.INSIDE ? adapter.children(Objects.requireNonNull(oldParent)).size()
                : lastIndex(target) + (position == DropPosition.AFTER ? 1 : 0);
        int removedBefore = 0;
        for (UiTreePath path : sources) if (path.parent().equals(parent) && lastIndex(path) < boundary) removedBefore++;
        boundary -= removedBefore;
        N removed = removeAll(before, sources);
        UiTreePath nextParent = afterRemoval(parent, sources);
        N destination = query(removed, nextParent);
        if (destination == null) return rejectedMove(before, sources, source, target, position, invalidPath);
        List<N> children = new ArrayList<>(adapter.children(destination));
        if (!adapter.acceptsChildren(destination, children.size() + sources.size())) {
            Component error = adapter.childCapacity(destination) == 0 ? moveErrors.noChildren() : moveErrors.fullParent();
            return rejectedMove(before, sources, source, target, position, error);
        }
        if (boundary < 0 || boundary > children.size()) return rejectedMove(before, sources, source, target, position, invalidPath);
        List<N> moving = sources.stream().map(path -> Objects.requireNonNull(query(before, path))).toList();
        children.addAll(boundary, moving);
        N after = replace(removed, nextParent.indices(), 0, adapter.withChildren(destination, children));
        Component error = validator.apply(after);
        if (error != null) return rejectedMove(before, sources, source, target, position, error);
        Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
        final int insertion = boundary;
        collectPaths(before, UiTreePath.ROOT, path -> {
            for (int index = 0; index < sources.size(); index++) {
                UiTreePath moved = sources.get(index);
                if (moved.equals(path) || moved.isAncestorOf(path)) return relocate(path, moved, nextParent.child(insertion + index));
            }
            return insertedPath(afterRemoval(path, sources), nextParent, insertion, sources.size());
        }, paths);
        return new MovePreview<>(before, after, source, target, position, nextParent.child(boundary), paths,
                new Outcome(!Objects.equals(before, after), null), sources);
    }

    private MovePreview<N> rejectedMove(@Nullable N before, List<UiTreePath> sources, UiTreePath source, UiTreePath target,
                                        DropPosition position, Component error) {
        return new MovePreview<>(before, before, source, target, position, null, Map.of(), new Outcome(false, error), sources);
    }

    /** 接受已展示的同一候选；基准改变、非法或无变化均不提交。 */
    public Outcome commitMove(MovePreview<N> preview) {
        if (!Objects.equals(root.get(), preview.before())) return new Outcome(false, moveErrors.stale());
        if (!preview.outcome().changed()) return preview.outcome();
        UiTreePath primary = selected;
        Outcome result = accept(preview.before(), preview.after(), Kind.MOVE, preview.paths());
        if (result.changed()) {
            remapView(preview.paths());
            List<UiTreePath> next = preview.sources().stream().map(preview.paths()::get).toList();
            setSelection(next, primary == null ? preview.movedTo() : preview.paths().get(primary));
        }
        return result;
    }

    /** 批量删除以最高选中子树为单位，父节点由适配器重建，单次提交。 */
    public Outcome deleteMany(Collection<UiTreePath> requested) {
        List<UiTreePath> sources = normalize(requested);
        beforeEdit.run();
        N before = root.get();
        if (sources.isEmpty()) return new Outcome(false, invalidPath);
        if (sources.stream().anyMatch(path -> query(before, path) == null)) return new Outcome(false, invalidPath);
        N after = removeAll(before, sources);
        Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
        collectPaths(before, UiTreePath.ROOT, path -> sources.stream().anyMatch(source -> source.equals(path) || source.isAncestorOf(path))
                ? null : afterRemoval(path, sources), paths);
        Outcome result = accept(before, after, Kind.DELETE, paths, Set.copyOf(sources));
        if (result.changed()) { remapView(paths); setSelection(List.of(), null); }
        return result;
    }

    /** 批量插入不替换既有根，不合并分组；容量或候选失败时整批不写入。 */
    public Outcome createMany(@Nullable UiTreePath parent, int index, List<N> nodes) {
        beforeEdit.run();
        N before = root.get();
        if (nodes.isEmpty()) return new Outcome(false, invalidPath);
        if (parent == null) {
            if (before != null || nodes.size() != 1) return new Outcome(false, invalidParent);
            Outcome result = accept(before, nodes.getFirst(), Kind.CREATE, Map.of(), Set.of(), Set.of(UiTreePath.ROOT));
            if (result.changed()) select(UiTreePath.ROOT);
            return result;
        }
        N target = query(before, parent);
        if (target == null) return new Outcome(false, invalidPath);
        List<N> children = new ArrayList<>(adapter.children(target));
        if (index < 0 || index > children.size()) return new Outcome(false, invalidPath);
        if (!adapter.acceptsChildren(target, children.size() + nodes.size())) {
            return new Outcome(false, adapter.childCapacity(target) == 0 ? moveErrors.noChildren() : moveErrors.fullParent());
        }
        children.addAll(index, nodes);
        N after = replace(before, parent.indices(), 0, adapter.withChildren(target, children));
        Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
        collectPaths(before, UiTreePath.ROOT, path -> insertedPath(path, parent, index, nodes.size()), paths);
        List<UiTreePath> added = new ArrayList<>();
        for (int child = 0; child < nodes.size(); child++) added.add(parent.child(index + child));
        Outcome result = accept(before, after, Kind.CREATE, paths, Set.of(), Set.copyOf(added));
        if (result.changed()) {
            remapView(paths);
            setSelection(added, added.getFirst());
        }
        return result;
    }

    /** 仅同父且连续的选中节点可包组；包装节点由宿主提供。 */
    public Outcome wrapMany(Collection<UiTreePath> requested, Function<List<N>, N> wrapper, Component invalidSelection) {
        List<UiTreePath> sources = normalize(requested);
        beforeEdit.run();
        N before = root.get();
        if (sources.isEmpty() || sources.stream().anyMatch(path -> query(before, path) == null)) return new Outcome(false, invalidSelection);
        if (sources.getFirst().indices().isEmpty()) {
            N after = wrapper.apply(List.of(Objects.requireNonNull(before)));
            Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
            collectPaths(before, UiTreePath.ROOT, path -> relocate(path, UiTreePath.ROOT, UiTreePath.ROOT.child(0)), paths);
            Outcome result = accept(before, after, Kind.UPDATE, paths);
            if (result.changed()) { remapView(paths); select(UiTreePath.ROOT); }
            return result;
        }
        UiTreePath parent = sources.getFirst().parent();
        int first = lastIndex(sources.getFirst());
        for (int index = 0; index < sources.size(); index++) {
            if (!sources.get(index).parent().equals(parent) || lastIndex(sources.get(index)) != first + index) {
                return new Outcome(false, invalidSelection);
            }
        }
        N target = Objects.requireNonNull(query(before, parent));
        List<N> children = new ArrayList<>(adapter.children(target));
        N group = wrapper.apply(List.copyOf(children.subList(first, first + sources.size())));
        children.subList(first, first + sources.size()).clear();
        children.add(first, group);
        N after = replace(before, parent.indices(), 0, adapter.withChildren(target, children));
        UiTreePath groupPath = parent.child(first);
        Map<UiTreePath, UiTreePath> paths = new LinkedHashMap<>();
        collectPaths(before, UiTreePath.ROOT, path -> {
            for (int index = 0; index < sources.size(); index++) {
                UiTreePath source = sources.get(index);
                if (source.equals(path) || source.isAncestorOf(path)) return relocate(path, source, groupPath.child(index));
            }
            return insertedPath(afterRemoval(path, sources), parent, first, 1);
        }, paths);
        Outcome result = accept(before, after, Kind.UPDATE, paths);
        if (result.changed()) { remapView(paths); select(groupPath); }
        return result;
    }

    private void remapView(Map<UiTreePath, UiTreePath> paths) {
        Set<UiTreePath> folded = new HashSet<>();
        collapsed.forEach(path -> { UiTreePath next = paths.get(path); if (next != null) folded.add(next); });
        collapsed.clear();
        collapsed.addAll(folded);
    }

    private @Nullable N removeAll(@Nullable N before, List<UiTreePath> sources) {
        N after = before;
        for (UiTreePath source : sources.reversed()) after = replace(after, source.indices(), 0, null);
        return after;
    }

    private static UiTreePath afterRemoval(UiTreePath path, List<UiTreePath> sources) {
        UiTreePath next = path;
        for (UiTreePath source : sources.reversed()) next = removedPath(next, source);
        return next;
    }

    private static UiTreePath relocate(UiTreePath path, UiTreePath source, UiTreePath destination) {
        List<Integer> indices = new ArrayList<>(destination.indices());
        indices.addAll(path.indices().subList(source.indices().size(), path.indices().size()));
        return new UiTreePath(indices);
    }

    private void collectPaths(@Nullable N node, UiTreePath path, Function<UiTreePath, UiTreePath> mapping,
                              Map<UiTreePath, UiTreePath> paths) {
        if (node == null) return;
        UiTreePath next = mapping.apply(path);
        if (next != null) paths.put(path, next);
        List<N> children = adapter.children(node);
        for (int index = 0; index < children.size(); index++) collectPaths(children.get(index), path.child(index), mapping, paths);
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

    private static UiTreePath insertedPath(UiTreePath path, UiTreePath parent, int boundary, int count) {
        if (!parent.isAncestorOf(path)) return path;
        int depth = parent.indices().size();
        if (path.indices().get(depth) < boundary) return path;
        List<Integer> indices = new ArrayList<>(path.indices());
        indices.set(depth, indices.get(depth) + count);
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
