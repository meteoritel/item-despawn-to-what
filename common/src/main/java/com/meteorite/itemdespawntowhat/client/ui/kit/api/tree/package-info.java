/**
 * 与业务节点无关的树编辑、编解码注入、投影、视图状态、导航与移动交互。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiTreeCodec}: host</li>
 *   <li>{@code UiTreeEditor}: host</li>
 *   <li>{@code UiTreeMoveInteraction}: host</li>
 *   <li>{@code UiTreeNavigation}: host</li>
 *   <li>{@code UiTreePath}: host</li>
 *   <li>{@code UiTreeProjection}: host</li>
 *   <li>{@code UiTreeRowMarker}: host</li>
 *   <li>{@code UiTreeViewState}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.tree;
