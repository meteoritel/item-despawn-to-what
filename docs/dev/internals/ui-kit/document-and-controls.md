# UI kit 文档与控件（`client/ui/kit`）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| UI kit 文档与控件 | `client/ui/kit` | 2 |

## 2. 模块：文档与控件

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：23（`UiDocument`、`UiNode`、`UiMetrics`、`UiTransform`、`UiFlowLayout`、`UiLinearLayout`、`UiControl`、`UiControlGroup`、`UiControlStyle`、`UiControlRole`、`UiStyleSource`、`UiRect`、`UiTarget`、`UiAction`、`UiNineSlice`、`UiScrollView`、`UiIcon`、`UiIconSheet`、`UiIconStrip`、`UiFloatingPanel`、`UiProgressBar`、`UiRenderLayers`、`UiPointer`）

### 2.2 职责

- 把声明式节点排成可绘制、可命中的块几何（`UiDocument`、`UiNode`）。
- 提供一维有界布局与无状态换行几何（`UiLinearLayout`、`UiFlowLayout`）。
- 提供内容坐标变换、档位缩放与裁剪（`UiTransform`）。
- 提供固定矩形控件、控件组与显式层序命中（`UiControl`、`UiControlGroup`）。
- 把样式拆成角色、结构色与宿主注入接口（`UiControlRole`、`UiControlStyle`、`UiStyleSource`）。
- 定义几何与命中基础记录（`UiRect`、`UiTarget`、`UiAction`、`UiNineSlice`、`UiPointer`）。
- 提供物品与贴图图标及其网格切片、图标条（`UiIcon`、`UiIconSheet`、`UiIconStrip`）。
- 提供浮动调板与纵向滚动视口（`UiFloatingPanel`、`UiScrollView`）。
- 提供进度条与作用域内绘制层级（`UiProgressBar`、`UiRenderLayers`）。
- 通过 `UiMetrics` 暴露可选计时计数。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiDocument` | class | `UiDocument(TextMeasurer, boolean)`、`UiDocument(TextMeasurer, UiTransform, int, boolean)`；`transform`、`metrics`、`viewport`、`contentWidth`、`contentHeight`、`setContent(long, Supplier)`、`setContent(List<UiNode>)`、`setViewport`、`invalidateLayout`、`layout`、`render`、`hit` | 声明式文档：版本门控构建、脏标记排版与共用几何 |
| `UiNode` | sealed interface | `NO_PARENT`；子类型 `Row`、`InlineIcon`、`Gap`、`Divider`、`FrameSpec`、`Frame` | 块序列；仅 `Frame` 承载一层子内容 |
| `UiMetrics` | class | `UiMetrics(boolean)`；`enabled`、`builds`、`layouts`、`renders`、`hits`、`buildNanos`、`layoutNanos`、`renderNanos`、`hitNanos` | 可选开发计时计数 |
| `UiTransform` | class | `ofScales(double...)`、`scale`、`zoomIndex`、`scalePercent`、`panX`、`panY`、`toLocalX`、`toLocalY`、`toScreenX`、`toScreenY`、`restore`、`setOrigin`、`setViewport`、`viewport`、`panBy`、`zoomBy`、`fit`、`centerOn`、`reset`、`push`、`enableScissor`、`disableScissor` | 正反坐标变换、档位缩放与裁剪 |
| `UiFlowLayout` | class | `wrap(int[], int, int, int)`；`Wrap` 提供 `size`、`rowCount`、`lineHeight`、`gap`、`bounds(int)`、`height` | 无状态换行几何 |
| `UiLinearLayout` | class | `UiLinearLayout(Axis, int, int)`、`axis`、`spacing`、`setSpacing`、`padding`、`setPadding`、`setChildren`、`setBounds`、`size`、`bounds`、`usedMain`；`Child` 提供 `fixed`、`content`、`remain`、`mainRange`、`crossFixed`、`crossContent`、`withLeading` | 有界一维布局 |
| `UiControl` | class | `configure`、`setBounds`、`bounds`、`setStyle`、`style`、`setEnabled`、`isEnabled`、`setVisible`、`isVisible`、`setSelected`、`isSelected`、`setFocused`、`isFocused`、`setPressed`、`isPressed`、`isHittable`、`canFocus`、`setAccessibleName`、`accessibleName`、`isFocusable`、`hit`、`activate`、`render` | 固定矩形轻量控件 |
| `UiControlGroup` | class | `beginUpdate`、`obtain`、`endUpdate`、`clear`、`size`、`isEmpty`、`controls`、`collectFocusTargets`、`setStyle`、`style`、`render`、`controlAt`、`targetAt`、`renderTooltip`、`mousePressed`、`mouseReleased`、`isPressed`、`isPressCaptured` | 稳定 key 复用与显式层序命中 |
| `UiControlStyle` | record | 组件 `background`、`hoverBackground`、`pressedBackground`、`selectedBackground`、`disabledBackground`、`focusOutline`、`scrollbarTrack`、`scrollbarThumb`；常量 `PARCHMENT`、`DARK`；`background(State)`；`State` 为 `NORMAL`、`HOVER`、`PRESSED`、`SELECTED`、`DISABLED` | 控件结构色 token 组 |
| `UiControlRole` | enum | `SECONDARY`、`PRIMARY`、`DANGER`、`DISABLED`、`LABEL` | 控件语义角色 |
| `UiStyleSource` | interface | `controlStyle(UiControlRole)`、`sliderSkin(UiSliderRole)` | 宿主样式注入接口 |
| `UiRect` | record | 组件 `x`、`y`、`width`、`height`；`right`、`bottom`、`contains(double, double)` | 逻辑矩形 |
| `UiTarget` | record | 组件 `kind`、`index`、`payload`、`rect`、`tooltip`、`action`；`Kind` 为 `ROW`、`ICON` | 唯一命中结果 |
| `UiAction` | interface | `run()` | 点击行为 |
| `UiNineSlice` | record | 组件 `texture`、`size`、`corner`；`render(GuiGraphics, UiRect)` | 九宫格贴图绘制 |
| `UiScrollView` | class | `SCROLLBAR_WIDTH`、`setViewport`、`viewport`、`setContentHeight`、`contentHeight`、`offset`、`maxOffset`、`setOffset`、`setStep`、`step`、`scrollBy`、`contains`、`ensureVisible`、`toContentX`、`toContentY`、`toScreenY`、`push`、`pop`、`setScrollbarVisible`、`isScrollbarVisible`、`hitScrollbar`、`hitThumb`、`isDragging`、`canHoverContent`、`mousePressed`、`mouseDragged`、`mouseReleased`、`renderScrollbar` | 纵向滚动视口 |
| `UiIcon` | sealed interface | `width`、`height`、`render(GuiGraphics, int, int)`；子类型 `Item`、`Sprite`、`Rendered` | 可测量图标 |
| `UiIconSheet` | record | 组件 `texture`、`cellWidth`、`cellHeight`、`columns`、`rows`；`cell(int, int)` | 规则网格切片 |
| `UiIconStrip` | class | `UiIconStrip(Font, Orientation)`、`add(UiIcon, Component, UiAction)`、`add(UiIcon, UiIcon, Component, UiAction)`、`setOrientation`、`setStyle`、`setBounds`、`bounds`、`cellsWidth(List<Item>)`、`render`、`hit`、`mousePressed`、`mouseDragged`、`mouseReleased`、`keyPressed`、`canFocus`、`setFocused`、`isFocused`、`activate`、`accessibleName`；`Item` 提供 `setEnabled`、`setVisible`、`setSelected`、`setIcon`、`isEnabled`、`isVisible`、`isSelected`、`bounds` | 通用图标条 |
| `UiFloatingPanel` | class | `UiFloatingPanel(Font, Component)`、`setContent`、`setStyle`、`setTitleColor`、`setDock`、`setCollapsed`、`isCollapsed`、`setSnapMargin`、`setWorkArea`、`setBounds`、`bounds`、`render`、`mousePressed`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped`、`canFocus`、`setFocused`、`isFocused`、`activate`、`accessibleName`；`Dock` 为 `FREE`、`TOP_RIGHT`、`BOTTOM_SHEET` | 通用浮动调板 |
| `UiProgressBar` | class | `UiProgressBar(Style)`、`reset(double, long)`、`render(GuiGraphics, UiRect, double, boolean, long)` | 平滑进度条 |
| `UiRenderLayers` | class | `ICON`、`FOREGROUND`、`MODAL_STEP`、`draw(GuiGraphics, int, Runnable)`、`item(GuiGraphics, Runnable)` | 作用域内 z 偏移绘制 |
| `UiPointer` | record | 组件 `x`、`y`；`gated(boolean, int, int)` | 渲染指针快照 |

