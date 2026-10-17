# UI kit 预览与灯箱（`client/ui/kit`）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| UI kit 预览与灯箱 | `client/ui/kit` | 2 |

## 2. 模块：预览与灯箱

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：8（`UiEntityPreview`、`UiBlockPreview`、`UiModelBounds`、`UiCarousel`、`UiSpinner`、`UiLightbox`、`UiImageView`、`LightboxImage`）

### 2.2 职责

- 绘制宿主已创建的实体，并按 renderer 提交的顶点测量模型范围（`UiEntityPreview`、`UiModelBounds`）。
- 用真实方块 renderer 绘制方块模型（`UiBlockPreview`）。
- 按宿主时间轮播图标与播放加载动画（`UiCarousel`、`UiSpinner`）。
- 提供灯箱外壳：遮罩、内容视口、标题与描述、控制按钮、焦点与关闭语义（`UiLightbox`）。
- 提供灯箱的图片内容实现与图片描述记录（`UiImageView`、`LightboxImage`）。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiEntityPreview` | class | `render(GuiGraphics, UiRect, T, float, float, float, float)`、`measure(T)`、`render(GuiGraphics, UiRect, T, float, UiModelBounds)` | 实体预览绘制与测量 |
| `UiBlockPreview` | class | `measure(BlockState)`、`render(GuiGraphics, UiRect, BlockState, float, UiModelBounds)` | 方块模型预览 |
| `UiModelBounds` | record | 组件 `centerX`、`centerY`、`centerZ`、`width`、`height`、`depth`；`scale(UiRect, float)`、`measure(Renderer)`；`Renderer` 的 `render(PoseStack, MultiBufferSource)` | 顶点包围盒与适框 |
| `UiCarousel` | class | `UiCarousel(List<UiIcon>)`、`index(long)`、`select(int, long)`、`step(int, long)`、`render(GuiGraphics, UiRect, long)` | 图标轮播 |
| `UiSpinner` | class | `render(GuiGraphics, int, int, long, int)` | 加载动画 |
| `UiLightbox` | class | `UiLightbox(Labels, Content, Runnable)`、`setContent`、`setGallery`、`setText`、`setStyle`、`setTextColor`、`setMaskColor`、`setSurface`、`setIcons`、`setBarHint`、`setMaskClickCloses`、`viewport`、`setBounds`、`invalidateResources`、`onClosed`、`render`、`mouseClicked`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`charTyped`；`Labels`、`Gallery`、`Content` 三个嵌套契约 | 灯箱外壳 |
| `UiImageView` | class | `UiImageView(LightboxImage)`、`UiImageView(LightboxImage, Component)`、`UiImageView(LightboxImage, Component, boolean)`、`contentWidth`、`contentHeight`、`setViewport`、`fit`、`zoom`、`zoomBy`、`panBy`、`mousePressed`、`mouseReleased`、`zoomPercent`、`hit`、`placeholder`、`invalidateResources`、`render` | 图片内容视图 |
| `LightboxImage` | record | 组件 `id`、`texture`、`u`、`v`、`width`、`height`、`textureWidth`、`textureHeight`、`title`、`description`；静态工厂 `whole` | 灯箱图片描述 |

### 2.4 依赖关系

- 依赖本模组：无。本分册 8 个文件的 import 中 `com.meteorite.*` 命中数为 0；同包类型直接引用，不产生 import。
- 依赖外部：`net.minecraft.`（8 个文件，21 处）、`org.jetbrains.`（6 个文件，7 处）、`java.util.`（4 个文件，5 处）、`com.mojang.`（3 个文件，4 处）、`org.joml.`（2 个文件，2 处）。
- 依赖外部（精确类）：`com.mojang.` 的 4 处 import 只涉及 `com.mojang.blaze3d.platform.Lighting`（2 处）、`com.mojang.blaze3d.vertex.PoseStack`（1 处）、`com.mojang.blaze3d.vertex.VertexConsumer`（1 处）。
- 被依赖：`client/ui/shared`（4 个文件，6 处）、`client/ui/widget`（1 个文件，1 处）。

### 2.5 关键实现位置

- `UiModelBounds#measure`：用只累计顶点的 `VertexConsumer` 测量模型范围，非有限顶点被跳过。
- `UiModelBounds#scale`：用水平对角直径与俯仰后的纵向投影取整圈最小缩放，结果再乘 0.90。
- `UiEntityPreview#measure`：调用实体 renderer 并带 render offset 测量包围盒。
- `UiEntityPreview#render`：设置裁剪与矩阵后直接调用实体 renderer，结束时恢复相机朝向与物品光照。
- `UiBlockPreview#render`：在顶点矩阵上压缩 z 比例，法线保留等比缩放后的朝向。
- `UiCarousel#index`：以宿主时间除以固定间隔并取模，手动选择重置计时起点。
- `UiLightbox#layout`：计算面板、内容视口、底部控制栏与按钮位置，并按视觉顺序登记焦点目标。
- `UiLightbox#applyDeferredFit`：内容原始尺寸未知时延后到尺寸就绪的那一帧再求 fit。
- `UiLightbox#readableTextOn`：在近白与近黑两档文本色中按 WCAG 对比度取更高的一档。
- `UiLightbox#renderBar`：按面板宽度与图集状态决定页码、缩放读数与提示是否绘制。
- `UiImageView#zoom`：以指针下的内容点为锚点换算新偏移。
- `UiImageView#clampOffsets`：目标尺寸不足视口时该轴偏移归零，超过时钳制在可平移范围内。
- `UiImageView#recheckIfMissing`：缺图时按固定帧数间隔重查资源可用性。

