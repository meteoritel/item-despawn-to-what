/**
 * 文本度量、悬停滚动、语法分类、文本值与局部选择和历史契约。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与 kit 外当前无直接生产引用；公开返回链随用途说明；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code TextMeasurer}: host</li>
 *   <li>{@code TextScroll}: host</li>
 *   <li>{@code UiJsonText}: host</li>
 *   <li>{@code UiLabelPresentation}: host</li>
 *   <li>{@code UiTextAcceptance}: library；用途：为自定义文本 adapter 提供独立码点接纳策略；UiTextEditSession.acceptance 返回，kit 文本控件配置码点上限、过滤及溢出通知；当前无直接生产引用</li>
 *   <li>{@code UiTextEditSession}: library；用途：自定义原版文本 adapter 的编辑与历史会话；当前无直接生产引用；kit 内控件复用</li>
 *   <li>{@code UiSingleLineEditor}: library；用途：自定义单行文本 adapter 与数值组合共用提交、取消和交互结束契约；当前无直接生产引用</li>
 *   <li>{@code UiTextValue}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;
