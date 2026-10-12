# 客户端 UI kit：边界、公开面与接入索引

> 事实来源：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`。
> 本文是 UI kit 的入口：只覆盖依赖边界、公开面与分册索引；具体契约按需读对应分册，不要把整套分册一次读完。
> 定位约定：分册按「类名 + 成员名」引用源码，不写行号（行号会随重构漂移）。
> 相关决策：[ADR-0022](../../../adr/0022-client-ui-kit-adoption.md)（同源引入）、[ADR-0025](../../../adr/0025-reusable-client-ui-kit-boundary.md)（可回流的维护副本与依赖边界）、[ADR-0033](../../../adr/0033-kit-zero-reference-public-api-retention.md)（零引用公开类的保留口径）。

## 1. 定位与依赖边界

kit 是「可回流的维护副本」：宿主可以依赖 kit，kit **绝不反向依赖宿主**。它面向 Minecraft 客户端，不要求成为纯 Java UI 库。

允许依赖：

- Minecraft 客户端通用类型（如 `net.minecraft.client.gui.Font`、`GuiGraphics`、`net.minecraft.network.chat.Component`）；
- Java 标准库、JOML、LWJGL（按键常量）、JetBrains annotations；
- 精确允许 `com.mojang.blaze3d.platform.Lighting` 与 `com.mojang.blaze3d.vertex` 下的 `PoseStack`、`VertexConsumer`；不放开它们的父包。

禁止依赖：

- 本模组其它包：`core`、`client.edit`、`client.net`、`client.ui.theme`、`client.ui.screen`、`client.ui.widget`，以及 `Constants`；
- 平台 API：`net.fabricmc.*`、`net.neoforged.*`；
- Gson 业务对象：`com.google.gson.*`。

同一 kit 包内的互相引用是允许的。边界由脚本强制校验：

```powershell
powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1
```

- 退出码 0 = 通过；1 = 存在违规；2 = 目录缺失或没有 `.java`（配置错误）。
- 规则：`import` 白名单为 `java.` / `javax.` / `net.minecraft.` / `org.jetbrains.` / `org.joml.` / `org.lwjgl.`；另精确允许 `com.mojang.blaze3d.platform.Lighting`；`com.meteorite.` 只允许 kit 自身包；`net.fabricmc.` / `net.neoforged.` / `com.google.gson.` 一律禁用；不在 `import` 行上的全限定引用同样扫描（含 javadoc 里的 FQ 名）。
- 可用 `-KitPath` 指向其它目录做自测：`powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1 -KitPath <目录>`。

## 2. 公开入口分组

| 分组 | 类型 |
| --- | --- |
| 内容排版 | `UiDocument`、`UiNode`、`UiIcon`、`UiTransform`、`UiMetrics`、`UiPointer`、`TextMeasurer`、`TextScroll` |
| 控件与交互 | `UiControl`、`UiControlStyle`、`UiControlGroup`、`UiControlRole`、`UiScrollView`、`UiLinearLayout`、`UiFlowLayout`、`UiTextSelection`、`UiTextHistory`、`UiHistoryDispatcher`、`UiHistoryShortcut` |
| 实体与加载预览 | `UiEntityPreview`、`UiBlockPreview`、`UiModelBounds`、`UiCarousel`、`UiIcon.Rendered`、`UiSpinner` |
| 输入与捕获 | `UiInputContext`、`UiInputTarget`、`UiInputRouter`、`UiInputCapture`、`UiValueInteraction` |
| 数值与滑块 | `UiNumberPolicy`、`UiNumbers`、`UiSliderWindow`；`UiScalarSlider`、`UiRangeSlider`、`UiRangeValue`、`UiRangeEnd`、`UiCyclicRange` |
| 滑块样式与样式注入 | `UiSliderStyle`、`UiSliderPainter`、`PixelSliderPainter`、`UiSliderRole`、`UiSliderSkin`、`UiStyleSource` |
| 焦点 | `UiFocusTarget`、`UiFocusManager` |
| 灯箱与几何 | `UiLightbox`、`UiImageView`、`LightboxImage`；`UiRect`、`UiTarget`、`UiAction`、`UiNineSlice` |
| 画布与树布局 | `UiTidyTreeLayout`、`UiDotGrid`、`UiOrthoLink`、`UiRubberBand`、`UiIconSheet`、`UiIconStrip`、`UiFloatingPanel` |
| 不可变树编辑 | `UiTreePath`、`UiTreeEditor`、`UiTreeMoveInteraction`、`UiTreeCodec` |
| 导航与绘制层级 | `UiNavigationHistory`、`UiRenderLayers` |

调用示例类：`UiSliderExamples`（无规则语义，只有装配与事件顺序）。

## 3. 分册

| 分册 | 什么时候读 | 内容 |
|---|---|---|
| [input-routing.md](input-routing.md) | 接入指针/键盘/滚轮，或排查「释放被谁消费」 | 事件路由顺序、输入上下文与目标契约、捕获与单值操作生命周期、按下归属与弹出层所有者契约 |
| [value-controls.md](value-controls.md) | 接入滑块/区间/周期控件或调数值策略 | `UiNumberPolicy` 与 `UiSliderWindow`、三类滑块核心、样式注入 |
| [text-controls.md](text-controls.md) | 接入单行/多行文本、码点上限或文本局部撤销 | `UiTextArea` 与 `UiTextInput` 的共同约定、码点长度上限、程序回填、文本历史与快捷键 |
| [tree-editing.md](tree-editing.md) | 接入条件树等不可变树的编辑 | `UiTreePath` / `UiTreeEditor`、单节点与批量结构操作、移动捕获、编解码注入 |
| [rendering-and-preview.md](rendering-and-preview.md) | 接入实体/方块预览、渲染层级或悬停门禁 | 预览 API、模型适框、渲染层级、指针门禁、滚动容器悬停、轮播 |

## 4. 宿主接入清单

- 文本、图标、renderer、主题全部由宿主传入；kit 不硬编码 `gui.itemdespawntowhat.*` 等本模组语言 key。
- HUD/GUI 文本使用本地化 key，并同步 `en_us.json` 与 `zh_cn.json`。
- 宿主提供指针/键盘快照（`UiInputContext`），不把全局输入状态塞进 kit；路由顺序与「同一事件只被消费一次」见 [input-routing.md](input-routing.md)。
- 数值控件的合法域与显示窗口分离，显示窗口不得当作提交硬限额；见 [value-controls.md](value-controls.md)。
- 文本长度上限一律按 Unicode 码点判定；见 [text-controls.md](text-controls.md)。
- 树编辑只绑定宿主节点，不创建第二份业务树；见 [tree-editing.md](tree-editing.md)。
- 预览、渲染层级与悬停门禁见 [rendering-and-preview.md](rendering-and-preview.md)。

## 5. 校验与拆包就绪门槛

校验顺序（与项目验证协议一致）：

1. 用 IDE 语言服务检查改动的 Java 文件；
2. `powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1` 校验依赖边界（依赖边界须无违规）；
3. `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`。

「拆包就绪门槛」（本仓库暂不把 kit 拆成独立 Gradle 模块/子包的理由）：

- 依赖边界可自动校验（第 1 节脚本），且当前无违规；
- 公开 API 有真实宿主示例（`UiSliderExamples`）与接入文档（本目录）；
- 样式、文本、图标、renderer 均可由外部注入，kit 不含本模组业务；
- 发布坐标、artifact/version、许可文本、跨 Minecraft 版本与多 loader 发行方式尚未确定，`api/` 与 `internal/` 子包拆分被评估为收益低于迁移成本，因此当前只冻结边界与检查方式，不发布、不用绝对路径依赖来源仓库（与 ADR-0025 一致）。

## 6. 已知限制

- 滑块、区间与周期三类核心均已接线使用：`UiSlider`（委托 `UiScalarSlider`）由表单的数值控件创建，`UiRangeBar` / `UiCyclicTimeBar` 分别委托 `UiRangeSlider` / `UiCyclicRange`，并在触发页的区间与时间条件面板中实例化。
- 区间与周期核心只提供逻辑与几何，高亮与时间条渲染由宿主完成。
- 不在 kit 内保证 1.21.1 以外的 Minecraft 版本兼容。
- 零引用公开面：`UiFloatingPanel`（浮动面板）、`UiNavigationHistory`（导航历史）、`UiLinearLayout`（线性布局）、`UiInputRouter`（输入路由）属通用预留面，`UiSliderExamples` 是滑块装配示例（无规则语义，只有装配与事件顺序）；这 5 个类当前无生产引用，按 [ADR-0033](../../../adr/0033-kit-zero-reference-public-api-retention.md) 保留并登记，不视为悬空。
- 数值入口两套语义不可互换：容差版 `UiNumbers.plain(double, int)`（按 `INTEGER_TOLERANCE` 消除浮点噪声）用于接受尾数抖动的读数，当前生产消费点是拖动中的滑块读数 `UiScalarSlider.formatValue()`；需要精确还原草稿值的显示点用严格入口 `UiNumbers.plainExact(double, int[, double])`、`isExactIntegral(double, double)`、`trimTrailingZeros(String)`、`roundedText(double)`（取 `Math.round` 语义，不等于 `%.0f` 的 HALF_UP）、`parseFinite(String)` / `parseInt(String)`，表单控件与区间条已改用严格入口。
