package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 每个树视图独立持有的不可变选择/折叠快照，视图更新不产生结构编辑历史。
 * <p>节点是否存在由拥有结构的宿主校验；remap 输入覆盖全部存活旧路径，包含未位移路径。</p>
 * <pre>{@code
 * state = state.select(path).fold(parent, false);
 * state = state.remap(change.paths());
 * }</pre>
 */
public record UiTreeViewState(@Nullable UiTreePath selected, Set<UiTreePath> collapsed, Set<UiTreePath> selection) {
    /** 空视图状态，所有节点展开。 */
    public static final UiTreeViewState EMPTY = new UiTreeViewState(null, Set.of(), Set.of());
    /** 对集合做防御复制，主选择始终属于选择集合。 */
    public UiTreeViewState {
        collapsed = Set.copyOf(collapsed);
        selection = Collections.unmodifiableSet(new LinkedHashSet<>(selection));
        if (selected != null && !selection.contains(selected)) throw new IllegalArgumentException("Primary selection missing");
    }
    /** 替换选择；主路径缺失时取集合中的第一项。 */
    public UiTreeViewState withSelection(Collection<UiTreePath> paths, @Nullable UiTreePath primary) {
        Set<UiTreePath> next = new LinkedHashSet<>(paths);
        UiTreePath main = primary != null && next.contains(primary) ? primary : next.stream().findFirst().orElse(null);
        return new UiTreeViewState(main, collapsed, next);
    }
    /** 单选或清除选择。 */
    public UiTreeViewState select(@Nullable UiTreePath path) {
        return withSelection(path == null ? Set.of() : Set.of(path), path);
    }
    /** 更改单个节点折叠值。 */
    public UiTreeViewState fold(UiTreePath path, boolean folded) {
        Set<UiTreePath> next = new LinkedHashSet<>(collapsed);
        if (folded) next.add(path); else next.remove(path);
        return new UiTreeViewState(selected, next, selection);
    }
    /** 展开全部节点，保留选择。 */
    public UiTreeViewState expandAll() { return new UiTreeViewState(selected, Set.of(), selection); }
    /** 按完整旧新映射迁移视图；映射缺失的已移除路径被丢弃。 */
    public UiTreeViewState remap(Map<UiTreePath, UiTreePath> paths) {
        Set<UiTreePath> folded = new LinkedHashSet<>();
        Set<UiTreePath> chosen = new LinkedHashSet<>();
        collapsed.forEach(path -> { if (paths.containsKey(path)) folded.add(paths.get(path)); });
        selection.forEach(path -> { if (paths.containsKey(path)) chosen.add(paths.get(path)); });
        UiTreePath main = selected == null ? null : paths.get(selected);
        return new UiTreeViewState(null, folded, Set.of()).withSelection(chosen, main);
    }
}
