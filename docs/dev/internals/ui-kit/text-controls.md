# UI kit：文本控件、码点上限与文本历史

> 事实来源：宿主 `client/ui/widget/UiTextArea.java`、`client/ui/widget/UiTextInput.java`、`client/ui/widget/UiModal.java`、`client/ui/widget/UiModalStack.java`、`client/ui/screen/form/FormControl.java`、`client/ui/screen/form/TextControl.java`，kit `UiTextSelection`、`UiTextHistory`、`UiHistoryShortcut`、`UiHistoryDispatcher`。
> 上承 [UI kit 入口](README.md)。`UiTextArea` 与 `UiTextInput` 属于**宿主侧** widget 包（不是 kit 包），边界规则不变。

## 1. UiTextArea：真正的多行文本编辑

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
- 输入：鼠标定位和拖动选择委托 `UiTextSelection`，捕获期间接收矩形外的拖动与左键释放；`keyPressed` 先处理 Esc→`onCancel`，其余交给文本模型；`charTyped` 需聚焦且 `StringUtil.isAllowedChatCharacter`。
- 程序化 `setValue` 期间不触发变化回调、不参与长度回退（`programmatic` 守卫）。

## 2. 长度上限一律按 Unicode 码点

显示名 128 码点、备注 1024 码点；**不能把 UTF-16 单元当码点**（emoji 等补充平面字符算 1 个）。两个控件口径完全一致：

- `UiTextArea#onTextChanged`：`text.codePointCount(0, text.length()) > maxLength` → 整段回退到 `lastAccepted`（光标钳在回退文本内）→ `reportOverflow()`；**整体拒绝、绝不截断半个代理对**。
- `UiTextInput`：过滤器先判码点上限、再判业务 `filter`，超限整体拒绝并 `reportOverflow`；原版 `EditBox` 的单元上限设为 `Integer.MAX_VALUE`，用户输入由码点过滤器整体接受或拒绝，程序回填完整保留；`onValueAccepted` 还有一次兜底回退。
- 宿主接线（`TextControl`）：上限 `textLimit()` = 备注 1024 / 显示名 128 / 其它 TEXT 256；`setOnOverflow` → `notifyTooLong()` 置 `rejectedOverflow = true` 并立即提示（提示经 `reportRejected` → `NOTICE_ISSUE` + 本地化 key）；**下一次被接受的输入变化**清除该标记。
- 行内提示是 **warning 而不是阻塞错误**：`issues(path)` 在 `rejectedOverflow || 码点 > 上限` 时返回 `FormIssue.warning`；被拒时缓冲值本身仍在合法范围，既有原文超限由规则级校验给出阻塞错误（该处注释即为此事实来源）。
- 提示 key 由 `TextControl#tooLongKey()` 选择：备注 → `issue.notes_too_long`，显示名 → `issue.display_name_too_long`，其它文本字段**没有专属文案、不提示**。

已知边界：显示名/备注一次性粘贴超过「2×码点上限」的纯补充平面字符（如 emoji）时，可能被原版 `EditBox` 的 UTF-16 单元上限静默截断到恰好上限而不弹超限提示；逐字符键入与常规粘贴不受影响（上限判定本身仍按码点）。

## 3. 程序回填：完整不截断，并从开头显示

- `TextControl#load` 先把上限抬到 `max(textLimit(), 装载文本码点数)` 再原样写入，随后调用 `moveCursorToStart()`；备注走 `UiTextArea`（多行分支），单行走 `UiTextInput`。这保证既有超长原文在浏览或编辑其它字段时不被截短，窄宽度下也不会只显示尾部。
- `UiTextArea#moveCursorToStart()` 把光标 seek 到 0 并置 `scrollOffset = 0`；`setValue` 的注释明确「回填路径随后调用」。
- `UiTextInput#moveCursorToStart()` = 内部单参重载 + 清除选择。
- 单行与多行**同一约定**：回填必须完整、首屏从开头可读；编辑期间宿主不要调用（会抢走用户光标）。
- 备注默认 3 行：`TextControl.NOTES_ROWS = 3`；只有 `LONG_TEXT` 才创建多行控件，单行输入框隐藏但保留装载基线（`load` 的控件创建分支）；多行备注不支持预设按钮。

## 4. 宿主公开 API（与文本行相关）

| API | 位置 | 说明 |
|---|---|---|
| `FormView.setRowAction(String path, UiButton)` | `FormView` | 给字段行挂行尾操作按钮；宽度按按钮文本自适应 |
| `FormView.pendingValue(String path)` | `FormView` | 读取该字段尚未提交的缓冲值；字段不存在返回 null |
| `FormView.setRowHeight(String path, int)` | `FormView` | 覆盖该行控件高度（<=0 恢复自然高度） |
| `FormControl.setHeightOverride(int)` | `FormControl` | 覆盖值默认 `-1`；实际行高 = 覆盖值或自然高度，见 `layoutHeight()` 与 `FormView` 的布局计算 |

实例：基本页优先级行用 `RuleEditorEditPages` 的 `PRIORITY_ROW_HEIGHT` 常量（取值 18）覆盖行高。

## 5. 文本局部历史与快捷键

