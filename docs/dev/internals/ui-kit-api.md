# 客户端 UI kit API 与宿主接入

> 状态：当前实现（2026-10-07，含实体预览扩展；2026-10-08 增补公共文本输入鼠标释放归属与多行文本控件）。面向宿主接入者，描述 `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/` 的公开面、输入与生命周期契约、样式注入方式与宿主要求。
> 相关：[ADR-0022](../../adr/0022-client-ui-kit-adoption.md)（同源引入与来源记录）、[ADR-0025](../../adr/0025-reusable-client-ui-kit-boundary.md)（维护副本与依赖边界）、[NOTICE-kit.md](../../../NOTICE-kit.md)（来源与差异）。
> 本文只描述当前源码事实；类名与方法名按源码核对，未实现的能力明确标注。

## 1. 定位与依赖边界

kit 是「记录来源与差异的维护副本」（ADR-0025）：宿主可以依赖 kit，kit **绝不反向依赖宿主**。它面向 Minecraft 客户端，不要求成为纯 Java UI 库。

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
| 内容排版 | `UiDocument`、`UiNode`、`UiIcon`、`UiTransform`、`UiMetrics`、`TextMeasurer`、`TextScroll` |
| 控件与交互 | `UiControl`、`UiControlStyle`、`UiControlGroup`、`UiScrollView`、`UiLinearLayout` |
| 实体与加载预览 | `UiEntityPreview`、`UiIcon.Rendered`、`UiSpinner` |
| 输入与捕获 | `UiInputContext`、`UiInputTarget`、`UiInputRouter`、`UiInputCapture`、`UiValueInteraction` |
| 数值策略 | `UiNumberPolicy`、`UiSliderWindow` |
| 滑块 | `UiScalarSlider`、`UiRangeSlider`、`UiRangeValue`、`UiRangeEnd`、`UiCyclicRange` |
| 滑块样式 | `UiSliderStyle`、`UiSliderPainter`、`PixelSliderPainter` |
| 焦点 | `UiFocusTarget`、`UiFocusManager` |
| 导航 / 灯箱 / 几何 | `UiNavigationHistory`；`UiLightbox`、`UiImageView`、`LightboxImage`；`UiRect`、`UiTarget`、`UiAction`、`UiNineSlice` |

调用示例类：`UiSliderExamples`（无规则语义，只有装配与事件顺序）。

## 3. 输入路由顺序

每个输入事件只消费一次，按以下顺序询问，任一环节返回 `true` 即停止：

```text
顶层 modal 作用域（由宿主解析，kit 不持有模态栈）
  → 该作用域内的捕获目标（UiInputCapture.captured()）
  → 聚焦控件（UiFocusManager.focused()）
  → 容器导航（PageUp/PageDown、滚动等）
```

`UiInputRouter` 固化这个顺序：

- `pressed/dragged/released/scrolled(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget, UiInputContext context[, double scrollY])`：非释放事件先问捕获目标、再问命中控件；`released` 优先交给捕获目标（保证指针拖出控件矩形后仍能收到唯一一次释放），**没有捕获目标时回退派发给命中控件**；`released` 的唯一性最终由宿主容器的按下归属保证（见下文《公共文本输入与复合表单的鼠标释放归属》）。
- `keyPressed/keyReleased(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, int keyCode, int scanCode, int modifiers)`：聚焦控件优先，未消费才交给容器。
- `charTyped(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, char codePoint, int modifiers)`。

宿主侧现状（已对齐）：`client/ui/screen/form/FormView#keyPressed` 已改为「picker → 聚焦控件 → 未消费才滚动」，`PAGE_UP`/`PAGE_DOWN` 在聚焦控件之后才处理；`FormView#keyReleased` 也把 key-up 交给聚焦控件，宿主屏幕 `RuleEditorScreen#keyReleased` 再转发给它。kit 侧控件在未显式启用页步长（`setPageStep(n>0)`）时不消费这两个键，因此普通滑块不会与容器滚动冲突。
事件只消费一次的约定由宿主保证：任何控件返回 true 后，容器与页面级处理都必须立即停止。

本节只约定「一次事件由谁消费」；容器把释放与拖动派发给哪个控件另有一套**按下归属**规则，见下文《公共文本输入与复合表单的鼠标释放归属》。

## 4. 输入上下文、捕获与交互生命周期

### 4.1 UiInputContext

```text
public record UiInputContext(double pointerX, double pointerY, int button,
                             boolean shift, boolean ctrl, boolean alt)
```

- `UiInputContext.pointer(double pointerX, double pointerY, int button, int modifiers)`：`modifiers` 为 GLFW 位掩码，直接来自原版事件；
- `UiInputContext.keys(int modifiers)`：没有指针信息时使用；
- `UiInputContext.of(...)` / `withPointer(double, double)` / `int modifiers()` / `shiftDown()` / `ctrlDown()` / `altDown()`。

