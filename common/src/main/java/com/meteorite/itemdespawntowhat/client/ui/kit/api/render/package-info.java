/**
 * 通用图标、文本、绘制层、画布绘制原语与有界预览缓存；资源和文案由宿主注入。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与 kit 外当前无直接生产引用；公开返回链随用途说明；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiDotGrid}: host</li>
 *   <li>{@code UiFlatText}: host</li>
 *   <li>{@code UiIcon}: host</li>
 *   <li>{@code UiIconSheet}: host</li>
 *   <li>{@code UiModelBounds}: library；用途：以 renderer 实际提交的顶点测量模型，不使用实体碰撞框或方块形状推断可视范围；当前无直接生产引用</li>
 *   <li>{@code UiNineSlice}: host</li>
 *   <li>{@code UiOrthoLink}: library；用途：正交折线连接：父节点底部到子节点顶部，按「竖 → 横 → 竖」三段绘制，屏幕像素取整；当前无直接生产引用</li>
 *   <li>{@code UiPreviewCache}: host</li>
 *   <li>{@code UiRenderLayers}: host</li>
 *   <li>{@code UiRubberBand}: host</li>
 *   <li>{@code UiSpinner}: host</li>
 *   <li>{@code UiTooltipPlacement}: library；用途：为提示面板提供显式定位偏好与越界翻转，默认宿主通过 UiTooltipPanel 使用；当前无直接生产引用</li>
 *   <li>{@code UiTooltipPanel}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;
