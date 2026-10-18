# UI kit 模块入口（`client/ui/kit`）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| UI kit | `client/ui/kit` | 2 |

本包的 69 个公开顶层类型由 6 篇分册覆盖，逐类型的分册归属见第 3 节，分册文档链接见第 4 节。

## 2. 模块：UI kit

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖：70 个 `.java` 文件（69 个公开顶层类型 + `package-info.java`）

### 2.2 职责

- 提供与宿主无关的客户端 UI 基础件：声明式文档排版、固定矩形控件与控件组、布局几何、图标、滚动视口与绘制层级。
- 通过注入类型接收样式、图标与文本测量：`UiControlStyle`、`UiSliderSkin`、`UiIcon`、`TextMeasurer`，不读全局主题。
- 定义输入契约、输入捕获、焦点与导航骨架，不持有模态栈。
- 提供实体、方块、模型预览与灯箱图片查看等可复用渲染入口。

### 2.3 对外接口与导出内容

成员级签名见各分册的「2.3 对外接口与导出内容」，本表只列出公开类型与分册归属。

| 分组 | 公开类型 | 说明 |
|---|---|---|
| 文档与排版 | `UiDocument`、`UiNode`、`UiTransform`、`UiMetrics`、`UiFlowLayout`、`UiLinearLayout` | `UiNode` 内含 `Row`、`InlineIcon`、`Gap`、`Divider`、`FrameSpec`、`Frame`；`UiFlowLayout` 内含 `Wrap`；`UiLinearLayout` 内含 `Axis`、`SizeRule`、`Child` |
| 控件与控件组 | `UiControl`、`UiControlGroup`、`UiControlStyle`、`UiControlRole`、`UiStyleSource`、`UiIcon`、`UiIconSheet`、`UiIconStrip`、`UiFloatingPanel` | `UiControlStyle` 内含 `State`；`UiIcon` 内含 `Rendered`、`Painter`、`Item`、`Sprite`；`UiIconStrip` 内含 `Orientation`、`Item`；`UiFloatingPanel` 内含 `Dock`、`Content` |
| 几何与命中 | `UiRect`、`UiTarget`、`UiAction`、`UiNineSlice`、`UiPointer` | `UiTarget` 内含 `Kind`；`UiAction` 是函数式接口 |
| 滚动与绘制 | `UiScrollView`、`UiRenderLayers`、`UiProgressBar` | `UiProgressBar` 内含 `Style` |
| 预览与灯箱 | `UiEntityPreview`、`UiBlockPreview`、`UiModelBounds`、`UiCarousel`、`UiSpinner`、`UiLightbox`、`UiImageView`、`LightboxImage` | `UiModelBounds` 内含 `Renderer`；`UiLightbox` 内含 `Labels`、`Gallery`、`Content` |
| 输入与焦点 | `UiInputContext`、`UiInputTarget`、`UiInputRouter`、`UiInputCapture`、`UiValueInteraction`、`UiFocusTarget`、`UiFocusManager`、`UiNavigationHistory`、`UiHistoryDispatcher` | `UiInputCapture` 内含 `EndReason`、`Target`；`UiInputRouter` 内含 `PointerPhase`；`UiFocusManager` 与 `UiValueInteraction` 各含 `Listener`；`UiHistoryDispatcher` 内含 `KeyHandler` |
| 数值与滑块 | `UiValueTextBinding`、`UiNumberPolicy`、`UiNumbers`、`UiSliderWindow`、`UiScalarSlider`、`UiRangeSlider`、`UiRangeValue`、`UiRangeEnd`、`UiCyclicRange`、`UiSliderStyle`、`UiSliderPainter`、`UiSliderSkin`、`UiSliderRole`、`PixelSliderPainter`、`UiSliderExamples` | `UiCyclicRange` 内含 `Tick`、`Marker`；`UiSliderPainter` 内含 `Frame`；`UiRangeSlider` 内含 `Listener` |
| 文本 | `UiTextHistory`、`UiTextSelection`、`UiHistoryShortcut`、`TextMeasurer`、`TextScroll`、`UiJsonText` | `UiTextHistory` 内含 `Snapshot`；`UiTextSelection` 内含 `Target`；`UiJsonText` 只做 JSON 词法分类与按类别绘制 |
| 树与画布 | `UiTreeEditor`、`UiTreePath`、`UiTreeCodec`、`UiTreeMoveInteraction`、`UiTidyTreeLayout`、`UiDotGrid`、`UiOrthoLink`、`UiRubberBand` | `UiTreeEditor` 内含 `Adapter`、`Kind`、`Change`、`DropPosition`、`MoveErrors`、`MovePreview`、`Outcome`、`ViewChange`；`UiTreeCodec` 内含 `DecodeResult`；`UiTidyTreeLayout` 内含 `Source` |

### 2.4 依赖关系

