# 客户端 UI kit API 与宿主接入

> 状态：当前实现（2026-10-07，含实体预览扩展）。面向宿主接入者，描述 `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/` 的公开面、输入与生命周期契约、样式注入方式与宿主要求。
> 相关：[ADR-0022](../../adr/0022-client-ui-kit-adoption.md)（同源引入与来源记录）、[ADR-0025](../../adr/0025-reusable-client-ui-kit-boundary.md)（维护副本与依赖边界）、[kit API 规格](../../plan/plan-gui-rule-update-kit.md)、[NOTICE-kit.md](../../../NOTICE-kit.md)（来源与差异）。
> 本文只描述当前源码事实；类名与方法名按源码核对，未实现的能力明确标注。

## 1. 定位与依赖边界

kit 是「记录来源与差异的维护副本」（ADR-0025）：宿主可以依赖 kit，kit **绝不反向依赖宿主**。它面向 Minecraft 客户端，不要求成为纯 Java UI 库。

允许依赖：

- Minecraft 客户端通用类型（如 `net.minecraft.client.gui.Font`、`GuiGraphics`、`net.minecraft.network.chat.Component`）；
- Java 标准库、JOML、LWJGL（按键常量）、JetBrains annotations；
- 精确允许 `com.mojang.blaze3d.platform.Lighting`，不允许整个 blaze3d 包。

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

- `pressed/dragged/released/scrolled(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget, UiInputContext context[, double scrollY])`：非释放事件先问捕获目标、再问命中控件；`released` **只**交给捕获目标，保证指针拖出控件矩形后仍能收到唯一一次释放。
- `keyPressed/keyReleased(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, int keyCode, int scanCode, int modifiers)`：聚焦控件优先，未消费才交给容器。
- `charTyped(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, char codePoint, int modifiers)`。

宿主侧现状（已对齐）：`client/ui/screen/form/FormView.keyPressed` 已改为「picker → 聚焦控件 → 未消费才滚动」，`PAGE_UP`/`PAGE_DOWN` 在聚焦控件之后才处理（客户端当前实现见 `FormView.java:613-633`）；`FormView.keyReleased` 也把 key-up 交给聚焦控件，宿主屏幕 `RuleEditorScreen.keyReleased` 再转发给它。kit 侧控件在未显式启用页步长（`setPageStep(n>0)`）时不消费这两个键，因此普通滑块不会与容器滚动冲突。
事件只消费一次的约定由宿主保证：任何控件返回 true 后，容器与页面级处理都必须立即停止。

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

## 9. 校验与拆包就绪门槛

校验顺序（与项目验证协议一致）：

1. `mcp__srv-e3b0c44298fc1c14__get_file_problems`（IDEA）检查改动的 Java 文件；
2. `powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1` 校验依赖边界（当前：39 个文件、112 条 import、0 处违规）；
3. `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`。

「拆包就绪门槛」（即本仓库暂不把 kit 拆成独立 Gradle 模块/子包的理由）：

- 依赖边界可自动校验（本页第 1 节脚本），且当前无违规；
- 公开 API 有真实宿主示例（`UiSliderExamples`）与接入文档（本页）；
- 样式、文本、图标、renderer 均可由外部注入，kit 不含本模组业务；
- 但发布坐标、artifact/version、许可文本、跨 Minecraft 版本与多 loader 发行方式尚未拍板，且 `api/` 与 `internal/` 子包拆分被评估为收益低于迁移成本，因此本期只冻结边界与检查方式，不发布、不用绝对路径依赖来源仓库（与 ADR-0025 一致）。

## 10. 当前状态与已知限制

- `client/ui/widget/UiSlider` 已是委托 `UiScalarSlider` 的适配层，但仓库中尚无屏幕调用它（P2+ 接线）；kit 的区间/周期核心同理，属于「已提供、待接线」。
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