`UiHistoryShortcut.fromKey(int keyCode, int modifiers)` 使用事件修饰键识别 Ctrl+Z（UNDO）、Ctrl+Shift+Z 和 Ctrl+Y（REDO）；含 Alt/Super 的组合或其他按键返回 null，不读取全局输入状态。

`UiHistoryDispatcher.dispatch(keyCode, scanCode, modifiers, activeScope, history)` 接收当前作用域的 `KeyHandler` 与宿主 `Consumer<UiHistoryShortcut>`。只处理历史快捷键，先调用当前作用域，让聚焦文本控件消费局部历史；未消费时才调用宿主规则历史。识别到快捷键始终返回 true，即使没有宿主历史也不向其他作用域穿透；普通按键返回 false。

`RuleEditorScreen` 和 `UiModalStack` 均通过此入口接入当前规则的历史。弹层只投递顶层内容，配置历史恢复后通过 `UiModal.onHistoryChanged` 通知已打开的内容回填；此通知不派发底层键盘事件。

快捷键是编辑页唯一的规则历史图形入口：编辑页底排只有「应用更改 / 变更清单 / 返回」，不提供撤销与重做按钮。条件树弹窗的功能栏仍有撤销与重做图标，其提示标注快捷键，`RuleEditorTriggerPage` 将其转为 `UiHistoryShortcut` 投递给同一条历史链路。

宿主 `UiModal.onClosed` 在确认、取消、按 Esc、显式关闭或清栈移除该弹层时调用一次；用于保存会话内输入及释放交互捕获，不自动提交正式配置。叶参数弹层已接入卸载，并在历史恢复后同步字段与区间值；节点已移除时关闭相应弹层。

`UiModalStack.setHistoryTarget` 接收返回是否恢复历史的函数；未恢复时不通知弹层回填。`UiModal.onHistoryChanged` 可接收无参数回调或带 `UiHistoryShortcut` 的回调。独立叶参数弹窗在结构历史恢复后关闭，避免同类型邻居占据原路径时错误重绑；字段历史恢复继续在弹窗内回填。

`UiTextHistory` 是控件局部历史，不持有规则草稿。默认容量 `DEFAULT_CAPACITY = 100`，也可通过构造参数指定正容量。`Snapshot(String text, int cursor, int anchor)` 使用 UTF-16 光标和选择锚点，与原版输入控件一致；位置钳在文本内。`record(before, after)` 只记录内容变化，超出容量丢弃最早记录，新输入清空重做；`undo()` / `redo()` 返回待回填快照，无记录返回 null。`canUndo()` / `canRedo()` 查询状态，`clear()` 清空历史。

宿主 `UiTextInput` 与 `UiTextArea` 均已接入：被接受的键入、删除、剪切、粘贴和多行换行各记录局部快照；光标、选择和拒绝输入不记历史。撤销恢复文本、光标与选择，通知已有输入变化回调一次，不调用配置提交接口。可编辑且聚焦的输入始终消费撤销/重做快捷键，即使历史为空，容器不得继续撤销底层规则。

`setValue` / 单行 `clear` 是程序回填，不触发变化回调、不追加历史；装载不同内容会清空局部历史。业务过滤和码点上限只限制用户新增输入，不截断回填或历史恢复。多行换行宽度变化时保留当前文本、选择与局部历史。局部历史不跨控件销毁保存；规则配置历史和完整字段编辑的提交粒度由宿主管理。

## 6. 公共鼠标文本选择 API

`UiTextSelection` 实现 `UiInputTarget` 与 `UiInputCapture.Target`，构造时接收宿主 `Target`：`cursor()` / `anchor()` 返回 UTF-16 索引，`seek(pointerX, pointerY)` 将指针映射到光标，`select(anchor, cursor)` 恢复有方向的选择。模型、字体测量、文本绘制与视口滚动由宿主负责，kit 不查询全局键盘状态或业务数据。

- `setBounds(UiRect)`、`setEnabled(boolean)`、`setVisible(boolean)` 控制按下命中；只有可见、启用且矩形内的左键按下开始捕获。
- 普通按下以命中光标为锚点；Shift 按下保留原选择锚点。拖动固定该锚点，支持向两个方向跨越它，且不受控件矩形限制；非左键事件不结束左键捕获。
- `mouseReleased(UiInputContext)` 正常结束捕获并保留选择；`isSelecting()` 查询捕获状态。`endInteraction(EndReason)` 用于隐藏、禁用、失焦、卸载或关闭；取消恢复按下前的光标和选择，不修改文本，也不产生文本历史。
- `UiTextInput` 与 `UiTextArea` 的鼠标入口构造显式 `UiInputContext`，分别使用原版 `EditBox.onClick` 与多行坐标定位实现映射；最终视口跟随活动光标。键盘操作结束鼠标捕获并保留选择，原版剪贴板、Delete / Backspace、Ctrl+A/C/X/V 和局部撤销继续操作当前选择。
- 控件隐藏、禁用、失焦与程序回填结束捕获；两个 widget 的 `endInteraction` 可供宿主显式调用。表单文本、数字、气候区间、字符串列表和候选值筛选均转发拖动与释放，表单生命周期结束入口一并终止选择；管理页搜索框也转发这两个事件。
