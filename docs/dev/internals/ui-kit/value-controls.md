# 数值策略、滑块与区间（client/ui/kit）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 数值策略、滑块与区间 | `client/ui/kit` | §2 |

## 2. 模块：数值策略、滑块与区间

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：15（本分组类型，不含 `package-info.java`；包级说明见 [UI kit 入口](README.md)）

本分组覆盖 `UiValueTextBinding`、`UiNumberPolicy`、`UiNumbers`、`UiSliderWindow`、`UiScalarSlider`、`UiRangeSlider`、`UiRangeValue`、`UiRangeEnd`、`UiCyclicRange`、`UiSliderStyle`、`UiSliderPainter`、`UiSliderSkin`、`UiSliderRole`、`PixelSliderPainter`、`UiSliderExamples`。

### 2.2 职责

- `UiValueTextBinding<V, T>` 统一数值与精确文本的双向绑定：宿主注入数值和文本快照读写、解析与格式化、提交通知；支持单值和多端文本。非法或未完成输入保留原文本与最近有效数值，程序回填不触发反向预览。
- `UiNumberPolicy` 描述「哪些值合法」：合法域、粗细两档步长、整数策略与细调拖动系数，构造期校验全部参数。
- `UiNumbers` 提供数值与文本互转：显示侧按「整数直出、否则定点小数」，解析侧只接受有限值。
- `UiSliderWindow` 描述「哪一段铺在整条轨道上」，只参与几何映射，不参与合法性判断。
- `UiScalarSlider` 是标量滑块核心：一个值、一条轨道、一套输入契约，区分数据回填、用户操作与取消三条通路。
- `UiRangeSlider` 是双端区间交互核心，只提供逻辑与几何，不绘制；两端都存在时保持 `low <= high`，端点身份不因拖动互换。
- `UiRangeValue` 是不可变区间状态：两端数值加两个「端点是否存在」标志，缺席端数值恒为 `Double.NaN`。
- `UiRangeEnd` 是区间端点身份，用于记录「当前端」而不依赖数值比较推断。
- `UiCyclicRange` 是周期区间交互核心，只提供逻辑与几何；数值折返到 `[0, period)`，`from > to` 表示跨周期。
- `UiSliderStyle` 是不可变滑块样式：轨道、填充、手柄、禁用、错误、溢出与文字槽用色，加轨道高度、手柄宽度与上下外扩。
- `UiSliderPainter` 是绘制策略接口，向实现方交付一帧只读几何与状态。
- `UiSliderSkin` 把样式与画法配成一对，供宿主一次注入。
- `UiSliderRole` 是滑块外观档位，只描述几何尺度，不描述颜色。
- `PixelSliderPainter` 是纯色像素画法，不依赖贴图，作为 kit 默认 painter。
- `UiSliderExamples` 提供三个无规则语义的装配示例，只演示 kit 自身的装配与事件顺序。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiValueTextBinding<V, T>` | final class | 构造 `(Supplier<V>, Predicate<V>, Supplier<T>, Consumer<T>, Function<T, V>, Function<V, T>, Runnable)`；`setText(T)`；`acceptText()`；`previewText()`；`commitText(T)`；`cancelText()`；`beginValueChange()`；`previewValue()`；`commitValue()`；`cancelValue()`；`finishValueChange()` | V 与 T 应为不可变快照；解析返回 `null` 表示暂不可用；写入器返回是否接受 |
| `UiNumberPolicy` | record | `UiNumberPolicy(double, double, double, double, boolean, double)`；`of(double, double, double)`；`of(double, double, double, double)`；`integer(int, int, int)`；`integer(int, int, int, int)`；`withFineDragFactor(double)`；`span()`；`step(boolean)`；`clamp(double)`；`snap(double, boolean)`；`snapUnclamped(double, boolean)`；`dragValuePerPixel(int, double, boolean)` | 分量依次为 `min`、`max`、`coarseStep`、`fineStep`、`integerOnly`、`fineDragFactor` |
| `UiNumbers` | final class | `INTEGER_TOLERANCE`；`MAX_EXACT_INTEGER`；`MAX_LONG_AS_DOUBLE`；`plain(double, int)`；`plain(double, int, String)`；`isExactIntegral(double, double)`；`plainExact(double, int)`；`plainExact(double, int, double)`；`trimTrailingZeros(String)`；`roundedText(double)`；`parseFinite(String)`；`parseInt(String)` | 常量取值 `1.0E-6D`、`9007199254740992.0D`、`(double) Long.MAX_VALUE`；私有构造 |
| `UiSliderWindow` | record | `UiSliderWindow(double, double)`；`of(double, double)`；`span()`；`contains(double)`；`fraction(double)`；`clampFraction(double)`；`valueAt(double)` | `fraction` 不钳制，`valueAt` 先钳制比例 |
| `UiScalarSlider` | final class | 构造 `(UiNumberPolicy)`、`(UiNumberPolicy, UiSliderWindow)`；`policy()`/`setPolicy`；`window()`/`setWindow`；`style()`/`setStyle`；`painter()`/`setPainter`；`value()`/`setValue(double)`/`isBackfillInvalid()`；`setError(boolean)`/`isError()`；`setPageStep(double)`/`pageStep()`；`label()`/`setLabel`；`formatter()`/`setFormatter`；`interactionListener()`/`setInteractionListener`；`isEnabled()`/`setEnabled`；`isVisible()`/`setVisible`；`isHovered()`；`isDragging()`；`isInteracting()`；`formatValue()`；`bounds()`/`setBounds(int, int, int, int)`；`trackLeft/trackRight/trackWidth()`；`valueAtPointer(double)`；`render(GuiGraphics, Font, UiInputContext)`；`endInteraction(EndReason)`；`unmount()`；`onFocusScopeChanged()`；`onHostClosed()` | 实现 `UiFocusTarget`、`UiInputTarget`、`UiInputCapture.Target`；setter 返回 `UiScalarSlider` |
| `UiRangeSlider` | final class | 构造 `(UiNumberPolicy)`、`(UiNumberPolicy, UiSliderWindow, UiRangeValue)`；`policy()`/`setPolicy`；`window()`/`setWindow`；`value()`/`setValue(UiRangeValue)`/`isBackfillInvalid()`；`selectedEnd()`/`selectEnd(UiRangeEnd)`/`cycleSelectedEnd()`；`setEndPresent(UiRangeEnd, boolean)`；`selectedValue()`/`setSelectedValue(double, boolean)`；`setSelectedValueOrPresent(double, boolean)`；`listener()`/`setListener`；`bounds()`/`setBounds`；`trackLeft/trackRight/trackWidth()`；`xOf(double)`；`valueAtPointer(double, boolean)`；`containsPointer(double, double)`；`isDragging()`；`isInteracting()`；`setPageStep(double)`/`pageStep()`；`thumbWidth()`/`setThumbWidth(int)`；`label()`/`setLabel`；`formatter()`/`setFormatter`；`endInteraction(EndReason)`；`unmount()`；`onFocusScopeChanged()`；`onHostClosed()`；`NONE` | 实现 `UiFocusTarget`、`UiInputTarget`、`UiInputCapture.Target`；setter 返回 `void`；嵌套 `interface Listener` |
| `UiRangeValue` | record | `UiRangeValue(double, double, boolean, boolean)`；`EMPTY`；`of(double, double)`；`single(double, boolean)`；`isPresent(UiRangeEnd)`；`value(UiRangeEnd)`；`withValue(UiRangeEnd, double)`；`withAbsent(UiRangeEnd)`；`isEmpty()`；`bothPresent()` | 构造校验「存在端有限、缺席端为 `NaN`、两端都存在时 `low <= high`」 |
| `UiRangeEnd` | enum | `LOW`、`HIGH`；`other()` | 端点身份与端点数值分离 |
| `UiCyclicRange` | final class | 构造 `(double period)`；`period()`/`setPeriod(double)`；`from()`/`to()`；`setRange(double, double)`；`wrap(double)`；`span()`；`isSinglePoint()`；`crossesPeriod()`；`ticks()`/`setTicks(List<Tick>)`；`markers()`/`setMarkers(List<Marker>)`；`tickStep()`/`setTickStep(double)`；`endTick()`；`snapTick(double)`；`selectedEnd()`/`selectEnd(UiRangeEnd)`/`cycleSelectedEnd()`；`selectedTick()`/`setSelectedTick(double)`；`listener()`/`setListener`；`formatter()`/`setFormatter`；`bounds()`/`setBounds`；`thumbWidth()`/`setThumbWidth(int)`；`trackLeft/trackRight/trackWidth()`；`fractionOf(double)`；`xOf(double)`；`tickAtPointer(double)`；`containsPointer(double, double)`；`isDragging()`；`isPressed()`；`endInteraction(EndReason)`；`unmount()`；`onFocusScopeChanged()`；`onHostClosed()`；`NONE` | 嵌套 `record Tick(double, @Nullable Component)`、`record Marker(double, int)`、`interface Listener` |
| `UiSliderStyle` | record | `UiSliderStyle(...16 个 int...)`；`pixelDefaults()`；`resolveTrack(boolean)`；`resolveFill(boolean)`；`resolveThumb(boolean, boolean)`；`resolveLabelText(boolean, boolean)`；`resolveValueText(boolean, boolean)` | 13 个颜色分量 + `trackHeight`、`thumbWidth`、`thumbOverhang`；构造校验轨道高 `>= 1`、手柄宽 `>= 1`、外扩 `>= 0` |
| `UiSliderPainter` | interface | `paint(GuiGraphics, Frame)`；嵌套 `record Frame(UiRect, UiRect, UiRect, UiRect, UiRect, UiSliderStyle, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean)` | `Frame` 分量顺序为 `bounds`、`track`、`filled`、`thumb`、`textSlot`、`style`、`enabled`、`hovered`、`pressed`、`focused`、`dragging`、`error`、`overflowBelow`、`overflowAbove` |
| `UiSliderSkin` | record | `UiSliderSkin(UiSliderStyle, UiSliderPainter)`；`pixelDefaults()` | 两个分量都不接受 `null` |
| `UiSliderRole` | enum | `COMPACT`、`ROOMY` | 紧凑档与宽松档，只表达几何尺度 |
| `PixelSliderPainter` | final class | `INSTANCE`；`paint(GuiGraphics, Frame)` | 无状态单例，私有构造 |
| `UiSliderExamples` | final class | `scalarSlider(Component, UiRect, UiValueInteraction.Listener)`；`rangeSlider(Component, UiRect, UiRangeSlider.Listener)`；`cyclicRange(Component, UiRect, double, List<Double>, UiCyclicRange.Listener)` | 私有构造；三个方法都返回装配好的控件 |

### 2.4 依赖关系

- 依赖本模组：无。本分组 15 个文件均不 import 其它模组包；同包类型直接引用，不产生 import。
- 依赖外部：`java.util`（7 个文件 import，共 17 处）、`net.minecraft.client.gui`（3 个文件，4 处，`Font`、`GuiGraphics`）、`net.minecraft.network.chat`（4 个文件，4 处，`Component`）、`java.util.function`（4 个文件，7 处）、`net.minecraft.util`（2 个文件，2 处，`Mth`）、`org.jetbrains.annotations`（5 个文件，5 处，`Nullable`）、`org.lwjgl.glfw`（3 个文件，3 处，`GLFW`）、`java.math`（1 个文件，1 处，`BigDecimal`）。
- 被依赖：`client/ui/widget`（3 个文件 import，共 19 处）、`client/ui/theme`（4 个文件，11 处）、`client/ui/screen/form`（3 个文件，12 处）、`client/ui/screen`（1 个文件，5 处）；合计 11 个文件、47 处。

### 2.5 关键实现位置

- `UiValueTextBinding#previewText`：首次文本编辑保存数值与原始文本快照；每次解析成功才回填数值，不规范化用户正在输入的文本。
- `UiValueTextBinding#commitText` / `#cancelText`：成功提交时规范化显示并通知一次；取消时恢复预览前的数值与文本。装载、失焦保存或撤销恢复后由宿主调用 `acceptText` 更新基线。
- `UiValueTextBinding#beginValueChange` / `#previewValue` / `#commitValue` / `#cancelValue`：接收滑条生命周期；滑条核心先回退数值，绑定再恢复原始文本。核心因无净变化而不发提交回调时，宿主在交互结束后调用 `finishValueChange`，保留留空端与原始表示。
- `UiValueTextBinding#commitText` 的写入器有两种装配：普通绑定使用无事件回填写入器并注入提交回调；弹出式精确编辑可使用核心单次操作写入器并注入空提交回调，提交只由核心生命周期发出。
- `UiRangeSlider#setSelectedValueOrPresent`：已有端复用当前档吸附；缺失端只钳制到合法域与对端之内并补齐，统一发出 begin/preview/commit。
- `UiNumberPolicy#snapUnclamped`：把值按档吸附到 `min + k * step`；`step == 1.0` 且 `min` 为整数时直接取 `Math.rint`，否则用 `BigDecimal` 的十进制位移计算商与余数。
- `UiNumberPolicy#snap`：只做域钳制，不按 `UiSliderWindow` 钳制。
- `UiNumberPolicy#dragValuePerPixel`：`windowSpan / trackPixels`，细调时先把窗口跨度乘以 `fineDragFactor`。
- `UiNumbers#plain`：整数判定使用 `INTEGER_TOLERANCE` 且要求绝对值不超过 `MAX_EXACT_INTEGER`，命中时用 `Math.round` 直出整数文本。
- `UiNumbers#plainExact`：无容差，命中 `isExactIntegral` 时按 `long` 直出，否则固定小数位输出；`trimTrailingZeros` 单独提供去尾零。
- `UiNumbers#parseFinite`/`#parseInt`：去首尾空白、空串判非法、只接受有限值；整数先按 `long` 解析再判 `int` 范围。
- `UiSliderWindow#fraction`/`#clampFraction`/`#valueAt`：比例不钳制用于绘制溢出标记，钳制只发生在指针位置换算数值时。
- `UiScalarSlider#setValue`：越出合法域或非有限值时拒绝并置 `backfillInvalid`，不吸附、不回调。
- `UiScalarSlider#mouseDragged`：以 `dragAnchorPointer`/`dragAnchorValue` 为锚点按像素增量换算，Shift 状态变化时先重设锚点再换算。
- `UiScalarSlider#adjustByKey`/`#oneShot`：方向键累积到 key-up 才提交；Home/End 与启用后的 PageUp/PageDown 在同一次调用内 begin、preview、commit。
- `UiScalarSlider#endInteraction`/`#finishInteraction`：`EndReason#cancels()` 为真时写回操作起点值，否则提交。
- `UiRangeSlider#previewSelected`/`#boundEnd`：候选先吸附再约束到对端之内，低端不越过高端的做法是停在同值。
- `UiRangeSlider#hitEnd`：两端屏幕距离不超过容差时返回 `null`，由调用方 `cycleSelectedEnd` 切换当前端。
- `UiRangeSlider#setSelectedValue`/`#oneShotSelected`：值确有变化时在一次调用内完成 begin、preview、commit；值未变化时直接返回，不产生操作记录。
- `UiCyclicRange#wrap`/`#span`/`#snapTick`：所有刻度先折返到 `[0, period)` 再比较；`span` 返回从起点顺时针到终点的长度。
- `UiCyclicRange#setPeriod`：替换周期后把 `from`、`to` 折返到新周期内。
- `UiSliderStyle#resolve*`：按「禁用优先、其次错误/悬停」的顺序解析最终 ARGB。
- `PixelSliderPainter#paint`：依次绘制轨道、已填充段、文字槽底色、两端溢出标记、手柄与底部错误线。
- `UiSliderExamples#scalarSlider`/`#rangeSlider`/`#cyclicRange`：演示精确回填、端点选择与宿主注入周期/刻度/标记的装配。