kit 控件不在内部查询全局 Screen/Minecraft 实例，宿主必须在事件入口构造本对象。

### 4.2 UiInputTarget

`UiInputTarget` 是 kit 侧的统一输入契约（全部 `default` 返回 `false`）：`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`mouseScrolled(UiInputContext, double)`、`keyPressed/keyReleased(int, int, int)`、`charTyped(char, int)`。返回「是否消费」。

宿主控件契约仍是 `client/ui/widget/UiWidget`（旧签名 + 新增 `keyReleased`），由 `client/ui/widget/UiSlider` 之类的适配层转成 `UiInputContext` 后调用 kit 核心。

### 4.3 UiInputCapture

一个输入作用域同一时刻只允许一个捕获目标：

```text
capture.begin(target);                    // 开始捕获；已有其它目标时先以 CANCEL 结束它
capture.isCaptured(); capture.isCapturedBy(target); capture.captured();
capture.end(UiInputCapture.EndReason.RELEASE);   // 统一结束入口
```

`EndReason`：`RELEASE`（正常收尾，应提交）、`CANCEL`、`HIDDEN`、`DISABLED`、`UNMOUNTED`、`FOCUS_SCOPE_CHANGED`、`HOST_CLOSED`；`cancels()` 对除 `RELEASE` 外的原因返回 `true`。

具名包装：`release()`、`cancel()`、`endForHidden()`、`endForDisabled()`、`endForUnmount()`、`endForFocusScopeChange()`、`endForHostClose()`。

**宿主要求**：控件被隐藏/禁用/卸载、焦点作用域切换、宿主窗口关闭时必须走统一结束入口（对方是 `UiScalarSlider.endInteraction(EndReason)` / `unmount()` / `onFocusScopeChanged()` / `onHostClosed()`，以及 `setVisible(false)` / `setEnabled(false)` 自动触发的 HIDDEN/DISABLED），否则会残留捕获与未提交预览。

### 4.4 UiValueInteraction

单值控件的一次操作生命周期：

```text
boolean begin(double startValue, @Nullable Listener listener);
boolean preview(double value);   // 同值/未开始/非有限值不回调
boolean commit();                // 无变化返回 false 且不回调
boolean cancel();                // 回退到 startValue 并回调 cancelled
```

`Listener`：`began(double)`、`previewed(double, double)`、`committed(double, double)`、`cancelled(double, double)`；`UiValueInteraction.NONE` 是空实现。

三条硬约定：

1. **回填不产生事件**：`UiScalarSlider.setValue(double)` 这类回填直接改值，不回调、不吸附、不产生编辑历史；越界或非有限回填返回 `false` 并置 `isBackfillInvalid()`。
2. **单动作单提交**：一次拖动/一次按键重复/一次精确编辑恰好 `commit` 一次；方向键重复在 `keyReleased` 合并。
3. **无变化无历史**：`commit()` 在值未变化时返回 `false`，宿主据此不写历史。

宿主务必区分：`previewed` 只做局部视觉刷新，`committed` 才写状态；`cancelled` 表示已回退，宿主不需要再改状态。

## 5. 数值策略：UiNumberPolicy 与 UiSliderWindow

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

## 6. 滑块核心的宿主接入

### 6.1 UiScalarSlider（标量滑块与步进）

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

### 6.2 UiRangeSlider（双端区间核心）

```text
public final class UiRangeSlider implements UiFocusTarget, UiInputTarget, UiInputCapture.Target
```

- 值模型 `UiRangeValue(double low, double high, boolean lowPresent, boolean highPresent)`：缺席端数值恒为 `Double.NaN`（绝不编码成 0），两端都存在时恒 `low <= high`；`of` / `single` / `isPresent(UiRangeEnd)` / `value(UiRangeEnd)` / `withValue(UiRangeEnd, double)` / `withAbsent(UiRangeEnd)` / `isEmpty()` / `bothPresent()`。
- 端点身份 `UiRangeEnd.LOW/HIGH`（`other()`）：拖动到对端只停在同值，**不交换身份**。
- 构造：`UiRangeSlider(UiNumberPolicy)` 或 `UiRangeSlider(UiNumberPolicy, UiSliderWindow, UiRangeValue)`。
- 端点操作：`selectedEnd()`、`selectEnd(UiRangeEnd)`、`cycleSelectedEnd()`（重叠端点点击时切换当前端）、`setEndPresent(UiRangeEnd, boolean)`（可选端点，独立于数值 0）、`setSelectedValue(double raw, boolean fine)`（精确编辑当前端，恰好一条操作）、`selectedValue()`。
- 输入与几何：`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`keyPressed/keyReleased(int, int, int)`、`containsPointer(double, double)`、`valueAtPointer(double, boolean)`、`xOf(double)`、`trackLeft()/trackRight()/trackWidth()`。
- 生命周期：`setValue(UiRangeValue)` 回填 + `isBackfillInvalid()`；`setListener(Listener)`（`began/previewed/committed/cancelled` 均传 `UiRangeValue`）；`endInteraction/unmount/onFocusScopeChanged/onHostClosed`。
- **不做渲染**：区间/时间条绘制按主计划留到 P4，本类只提供逻辑与几何。