- 依赖本模组：无。kit 全部文件的 import 中 `com.meteorite.*` 命中数为 0，包级依赖矩阵也没有以本包为起点的行。
- 依赖外部：`net.minecraft.`（37 个文件，72 处）、`java.util.`（33 个文件，91 处）、`org.jetbrains.`（29 个文件，30 处）、`org.lwjgl.`（8 个文件，8 处）、`com.mojang.`（3 个文件，4 处）、`org.joml.`（3 个文件，3 处）、`java.math.`（1 个文件，1 处）。
- 依赖外部（精确类）：`com.mojang.` 的 4 处 import 只涉及 `com.mojang.blaze3d.platform.Lighting`（2 处）、`com.mojang.blaze3d.vertex.PoseStack`（1 处）、`com.mojang.blaze3d.vertex.VertexConsumer`（1 处）。
- 依赖外部（无 import）：70 个文件中 56 个含 import，14 个不含 import。
- 被依赖：`client/ui/widget`（26 个文件，133 处）、`client/ui/screen`（20 个文件，61 处）、`client/ui/screen/form`（19 个文件，72 处）、`client/ui/theme`（5 个文件，31 处）、`client/ui/shared`（5 个文件，14 处）、`client/edit`（2 个文件，3 处）、`client/ui/prototype`（1 个文件，3 处）。
- 边界校验：`tools/check-ui-kit-boundaries.ps1` 扫描本目录全部 `.java` 的 import 与全限定引用；import 白名单为 `java.`、`javax.`、`net.minecraft.`、`org.jetbrains.`、`org.joml.`、`org.lwjgl.`，另精确允许 `com.mojang.blaze3d.platform.Lighting`、`com.mojang.blaze3d.vertex.PoseStack`、`com.mojang.blaze3d.vertex.VertexConsumer`；同包互引允许；`com.meteorite.` 只允许 kit 自身包；`net.fabricmc.`、`net.neoforged.`、`com.google.gson.` 禁用；退出码 0 通过、1 存在违规、2 配置错误。

### 2.5 关键实现位置

- `UiDocument#layout`：把声明式节点排成块几何并缓存，脏标记为假时直接复用。
- `UiDocument#hit`：与绘制共用同一份块几何，按内容坐标返回命中目标。
- `UiControl#render`：按自身标志解析交互状态与文字色，只在内容越出矩形时设置裁剪。
- `UiControlGroup#beginUpdate` / `UiControlGroup#obtain` / `UiControlGroup#endUpdate`：按稳定 key 复用控件实例，结束时丢弃未被复用的控件。
- `UiTransform#push` 与 `UiTransform#enableScissor`：进入内容变换并按视口设置裁剪。
- `UiScrollView#push` 与 `UiScrollView#pop`：把绘制切到内容坐标并恢复宿主坐标。
- `UiFloatingPanel#rebuild`：统一重算停靠位置、折叠高度与内容矩形。
- `UiIconStrip#relayout`：按可见格子的最大字形统一格子尺寸并重排。
- `UiRenderLayers#draw`：在相对 z 偏移内执行绘制，并在进出时提交批次、恢复 pose。
- `UiLightbox#layout`：计算面板、内容视口与控制按钮位置，并按视觉顺序登记焦点目标。

### 2.6 代码现状（可核对）

- 目录下 70 个 `.java` 与 69 个公开顶层类型一一对应，`package-info.java` 是唯一没有类型声明的文件。
- `package-info.java` 的说明文本记录发布形式与 `api/`、`internal/` 子包拆分两类事项；源码目录没有子包目录，构建脚本也没有 kit 独立模块。
- 本包 import 中不存在 `com.meteorite.`、`net.fabricmc.`、`net.neoforged.`、`com.google.gson.` 前缀。
- 注释引用 `OverlayLayer`、`LightboxOverlay`（`UiLightbox`）与 `UiTextPalette`（`UiControlStyle`），全仓没有同名类型声明，命中只出现在 javadoc 与生成产物中。
- 命中入口命名不统一：`UiDocument#hit`、`UiControl#hit`、`UiIconStrip#hit` 返回 `UiTarget`；`UiControlGroup#controlAt` 与 `UiControlGroup#targetAt` 分别返回控件与目标；`UiScrollView#hitScrollbar`、`UiScrollView#hitThumb` 返回 `boolean`。
- `UiLightbox` 与 `UiScrollView` 不实现 `UiInputTarget`：前者使用 `mouseClicked(double, double, int)` 与 `mouseScrolled(double, double, double)`，后者使用 `mousePressed(double, double, int)`、`mouseDragged(double)`、`mouseReleased()`。
- kit 内实现 `UiInputTarget` 的类型是 `UiCyclicRange`、`UiFloatingPanel`、`UiIconStrip`、`UiRangeSlider`、`UiScalarSlider`、`UiTextSelection`。
- `UiIcon` 与 `UiNode` 是 sealed interface，`permits` 列表固定为内部子类型。
- `UiControlStyle` 的 JavaDoc 说明 `PARCHMENT` 的普通与悬停背景与 `UiControl` 内的硬编码颜色逐像素相同。
- 公开成员的说明形式不一致：`UiControl#setBounds` 用行注释、`UiControl#setStyle` 与 `UiRect#right` 没有说明、`UiScrollView#setOffset` 没有 JavaDoc。