### 2.6 代码现状（可核对）

- `UiLightbox#setBounds` 把负宽高钳到 1；`UiRect` 构造器在宽高为负时抛 `IllegalArgumentException`，见 [文档与控件分册](document-and-controls.md)。
- `UiLightbox` 不实现 `UiInputTarget`，输入方法为 `mouseClicked(double, double, int)`、`mouseDragged(double, double, int, double, double)`、`mouseReleased(double, double, int)`、`mouseScrolled(double, double, double)`、`keyPressed(int, int, int)`、`charTyped(char, int)`；`charTyped` 恒返回 `false`。
- `UiLightbox` 的 `Labels#zoomReadout(int)` 是 default 方法，默认返回 `Component.literal(percent + "%")`；kit 不持有文案 key，其余文案全部由宿主实现。
- `UiLightbox` 的 Tab 顺序按视觉顺序登记为关闭、上一张、下一张、缩小、放大、适应窗口；图集只在一项时不出导航按钮。
- `UiLightbox` 的遮罩默认色 `0xC0101010`，面板边距 12，`BAR_HEIGHT` 为 22，按钮边长 16、间距 2。
- `UiLightbox` 的 JavaDoc 引用 `OverlayLayer` 与 `LightboxOverlay`，全仓没有同名类型声明。
- `UiLightbox` 在签名与实现中使用全限定名 `java.util.Map` 与 `org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE`；同包 `UiControlGroup`、`UiTreeEditor` 用 import 引入 `Map`，`UiIconStrip`、`UiInputContext` 等用 import 引入 `GLFW`。
- `UiLightbox#mouseClicked` 先清焦点再分派，鼠标点击不夺取键盘焦点。
- `UiLightbox#render` 的 tooltip 来源顺序是控件优先、内容命中其次，两者都不会重复渲染同一坐标。
- `UiImageView#hit` 恒返回 `null`，图片内容没有可命中行。
- `UiImageView` 的缩放步长是 1.25，缩放范围 `[0.05, 8.0]`；`allowUpscale` 默认 `false`，`fit` 不放大原像素尺寸。
- `UiImageView` 缺图时按 `RECHECK_INTERVAL`（20 帧）间隔重查资源。
- `UiCarousel#index` 的自动切换间隔是字面量 1500（毫秒），没有提取为常量。
- `UiSpinner#render` 使用字面量 1800（毫秒周期）、0.34（相邻圆点角差）与 35（透明度递减量），没有提取为常量。
- `UiEntityPreview` 与 `UiBlockPreview` 捕获 `RuntimeException` 后返回 `false` 或 `null`，不区分异常原因；实体预览的绘制区域宽或高不超过 2 像素时直接返回 `false`。
- `UiBlockPreview` 的俯仰角固定为 22 度的弧度值，渲染时满亮、无环境遮蔽与落地阴影。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiEntityPreview` | 绘制宿主已创建的实体；不创建、不 tick、不加入世界 | `render`、`measure` |
| `UiBlockPreview` | 用真实方块 renderer 绘制方块模型，固定俯仰与满亮 | `measure`、`render` |
| `UiModelBounds` | 以 renderer 提交的顶点测量模型包围盒并给出适框缩放 | `measure`、`scale` |
| `UiCarousel` | 固定尺寸图标轮播：宿主时间驱动自动切换与手动选择 | `index`、`select`、`step` |
| `UiSpinner` | 宿主时间驱动的追逐圆点动画，无贴图 | `render` |
| `UiLightbox` | 灯箱外壳：遮罩、内容视口、标题描述、控制按钮、焦点与关闭语义 | `setContent`、`setGallery`、`render`、`keyPressed` |
| `UiImageView` | 灯箱的图片内容实现：contain 适配、锚点缩放、平移钳制与缺图占位 | `fit`、`zoom`、`panBy`、`placeholder` |
| `LightboxImage` | 灯箱展示的一张图：稳定 ID、贴图区域与可本地化标题/描述 | `whole`、`title`、`description` |

包级说明文件 `package-info.java` 属于整个 `client/ui/kit`，登记在 [UI kit 模块入口](README.md) 第 3 节。

## 4. 相关文档

- [UI kit 模块入口](README.md)
- [UI kit 文档与控件分册](document-and-controls.md)
- [UI kit 输入路由分册](input-routing.md)
- [UI kit 数值控件分册](value-controls.md)
- [UI kit 文本分册](text-controls.md)
- [UI kit 树与画布分册](tree-editing.md)
- [前端实现文档索引](../README.md)
