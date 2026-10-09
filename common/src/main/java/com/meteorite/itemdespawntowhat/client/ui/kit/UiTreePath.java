package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.List;

/** 不可变节点路径：空列表为根，各下标指向父节点中的子项。 */
public record UiTreePath(List<Integer> indices) {
    public static final UiTreePath ROOT = new UiTreePath(List.of());

    public UiTreePath {
        indices = List.copyOf(indices);
        if (indices.stream().anyMatch(index -> index < 0)) throw new IllegalArgumentException("Negative tree index");
    }

    public UiTreePath child(int index) {
        List<Integer> next = new ArrayList<>(indices);
        next.add(index);
        return new UiTreePath(next);
    }

    public UiTreePath parent() {
        return indices.isEmpty() ? ROOT : new UiTreePath(indices.subList(0, indices.size() - 1));
    }

    public boolean isAncestorOf(UiTreePath other) {
        return indices.size() < other.indices.size() && other.indices.subList(0, indices.size()).equals(indices);
    }
}