### 2.4 依赖关系

- 依赖本模组：无。本分册 23 个文件的 import 中 `com.meteorite.*` 命中数为 0；同包类型直接引用，不产生 import。
- 依赖外部：`net.minecraft.`（14 个文件，27 处）、`java.util.`（11 个文件，26 处）、`org.jetbrains.`（7 个文件，7 处）、`org.lwjgl.`（1 个文件，1 处）、`org.joml.`（1 个文件，1 处）。
- 无 import 的文件：`UiAction`、`UiControlRole`、`UiControlStyle`、`UiMetrics`、`UiPointer`、`UiRect`、`UiStyleSource`，共 7 个。
- 被依赖：`client/ui/widget`（24 个文件，46 处）、`client/ui/screen`（18 个文件，33 处）、`client/ui/screen/form`（12 个文件，23 处）、`client/ui/theme`（5 个文件，20 处）、`client/ui/shared`（5 个文件，8 处）、`client/ui/prototype`（1 个文件，1 处）。

### 2.5 关键实现位置

- `UiDocument#layout`：一次排版产出块数组、内容宽高与每个块的滚动计时槽。
- `UiDocument#layoutRow`：按缩进、行首图标与行内图标宽度计算文本带与命中目标。
- `UiDocument#branchFor`：按父子行关系推导折线与祖先续行线几何。
- `UiDocument#hitContent`：换算内容坐标后按块二分定位，图标优先于行命中。
- `UiControl#stateFor`：按禁用、按下、悬停、选中、普通的顺序解析结构色状态。
- `UiControl#needsClip`：只在图标或文字越出控件矩形时设置裁剪。
- `UiControlGroup#refreshInteraction`：交互入口先校验按压目标是否仍有效。
- `UiFlowLayout#wrap`：行首项直接入行，其余项超出可用宽度时另起一行。
- `UiLinearLayout#layout`：先为剩余规则子项保留 min，再均分余量并按 min·max 钳制。
- `UiScrollView#thumbHeight`：滑块高度按视口与内容高度比缩放，并保底最小高度。
- `UiScrollView#mousePressed`：点击轨道空白时先把滑块中心对到指针，再记录抓取偏移。
- `UiIconStrip#relayout`：取可见格子两种字形的最大宽高，统一格子尺寸并沿排列方向排布。
- `UiFloatingPanel#rebuild`：按停靠方式重算位置、宽度与折叠后的高度。
- `UiFloatingPanel#snapToEdges`：释放时把面板贴到最近的可用区域边界。
- `UiNineSlice#render`：按四角原生尺寸切分九个四边形并逐次拉伸绘制。
- `UiTransform#pushScissor`：把当前 pose 坐标系中的矩形换算成 GUI 像素后设置 scissor。
- `UiRenderLayers#draw`：提交批次后压栈并平移 z，绘制结束再提交并恢复 pose。