### 6.3 UiCyclicRange（周期区间核心）

```text
public final class UiCyclicRange implements UiFocusTarget, UiInputTarget, UiInputCapture.Target
```

- 周期与两端：`UiCyclicRange(double period)`、`period()/setPeriod(double)`、`from()/to()`、`setRange(double from, double to)`（不强制 `min <= max`，两端都折返到 `[0, period)`）、`wrap(double)`、`span()`、`isSinglePoint()`（同刻）、`crossesPeriod()`（`from > to`，宿主据此高亮跨周期）。
- 宿主注入：`setTicks(List<Tick>)`（`Tick(double tick, @Nullable Component label)`）、`setMarkers(List<Marker>)`（`Marker(double tick, int color)`）、`setTickStep(double)`（吸附网格，必须为有限正数）、`endTick()`（`END` 键落点）、`snapTick(double)`。
- 端点与输入：`selectedEnd()/selectEnd(UiRangeEnd)/cycleSelectedEnd()`、`setSelectedTick(double)`（精确编辑当前端，一条操作）、`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`keyPressed/keyReleased(int, int, int)`、`fractionOf(double)/xOf(double)/tickAtPointer(double)`。
- **不读取 `Level`、不假定世界时间**：周期、刻度、标记全部由宿主给出；kit 不知道一天/一月多长。
- **不做渲染**：跨周期高亮的画法留到 P4。

### 6.4 装配示例

`UiSliderExamples` 提供三个无规则语义示例（签名与源码一致）：

```text
UiScalarSlider UiSliderExamples.scalarSlider(Component label, UiRect bounds, UiValueInteraction.Listener listener)
UiRangeSlider  UiSliderExamples.rangeSlider(Component label, UiRect bounds, UiRangeSlider.Listener listener)
UiCyclicRange  UiSliderExamples.cyclicRange(Component label, UiRect bounds, double period,
                                            List<Double> hostTicks, UiCyclicRange.Listener listener)
