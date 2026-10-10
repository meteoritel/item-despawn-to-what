# UI kit：输入路由、捕获与释放归属

> 事实来源：`client/ui/kit/`（`UiInputRouter`、`UiInputContext`、`UiInputTarget`、`UiInputCapture`、`UiValueInteraction`）与宿主 `client/ui/screen/form/FormView`、`client/ui/widget`。
> 上承 [UI kit 入口](README.md)。本册只回答「一次输入事件由谁消费、由谁接收释放」。

## 1. 输入路由顺序

每个输入事件只消费一次，按以下顺序询问，任一环节返回 `true` 即停止：

```text
顶层 modal 作用域（由宿主解析，kit 不持有模态栈）
  → 该作用域内的捕获目标（UiInputCapture.captured()）
  → 聚焦控件（UiFocusManager.focused()）
  → 容器导航（PageUp/PageDown、滚动等）
```

`UiInputRouter` 固化这个顺序：

- `pressed/dragged/released/scrolled(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget, UiInputContext context[, double scrollY])`：非释放事件先问捕获目标、再问命中控件；`released` 优先交给捕获目标（保证指针拖出控件矩形后仍能收到唯一一次释放），**没有捕获目标时回退派发给命中控件**；`released` 的唯一性最终由宿主容器的按下归属保证（见第 4 节）。
- `keyPressed/keyReleased(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, int keyCode, int scanCode, int modifiers)`：聚焦控件优先，未消费才交给容器。
- `charTyped(@Nullable UiInputTarget focused, @Nullable UiInputTarget container, char codePoint, int modifiers)`。

宿主侧现状：`client/ui/screen/form/FormView#keyPressed` 的处理顺序是「picker → 聚焦控件 → 未消费才滚动」，`PAGE_UP`/`PAGE_DOWN` 在聚焦控件之后才处理；`FormView#keyReleased` 把 key-up 交给聚焦控件，宿主屏幕 `RuleEditorScreen#keyReleased` 再转发给它。kit 侧控件在未显式启用页步长（`setPageStep(n>0)`）时不消费这两个键，因此普通滑块不会与容器滚动冲突。
事件只消费一次的约定由宿主保证：任何控件返回 true 后，容器与页面级处理都必须立即停止。

本节只约定「一次事件由谁消费」；容器把释放与拖动派发给哪个控件另有一套**按下归属**规则，见第 4 节。

## 2. 输入上下文与目标契约

### 2.1 UiInputContext

```text
public record UiInputContext(double pointerX, double pointerY, int button,
                             boolean shift, boolean ctrl, boolean alt)
```

- `UiInputContext.pointer(double pointerX, double pointerY, int button, int modifiers)`：`modifiers` 为 GLFW 位掩码，直接来自原版事件；
- `UiInputContext.keys(int modifiers)`：没有指针信息时使用；
- `UiInputContext.of(...)` / `withPointer(double, double)` / `int modifiers()` / `shiftDown()` / `ctrlDown()` / `altDown()`。

kit 控件不在内部查询全局 Screen/Minecraft 实例，宿主必须在事件入口构造本对象。

### 2.2 UiInputTarget

`UiInputTarget` 是 kit 侧的统一输入契约（全部 `default` 返回 `false`）：`mousePressed/mouseDragged/mouseReleased(UiInputContext)`、`mouseScrolled(UiInputContext, double)`、`keyPressed/keyReleased(int, int, int)`、`charTyped(char, int)`。返回「是否消费」。

宿主控件契约是 `client/ui/widget/UiWidget`，由 `client/ui/widget/UiSlider` 之类的适配层转成 `UiInputContext` 后调用 kit 核心。

## 3. 捕获与单值操作生命周期

### 3.1 UiInputCapture

一个输入作用域同一时刻只允许一个捕获目标：

```text
capture.begin(target);                    // 开始捕获；已有其它目标时先以 CANCEL 结束它
capture.isCaptured(); capture.isCapturedBy(target); capture.captured();
capture.end(UiInputCapture.EndReason.RELEASE);   // 统一结束入口
```

`EndReason`：`RELEASE`（正常收尾，应提交）、`CANCEL`、`HIDDEN`、`DISABLED`、`UNMOUNTED`、`FOCUS_SCOPE_CHANGED`、`HOST_CLOSED`；`cancels()` 对除 `RELEASE` 外的原因返回 `true`。

