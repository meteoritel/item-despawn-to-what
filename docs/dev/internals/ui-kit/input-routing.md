# 输入路由、捕获与值交互（client/ui/kit）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 输入路由、捕获与值交互 | `client/ui/kit` | §2 |

## 2. 模块：输入路由、捕获与值交互

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：9（本分组类型，不含 `package-info.java`；包级说明见 [UI kit 入口](README.md)）

本分组覆盖 `UiInputContext`、`UiInputTarget`、`UiInputRouter`、`UiInputCapture`、`UiValueInteraction`、`UiFocusManager`、`UiFocusTarget`、`UiNavigationHistory`、`UiHistoryDispatcher`。

### 2.2 职责

- `UiInputContext` 以显式快照携带指针坐标、按键编号与 Shift/Ctrl/Alt 修饰键；kit 控件不在内部查询全局屏幕或客户端实例。
- `UiInputTarget` 定义 kit 统一的指针、滚轮、键盘与字符输入契约，每个方法返回「是否消费」。
- `UiInputRouter` 固化路由顺序：指针事件先问捕获目标、再问命中控件；键盘事件先问聚焦控件、未消费才交给容器导航。
- `UiInputCapture` 让一个输入作用域同一时刻只有一个捕获目标，并在结束时按 `EndReason` 通知目标。
- `UiValueInteraction` 记录单值控件一次操作的 begin → preview → commit/cancel 生命周期，并在提交时报告「值是否变化」。
- `UiFocusManager` 按宿主给出的视觉顺序持有一组可聚焦目标，唯一决定当前焦点，并处理 Tab/Shift+Tab 与 Enter/Space。
- `UiFocusTarget` 是焦点系统读取可聚焦性、边界、焦点状态与激活语义的适配接口。
- `UiNavigationHistory` 提供泛型、有界、可按有效性过滤的导航快照历史。
- `UiHistoryDispatcher` 把撤销/重做快捷键只派发给当前作用域，作用域未消费时转交宿主历史。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiInputContext` | record | `UiInputContext(double, double, int, boolean, boolean, boolean)`；`EMPTY`；`pointer(double, double, int, int)`；`keys(int)`；`of(double, double, int, boolean, boolean, boolean)`；`withPointer(double, double)`；`modifiers()`；`shiftDown()`/`ctrlDown()`/`altDown()` | 一次输入事件的显式上下文；`button` 为 GLFW 鼠标键编号，键盘或程序化调用传 `-1` |
| `UiInputTarget` | interface | `mousePressed/mouseDragged/mouseReleased(UiInputContext)`；`mouseScrolled(UiInputContext, double)`；`keyPressed/keyReleased(int, int, int)`；`charTyped(char, int)` | 全部方法为 `default` 且返回 `false`；返回 `true` 表示本次事件已被消费 |
| `UiInputRouter` | final class | `pressed/dragged/released/scrolled(@Nullable UiInputTarget, @Nullable UiInputTarget, UiInputContext[, double])`；`keyPressed/keyReleased(@Nullable UiInputTarget, @Nullable UiInputTarget, int, int, int)`；`charTyped(@Nullable UiInputTarget, @Nullable UiInputTarget, char, int)`；嵌套 `enum PointerPhase {PRESSED, DRAGGED, RELEASED, SCROLLED}` | 私有构造，全部入口为静态方法 |
| `UiInputCapture` | final class | `begin(Target)`；`isCaptured()`；`isCapturedBy(Target)`；`captured()`；`end(EndReason)`；`release()`/`cancel()`/`endForHidden()`/`endForDisabled()`/`endForUnmount()`/`endForFocusScopeChange()`/`endForHostClose()`；嵌套 `enum EndReason` 与 `interface Target` | `EndReason` 取值 `RELEASE`、`CANCEL`、`HIDDEN`、`DISABLED`、`UNMOUNTED`、`FOCUS_SCOPE_CHANGED`、`HOST_CLOSED`；`EndReason#cancels()` 除 `RELEASE` 外均为 `true` |
| `UiValueInteraction` | final class | `begin(double, @Nullable Listener)`；`isActive()`；`startValue()`；`currentValue()`；`changed()`；`preview(double)`；`commit()`；`cancel()`；`reset()`；`NONE`；嵌套 `interface Listener` | `Listener`：`began(double)`、`previewed(double, double)`、`committed(double, double)`、`cancelled(double, double)`，均有默认空实现 |
| `UiFocusManager` | final class | `beginUpdate()`；`add(UiFocusTarget)`；`endUpdate()`；`clear()`；`size()`；`targets()`；`focused()`；`focusedIndex()`；`focusOn(@Nullable UiFocusTarget)`；`clearFocus()`；`focusFirst()`；`moveFocus(int)`；`activateFocused()`；`keyPressed(int, int, int)`；`setEnterActivates(boolean)`；`setSpaceActivates(boolean)`；`setListener(@Nullable Listener)`；嵌套 `interface Listener` | `Listener#focusChanged(@Nullable UiFocusTarget, @Nullable UiFocusTarget)` |
| `UiFocusTarget` | interface | `canFocus()`；`setFocused(boolean)`；`isFocused()`；`activate()`；`bounds()`；`accessibleName()` | `bounds()` 返回 `UiRect`；`accessibleName()` 可返回 `null` |
| `UiNavigationHistory<State>` | final class | `UiNavigationHistory(int capacity)`；`push(State)`；`canGoBack(Predicate<State>)`；`back(Predicate<State>, Consumer<State>)`；`clear()` | 容量小于 1 时构造抛 `IllegalArgumentException` |
| `UiHistoryDispatcher` | final class | `dispatch(int, int, int, KeyHandler, @Nullable Consumer<UiHistoryShortcut>)`；嵌套 `interface KeyHandler` | `KeyHandler#keyPressed(int, int, int)`；私有构造 |

