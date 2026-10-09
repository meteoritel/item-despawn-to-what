# NOTICE：客户端 UI kit 来源声明

本模组客户端 UI 基础设施（`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`）取自同作者的 Minecraft 模组 **unsuspiciousBlock**（1.21.1 多加载器项目）的客户端 UI kit，属同源复用，不引入任何第三方库依赖。

## 来源

- 源仓库路径：`D:/system_default/desktop/mc_mod_project/unsuspiciousBlock-1.21.1-multi`
- 源目录：`common/src/main/java/com/meteorite/unsuspiciousblock/client/ui/kit/`
- 源提交：`625747e1f34ea957841d7136be4635469687c227`
- 复制日期：2026-10-02
- 目标目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`

## 重命名规则

复制过程为机械文本替换，仅改动以下两类内容：

1. `package` 声明：`com.meteorite.unsuspiciousblock.client.ui.kit` → `com.meteorite.itemdespawntowhat.client.ui.kit`；
2. javadoc 中的全限定名（仅 `package-info.java` 的示例代码块内）。

未改动：类名、字段名、方法签名、实现逻辑、常量值、注释语义。

## 文件清单（23 个，共 2824 行）

| 文件 | 行数 |
| --- | --- |
| `LightboxImage.java` | 46 |
| `package-info.java` | 61 |
| `TextMeasurer.java` | 18 |
| `TextScroll.java` | 131 |
| `UiAction.java` | 7 |
| `UiControl.java` | 204 |
| `UiControlGroup.java` | 187 |
| `UiControlStyle.java` | 40 |
| `UiDocument.java` | 380 |
| `UiFocusManager.java` | 199 |
| `UiFocusTarget.java` | 33 |
| `UiIcon.java` | 40 |
| `UiImageView.java` | 229 |
| `UiLightbox.java` | 517 |
| `UiLinearLayout.java` | 212 |
| `UiMetrics.java` | 24 |
| `UiNavigationHistory.java` | 44 |
| `UiNineSlice.java` | 33 |
| `UiNode.java` | 83 |
| `UiRect.java` | 21 |
| `UiScrollView.java` | 197 |
| `UiTarget.java` | 17 |
| `UiTransform.java` | 101 |

## 依赖与约束

- kit 仅依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL 与 JetBrains annotations；
- 不 import 本项目其他包，不 import Fabric / NeoForge 平台 API，不引用 `Constants.MOD_ID`；
- kit 不依赖 `core.model`，可在 `common` 内独立编译；`client/` 之外不得引用该包。

## 贴图资源

源项目 kit 自带贴图资源（`UiNineSlice` 与 `UiIcon.Sprite` 使用的纹理）。**本次未复制任何贴图资源**：两者的贴图 id 由调用方提供；主题层（`client/ui/theme/`）当前使用纯色像素边框绘制，需要美术资源接入的位置均以 `TODO(美术)` 注释标出。

## 已知差异

来源版本仍为 `625747e1f34ea957841d7136be4635469687c227`（见上文 23 文件清单，该清单**未**改写为「全部来自新提交」）。
本目录按 ADR-0025 作为「记录来源与差异的维护副本」继续演进，因此下表逐条记录与来源版本的差异、差异原因、
公开接口变化与可回流状态；在回流评估完成前，不得再把本目录描述为「与来源等价」。

### P1 差异：输入 / 捕获 / 数值策略 / 滑块核心（2026-10-03）

新增文件（16 个，来源版本中不存在，均可回流；回流前需按来源项目的包名与目录结构重定位）：

| 文件 | 作用 |
| --- | --- |
| `UiInputContext.java` | 指针坐标、按钮与 Shift/Ctrl/Alt 显式快照 |
| `UiInputTarget.java` | 指针 / 拖动 / 释放 / 滚轮 / 键盘 / 字符输入的 default 接收接口 |
| `UiInputRouter.java` | 路由顺序：顶层 modal 作用域 → 捕获目标 → 聚焦控件 → 容器导航，事件只消费一次 |
| `UiInputCapture.java` | 捕获登记与统一结束原因（释放 / 取消 / 隐藏 / 禁用 / 卸载 / 焦点范围切换 / 宿主关闭） |
| `UiValueInteraction.java` | begin / preview / commit / cancel 与起始值、当前值；无变化不提交 |
| `UiNumberPolicy.java` | 合法数值域、粗细档、整数策略、吸附与拖动灵敏度 |
| `UiSliderWindow.java` | 轨道显示窗口（可与合法数值域不同，不用窗口边界拒绝域内值） |
| `UiSliderStyle.java` | 不可变滑块样式（轨道、已填充段、手柄、焦点、禁用、错误、文字槽） |
| `UiSliderPainter.java` | 画法接口与绘制帧 |
| `PixelSliderPainter.java` | 纯色像素画法（不引入贴图资源） |
| `UiScalarSlider.java` | 标量滑块核心（精确回填、用户操作吸附、Shift 细调、键盘合并提交、Esc 回退） |
| `UiRangeEnd.java`、`UiRangeValue.java`、`UiRangeSlider.java` | 双端区间核心（端点身份不交换、可选端点独立于 0、重叠端点可切换） |
| `UiCyclicRange.java` | 周期区间核心（宿主注入周期 / 刻度 / 标记，不读取世界时间） |
| `UiSliderExamples.java` | 无规则语义调用示例（规格 §7） |

上述 23 个来源文件里，仅 `package-info.java` 有更新（新增输入 / 数值 / 滑块的公开入口与调用示例段落，并把宿主适配层改为指向目标项目实际存在的 `client/ui/theme/`、`client/ui/widget/`、`client/ui/prototype/`）；其余 22 个来源文件未改动，无公开接口变化。

### P1 差异：宿主适配层（不在 kit 目录内，但属于 kit 的接入面）

| 文件 | 差异 | 原因 | 公开接口变化 |
| --- | --- | --- | --- |
| `common/.../client/ui/widget/UiWidget.java` | 新增 `default boolean keyReleased(int, int, int)` | 让按住的方向键重复能在抬起时合并为一次提交 | 仅新增 default 方法，既有实现无需改动 |
| `common/.../client/ui/widget/UiSlider.java` | 改为委托 `UiScalarSlider` 的适配层 | 控件逻辑上移到 kit，宿主只做主题 / 快照注入 | 保留全部旧公开签名的返回值形状；新增 `core()`、`trySetValue`、`isBackfillInvalid`、`setError`、`isDragging`、`endInteraction`、`unmount`、`onFocusScopeChanged`、`onHostClosed`；`setValue` 不再按步长吸附（只钳制），`setRange` 不再吸附当前值 |
| `common/.../client/ui/theme/UiTheme.java` | 新增 `public static UiSliderStyle sliderStyle()` | 宿主把主题色调色板注入 kit 样式，kit 不反向依赖主题 | 仅新增静态方法 |

### 可回流状态

- 16 个 kit 新增文件（含 `UiSliderExamples`）与 `package-info.java` 的更新：逻辑与来源项目无关，可直接回流，回流时只做包名重定位与示例标签替换。
- 宿主适配层（`widget/UiSlider.java`、`theme/UiTheme.java`）依赖本模组的调色板与 `Screen` 按键快照，回流前需要按来源项目的主题层改写。
- 尚未回流到来源项目：来源仓库本次未被修改。

### P4 之后：kit 目录本身未再改动（2026-10-03）

- P4（目录图标面板、区间 / 时间条、结构图解、会话恢复、键盘与朗读）与后续 P5-a/P5-c 的改动**全部落在宿主层**（`client/ui/screen/`、`client/ui/widget/`、`client/ui/theme/`）与 `client/edit/`、语言文件；`client/ui/kit/` 内的 16 个新增文件自 P1 之后**未再改动**。`package-info.java` 在 P5-e 仅更新说明文字（把引用的依赖检查脚本与 API 文档指向真实存在的文件），公开 API 与已有差异记录未变（上表 P1 差异仍是最新差异记录）。
- 静态核对（2026-10-03）：kit 目录 112 条 `import` 全部落在声明的白名单内（`java.*` / `net.minecraft.*` / `org.jetbrains.*` / `org.joml.*` / `org.lwjgl.*` / `com.mojang.*`），无任何项目内包与两端 loader API 引用；`UiRect` 构造期对负宽高抛 `IllegalArgumentException`，`UiControl.setBounds` 钳到 ≥0，与 `package-info.java` 的「公开行为约定」一致。

### 已知限制

- **`UiInputRouter` 未接入**：kit 规格 §8.5 要求「没有真实使用到的抽象要么接入、要么延后」。全仓引用仅出现在 `UiInputRouter.java` 自身、`package-info.java` 的公开入口列表与 `UiSliderExamples` 的说明注释中；宿主控件（`client/ui/widget/`）仍各自分发事件。本期记为已知限制，未删除该类，也未声称其已接入。
- **文档引用已落地（P5-e 修复）**：`client/ui/kit/package-info.java` 原引用的 `docs/dev/internals/ui-kit-api.md` 与 `scripts/check-ui-kit-boundaries.ps1` 曾不存在，已在 P5-e 落地为真实文件——新建 [docs/dev/internals/ui-kit-api.md](docs/dev/internals/ui-kit-api.md)（246 行：依赖边界、输入路由、生命周期、数值策略、接入示例与 style 注入）与 [tools/check-ui-kit-boundaries.ps1](tools/check-ui-kit-boundaries.ps1)（137 行：扫描 kit 目录的 import 与全限定引用，违规 exit 1、通过 exit 0），并让 `package-info.java` 指向真实脚本。验证：该脚本对当前 kit 实跑输出「扫描 39 个文件、112 条 import、0 处违规」并 exit 0；对临时构造的违规样本输出 5 处违规并 exit 1（样本已删除）。kit 规格 §8「不能调用不存在的脚本」由此满足。
- 独立 kit 库**本期未发布**：仍只冻结公开边界与依赖检查方式，独立 Gradle 模块与发布脚本需另立拆包任务。


### GUI 功能修复：宿主接入差异（2026-10-03）

本次未修改 `client/ui/kit/` 或来源仓库，改动仍位于宿主层：

| 文件 | 差异 | 原因 | 回流状态 |
|---|---|---|---|
| `widget/UiListView.java` | 新增 `dragIndexAt(double)`、`itemIndexAt(double, double)` 与 `visibleRowBounds(int)`；统一视口偏移、滚动条排除和可见行矩形 | 滚动后拖动排序不能使用可见行号替代实际数据下标 | 通用几何能力；尚未回流 |
| `widget/UiListEditor.java` | 新增 `commitPendingInput()`，复用已有条目提交逻辑 | 切页前保留输入中的列表条目 | 通用输入生命周期能力；尚未回流 |
| `widget/UiConditionTreeEditor.java` | 新增使能状态；收窄类型选择框宽度以适应实际列宽 | 冻结后仍展示树内容，禁止修改；小列宽不自动关闭选择框 | 条件 JSON 属于宿主业务，不提入 kit；尚未回流 |
| `widget/UiModalStack.java` | Space 的通用激活仅交给按钮，其余先由内容控件处理 | 叶参数与文本输入中的空格不能误触提交 | 宿主焦点适配；尚未回流 |

规则页面、可滚动原始详情、本地化与名称解析属于 `screen/` 与 `screen/form/` 业务层，不新增 kit 依赖。依赖边界脚本本次核验通过：39 个文件、112 条 import、0 处违规。

## 2026-10-07：本项目实体预览扩展

新增 `UiEntityPreview` 和 `UiSpinner` 为本项目编写的通用绘制能力，不改写上文同源引入 23 文件的来源提交。`UiIcon` 新增受控 `Rendered` 子类；宿主承担实体创建、缓存/失效、尺寸修正和屏障/本地化兜底。精确放行 `com.mojang.blaze3d.platform.Lighting`，已同步 package-info、API 文档与边界脚本；禁止业务、Gson 和 loader 依赖仍有效。

新增 sealed 子类型可能影响外部穷尽 switch。此轮没有独立模块、版本或库发布；当前维护副本仍在本项目内使用，来源与历史差异记录保持有效。

## 2026-10-07：UI 缺陷修复与模型适框

本项目新增 `UiRenderLayers`、`UiPointer`、`UiCarousel`、`UiModelBounds` 和 `UiBlockPreview`，未修改来源仓库。`UiIcon.Item` 补偿原版 renderItem 的 150 深度，图标统一置于作用域的 20 层；文字/角标采用 40 层，模态作用域间隔 600。`UiScrollView` 增加 `canHoverContent`，鼠标位于滚动条或拖动期间统一阻止内容高亮与提示。宿主模态栈及管理页、原型页已接入作用域坐标门禁。

`UiEntityPreview` 保留原签名，新增实际顶点测量与 `UiModelBounds` 绘制重载。宿主缓存真实可视范围，模型绕实际中心旋转、整圈固定缩放；测量失败的第三方实体 renderer 使用加留边的尺寸估计。方块复用同一适框几何，走原版方块 renderer、满亮和平面光照，无环境遮蔽或落地阴影。绘制深度压缩在图标层附近，防止较大预览穿过文字或面板。

为实际几何测量精确放行 `PoseStack` 和 `VertexConsumer`，不扩大到整个父包。标签成员解析、展开弹窗、名称、tooltip、缓存与规则语义留在宿主；kit 不依赖本模组业务、loader 或 Gson。新增能力均已有真实调用，可回流但尚未回流；无独立库发布。

实测反馈后，`UiBlockPreview` 的光照改为 GUI 3D 物品打光，替代上文平面光照；z 深度压缩仅作用于顶点位置，法线仍保持模型真实朝向。未改变公开签名或依赖白名单。宿主标签展开页取消全区域 tooltip 与翻页按钮，将标签名称放在左上角标题栏；自动轮播及点击条目选择保留。

## 文本历史与快捷键接入

本项目新增 `UiTextHistory` 与 `UiHistoryShortcut`：前者提供有界文本、光标和选择快照，后者根据事件修饰键识别撤销与重做组合。两者只依赖 Java 标准库、JetBrains annotations 和 GLFW，不持有宿主配置。宿主 `UiTextInput`、`UiTextArea` 已使用公开 API，实现当前输入优先的局部撤销，历史为空时也消费快捷键。程序回填不通知宿主或追加历史，装载不同文本重置局部历史。

`UiHistoryDispatcher` 提供当前作用域优先的历史快捷键派发。宿主屏幕与模态栈提供局部按键处理和规则历史回调，Kit 不持有业务历史；原始上游快照中没有此接口。接口由本项目维护，未回流上游。

新增能力未修改来源仓库，可按来源包名重定位后回流；尚未回流、未发布独立库。原版 Minecraft 1.21.1 的 `EditBox` 和 `MultilineTextField` 不提供撤销栈，这里由通用历史适配，不使用 Mixin。

## 通用树操作与宿主条件页

本项目新增 `UiTreePath` 与 `UiTreeEditor`，为宿主不可变节点提供路径查询、创建、删除、更新、容量约束、绑定操作事件及独立选择/折叠事件。接口不依赖条件模型、Gson、主题或 loader；`ConditionTreeNodes` 与 `ConditionTreeOverlay` 是实际接入的宿主适配和页面，不属于 Kit。

该通用核心由本项目实现，未复制开源 Web 树编辑器源码，未修改来源仓库，也未回流上游。维护副本的原始23文件来源记录保持有效。
