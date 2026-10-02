package com.meteorite.itemdespawntowhat.client.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;

/***
 * 树节点。
 * <p>只承载展示数据（业务值、显示文本、子节点、展开状态），
 * 不包含布局与渲染逻辑，几何计算由 {@link UiTreeView} 负责。
 */
public final class UiTreeNode<T> {

    // 业务值
    private final T value;
    // 显示文本
    private final Component label;
    // 子节点
    private final List<UiTreeNode<T>> children = new ArrayList<>();
    // 是否展开
    private boolean expanded;

    public UiTreeNode(T value, Component label) {
        this.value = value;
        this.label = label;
    }

    // 追加子节点
    public UiTreeNode<T> addChild(UiTreeNode<T> child) {
        children.add(child);
        return this;
    }

    // 新建并追加子节点，返回子节点
    public UiTreeNode<T> addChild(T childValue, Component childLabel) {
        UiTreeNode<T> child = new UiTreeNode<>(childValue, childLabel);
        children.add(child);
        return child;
    }

    // 业务值
    public T value() {
        return value;
    }

    // 显示文本
    public Component label() {
        return label;
    }

    // 子节点（只读）
    public List<UiTreeNode<T>> children() {
        return Collections.unmodifiableList(children);
    }

    // 是否叶子节点
    public boolean isLeaf() {
        return children.isEmpty();
    }

    // 是否展开
    public boolean isExpanded() {
        return expanded;
    }

    // 设置展开状态
    public UiTreeNode<T> setExpanded(boolean expanded) {
        this.expanded = expanded;
        return this;
    }

    // 切换展开状态
    public UiTreeNode<T> toggleExpanded() {
        this.expanded = !expanded;
        return this;
    }

    // 递归展开自身与全部后代
    public void expandAll() {
        expanded = true;
        for (UiTreeNode<T> child : children) {
            child.expandAll();
        }
    }

    // 递归折叠自身与全部后代
    public void collapseAll() {
        expanded = false;
        for (UiTreeNode<T> child : children) {
            child.collapseAll();
        }
    }
}
