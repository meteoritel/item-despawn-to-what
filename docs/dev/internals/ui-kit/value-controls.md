# UI kit：数值策略与滑块核心

> 事实来源：`client/ui/kit/` 的 `UiNumberPolicy`、`UiSliderWindow`、`UiScalarSlider`、`UiRangeSlider`、`UiRangeValue`、`UiRangeEnd`、`UiCyclicRange`、`UiSliderStyle`、`UiSliderPainter`、`PixelSliderPainter`、`UiSliderExamples`；宿主主题 `client/ui/theme/UiTheme`。
> 上承 [UI kit 入口](README.md)；输入路由与捕获生命周期见 [input-routing.md](input-routing.md)。

## 1. 数值策略：UiNumberPolicy 与 UiSliderWindow

```text
public record UiNumberPolicy(double min, double max, double coarseStep, double fineStep,
                             boolean integerOnly, double fineDragFactor)
```

- 表达**合法数值域**：`min`/`max`、粗档 `coarseStep`、细档 `fineStep`、`integerOnly`、细调拖动系数。
- 构造即校验：非有限边界、`max <= min`、步长 `<= 0`、`fineDragFactor` 不在 `(0, 1]`、整数策略下边界/步长非整数（步长需 `>= 1`）都抛 `IllegalArgumentException`。
- 工厂：`of(min, max, step)`、`of(min, max, coarseStep, fineStep)`、`integer(min, max, coarseStep)`、`integer(min, max, coarseStep, fineStep)`、`withFineDragFactor(double)`。
- 取值：`clamp(double)`、`snap(double, boolean fine)`（十进制吸附，消除 0.1 连加尾数）、`snapUnclamped(...)`、`step(boolean)`、`dragValuePerPixel(int trackPixels, double windowSpan, boolean fine)`。

```text
public record UiSliderWindow(double min, double max)
```

- 表达**轨道显示窗口**（几何映射），`of` / `span()` / `contains(double)` / `fraction(double)` / `clampFraction(double)` / `valueAt(double)`。
- `fraction(double)` 故意不钳制：返回 `<0` 或 `>1` 时控件画溢出标记；`valueAt(double)` 先钳制比例，因此点击轨道不会产生窗口外的值。

区别与硬规则：

| 维度 | UiNumberPolicy | UiSliderWindow |
| --- | --- | --- |
| 含义 | 哪些值合法、按几档吸附 | 哪一段铺在整条轨道上 |
| 越界行为 | 钳制 + 报告回填状态 | 不拒绝，只影响几何与溢出标记 |
| 允许范围 | 提交与校验的唯一依据 | 仅是显示/交互便利 |

**窗口不得当作提交硬限额**：超出显示窗口但仍在合法域内的值必须可以显示、可以用键盘/精确入口到达；控件打开时绝不因为窗口而钳制合法值。

## 2. 滑块核心的宿主接入

### 2.1 UiScalarSlider（标量滑块与步进）

```text
public final class UiScalarSlider implements UiFocusTarget, UiInputTarget, UiInputCapture.Target
```

- 构造：`UiScalarSlider(UiNumberPolicy)`（窗口默认等于合法域）或 `UiScalarSlider(UiNumberPolicy, UiSliderWindow)`。
- 装配：`setStyle(UiSliderStyle)`、`setPainter(UiSliderPainter)`、`setLabel(Component)`、`setFormatter(Function<Double, Component>)`、`setInteractionListener(UiValueInteraction.Listener)`、`setPageStep(double)`、`setError(boolean)`、`setBounds(int, int, int, int)`。
- 渲染：`render(GuiGraphics, Font, UiInputContext)`（悬停状态从上下文指针更新，绘制交给 painter，文字由控件绘制）。
- 输入：`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`keyPressed/keyReleased(int, int, int)`。
- 键位：`Left/Down` 减、`Right/Up` 加（`Shift` 走细档）；`Home/End` 到显示窗口端点；`PageUp/PageDown` 仅在 `setPageStep(n>0)` 后消费；`Esc` 回退本次操作。
- 指针：点击轨道定位并捕获；拖动中按/松 `Shift` 重设锚点且值不变；指针移出矩形仍收到释放。
- 回填：`setValue(double)` 返回是否接受；`isBackfillInvalid()` 报告最近一次回填状态。
- 结束：`endInteraction(EndReason)`、`unmount()`、`onFocusScopeChanged()`、`onHostClosed()`。

### 2.2 UiRangeSlider（双端区间核心）

```text
public final class UiRangeSlider implements UiFocusTarget, UiInputTarget, UiInputCapture.Target
```

- 值模型 `UiRangeValue(double low, double high, boolean lowPresent, boolean highPresent)`：缺席端数值恒为 `Double.NaN`（绝不编码成 0），两端都存在时恒 `low <= high`；`of` / `single` / `isPresent(UiRangeEnd)` / `value(UiRangeEnd)` / `withValue(UiRangeEnd, double)` / `withAbsent(UiRangeEnd)` / `isEmpty()` / `bothPresent()`。
- 端点身份 `UiRangeEnd.LOW/HIGH`（`other()`）：拖动到对端只停在同值，**不交换身份**。
- 构造：`UiRangeSlider(UiNumberPolicy)` 或 `UiRangeSlider(UiNumberPolicy, UiSliderWindow, UiRangeValue)`。
- 端点操作：`selectedEnd()`、`selectEnd(UiRangeEnd)`、`cycleSelectedEnd()`（重叠端点点击时切换当前端）、`setEndPresent(UiRangeEnd, boolean)`（可选端点，独立于数值 0）、`setSelectedValue(double raw, boolean fine)`（精确编辑当前端，恰好一条操作）、`selectedValue()`。
- 输入与几何：`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`keyPressed/keyReleased(int, int, int)`、`containsPointer(double, double)`、`valueAtPointer(double, boolean)`、`xOf(double)`、`trackLeft()/trackRight()/trackWidth()`。
- 生命周期：`setValue(UiRangeValue)` 回填 + `isBackfillInvalid()`；`setListener(Listener)`（`began/previewed/committed/cancelled` 均传 `UiRangeValue`）；`endInteraction/unmount/onFocusScopeChanged/onHostClosed`。
- **不做渲染**：本类只提供逻辑与几何，区间/时间条的绘制由宿主完成。

