/**
 * 图标场景的主体快照、异步编译、实例、转台取景与资源生命周期；单向依赖基础能力。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与 kit 外当前无直接生产引用；公开返回链随用途说明；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 * <li>{@code PreviewBudget}: host</li>
 *   <li>{@code PreviewMotion}: host</li>
 *   <li>{@code PreviewScene}: library；用途：场景句柄；状态与包围可查询，资源释放由创建它的 PreviewScenes 统一管理；宿主经 request 返回链查询 state/reason；当前无直接生产引用</li>
 *   <li>{@code PreviewScenes}: host</li>
 *   <li>{@code PreviewState}: host</li>
 *   <li>{@code PreviewStructure}: host</li>
 *   <li>{@code PreviewSubject}: host</li>
 *   <li>{@code UiSceneCamera}: library；用途：单次绘制的相机角度，单位为弧度；缩放由视图按整圈包围计算；当前无直接生产引用</li>
 *   <li>{@code UiSceneStyle}: host</li>
 *   <li>{@code UiSceneView}: library；用途：唯一三维取景入口：整圈适框、裁剪、光照与 GUI 状态成对恢复；当前无直接生产引用</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.preview;
