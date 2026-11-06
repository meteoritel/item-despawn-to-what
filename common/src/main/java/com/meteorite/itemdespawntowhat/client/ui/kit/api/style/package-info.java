/**
 * 宿主样式注入契约、控件角色与不可变结构样式；滑块皮肤由 number 能力提供。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与 kit 外当前无直接生产引用；公开返回链随用途说明；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiControlRole}: host</li>
 *   <li>{@code UiControlStyle}: host</li>
 *   <li>{@code UiStyleSource}: host</li>
 *   <li>{@code UiControlPainter}: host</li>
 *   <li>{@code UiControlSkin}: host</li>
 *   <li>{@code UiSurfaceStyle}: host</li>
 *   <li>{@code UiTooltipSkin}: host</li>
 *   <li>{@code UiThemeMetrics}: host</li>
 *   <li>{@code UiThemeContext}: host</li>
 *   <li>{@code UiMinecraftTheme}: host</li>
 *   <li>{@code UiSurfaceRole}: host</li>
 *   <li>{@code UiSurfaceSkin}: host</li>
 *   <li>{@code UiThemes}: library；用途：宿主组合完整主题；当前无直接生产引用</li>
 *   <li>{@code UiSkinImages}: library；用途：sprite/九宫格状态皮肤工厂；当前无直接生产引用</li>
 *   <li>{@code UiThemePublication}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;