具名包装：`release()`、`cancel()`、`endForHidden()`、`endForDisabled()`、`endForUnmount()`、`endForFocusScopeChange()`、`endForHostClose()`。

**宿主要求**：控件被隐藏/禁用/卸载、焦点作用域切换、宿主窗口关闭时必须走统一结束入口（对方是 `UiScalarSlider.endInteraction(EndReason)` / `unmount()` / `onFocusScopeChanged()` / `onHostClosed()`，以及 `setVisible(false)` / `setEnabled(false)` 自动触发的 HIDDEN/DISABLED），否则会残留捕获与未提交预览。

### 3.2 UiValueInteraction

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

## 4. 按下归属模型

**契约句**：只有真正承接对应按下/捕获的控件消费释放，按钮必须收到自己的释放事件；复合表单遵循同一规则，不为恢复命名按钮写事件绕过特例。

容器级「按下归属」：容器在 `mouseClicked` 命中控件时记录归属（`pressedWidget` / `pressedControl`），`mouseReleased` / `mouseDragged` 只派发给该归属，**没有归属时不派发**；控件自身的 `pressed` 守卫只作控件级兜底。两者叠加的含义：

| 情况 | 释放归属 | 结果 |
|---|---|---|
| 容器记录了归属，且控件这次确实承接了按下 | 该控件 | 恰好消费一次释放（指针拖出矩形后仍收到） |
| 容器记录了归属，但控件未承接按下 | 该控件 | 控件级守卫返回 false，不误消费 |
| 容器没有归属（按下发生在容器外等） | 无 | 不派发、不广播、不残留按下态 |

各宿主的归属实现（按源码核对）：

- `FormView`：字段 `pressedWidget` / `pressedControl`。`mouseClicked` 依次询问 picker → 行内 picker 按钮 → 行尾 action 按钮（如「恢复自动命名」）→ 行控件，命中即 `rememberPress`；`mouseReleased` 先取出并清空归属，再只派发一次；`mouseDragged` 同样只给归属；行内 picker 打开时按第 5 节处理。
- `RuleEditorEditPages`：字段 `pressedWidget`。`mouseClicked` 先给页面滚动条，再遍历 `liveWidgets` 记录归属；`mouseReleased` 只在归属仍 `isVisible()` 时派发一次并清空；无归属返回 false。
- 控件级兜底：`UiTextArea.pressed`、`UiTextInput.pressed` 与 `UiButton.mouseReleased` 的 `control.isPressed()` 检查。它们共同保证「容器派给我、但我这次没接住按下」时也不消费释放。
- 复合表单（`FormControl.TextControl`）把同行多类输入合成一次命中：`mouseClicked` 先试预设按钮再给输入控件，`mouseReleased` / `mouseDragged` 只转发给当前可见的那个；释放归属仍由外层容器记录，子控件不重复记账。

## 5. 弹出层所有者契约（行内 picker）

**契约句**：当行内 picker 处于打开状态时，`FormView` 把 `mouseClicked` / `mouseReleased` / `mouseDragged` 直接交给该 picker，picker 即当前唯一事件所有者；关闭后恢复容器归属派发。**这是设计契约，不是例外**——弹出层在其存续期间是唯一所有者；「按钮必须收到自己的释放事件」只在本层未接管时适用。

- 原因：下拉/选择层覆盖在表单之上，若仍按「按下归属」派发，释放会被下层控件抢走，选择项点不到；让弹出层独占本层事件是与归属模型一致的更强所有者语义。
- 与之对应：picker 关闭时必须清掉归属状态，避免下一次释放沿用陈旧所有者。

## 6. 禁止与反例

- 不得为某个按钮（尤其「恢复自动命名」）在 `mouseReleased` 里加特例绕过归属，也不得把释放广播给所有控件。
- 不得在未记录归属时转发释放：否则后绘制的控件会吞掉释放，先绘制的按钮会停在按下态。
- 与第 1 节的分工：`UiInputRouter` 决定「一次事件由谁消费」，容器归属决定「释放与拖动派给谁」；同一事件仍只能被消费一次。
