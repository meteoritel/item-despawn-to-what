/**
 * 文本度量、悬停滚动、语法分类、文本值与局部选择和历史契约。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code TextMeasurer}: host</li>
 *   <li>{@code TextScroll}: host</li>
 *   <li>{@code UiJsonText}: host</li>
 *   <li>{@code UiTextAcceptance}: library；用途：为自定义文本 adapter 提供独立码点接纳策略；当前无生产引用</li>
 *   <li>{@code UiTextEditSession}: host</li>
 *   <li>{@code UiTextHistory}: host</li>
 *   <li>{@code UiTextSelection}: library；用途：为自定义文本模型提供独立指针选择；当前无生产引用</li>
 *   <li>{@code UiTextValue}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;