```

示例类注释里写明了事件顺序与生命周期要求，可作为接入起点；实际标签由宿主传本地化 `Component`。

## 7. 样式注入

- `UiSliderStyle` 是不可变记录：13 个 ARGB 颜色（`trackColor`、`fillColor`、`thumbColor`、`thumbHoverColor`、`disabledColor`、`focusColor`、`errorColor`、`overflowColor`、`textSlotColor`、`labelTextColor`、`valueTextColor`、`disabledTextColor`、`errorTextColor`）+ 3 个尺寸（`trackHeight`、`thumbWidth`、`thumbOverhang`）；构造期校验尺寸。
- 取值方法：`resolveTrack(boolean)`、`resolveFill(boolean)`、`resolveThumb(boolean, boolean)`、`resolveLabelText(boolean, boolean)`、`resolveValueText(boolean, boolean)`；无主题宿主可用 `UiSliderStyle.pixelDefaults()`。
- 画法：`UiSliderPainter.paint(GuiGraphics, Frame)`，`Frame` 携带 `bounds/track/filled/thumb/textSlot/style` 与 `enabled/hovered/pressed/focused/dragging/error/overflowBelow/overflowAbove`；kit 自带 `PixelSliderPainter.INSTANCE`（纯色像素，无贴图）。
- 宿主主题：`client/ui/theme/UiTheme.sliderStyle()` 把调色板映射成 `UiSliderStyle` 供注入。**kit 不 import 主题包**，样式只能由宿主注入；替换 painter 不影响控件逻辑。

## 8. 宿主要求

- 文本、图标、renderer、主题全部由宿主传入；kit 不硬编码 `gui.itemdespawntowhat.*` 等本模组语言 key。
- HUD/GUI 文本使用本地化 key，并同步 `en_us.json` 与 `zh_cn.json`。
- 宿主提供指针/键盘快照（`UiInputContext`），不把全局输入状态塞进 kit。
- 遵守第 3 节路由顺序，保证同一事件只被消费一次；容器键必须在聚焦控件之后询问。
- 遵守第 4.4 节生命周期：回填不产生事件、单动作单提交、无变化无历史、取消要回退、销毁/隐藏/禁用/失焦范围切换/关闭要释放捕获。
- 显示窗口（`UiSliderWindow`）只做几何，不得当作提交硬限额。
- 遵守《公共文本输入与复合表单的鼠标释放归属》：容器记录本次按下命中的控件，`mouseReleased` / `mouseDragged` 只派发给该归属，**没有归属时不派发、不广播**；不要为单个按钮（如恢复自动命名）写事件特例。
- 文本长度上限一律按 Unicode 码点判定（`String.codePointCount`）：超限输入整体拒绝、不截断，经 `onOverflow` 上报；程序回填必须完整，并在回填后从开头显示（`moveCursorToStart()`）。

## 9. 校验与拆包就绪门槛

校验顺序（与项目验证协议一致）：

1. 用 IDE 语言服务检查改动的 Java 文件；
2. `powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1` 校验依赖边界（当前：46 个文件、136 条 import、0 处违规）；
3. `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`。

「拆包就绪门槛」（即本仓库暂不把 kit 拆成独立 Gradle 模块/子包的理由）：

- 依赖边界可自动校验（本页第 1 节脚本），且当前无违规；
- 公开 API 有真实宿主示例（`UiSliderExamples`）与接入文档（本页）；
- 样式、文本、图标、renderer 均可由外部注入，kit 不含本模组业务；
- 但发布坐标、artifact/version、许可文本、跨 Minecraft 版本与多 loader 发行方式尚未拍板，且 `api/` 与 `internal/` 子包拆分被评估为收益低于迁移成本，因此本期只冻结边界与检查方式，不发布、不用绝对路径依赖来源仓库（与 ADR-0025 一致）。

## 10. 当前状态与已知限制

- 滑块、区间与周期三类核心均已接线使用：`UiSlider`（委托 `UiScalarSlider`）由表单的数值控件创建，`UiRangeBar` / `UiCyclicTimeBar` 分别委托 `UiRangeSlider` / `UiCyclicRange`，并在触发页的区间与时间条件面板中实例化。
- 区间与周期的高亮/时间条渲染留到 P4；P1 只交付逻辑与几何。
- 不在 kit 内保证 1.21.1 以外的 Minecraft 版本兼容。
- 来源、23 文件原始清单与本次新增/修改差异见 [NOTICE-kit.md](../../../NOTICE-kit.md)。

## 2026-10-07：通用实体预览 API

```java
boolean drawn = UiEntityPreview.render(graphics, box, preparedEntity,
        angleRadians, visualWidth, visualHeight, centerY);
