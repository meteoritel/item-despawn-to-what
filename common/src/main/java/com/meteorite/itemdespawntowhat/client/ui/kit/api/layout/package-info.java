/**
 * 声明式文档、节点与布局算法，以及供宿主读取的排版快照和度量。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiDocument}: library；用途：声明式文档：内容版本门控构建，脏标记门控排版，绘制与命中复用同一份几何缓存；当前无生产引用</li>
 *   <li>{@code UiFlowLayout}: host</li>
 *   <li>{@code UiLayoutNode}: host</li>
 *   <li>{@code UiLayoutSource}: host</li>
 *   <li>{@code UiLinearLayout}: library；用途：有界横纵布局：把子项按显式尺寸规则排成一行或一列，产出逻辑矩形，供宿主摆放控件与原生输入框；当前无生产引用</li>
 *   <li>{@code UiMetrics}: library；用途：可选开发计时出口；生产关闭时不读取时钟，计数与最近耗时不创建逐帧快照；当前无生产引用</li>
 *   <li>{@code UiNode}: library；用途：声明式文档节点与单层 Frame 内容；当前无生产引用</li>
 *   <li>{@code UiTidyTreeLayout}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.layout;
