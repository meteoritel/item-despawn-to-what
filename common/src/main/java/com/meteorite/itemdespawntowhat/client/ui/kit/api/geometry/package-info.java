/**
 * GUI 矩形、内容坐标变换与指针门控；几何换算与内容命中各有明确入口。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiPointer}: host</li>
 *   <li>{@code UiRect}: host</li>
 *   <li>{@code UiTransform}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry;
