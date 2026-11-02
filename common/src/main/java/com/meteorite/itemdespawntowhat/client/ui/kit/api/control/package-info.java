/**
 * 通用控件、滚动视口、灯箱与浮动面板；使用输入、几何与样式契约组合交互。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code LightboxImage}: library；用途：灯箱要展示的一张图：稳定 ID、贴图区域（原始宽高显式给出）与可本地化标题/描述；当前无生产引用</li>
 *   <li>{@code UiAction}: host</li>
 *   <li>{@code UiCarousel}: host</li>
 *   <li>{@code UiControl}: host</li>
 *   <li>{@code UiControlGroup}: library；用途：复用控件实例、按层序命中并收集焦点目标；当前无生产引用</li>
 *   <li>{@code UiFloatingPanel}: library；用途：通用浮动调板：标题栏可拖动、可折叠，支持贴边吸附与贴底抽屉，内容由宿主注入；当前无生产引用</li>
 *   <li>{@code UiIconStrip}: host</li>
 *   <li>{@code UiImageView}: library；用途：灯箱的图片内容适配，负责适框、缩放与平移；当前无生产引用</li>
 *   <li>{@code UiLightbox}: library；用途：灯箱外壳：可复用的模态查看器——遮罩、内容视口、标题与描述、控制按钮组、键盘焦点与关闭语义；当前无生产引用</li>
 *   <li>{@code UiProgressBar}: host</li>
 *   <li>{@code UiScrollView}: host</li>
 *   <li>{@code UiScrollbarGeometry}: host</li>
 *   <li>{@code UiTarget}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;
