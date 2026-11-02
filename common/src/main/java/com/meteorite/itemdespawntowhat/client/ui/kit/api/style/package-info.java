/**
 * 宿主样式注入契约、控件角色与不可变结构样式；滑块皮肤由 number 能力提供。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiControlRole}: host</li>
 *   <li>{@code UiControlStyle}: host</li>
 *   <li>{@code UiStyleSource}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;