### 2.4 依赖关系

- 依赖本模组：无。本分组 9 个文件均不 import 其它模组包；同包类型直接引用，不产生 import。
- 依赖外部：`java.util`（3 个文件 import，共 10 处）、`java.util.function`（2 个文件，3 处）、`net.minecraft.network.chat`（1 个文件，1 处，`Component`）、`org.jetbrains.annotations`（6 个文件，6 处，`Nullable`）、`org.lwjgl.glfw`（2 个文件，2 处，`GLFW`）。
- 被依赖：`client/ui/widget`（17 个文件 import，共 32 处）、`client/ui/screen/form`（18 个文件，29 处）、`client/ui/screen`（10 个文件，16 处）、`client/ui/prototype`（1 个文件，2 处）；合计 46 个文件、79 处。

### 2.5 关键实现位置

- `UiInputRouter#pressed`/`#dragged`/`#scrolled`：转调 `routePointer`，捕获目标消费则不再向下询问，否则交给命中控件；命中控件与捕获目标相同时不重复派发。
- `UiInputRouter#released`：有捕获目标时只交给捕获目标，其返回值即最终结果，不再回退派发给命中控件；没有捕获目标时交给命中控件。
- `UiInputRouter#keyPressed`/`#keyReleased`/`#charTyped`：先问聚焦控件，未消费且容器不是同一对象才交给容器。
- `UiInputRouter#routePointer`/`#dispatch`：按 `PointerPhase` 分派到 `UiInputTarget` 的四个指针方法。
- `UiInputCapture#begin`：已有其它目标时先以 `CANCEL` 结束旧捕获，再登记新目标；目标相同时直接返回。
- `UiInputCapture#end`：先清空引用再回调 `Target#captureEnded`，因此回调内重入不会重复结束。
- `UiValueInteraction#preview`/`#commit`/`#cancel`/`#reset`：维护 `active`、`startValue`、`currentValue` 与 `listener`；`commit` 只在值变化时回调。
- `UiFocusManager#endUpdate`：仅当 `updating` 为真时用本帧登记结果替换目标列表，随后清空暂存集合并调用 `refresh`。
- `UiFocusManager#refresh`/`#setFocusedInternal`：目标失效时清除焦点并通知 `Listener`；焦点未变时不重复通知。
- `UiFocusManager#moveFocus`：只在 `canFocus()` 为真的目标之间移动，支持环绕，无目标时不消费。
- `UiNavigationHistory#push`/`#back`：`push` 忽略与栈顶相同的快照与恢复期间的写入；`back` 弹出并跳过失效快照，恢复期间置位 `restoring`。
- `UiHistoryDispatcher#dispatch`：先由 `UiHistoryShortcut#fromKey` 判定是否为历史快捷键，再由当前作用域 `KeyHandler` 消费，未消费时调用宿主 `Consumer`。