UiIcon icon = new UiIcon.Rendered(18, 18, (g, target) -> {
    if (!UiEntityPreview.render(g, target, preparedEntity, angleRadians,
            visualWidth, visualHeight, centerY)) {
        // 宿主绘制兜底，并提供自己的本地化提示。
    }
});
```

在客户端渲染线程调用。宿主准备实体、参数、单调时间角度和生命周期；Kit 不创建实体、不运行 tick、不加入世界、不保留业务缓存。尺寸是宿主估计的可视范围，正数且有限；box 太小或无效参数返回 false。固定全周尺度、局部矩阵与裁剪保证常规 renderer 的等比适框，模型实际边界超出估计时仍可能被裁剪。渲染异常/缺 renderer 返回 false；静默无几何的第三方 renderer 没有通用检测保证。

Kit 提交缓冲、恢复 pose/camera/裁剪，并回到普通 GUI 的 3D 物品光照基线；不保存任意先前光照状态、不更改 dispatcher 的阴影/命中框开关，也不保证恢复第三方 renderer 私自修改的所有全局状态。宿主应在 false 时画替代物品图标及说明，缓存失败避免每帧重试。

`UiIcon.Rendered(width,height,Painter)` 是受控回调适配，不修改 UiImageView 的纹理契约；回调不得泄漏矩阵/裁剪状态。sealed UiIcon 新增子类可能要求外部穷尽 switch 更新。`UiSpinner.render(GuiGraphics,int x,int y,long milliseconds,int color)` 提供环形追逐点阵，时间与颜色由宿主传入；宿主仍负责加载/失败/完成状态文本。

本项目四类展示点均通过 host `EntityPreviewIcons` / `RulePreviewIcons` 使用此 API。缓存 256 个预览，资源/语言/世界变化失效，退出屏幕释放；已知原版龙、恶魂、鱿鱼有尺寸修正。不可预览显示屏障物品及“预览不可用”，不因此禁止选择。Kit 不依赖规则 JSON、网络、loader 或宿主主题。

## 2026-10-07：层级、悬停门禁与实际模型适框

上述实体尺寸估计接口继续保留。当前宿主优先调用 `UiEntityPreview.measure(entity)` 获取 renderer 提交顶点的范围，缓存后交给新重载 `render(graphics, box, entity, angle, bounds)`；测量失败时才使用加留边的碰撞尺寸估计。测量不提交绘制、不运行 tick、不加入世界，但会调用 renderer，因此须在客户端渲染线程执行；缓存由宿主在世界/资源变化和关闭屏幕时失效。

`UiModelBounds` 包含实际中心与宽/高/深，`scale(box, pitch)` 用整圈水平对角直径和俯仰后的纵向投影计算固定尺度，保留边距。不能以一次测量保证第三方动态 renderer 在所有动画姿态下都不超框；本项目样例不运行 tick，按静态姿态预览。无法通过标准 VertexConsumer 测量的特殊 renderer 可以返回 null，由宿主提供范围。

`UiBlockPreview.measure(state)` / `render(graphics, box, state, angle, bounds)` 绘制真实默认状态的方块模型，包括原版方块实体的物品 renderer。固定 22° 俯仰和绕模型中轴的 yaw，满亮、无世界环境遮蔽、无落地阴影；矩阵、裁剪与 GUI 光照基线在 finally 恢复。无可测模型或绘制失败返回失败，由宿主绘制兜底。

绘制层级按作用域管理，不依赖组件绘制先后：

| 层 | 相对 z | 调用方式 |
|---|---:|---|
| 背景/边框 | 0 | 普通 fill/主题绘制 |
| 图标/模型 | 20 | UiIcon.Item 自动补偿原版 +150；模型使用 ICON |
| 输入框文字/角标 | 40 | UiRenderLayers.draw(graphics, FOREGROUND, painter) |
| 原版 tooltip | 400 | 当前作用域下原版 renderTooltip |
| 下一层弹窗遮罩/正文 | 600/601 | 模态栈为每层独立提升 MODAL_STEP |

`UiRenderLayers.draw` 以当前 pose 为基础，相对提升并在进出时提交缓冲、恢复矩阵。模型绘制压缩 z 比例，让较大预览仍处于图标层附近。受控 Rendered 回调须遵循该层级规则。层级负责视觉遮挡；输入隔离仍必须同时实施。

方块预览只在顶点位置矩阵中压缩 z，法线矩阵保持等比缩放后的真实朝向；使用 GUI 3D 物品打光，避免非等比深度缩放和平面物品打光造成灰暗。满亮、无世界环境遮蔽和落地阴影的约定保持有效。

宿主在绘制底层控件前调用 `UiPointer.gated(modals.isEmpty(), mouseX, mouseY)`，只把结果坐标交给底层 renderer；顶层模态继续使用真实坐标，其余模态也使用 gated。打开模态时通过 `UiModalStack.setOnScopeChanged` 结束底层捕获。模态栈消费鼠标点击、拖动、释放和滚轮，仅将事件交给顶层；关闭后底层不残留拖动状态。

滚动容器的条目高亮、tooltip 和行内入口应统一通过 `UiScrollView.canHoverContent(x,y)`：滚动条轨道和拖动期间返回 false，即使指针已拖出轨道。UiTreeView 与 UiListView 已接入；页面外层滚动条拖动时也给子组件屏蔽坐标。实际选择态不因屏蔽 hover 而清除。

`UiCarousel` 接收宿主准备的图标列表，通过 `index(milliseconds)`、`select(index,milliseconds)`、`step(direction,milliseconds)` 和 `render(graphics,box,milliseconds)` 控制轮播。默认 1.5 秒切换，手动选择重新计时；空列表不绘制。标签注册表查询、显示范围、展开容器和本地化仍属于宿主。

精确依赖白名单新增 `com.mojang.blaze3d.vertex.PoseStack` 与 `VertexConsumer`（和已有 Lighting），边界检查脚本同步维护，不放开整个父包。

## 2026-10-08：公共文本输入与复合表单的鼠标释放归属

**契约句**：只有真正承接对应按下/捕获的控件消费释放，按钮必须收到自己的释放事件；复合表单遵循同一规则，不为恢复命名按钮写事件绕过特例。

### 归属模型

容器级「按下归属」：容器在 `mouseClicked` 命中控件时记录归属（`pressedWidget` / `pressedControl`），`mouseReleased` / `mouseDragged` 只派发给该归属，**没有归属时不派发**；控件自身的 `pressed` 守卫只作控件级兜底。两者叠加的含义：

| 情况 | 释放归属 | 结果 |
|---|---|---|
| 容器记录了归属，且控件这次确实承接了按下 | 该控件 | 恰好消费一次释放（指针拖出矩形后仍收到） |
| 容器记录了归属，但控件未承接按下 | 该控件 | 控件级守卫返回 false，不误消费 |
| 容器没有归属（按下发生在容器外等） | 无 | 不派发、不广播、不残留按下态 |

### 现状实现（按源码核对）

> 定位约定：本节按「类名 + 成员名」引用源码，不写行号——行号会随重构漂移，成员名才是稳定锚点。

- `FormView`：字段 `pressedWidget` / `pressedControl`。`mouseClicked` 依次询问 picker → 行内 picker 按钮 → 行尾 action 按钮（如「恢复自动命名」）→ 行控件，命中即 `rememberPress`；`mouseReleased` 先取出并清空归属，再只派发一次；`mouseDragged` 同样只给归属；行内 picker 打开时按《弹出层所有者契约》处理。
- `RuleEditorEditPages`：字段 `pressedWidget`。`mouseClicked` 先给页面滚动条，再遍历 `liveWidgets` 记录归属；`mouseReleased` 只在归属仍 `isVisible()` 时派发一次并清空；无归属返回 false。
- 控件级兜底：`UiTextArea.pressed`、`UiTextInput.pressed` 与 `UiButton.mouseReleased` 的 `control.isPressed()` 检查。它们共同保证「容器派给我、但我这次没接住按下」时也不消费释放。
- 复合表单（`FormControl.TextControl`）把同行多类输入合成一次命中：`mouseClicked` 先试预设按钮再给输入控件，`mouseReleased` / `mouseDragged` 只转发给当前可见的那个；释放归属仍由外层容器记录，子控件不重复记账。

### 弹出层所有者契约（行内 picker）

**契约句**：当行内 picker 处于打开状态时，`FormView` 把 `mouseClicked` / `mouseReleased` / `mouseDragged` 直接交给该 picker，picker 即当前唯一事件所有者；关闭后恢复容器归属派发。**这是设计契约，不是例外**——弹出层在其存续期间是唯一所有者；「按钮必须收到自己的释放事件」只在本层未接管时适用。

- 原因：下拉/选择层覆盖在表单之上，若仍按「按下归属」派发，释放会被下层控件抢走，选择项点不到；让弹出层独占本层事件是与归属模型一致的更强所有者语义。
- 与之对应：picker 关闭时必须清掉归属状态，避免下一次释放沿用陈旧所有者。

### 禁止与反例

- 不得为某个按钮（尤其「恢复自动命名」）在 `mouseReleased` 里加特例绕过归属，也不得把释放广播给所有控件。
- 不得在未记录归属时转发释放：否则后绘制的控件会吞掉释放，先绘制的按钮会停在按下态。
- 与第 3 节的分工：`UiInputRouter` 决定「一次事件由谁消费」，容器归属决定「释放与拖动派给谁」；同一事件仍只能被消费一次。
## 2026-10-08：多行文本控件 UiTextArea 与码点长度上限

> `client/ui/widget/UiTextArea.java` 属于**宿主侧** widget 包（不是 kit 包），第 1 节的边界规则不变；本节记录它与 `UiTextInput` 的共同约定与宿主接线。

### UiTextArea：真正的多行文本编辑

```text
public final class UiTextArea implements UiWidget, UiFocusTarget
public UiTextArea(Font font, @Nullable Component hint)