### 2.6 代码现状（可核对）

- 负尺寸口径：`UiRect` 构造器对负宽高抛 `IllegalArgumentException`；`UiControl#setBounds`、`UiDocument#setViewport`、`UiScrollView#setViewport`、`UiTransform#setViewport` 先把负值钳到 0。`UiLightbox#setBounds` 钳到 1，见 [预览与灯箱分册](rendering-and-preview.md)。
- `UiFlowLayout#wrap` 的 `gap` 同时用于行内水平间距与行间垂直间距，参数说明只写「同一行相邻项的间距」。
- `UiScrollView` 的初始视口是 `(0, 0, 1, 1)`；`UiDocument`、`UiControl`、`UiTransform`、`UiFloatingPanel` 的初始矩形是 `(0, 0, 0, 0)`。
- `UiScrollView` 的 `step` 默认 20，`setStep` 把下界钳到 1。
- `UiScrollView#scrollBy` 以正数表示向内容顶部滚动（偏移减小），实际步长恒为一个 `step`。
- `UiControlGroup#mousePressed` 在按下的同一帧调用 `activate`，释放只结束按压捕获；`UiIconStrip#mouseReleased` 只在指针回到按下时的格子才激活。
- `UiControl` 与 `UiControlGroup` 的默认样式是 `UiControlStyle.PARCHMENT`，`UiIconStrip` 与 `UiFloatingPanel` 的默认样式是 `UiControlStyle.DARK`。
- `UiFloatingPanel` 的标题色默认 `0xFFE6E6E6`；结构色取 `background`、`hoverBackground` 与 `scrollbarThumb` 三项。
- `UiIconStrip#cellsWidth` 是静态只读度量，只统计可见格子，不改变布局与命中。
- `UiIcon.Item` 在构造时复制 `ItemStack`；`UiIcon.Rendered` 要求宽高为正；`UiIcon.Sprite` 校验区域落在贴图范围内；`UiIconSheet#cell` 对越界行列抛 `IllegalArgumentException`。
- `UiDocument#setContent(List<UiNode>)` 用行注释说明构建器计时使用另一个重载；两个入口都调用 `UiMetrics#built` 累加构建计数。
- `UiTarget` 的紧凑构造器用 `List.copyOf` 复制提示行；`UiNode.Row` 与 `UiNode.InlineIcon` 的提示行逐条复制。
- `UiPointer#gated` 在门控失败时返回固定屏幕外坐标，其 `x`、`y` 为 `Integer.MIN_VALUE / 2`。
- 公开成员的说明形式不一致：`UiControl#setBounds` 用行注释、`UiControl#setStyle` 与 `UiRect#right` 没有说明、`UiScrollView#setOffset` 没有 JavaDoc。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiDocument` | 声明式文档：内容版本门控构建、脏标记门控排版、绘制与命中复用块几何 | `setContent`、`layout`、`hit` |
| `UiNode` | 声明式块序列：`Row`、`Gap`、`Divider`、`Frame`，一层子内容只由 `Frame` 承载 | `NO_PARENT`、`Row#parentRow` |
| `UiMetrics` | 可选开发计时出口；关闭时不读时钟 | `UiMetrics(boolean)`、`buildNanos` |
| `UiTransform` | 内容与调用方 GUI 坐标的正反变换、档位缩放、平移、适配与裁剪 | `toLocalX`、`zoomBy`、`fit`、`enableScissor` |
| `UiFlowLayout` | 无状态流式换行几何：按可用宽度排出多行矩形 | `wrap`、`Wrap#height` |
| `UiLinearLayout` | 有界一维布局：固定 / 内容 / 剩余三类主轴规则加 min·max 钳制 | `setChildren`、`setBounds`、`bounds` |
| `UiControl` | 固定矩形轻量控件：语义状态、图标与文字绘制、命中与激活 | `configure`、`render`、`activate` |
| `UiControlGroup` | 按显式层序管理控件、返回最上层命中目标与唯一 tooltip | `beginUpdate`、`obtain`、`targetAt` |
| `UiControlStyle` | 控件结构色 token 组：各状态背景、焦点轮廓与滚动条颜色 | `PARCHMENT`、`DARK`、`background` |
| `UiControlRole` | 控件语义角色枚举，颜色由宿主按角色注入 | `SECONDARY`、`PRIMARY` |
| `UiStyleSource` | 宿主样式来源：按角色返回控件样式与滑块外观 | `controlStyle`、`sliderSkin` |
| `UiRect` | 逻辑矩形：右、下边界不参与命中，构造期拒绝负尺寸 | `right`、`contains` |
| `UiTarget` | 唯一命中结果：行或行内图标，矩形位于内容坐标 | `Kind`、`tooltip` |
| `UiAction` | 点击行为函数式接口 | `run` |
| `UiNineSlice` | 九宫格贴图绘制：四角原生尺寸，边与中心各一次拉伸 | `render` |
| `UiScrollView` | 纵向滚动视口：裁剪、坐标换算、偏移钳制与滚动条几何 | `push`、`pop`、`maxOffset`、`canHoverContent` |
| `UiIcon` | 可测量的物品或贴图图标；尺寸显式给出 | `Item`、`Sprite`、`Rendered` |
| `UiIconSheet` | 规则网格贴图切片：按行列切出 `UiIcon.Sprite` | `cell` |
| `UiIconStrip` | 通用图标条：每格复用 `UiControl`，统一格子尺寸与焦点移动 | `add`、`cellsWidth`、`Item` |
| `UiFloatingPanel` | 通用浮动调板：拖动、折叠、贴边吸附与贴底抽屉 | `rebuild`、`setDock`、`Content` |
| `UiProgressBar` | 宿主时间与样式驱动的平滑进度条，含未知总量往返动画 | `reset`、`render` |
| `UiRenderLayers` | 同一作用域内的 z 偏移绘制与物品层级补偿 | `ICON`、`FOREGROUND`、`MODAL_STEP`、`draw` |
| `UiPointer` | 渲染指针快照；门控失败时给出屏幕外坐标 | `gated` |

包级说明文件 `package-info.java` 属于整个 `client/ui/kit`，登记在 [UI kit 模块入口](README.md) 第 3 节。

## 4. 相关文档

- [UI kit 模块入口](README.md)
- [UI kit 预览与灯箱分册](rendering-and-preview.md)
- [UI kit 输入路由分册](input-routing.md)
- [UI kit 数值控件分册](value-controls.md)
- [UI kit 文本分册](text-controls.md)
- [UI kit 树与画布分册](tree-editing.md)
- [前端实现文档索引](../README.md)