### 2.6 代码现状（可核对）

- `UiInputRouter` 与 `UiNavigationHistory` 在 kit 包外没有 import（0 个文件、0 处）；包内除自身外，`UiInputRouter` 只出现在 `package-info.java` 的公开面清单与 `UiSliderExamples` 的 JavaDoc 引用里，`UiNavigationHistory` 只出现在 `package-info.java` 的公开面清单里。
- `UiInputContext` 对同一状态提供两套访问名：record 访问器 `shift()`/`ctrl()`/`alt()` 与 `shiftDown()`/`ctrlDown()`/`altDown()`。
- `UiInputTarget` 的七个方法使用行注释而非 JavaDoc。
- `UiFocusManager#size` 返回目标数量，语义与 `targets().size()` 相同，且没有 `isEmpty()`。
- `UiFocusManager#add` 在 `beginUpdate` 之外调用时不会被 `endUpdate` 写入目标列表：`endUpdate` 只在 `updating` 为真时替换 `targets`，随后无条件清空 `nextTargets`。
- `UiFocusManager#keyPressed` 只识别 Tab、Enter、小键盘 Enter 与 Space；Esc 与方向键不属于本类。
- `UiNavigationHistory` 没有容量或长度读取入口（无 `capacity()`、`size()`），容量仅在构造期校验。
- `UiNavigationHistory#canGoBack` 会就地移除失效快照（遍历时调用 `removeIf`），带副作用；`back` 同样移除被跳过的失效快照。
- `UiValueInteraction#cancel` 不修改任何外部状态，只把 `currentValue` 重置为 `startValue` 并回报该值；值的实际回退由控件完成（`UiScalarSlider#finishInteraction`）。
- `UiValueInteraction#commit` 与 `#cancel` 都会先重置状态再回调，同一次操作后一次调用变为空操作。
- `UiHistoryDispatcher#dispatch` 对识别到的历史快捷键恒返回 `true`，即使当前作用域未消费且宿主回调为 `null`。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiInputContext` | 一次输入事件的上下文快照 | `pointer`、`keys`、`withPointer`、`modifiers` |
| `UiInputTarget` | kit 统一输入契约 | `mousePressed`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped` |
| `UiInputRouter` | 固化「捕获 → 命中」「聚焦 → 容器」的路由顺序 | `pressed`、`dragged`、`released`、`scrolled`、`keyPressed`、`charTyped`、`PointerPhase` |
| `UiInputCapture` | 单捕获登记与统一结束原因 | `begin`、`end`、`EndReason`、`Target#captureEnded` |
| `UiValueInteraction` | 单值操作生命周期 | `begin`、`preview`、`commit`、`cancel`、`Listener` |
| `UiFocusManager` | 视觉顺序焦点登记与键盘导航 | `beginUpdate`、`add`、`endUpdate`、`focusOn`、`moveFocus`、`keyPressed` |
| `UiFocusTarget` | 焦点目标适配接口 | `canFocus`、`setFocused`、`isFocused`、`activate`、`bounds`、`accessibleName` |
| `UiNavigationHistory` | 有界导航快照历史 | `push`、`canGoBack`、`back`、`clear` |
| `UiHistoryDispatcher` | 历史快捷键的作用域派发 | `dispatch`、`KeyHandler` |

`package-info.java` 承载包级公开面说明，见 [UI kit 入口](README.md)。

## 4. 相关文档

- [UI kit 入口](README.md)
- [数值控件与滑块](value-controls.md)
- [文本历史与文本测量](text-controls.md)
- [不可变树编辑与画布原语](tree-editing.md)
- [internals 索引](../README.md)