String value();                void setValue(String value);        int height();
void setRows(int rows);        void setMaxLength(int maxLength);   // 默认 rows = 3、maxLength = 1024
void setOnChanged(Runnable);   void setOnOverflow(Runnable);       void setOnCancel(Runnable);
void setHint(Component);       void setAccessibleName(Component);  void setEditable(boolean);   void setVisible(boolean);
void moveCursorToStart();      // 回填后调用：光标与垂直滚动都回到开头
```

- 文本模型用原版 `MultilineTextField`（换行、选择、剪贴板、光标行号），外观按 kit 主题自绘；换行宽度变化时按当前文本与光标重建模型（重建逻辑在 `UiTextArea` 内）。
- `UiTextArea#activate()` **恒返回 false**：Enter 属于换行而不是激活，按键继续交给文本模型；宿主不要把 Enter 当提交。
- 高度 = `rows × font.lineHeight + 2×2`（`UiTextArea#height()`）；可见行数由 `setRows` 控制（`UiTextArea#setRows`，默认 3 行）。
- 垂直滚动是像素级 `scrollOffset`；`scrollToCursor` 保证光标所在行可见，上限见 `maxScroll()`；内容超出可视高度时右侧画 3px 滚动条，滚轮按 `font.lineHeight × 2` 步进（`UiTextArea#mouseScrolled`）。
- 输入：`mouseClicked` 命中即置 `pressed` 并定位光标；`mouseReleased` / `mouseDragged` 需 `pressed`；`keyPressed` 先处理 Esc→`onCancel`，其余交给文本模型；`charTyped` 需聚焦且 `StringUtil.isAllowedChatCharacter`。
- 程序化 `setValue` 期间不触发变化回调、不参与长度回退（`programmatic` 守卫）。

