/**
 * UI kit：Java 声明式客户端 UI 组件集（当前公开边界，尚未拆包发布）。
 *
 * <p><b>公开入口</b>（宿主直接依赖，当前在本项目内开发与测试）：</p>
 * <ul>
 *   <li>内容排版：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiDocument}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNode}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiMetrics}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.TextMeasurer}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiJsonText}（JSON 语法着色：按词法分类，供只读代码视图按类别取色）；
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout}（按可用宽度换行的流式排布，
 *       测量与实际摆放共用同一份几何）</li>
 *   <li>控件与交互：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControl}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlGroup}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLinearLayout}</li>
 *   <li>宿主样式注入：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiStyleSource}（宿主主题实现、
 *       控件消费；kit 不持有全局主题）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole}（控件结构样式的语义角色）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderSkin}（滑块样式与画法成对注入）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole}（滑块外观档位）；
 *       取值类型为既有的 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle} 与
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter}</li>
 *   <li>树编辑：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor}（节点操作、绑定与视图事件）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath}（节点路径）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeCodec}（宿主编解码注入）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeMoveInteraction}（捕获、移动预览、释放提交与取消）；
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeProjection}（结构版本的先序索引）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeViewState}（各视图独立的选择与折叠）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeNavigation}（可见行导航）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeRowMarker}（缩进、命中与展开标记）</li>
 *   <li>输入作用域：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputScope}（内容、唯一焦点与持续交互）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInputRouter}（宿主选域、统一派发及切换收尾）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession}（来源匹配与完成、取消通知）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction}（值快照与预览、提交、取消）</li>
 *   <li>数值策略：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy}（合法数值域、粗细档与吸附）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow}（轨道显示窗口，可与数值域不同）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers}（数值文本互转：整数直出与定点小数、有限值解析）</li>
 *   <li>数值与文本绑定：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiValueTextBinding}（单值或区间文本的
 *       双向预览、提交、取消、无净变化收束与程序回填重入保护，转换规则由宿主注入）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLinkedValues}（驱动值到跟随值的联动写入、手动改写后停止跟随、resetManual 恢复）</li>
 *   <li>滑块：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScalarSlider}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeSlider}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange}；
 *       样式由宿主注入：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.PixelSliderPainter}</li>
 *   <li>文本能力：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTextValue} 与
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTextAcceptance}；滚动条几何由
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollbarGeometry} 共用</li>
 *   <li>文本局部历史：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTextHistory}（文本、光标与选择快照）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTextSelection}（鼠标选择：固定锚点、Shift 扩选与矩形外拖动）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut}（事件修饰键识别撤销与重做）</li>
 *   <li>焦点：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager}（唯一焦点持有者）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState}（控件焦点视图与请求）</li>
 *   <li>导航：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNavigationHistory}（泛型快照、有界历史与有效性过滤）</li>
 *   <li>灯箱：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLightbox}（含 Content / Gallery / Labels）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiImageView}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.LightboxImage}</li>
 *   <li>加载预览：
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSpinner}；
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiPreviewCache} 提供有界 LRU 与显式世代失效，
 *       资源世代由宿主注入，三维预览由 preview 子包持有实例和资源</li>
 *   <li>进度：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiProgressBar}（宿主时间与样式驱动的平滑计数进度与未知总量往返动画）</li>
 *   <li>模型与图标轮播：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds}（实际顶点测量与整圈适框）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiCarousel}；数据解析与测量缓存由宿主负责</li>
 *   <li>绘制与悬停作用域：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers}（补偿原版物品深度、前景和模态层）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText}（无阴影的居中、折行文本与悬停提示面板）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer}（只给当前交互层真实坐标）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView#canHoverContent(double, double)}（滚动条拖动期间屏蔽内容命中）</li>
 *   <li>命中与几何：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRect}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTarget}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiAction}、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiNineSlice}</li>
 *   <li>只读排版快照：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode}（矩形、文案、文本测量宽度与带宽；
 *       {@code clipped()} 表示这行被带宽裁过）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource}（宿主与控件把当前排版导成 GUI 坐标快照）；
 *       快照只读，不参与绘制与命中</li>
 *   <li>画布原语：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiTidyTreeLayout}（固定层高清量树布局）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiDotGrid}（随缩放换步长的点阵网格）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiOrthoLink}（正交折线）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRubberBand}（框选）</li>
 *   <li>图标与调板：{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIconSheet}（规则网格贴图切片）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiIconStrip}（水平功能栏 / 竖直工具列）、
 *       {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiFloatingPanel}（拖动 / 折叠 / 贴边吸附 / 贴底抽屉）</li>
 * </ul>
 *
 * <p><b>消费角色登记</b>：host 表示本项目生产源码直接使用；library 表示面向外部宿主保留的库 API，
 * 本项目当前无直接生产引用；example 表示装配示例。角色只说明消费现状，三类均为公开契约。
 * 嵌套公开契约随所属顶层类型维护。新增类型必须在下表登记一次。</p>
 * <ul>
 *   <li>{@code LightboxImage}: library</li>
 *   <li>{@code PixelSliderPainter}: host</li>
 *   <li>{@code TextMeasurer}: library</li>
 *   <li>{@code TextScroll}: host</li>
 *   <li>{@code UiAction}: host</li>
 *   <li>{@code UiCarousel}: host</li>
 *   <li>{@code UiControl}: host</li>
 *   <li>{@code UiControlGroup}: library</li>
 *   <li>{@code UiControlRole}: host</li>
 *   <li>{@code UiControlStyle}: host</li>
 *   <li>{@code UiCyclicRange}: host</li>
 *   <li>{@code UiDocument}: library</li>
 *   <li>{@code UiDotGrid}: host</li>
 *   <li>{@code UiFlatText}: host</li>
 *   <li>{@code UiFloatingPanel}: library</li>
 *   <li>{@code UiFlowLayout}: host</li>
 *   <li>{@code UiFocusManager}: host</li>
 *   <li>{@code UiFocusTarget}: host</li>
 *   <li>{@code UiFocusState}: host</li>
 *   <li>{@code UiHistoryShortcut}: host</li>
 *   <li>{@code UiIcon}: host</li>
 *   <li>{@code UiIconSheet}: host</li>
 *   <li>{@code UiIconStrip}: host</li>
 *   <li>{@code UiImageView}: library</li>
 *   <li>{@code UiInteractionSession}: host</li>
 *   <li>{@code UiInputContext}: host</li>
 *   <li>{@code UiInputRouter}: host</li>
 *   <li>{@code UiInputScope}: host</li>
 *   <li>{@code UiInputTarget}: host</li>
 *   <li>{@code UiJsonText}: host</li>
 *   <li>{@code UiLayoutNode}: host</li>
 *   <li>{@code UiLayoutSource}: host</li>
 *   <li>{@code UiLightbox}: library</li>
 *   <li>{@code UiLinearLayout}: library</li>
 *   <li>{@code UiLinkedValues}: host</li>
 *   <li>{@code UiMetrics}: library</li>
 *   <li>{@code UiModelBounds}: host</li>
 *   <li>{@code UiNavigationHistory}: library</li>
 *   <li>{@code UiNineSlice}: host</li>
 *   <li>{@code UiNode}: library</li>
 *   <li>{@code UiNumberPolicy}: host</li>
 *   <li>{@code UiNumbers}: host</li>
 *   <li>{@code UiOrthoLink}: library</li>
 *   <li>{@code UiPointer}: host</li>
 *   <li>{@code UiPreviewCache}: host</li>
 *   <li>{@code UiProgressBar}: host</li>
 *   <li>{@code UiRangeEnd}: host</li>
 *   <li>{@code UiRangeSlider}: host</li>
 *   <li>{@code UiRangeValue}: host</li>
 *   <li>{@code UiRect}: host</li>
 *   <li>{@code UiRenderLayers}: host</li>
 *   <li>{@code UiRubberBand}: host</li>
 *   <li>{@code UiScalarSlider}: host</li>
 *   <li>{@code UiScrollView}: host</li>
 *   <li>{@code UiSliderExamples}: example</li>
 *   <li>{@code UiSliderPainter}: host</li>
 *   <li>{@code UiSliderRole}: host</li>
 *   <li>{@code UiSliderSkin}: host</li>
 *   <li>{@code UiSliderStyle}: host</li>
 *   <li>{@code UiSliderWindow}: host</li>
 *   <li>{@code UiSpinner}: host</li>
 *   <li>{@code UiStyleSource}: host</li>
 *   <li>{@code UiTarget}: host</li>
 *   <li>{@code UiTextValue}: host</li>
 *   <li>{@code UiTextAcceptance}: host</li>
 *   <li>{@code UiScrollbarGeometry}: host</li>
 *   <li>{@code UiTextHistory}: host</li>
 *   <li>{@code UiTextSelection}: host</li>
 *   <li>{@code UiTidyTreeLayout}: host</li>
 *   <li>{@code UiTransform}: host</li>
 *   <li>{@code UiTreeCodec}: host</li>
 *   <li>{@code UiTreeProjection}: host</li>
 *   <li>{@code UiTreeViewState}: host</li>
 *   <li>{@code UiTreeNavigation}: host</li>
 *   <li>{@code UiTreeRowMarker}: host</li>
 *   <li>{@code UiTreeEditor}: host</li>
 *   <li>{@code UiTreeMoveInteraction}: host</li>
 *   <li>{@code UiTreePath}: host</li>
 *   <li>{@code UiValueInteraction}: host</li>
 *   <li>{@code UiValueTextBinding}: host</li>
 * </ul>
 *
 * <p><b>内部实现</b>：公开类型之外只有包内可见的
 * 共享外壳 {@code AbstractRangeCore}（{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange} 与
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeSlider} 共同的输入生命周期与容器几何），
 * 其余内部口径落在各类的非公开成员上——{@link com.meteorite.itemdespawntowhat.client.ui.kit.UiDocument}
 * 的排版缓存与私有几何公式、{@code UiMetrics} 的计时细节等。
 * 它们可以随实现改动而不进变更记录。</p>
 *
 * <p><b>依赖方向</b>：本包只依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL（按键常量）与
 * JetBrains annotations，并精确允许 {@code com.mojang.blaze3d.platform.Lighting}、
 * {@code com.mojang.blaze3d.vertex.PoseStack} 与 {@code com.mojang.blaze3d.vertex.VertexConsumer}；
 * 子包使用的顶点构建、GPU 缓冲与 RenderSystem 类型按边界脚本逐类放行。依赖方向为子包到基础 kit，基础 kit 不引用子包。
 * kit 不 import 任何项目包、不 import 两端 loader API、不引用 {@code Constants.MOD_ID}；
 * {@code client/} 以外的代码不得引用本包。该约束由 {@code tools/check-ui-kit-boundaries.ps1} 检查，
 * 运行方式：{@code powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1}
 * （退出码 0 通过、1 有违规、2 配置错误）。脚本扫描本目录全部 {@code .java} 的 import 与全限定引用：
 * import 白名单为 {@code java.*} / {@code javax.*} / {@code net.minecraft.*} / {@code org.jetbrains.*} /
 * {@code org.joml.*} / {@code org.lwjgl.*}，另精确允许脚本登记的渲染类，不放开其父包；同一 kit 包内互相引用允许；本模组其它包与
 * {@code net.fabricmc.*} / {@code net.neoforged.*} / {@code com.google.gson.*} 一律禁用；
 * 同一脚本还核对本文件的登记清单：kit 顶层 {@code public} 类型缺少唯一的 host/library/example 角色登记即失败。</p>
 *
 * <p><b>宿主适配层</b>：{@code client/ui/theme/}（把主题色映射成 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle} 等 kit 样式后注入）、
 * {@code client/ui/widget/}（组合 kit 核心与项目文案、目录及业务协议）属于宿主层，
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
 * {@code trimToWidth(Font, String, int)}（超宽时截断并补 ASCII 省略号），与悬停滚动入口并存。</p>
 *
 * <p><b>调用示例</b>：无规则语义的装配示例见 {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples}——
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#scalarSlider}（普通小数滑块：精确回填、
 * Shift 细调、方向键合并提交、Esc 取消回退）、
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#rangeSlider}（双端区间：端点选择、可选端点、单端精确编辑）、
 * {@link com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples#cyclicRange}（周期区间：宿主注入周期/刻度/标记）。</p>
 *
 * <p><b>尚未完成</b>：独立 Gradle 模块与发布脚本，需要另立拆包任务；当前只冻结边界与检查方式。
 * 子包拆分（{@code api/} 与 {@code internal/}）已评估并决定不做。</p>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit;