### 2.6 代码现状（可核对）

- `UiNumbers` 有两套入口：容差版 `plain(double, int)`（按 `INTEGER_TOLERANCE` 消除浮点噪声）与严格版 `plainExact(double, int[, double])`、`isExactIntegral(double, double)`；两者不可互换。
- 常量取值：`UiNumbers#INTEGER_TOLERANCE` 为 `1.0E-6D`，`#MAX_EXACT_INTEGER` 为 `9007199254740992.0D`（2^53），`#MAX_LONG_AS_DOUBLE` 为 `(double) Long.MAX_VALUE`。
- 合法域与显示窗口分离：`UiNumberPolicy#snap` 只按域钳制；`UiSliderWindow#fraction` 故意不钳制，`#valueAt` 才钳制比例。
- 吸附档：`UiNumberPolicy#step(boolean)` 在 `coarseStep` 与 `fineStep` 间选择；`#snapUnclamped` 对非有限输入返回 `min` 而不是 `NaN`。
- 三个滑块对同一 begin/preview/commit/cancel 契约的实现方式不同：`UiScalarSlider` 委托 `UiValueInteraction`，`UiRangeSlider` 与 `UiCyclicRange` 各自维护 `operationActive` 与 `operationStart`。
- 取消回退的条件不同：`UiScalarSlider#finishInteraction` 总是写回起点值；`UiRangeSlider#finishOperation` 与 `UiCyclicRange#finishOperation` 在 `listener == null` 时提前返回，未设置 listener 时取消不回退已预览的值。
- 左键判定写法不一致：`UiScalarSlider#mousePressed` 使用字面量 `0`，`UiRangeSlider#mousePressed` 与 `UiCyclicRange#mousePressed` 使用 `GLFW.GLFW_MOUSE_BUTTON_LEFT`。
- 端点重叠容差一处具名一处字面量：`UiRangeSlider#OVERLAP_TOLERANCE_PX` 为私有常量 `4`，`UiCyclicRange#hitEnd` 直接写 `4`。
- `UiScalarSlider#buildFrame` 把 `dragging` 同时传入 `Frame` 的 `pressed` 与 `dragging`，因此 `Frame#pressed` 与 `Frame#dragging` 在标量滑块上恒等。
- `UiSliderStyle#focusColor` 没有对应的解析方法，`PixelSliderPainter#paint` 也不读取该分量；`UiSliderExamples#cyclicRange` 把它用作 `UiCyclicRange.Marker` 的颜色。
- setter 返回风格不统一：`UiScalarSlider` 的 setter 返回 `this`，`UiRangeSlider`、`UiCyclicRange` 的对应 setter 返回 `void`。
- `UiRangeSlider#isInteracting()` 与 `UiScalarSlider#isInteracting()` 公开「预览进行中」状态，供绑定调用方收束没有净变化的操作。
- `UiCyclicRange` 不提供 Shift 细调与 PageUp/PageDown：`#mouseDragged` 按 `period / trackWidth` 换算，`#keyPressed` 只处理方向键、Home、End 与 Esc。
- 同一模块存在两个钳制来源：`UiRangeSlider#valueAtPointer` 使用 `net.minecraft.util.Mth#clamp`，`UiSliderWindow#clampFraction` 使用 `Math.clamp`。
- `UiRangeValue#withValue` 可能抛 `IllegalArgumentException`（两端都存在且 `low > high`）；`#withAbsent` 把该端数值置为 `NaN`。
- `UiScalarSlider` 的显示小数位为私有常量 `VALUE_DECIMALS = 2`；无 formatter 时 `formatValue` 调用 `UiNumbers#plain(value, 2)`。
- `UiCyclicRange#tickStep` 默认为 `1.0`；`endTick()` 返回 `wrap(max(0, period - tickStep))`，End 键落点是最高一格而不是 `period`。
- `UiRangeSlider#selectEnd` 在目标端不存在时回退到另一端并可能返回 `false`；`UiCyclicRange#selectEnd` 直接记录并返回所选端。
- `UiSliderExamples` 在 kit 包外没有 import（0 个文件）；它引用 `UiInputRouter` 只出现在 JavaDoc。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiValueTextBinding` | 单值或区间的双向文本绑定与回退快照 | `previewText`、`commitText`、`cancelText`、`beginValueChange`、`finishValueChange` |
| `UiNumberPolicy` | 合法数值域、两档步长与整数策略 | `of`、`integer`、`snap`、`snapUnclamped`、`dragValuePerPixel` |
| `UiNumbers` | 数值与文本互转 | `plain`、`plainExact`、`isExactIntegral`、`parseFinite`、`parseInt` |
| `UiSliderWindow` | 轨道显示窗口 | `fraction`、`clampFraction`、`valueAt` |
| `UiScalarSlider` | 标量滑块核心 | `setValue`、`mouseDragged`、`keyPressed`、`endInteraction`、`render` |
| `UiRangeSlider` | 双端区间交互核心 | `setEndPresent`、`selectEnd`、`setSelectedValue`、`isInteracting` |
| `UiRangeValue` | 不可变区间状态 | `of`、`single`、`withValue`、`withAbsent` |
| `UiRangeEnd` | 区间端点身份 | `LOW`、`HIGH`、`other` |
| `UiCyclicRange` | 周期区间交互核心 | `setRange`、`wrap`、`snapTick`、`setTicks`、`setMarkers` |
| `UiSliderStyle` | 滑块样式与状态解析 | `pixelDefaults`、`resolveTrack`、`resolveThumb` |
| `UiSliderPainter` | 滑块绘制策略 | `paint`、`Frame` |
| `UiSliderSkin` | 样式与画法成对注入 | `pixelDefaults` |
| `UiSliderRole` | 滑块外观档位 | `COMPACT`、`ROOMY` |
| `PixelSliderPainter` | 纯色像素画法 | `INSTANCE`、`paint` |
| `UiSliderExamples` | 无规则语义的装配示例 | `scalarSlider`、`rangeSlider`、`cyclicRange` |

`package-info.java` 承载包级公开面说明，见 [UI kit 入口](README.md)。

## 4. 相关文档

- [UI kit 入口](README.md)
- [输入路由、捕获与值交互](input-routing.md)
- [文本历史与文本测量](text-controls.md)
- [不可变树编辑与画布原语](tree-editing.md)
- [internals 索引](../README.md)