### 长度上限一律按 Unicode 码点

显示名 128 码点、备注 1024 码点；**不能把 UTF-16 单元当码点**（emoji 等补充平面字符算 1 个）。两个控件口径完全一致：

- `UiTextArea#onTextChanged`：`text.codePointCount(0, text.length()) > maxLength` → 整段回退到 `lastAccepted`（光标钳在回退文本内）→ `reportOverflow()`；**整体拒绝、绝不截断半个代理对**。
- `UiTextInput`：过滤器先判码点上限、再判业务 `filter`，超限整体拒绝并 `reportOverflow`；原版 `EditBox` 的单元上限设为 `Integer.MAX_VALUE`，用户输入由码点过滤器整体接受或拒绝，程序回填完整保留；`onValueAccepted` 还有一次兜底回退。
- 宿主接线（`FormControl.TextControl`）：上限 `textLimit()` = 备注 1024 / 显示名 128 / 其它 TEXT 256；`setOnOverflow` → `notifyTooLong()` 置 `rejectedOverflow = true` 并立即提示（提示经 `reportRejected` → `NOTICE_ISSUE` + 本地化 key）；**下一次被接受的输入变化**清除该标记。
- 行内提示是 **warning 而不是阻塞错误**：`issues(path)` 在 `rejectedOverflow || 码点 > 上限` 时返回 `FormIssue.warning`；被拒时缓冲值本身仍在合法范围，既有原文超限由规则级校验给出阻塞错误（该处注释即为此事实来源）。
- 提示 key 由 `FormControl.TextControl#tooLongKey()` 选择：备注 → `issue.notes_too_long`，显示名 → `issue.display_name_too_long`，其它文本字段**没有专属文案、不提示**。

### 程序回填：完整不截断，并从开头显示

- `FormControl.TextControl#load` 先把上限抬到 `max(textLimit(), 装载文本码点数)` 再原样写入，随后调用 `moveCursorToStart()`；备注走 `UiTextArea`（多行分支），单行走 `UiTextInput`。这保证既有超长原文在浏览或编辑其它字段时不被截短，窄宽度下也不会只显示尾部。
- `UiTextArea#moveCursorToStart()` 把光标 seek 到 0 并置 `scrollOffset = 0`；`setValue` 的注释明确「回填路径随后调用」。
- `UiTextInput#moveCursorToStart()` = 内部单参重载 + 清除选择。
- 单行与多行**同一约定**：回填必须完整、首屏从开头可读；编辑期间宿主不要调用（会抢走用户光标）。
- 备注默认 3 行：`FormControl.TextControl.NOTES_ROWS = 3`；只有 `LONG_TEXT` 才创建多行控件，单行输入框隐藏但保留装载基线（`load` 的控件创建分支）；多行备注不支持预设按钮。

### 相关公开 API（宿主接入面）

| API | 位置 | 说明 |
|---|---|---|
| `FormView.setRowAction(String path, UiButton)` | `FormView` | 给字段行挂行尾操作按钮（如「恢复自动命名」）；宽度按按钮文本自适应 |
| `FormView.pendingValue(String path)` | `FormView` | 读取该字段尚未提交的缓冲值；字段不存在返回 null |
| `FormView.setRowHeight(String path, int)` | `FormView` | 覆盖该行控件高度（<=0 恢复自然高度） |
| `FormControl.setHeightOverride(int)` | `FormControl` | 覆盖值默认 `-1`；实际行高 = 覆盖值或自然高度，见 `layoutHeight()` 与 `FormView` 的布局计算 |

- 实例：基本页优先级行用 `RuleEditorEditPages` 的 `PRIORITY_ROW_HEIGHT` 常量（取值 18）覆盖行高；「恢复自动命名」按钮挂在显示名行尾并随表单禁用。

## 文本局部历史与快捷键

`UiHistoryShortcut.fromKey(int keyCode, int modifiers)` 使用事件修饰键识别 Ctrl+Z（UNDO）、Ctrl+Shift+Z 和 Ctrl+Y（REDO）；含 Alt/Super 的组合或其他按键返回 null，不读取全局输入状态。

`UiHistoryDispatcher.dispatch(keyCode, scanCode, modifiers, activeScope, history)` 接收当前作用域的 `KeyHandler` 与宿主 `Consumer<UiHistoryShortcut>`。只处理历史快捷键，先调用当前作用域，让聚焦文本控件消费局部历史；未消费时才调用宿主规则历史。识别到快捷键始终返回 true，即使没有宿主历史也不向其他作用域穿透；普通按键返回 false。