### 2.3 UiCyclicRange（周期区间核心）

```text
public final class UiCyclicRange implements UiFocusTarget, UiInputTarget, UiInputCapture.Target
```

- 周期与两端：`UiCyclicRange(double period)`、`period()/setPeriod(double)`、`from()/to()`、`setRange(double from, double to)`（不强制 `min <= max`，两端都折返到 `[0, period)`）、`wrap(double)`、`span()`、`isSinglePoint()`（同刻）、`crossesPeriod()`（`from > to`，宿主据此高亮跨周期）。
- 宿主注入：`setTicks(List<Tick>)`（`Tick(double tick, @Nullable Component label)`）、`setMarkers(List<Marker>)`（`Marker(double tick, int color)`）、`setTickStep(double)`（吸附网格，必须为有限正数）、`endTick()`（`END` 键落点）、`snapTick(double)`。
- 端点与输入：`selectedEnd()/selectEnd(UiRangeEnd)/cycleSelectedEnd()`、`setSelectedTick(double)`（精确编辑当前端，一条操作）、`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`keyPressed/keyReleased(int, int, int)`、`fractionOf(double)/xOf(double)/tickAtPointer(double)`。
- **不读取 `Level`、不假定世界时间**：周期、刻度、标记全部由宿主给出；kit 不知道一天/一月多长。
- **不做渲染**：本类只提供周期、刻度与几何计算，跨周期高亮由宿主绘制。

### 2.4 装配示例

`UiSliderExamples` 提供三个无规则语义示例（签名与源码一致）：

```text
UiScalarSlider UiSliderExamples.scalarSlider(Component label, UiRect bounds, UiValueInteraction.Listener listener)
UiRangeSlider  UiSliderExamples.rangeSlider(Component label, UiRect bounds, UiRangeSlider.Listener listener)
UiCyclicRange  UiSliderExamples.cyclicRange(Component label, UiRect bounds, double period,
                                            List<Double> hostTicks, UiCyclicRange.Listener listener)
```

示例类注释里写明了事件顺序与生命周期要求，可作为接入起点；实际标签由宿主传本地化 `Component`。

## 3. 样式注入

- `UiSliderStyle` 是不可变记录：13 个 ARGB 颜色（`trackColor`、`fillColor`、`thumbColor`、`thumbHoverColor`、`disabledColor`、`focusColor`、`errorColor`、`overflowColor`、`textSlotColor`、`labelTextColor`、`valueTextColor`、`disabledTextColor`、`errorTextColor`）+ 3 个尺寸（`trackHeight`、`thumbWidth`、`thumbOverhang`）；构造期校验尺寸。
- 取值方法：`resolveTrack(boolean)`、`resolveFill(boolean)`、`resolveThumb(boolean, boolean)`、`resolveLabelText(boolean, boolean)`、`resolveValueText(boolean, boolean)`；无主题宿主可用 `UiSliderStyle.pixelDefaults()`。
- 画法：`UiSliderPainter.paint(GuiGraphics, Frame)`，`Frame` 携带 `bounds/track/filled/thumb/textSlot/style` 与 `enabled/hovered/pressed/focused/dragging/error/overflowBelow/overflowAbove`；kit 自带 `PixelSliderPainter.INSTANCE`（纯色像素，无贴图）。
- 宿主主题：`client/ui/theme/UiTheme.sliderStyle()` 把调色板映射成 `UiSliderStyle` 供注入。**kit 不 import 主题包**，样式只能由宿主注入；替换 painter 不影响控件逻辑。

## 4. 宿主要求

- 数值控件的合法域与显示窗口分离：显示窗口只做几何，不得当作提交硬限额。
- 遵守 [input-routing.md](input-routing.md) §3 的操作生命周期：回填不产生事件、单动作单提交、无变化无历史、取消要回退，隐藏/禁用/卸载/失焦范围切换/关闭时必须释放捕获。
- 控件文字与标签由宿主传入本地化 `Component`；kit 不提供文案。
