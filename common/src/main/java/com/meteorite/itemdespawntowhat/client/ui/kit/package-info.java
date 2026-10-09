/***
 * UI kit：Java 声明式客户端 UI 组件集（阶段 E 记录的**公开边界**，尚未拆包发布）。
 *
 * <p><b>公开入口</b>（第三方宿主可直接依赖，签名变更按兼容策略走变更记录）：</p>
 * <ul>
 *   <li>内容排版：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiDocument}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNode}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiMetrics}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.TextMeasurer}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll}</li>
 *   <li>控件与交互：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControl}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlGroup}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLinearLayout}</li>
 *   <li>树编辑：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor}（节点操作、绑定与视图事件）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath}（节点路径）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeCodec}（宿主编解码注入）</li>
 *   <li>输入与捕获：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputRouter}（路由顺序：顶层 modal 作用域 →
 *       捕获目标 → 聚焦控件 → 容器导航，事件只消费一次）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture}（捕获与统一结束原因）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction}（begin / preview / commit / cancel）</li>
 *   <li>数值策略：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy}（合法数值域、粗细档与吸附）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow}（轨道显示窗口，可与数值域不同）</li>
 *   <li>滑块：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScalarSlider}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeSlider}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange}；
 *       样式由宿主注入：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.PixelSliderPainter}</li>
 *   <li>文本局部历史：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTextHistory}（文本、光标与选择快照）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut}（事件修饰键识别撤销与重做）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryDispatcher}（当前作用域优先与宿主历史派发）</li>
 *   <li>焦点：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager}</li>
 *   <li>导航：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNavigationHistory}（泛型快照、有界历史与有效性过滤）</li>
 *   <li>灯箱：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLightbox}（含 Content / Gallery / Labels）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiImageView}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.LightboxImage}</li>
 *   <li>实体与加载预览：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiEntityPreview}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSpinner}；实体创建、缓存和兜底由宿主负责</li>
 *   <li>模型与图标轮播：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds}（实际顶点测量与整圈适框）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiBlockPreview}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiCarousel}；数据解析与测量缓存由宿主负责</li>
 *   <li>绘制与悬停作用域：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers}（补偿原版物品深度、前景和模态层）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer}（只给当前交互层真实坐标）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView#canHoverContent(double, double)}（滚动条拖动期间屏蔽内容命中）</li>
 *   <li>命中与几何：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRect}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiAction}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNineSlice}</li>
 * </ul>
 *
 * <p><b>内部实现</b>（同为 {@code public}，但不承诺兼容，宿主不应依赖）：没有独立的内部类型，
 * 内部口径都落在各类的非公开成员上——{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiDocument}
 * 的排版缓存与私有几何公式、{@code UiMetrics} 的计时细节、{@code UiTransform} 的裁剪工具等。
 * 它们可以随实现改动而不进变更记录。</p>
 *
 * <p><b>依赖方向</b>：本包只依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL（按键常量）与
 * JetBrains annotations，并精确允许 {@code com.mojang.blaze3d.platform.Lighting}、
 * {@code com.mojang.blaze3d.vertex.PoseStack} 与 {@code com.mojang.blaze3d.vertex.VertexConsumer}——不 import 任何项目包、不 import 两端 loader API、不引用 {@code Constants.MOD_ID}；
 * {@code client/} 以外的代码不得引用本包。该约束由 {@code tools/check-ui-kit-boundaries.ps1} 检查，
 * 运行方式：{@code powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1}
 * （退出码 0 通过、1 有违规、2 配置错误）。脚本扫描本目录全部 {@code .java} 的 import 与全限定引用：
 * import 白名单为 {@code java.*} / {@code javax.*} / {@code net.minecraft.*} / {@code org.jetbrains.*} /
 * {@code org.joml.*} / {@code org.lwjgl.*}，另精确允许上述三个渲染类，不放开其父包；同一 kit 包内互相引用允许；本模组其它包与
 * {@code net.fabricmc.*} / {@code net.neoforged.*} / {@code com.google.gson.*} 一律禁用。</p>
 *
 * <p><b>宿主适配层</b>：{@code client/ui/theme/}（把主题色映射成 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle} 等 kit 样式后注入）、
 * {@code client/ui/widget/}（把既有控件委托给 kit 核心并保持公开 API 兼容）与 {@code client/ui/prototype/} 属于宿主层，
 * 允许依赖项目常量与平台服务；它们依赖 kit，而不是反过来。kit 自身不 import {@code theme} 或 {@code widget}。</p>
 *
 * <p><b>公开行为约定</b>：几何入口对负尺寸一律**钳制**而不是抛异常——{@code UiControl.setBounds} 与
 * {@code UiDocument.setViewport} 钳到 ≥0，{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView}
 * 同口径，{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLightbox#setBounds(int, int)} 钳到 ≥1；
 * 只有 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRect} 自身在构造期拒绝负尺寸。
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLightbox} 默认用
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle#DARK} 暗底，文本默认色按结构底色反推，
 * {@code setStyle} 覆盖样式后文本色随之重算（除非先调用 {@code setTextColor}）；
 * 其 {@code Labels} 新增 default 方法 {@code zoomReadout(int)}，既有实现无需改动。
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll} 另有非交互工具方法
 * {@code trimToWidth(Font, String, int)}（超宽时截断并补 ASCII 省略号），与悬停滚动入口并存。
 * 依赖边界、输入路由顺序、捕获与操作生命周期、数值策略、滑块接入示例与 style 注入方式见
 * {@code docs/dev/internals/ui-kit-api.md}。</p>
 *
 * <p><b>调用示例</b>：无规则语义的装配示例见 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples}——
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#scalarSlider}（普通小数滑块：精确回填、
 * Shift 细调、方向键合并提交、Esc 取消回退）、
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#rangeSlider}（双端区间：端点选择、可选端点、单端精确编辑）、
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#cyclicRange}（周期区间：宿主注入周期/刻度/标记）。</p>
 *
 * <p><b>尚未完成</b>：独立 Gradle 模块与发布脚本，需要另立拆包任务；当前只冻结边界与检查方式。
 * 子包拆分（{@code api/} 与 {@code internal/}）已评估并决定不做，理由见
 * {@code docs/dev/internals/ui-kit-api.md} 的「拆包就绪门槛」。</p>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit;