`RuleEditorScreen` 和 `UiModalStack` 均通过此入口接入当前规则的历史。弹层只投递顶层内容，配置历史恢复后通过 `UiModal.onHistoryChanged` 通知已打开的内容回填；此通知不派发底层键盘事件。撤销与重做按钮保留，并标注快捷键。

宿主 `UiModal.onClosed` 在确认、取消、按 Esc、显式关闭或清栈移除该弹层时调用一次；用于保存会话内输入及释放交互捕获，不自动提交正式配置。叶参数弹层已接入卸载，并在历史恢复后同步字段与区间值；节点已移除时关闭相应弹层。

`UiTextHistory` 是控件局部历史，不持有规则草稿。默认容量 `DEFAULT_CAPACITY = 100`，也可通过构造参数指定正容量。`Snapshot(String text, int cursor, int anchor)` 使用 UTF-16 光标和选择锚点，与原版输入控件一致；位置钳在文本内。`record(before, after)` 只记录内容变化，超出容量丢弃最早记录，新输入清空重做；`undo()` / `redo()` 返回待回填快照，无记录返回 null。`canUndo()` / `canRedo()` 查询状态，`clear()` 清空历史。

宿主 `UiTextInput` 与 `UiTextArea` 均已接入：被接受的键入、删除、剪切、粘贴和多行换行各记录局部快照；光标、选择和拒绝输入不记历史。撤销恢复文本、光标与选择，通知已有输入变化回调一次，不调用配置提交接口。可编辑且聚焦的输入始终消费撤销/重做快捷键，即使历史为空，容器不得继续撤销底层规则。

`setValue` / 单行 `clear` 是程序回填，不触发变化回调、不追加历史；装载不同内容会清空局部历史。业务过滤和码点上限只限制用户新增输入，不截断回填或历史恢复。多行换行宽度变化时保留当前文本、选择与局部历史。局部历史不跨控件销毁保存；规则配置历史和完整字段编辑的提交粒度由宿主管理。

## 通用不可变树编辑

`UiTreePath(List<Integer> indices)` 使用父节点子项下标表示位置，空路径 `ROOT` 为根；`child`、`parent`、`isAncestorOf` 用于路径组合与祖先判断。构造复制列表并拒绝负下标。

`UiTreeEditor<N>` 直接绑定宿主节点，不创建第二份业务树。构造注入 `Adapter<N>`、根 `Supplier<N>`、接受变更的 `Consumer<Change<N>>`、候选根校验函数及非法路径/父节点提示；空根允许为 null。Kit 不读取注册表、不持有配置历史。适配器通过 `children`、`withChildren` 读写不可变子项，并用 `childCapacity` 声明容量；`UNLIMITED_CHILDREN` 表示不限制，`acceptsChildren(node, count)` 检查候选数量。

| 入口 | 行为 |
| --- | --- |
| `root()` / `query(UiTreePath)` | 读取当前绑定根/节点，路径不存在返回 null |
| `create(parent, index, node)` | 插入父节点子项；parent 为 null 仅用于创建空树根；父容量和插入下标先检查 |
| `delete(path)` | 删除指定节点；删除根产生空树，父重建由适配器决定 |
| `update(path, updater)` | 不可变替换节点；updater 返回 null 表示删除 |
| `accept(before, after, Kind)` | 校验并接受候选根；无变化与校验失败不触发绑定 |
| `setBeforeEdit(Runnable)` | 操作读取根之前完成宿主合法输入；宿主不能将非法文本写入配置 |
| `select(path)` / `selected()` | 选择真实节点或清空选择；不存在的路径忽略 |
| `setCollapsed(path, value)` / `collapsed()` / `expandAll()` | 管理只读可查询的折叠状态，不修改绑定树 |
| `setOnViewChanged(listener)` | 接收 `ViewChange(selected, collapsed)`，与配置操作事件分开 |

`Outcome(changed, error)` 区分已接受、无变化和错误。配置操作仅在实际改变时产生一次 `Change(before, after, Kind)`；Kind 为 CREATE、DELETE、UPDATE。宿主在接受回调内写历史，回填根不调用修改入口。节点与子项列表由宿主适配器保持不可变，不得修改既有节点。

实际接入：`ConditionTreeNodes` 适配现有 ConditionNode；`UiConditionTreeEditor` 的创建、删除、更新、查询、选择和折叠均调用 Kit。空 NOT 由宿主重建为 `Inverted(null)`；NOT 的容量为1，叶容量为0。逐表达式限额校验与本地化提示由宿主注入。`ConditionTreeOverlay` 将配置操作与输入路径迁移放入同一 EditSession 事务；JSON、参数表单、服务端保存均留在宿主。