## 3. 类清单

| 类型 | 分册 | 源码文件 |
|---|---|---|
| `UiDocument` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiDocument.java` |
| `UiNode` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiNode.java` |
| `UiMetrics` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiMetrics.java` |
| `UiTransform` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTransform.java` |
| `UiFlowLayout` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiFlowLayout.java` |
| `UiLinearLayout` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiLinearLayout.java` |
| `UiControl` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiControl.java` |
| `UiControlGroup` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiControlGroup.java` |
| `UiControlStyle` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiControlStyle.java` |
| `UiControlRole` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiControlRole.java` |
| `UiStyleSource` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiStyleSource.java` |
| `UiIcon` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiIcon.java` |
| `UiIconSheet` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiIconSheet.java` |
| `UiIconStrip` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiIconStrip.java` |
| `UiFloatingPanel` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiFloatingPanel.java` |
| `UiRect` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRect.java` |
| `UiTarget` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTarget.java` |
| `UiAction` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiAction.java` |
| `UiNineSlice` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiNineSlice.java` |
| `UiPointer` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiPointer.java` |
| `UiScrollView` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiScrollView.java` |
| `UiRenderLayers` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRenderLayers.java` |
| `UiProgressBar` | [document-and-controls.md](document-and-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiProgressBar.java` |
| `UiEntityPreview` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiEntityPreview.java` |
| `UiBlockPreview` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiBlockPreview.java` |
| `UiModelBounds` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiModelBounds.java` |
| `UiCarousel` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiCarousel.java` |
| `UiSpinner` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSpinner.java` |
| `UiLightbox` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiLightbox.java` |
| `UiImageView` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiImageView.java` |
| `LightboxImage` | [rendering-and-preview.md](rendering-and-preview.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/LightboxImage.java` |
| `UiInputContext` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiInputContext.java` |
| `UiInputTarget` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiInputTarget.java` |
| `UiInputRouter` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiInputRouter.java` |
| `UiInputCapture` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiInputCapture.java` |
| `UiValueInteraction` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiValueInteraction.java` |
| `UiFocusTarget` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiFocusTarget.java` |
| `UiFocusManager` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiFocusManager.java` |
| `UiNavigationHistory` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiNavigationHistory.java` |
| `UiHistoryDispatcher` | [input-routing.md](input-routing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiHistoryDispatcher.java` |
| `UiValueTextBinding` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiValueTextBinding.java` |
| `UiNumberPolicy` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiNumberPolicy.java` |
| `UiNumbers` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiNumbers.java` |
| `UiSliderWindow` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderWindow.java` |
| `UiScalarSlider` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiScalarSlider.java` |
| `UiRangeSlider` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRangeSlider.java` |
| `UiRangeValue` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRangeValue.java` |
| `UiRangeEnd` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRangeEnd.java` |
| `UiCyclicRange` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiCyclicRange.java` |
| `UiSliderStyle` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderStyle.java` |
| `UiSliderPainter` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderPainter.java` |
| `UiSliderSkin` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderSkin.java` |
| `UiSliderRole` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderRole.java` |
| `PixelSliderPainter` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/PixelSliderPainter.java` |
| `UiSliderExamples` | [value-controls.md](value-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiSliderExamples.java` |
| `UiTextHistory` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTextHistory.java` |
| `UiTextSelection` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTextSelection.java` |
| `UiHistoryShortcut` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiHistoryShortcut.java` |
| `TextMeasurer` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/TextMeasurer.java` |
| `TextScroll` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/TextScroll.java` |
| `UiJsonText` | [text-controls.md](text-controls.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiJsonText.java` |
| `UiTreeEditor` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTreeEditor.java` |
| `UiTreePath` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTreePath.java` |
| `UiTreeCodec` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTreeCodec.java` |
| `UiTreeMoveInteraction` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTreeMoveInteraction.java` |
| `UiTidyTreeLayout` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiTidyTreeLayout.java` |
| `UiDotGrid` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiDotGrid.java` |
| `UiOrthoLink` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiOrthoLink.java` |
| `UiRubberBand` | [tree-editing.md](tree-editing.md) | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiRubberBand.java` |
| `package-info.java` | 本文（第 2 节） | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/package-info.java` |

## 4. 相关文档

- [UI kit 文档与控件分册](document-and-controls.md)
- [UI kit 输入路由分册](input-routing.md)
- [UI kit 数值控件分册](value-controls.md)
- [UI kit 文本分册](text-controls.md)
- [UI kit 树与画布分册](tree-editing.md)
- [UI kit 预览与灯箱分册](rendering-and-preview.md)
- [前端实现文档索引](../README.md)
- [客户端层职责](../../layers/client.md)
