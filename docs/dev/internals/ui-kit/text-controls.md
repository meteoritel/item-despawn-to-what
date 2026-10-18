# 文本历史、鼠标选择与文本测量（client/ui/kit）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 文本历史、鼠标选择与文本测量 | `client/ui/kit` | §2 |

## 2. 模块：文本历史、鼠标选择与文本测量

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：6（本分组类型，不含 `package-info.java`；包级说明见 [UI kit 入口](README.md)）

本分组覆盖 `UiTextHistory`、`UiTextSelection`、`UiHistoryShortcut`、`TextMeasurer`、`TextScroll`、`UiJsonText`。

### 2.2 职责

- `UiTextHistory` 保存控件局部的文本撤销/重做记录；快照采集、回填与变化通知由宿主完成。
- `UiTextSelection` 实现单行与多行文本共用的鼠标选择：固定锚点、Shift 扩选、矩形外继续拖动。
- `UiHistoryShortcut` 识别撤销与重做快捷键，只使用事件携带的修饰键，不读取全局输入状态。
- `TextMeasurer` 是排版侧的字体度量注入点：按 `Component` 取宽度，并给出行高。
- `TextScroll` 绘制超宽文本的悬停滚动，并提供非交互场景的截断工具。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiTextHistory` | final class | `DEFAULT_CAPACITY`（`100`）；`UiTextHistory()`；`UiTextHistory(int)`；`record Snapshot(String, int, int)`；`record(Snapshot, Snapshot)`；`undo()`；`redo()`；`canUndo()`；`canRedo()`；`clear()` | 容量小于 1 时构造抛 `IllegalArgumentException`；`undo()`/`redo()` 无记录时返回 `null` |
| `UiTextSelection` | final class | `UiTextSelection(Target)`；`setBounds(UiRect)`；`setEnabled(boolean)`；`setVisible(boolean)`；`mousePressed/mouseDragged/mouseReleased(UiInputContext)`；`isSelecting()`；`endInteraction(UiInputCapture.EndReason)`；`captureEnded(UiInputCapture.EndReason)`；嵌套 `interface Target` | 实现 `UiInputTarget` 与 `UiInputCapture.Target`；`Target`：`cursor()`、`anchor()`、`seek(double, double)`、`select(int, int)`，索引为 UTF-16 位置 |
| `UiHistoryShortcut` | enum | `UNDO`、`REDO`；`fromKey(int, int)` | 非历史快捷键返回 `null` |
| `TextMeasurer` | interface | `width(Component)`；`lineHeight()`；`of(Font)`；嵌套 `record FontMeasurer(Font)` | `of` 返回 `FontMeasurer`，宽度与行高直接转发原版 `Font` |
| `TextScroll` | final class | `draw(GuiGraphics, Font, FormattedCharSequence, int, int, int, int, int, boolean, int)`；`draw(GuiGraphics, Font, String, int, int, int, int, boolean, int, boolean)`；`draw(GuiGraphics, Font, String, int, int, int, int, int, boolean, int, boolean)`；`trimToWidth(Font, String, int)` | 私有构造；三个 `draw` 重载的差异是文本类型、是否由调用方提供已测宽度、以及宽度足够时是否居中 |
| `UiJsonText` | final class | `PUNCTUATION`（`0`）、`KEY`（`1`）、`STRING`（`2`）、`NUMBER`（`3`）、`LITERAL`（`4`）、`KIND_COUNT`（`5`）；`classify(String)`；`paint(GuiGraphics, Font, String, byte[], int[], int, int, int, int)` | 私有构造；`classify` 返回与文本等长的类别表，`paint` 把相邻同类别字符合并绘制；颜色由调用方按类别传入 |

### 2.4 依赖关系

- 依赖本模组：无。本分组 6 个文件均不 import 其它模组包；同包类型直接引用，不产生 import。
- 依赖外部：`java.util`（3 个文件 import，共 5 处）、`net.minecraft.client.gui`（3 个文件，5 处，`Font`、`GuiGraphics`）、`net.minecraft.network.chat`（2 个文件，2 处，`Component`）、`net.minecraft.util`（1 个文件，1 处，`FormattedCharSequence`）、`org.jetbrains.annotations`（2 个文件，2 处，`Nullable`）、`org.lwjgl.glfw`（2 个文件，2 处，`GLFW`）。
- 被依赖：`client/ui/widget`（17 个文件 import，共 22 处）、`client/ui/screen/form`（8 个文件，8 处）、`client/ui/screen`（7 个文件，7 处）；合计 32 个文件、37 处。

### 2.5 关键实现位置

- `UiTextHistory#record`：文本未变化时返回 `false` 且不记录；记录后超出容量丢弃最早项，并清空重做栈。
- `UiTextHistory#undo`/`#redo`：在撤销栈与重做栈之间搬运同一条记录，返回待回填的 `Snapshot`。
- `UiTextHistory.Snapshot`：规范构造器把 `cursor` 与 `anchor` 钳制到 `text.length()` 之内。
- `UiTextSelection#setEnabled`/`#setVisible`：转 false 时分别以 `DISABLED`、`HIDDEN` 结束进行中的选择。
- `UiTextSelection#mousePressed`：校验可见、启用、左键与命中矩形；先以 `RELEASE` 结束上次捕获，记录按下前的锚点与光标，再按 Shift 决定锚点，最后 `capture.begin(this)`。
- `UiTextSelection#mouseDragged`：捕获中且左键时把指针映射为光标并重设选择，允许拖出命中矩形。
- `UiTextSelection#mouseReleased`：左键且本次确实持有捕获时结束捕获并保留选择。
- `UiTextSelection#captureEnded`：`EndReason#cancels()` 为真时恢复按下前的光标与选择，不修改文本。
- `UiHistoryShortcut#fromKey`：要求 Ctrl 且排除 Alt/Super；`Z` 在无 Shift 时为 `UNDO`、带 Shift 时为 `REDO`；`Y` 仅在无 Shift 时为 `REDO`。
- `TextScroll#draw`（`FormattedCharSequence`）：宽度足够直接绘制；超宽时按 `ticks` 计算位移，在 `UiTransform#enableScissorInPose` 的裁剪内平移绘制。
- `TextScroll#offset`：起点与终点各停顿 `PAUSE_DISTANCE`，中间按 `SPEED` 往返平移，位移以像素为单位。
- `TextScroll#trimToWidth`：宽度足够原样返回；连 `"..."` 都放不下时退化为纯宽度截断；否则按 `maxWidth - font.width("...")` 截断后补省略号。
- `TextMeasurer#of`/`FontMeasurer`：`width` 转发 `Font#width(Component)`，`lineHeight` 转发 `Font#lineHeight`。
- `UiJsonText#classify`：单趟字符扫描；字符串扫到右引号（反斜杠转义连带其下一个字符一起跳过）后再跳过空白，紧跟 `:` 的判为键、其余判为字符串值；`-` 或数字起始按 `NUMBER_CHARS` 收数字；字母裸词只把 `true`、`false`、`null` 判为字面量，其余按标点处理。
- `UiJsonText#paint`：按类别表把 `[begin, end)` 内的相邻同类别字符合并成一次 `drawString`，逐段累加 `font.width`；类别下标越界或段落越出类别表时按标点色兜底。

### 2.6 代码现状（可核对）

- `UiHistoryShortcut` 属于本分组，但唯一消费者 `UiHistoryDispatcher` 在 [输入路由、捕获与值交互](input-routing.md) 分组；同包调用不产生 import。
- `UiTextSelection` 同时声明嵌套 `interface Target` 并实现 `UiInputCapture.Target`：两个 `Target` 同名但契约不同。
- `UiTextSelection#mouseReleased` 在非左键时返回 `false`，即使捕获仍在持有中。
- `UiTextSelection` 在每次左键按下前保存 `initialAnchor`/`initialCursor`，只在 `captureEnded` 收到 `cancels()` 为真的原因时恢复；正常释放不恢复。
- `UiTextSelection` 的命中矩形初始为 `(0, 0, 0, 0)`，未调用 `setBounds` 时任何位置都不接受按下。
- `UiTextHistory#record` 只比较文本：仅光标或选择变化、被拒绝的输入都不产生记录。
- `UiTextHistory` 除 `DEFAULT_CAPACITY` 常量外没有容量查询入口；`clear()` 同时清空撤销与重做。
- `UiTextHistory` 同时存在方法 `record(Snapshot, Snapshot)` 与嵌套记录类型 `Snapshot`：方法名 `record` 与 Java 记录声明关键字同名。
- `TextMeasurer` 在 kit 包外没有 import（0 个文件）；kit 内唯一消费者是 `UiDocument` 的私有字段 `measurer`。
- `TextMeasurer#width` 每次调用直接转发 `Font#width`，不做缓存；`TextScroll` 的 JavaDoc 也说明不为 `String` 建全局宽度缓存。
- `TextScroll` 的停顿距离 `PAUSE_DISTANCE = 20.0F` 与速度 `SPEED = 0.35F` 是私有常量，没有公开调参入口。
- `TextScroll#draw` 的裁剪高度取 `font.lineHeight + 1`，裁剪矩形经 `UiTransform#enableScissorInPose` 换算。
- 三个 `draw` 重载并存：`FormattedCharSequence` 版强制调用方传入 `textWidth`；`String` 版分「内部测量」与「调用方提供宽度」两条路径，`centered` 只影响宽度足够时的水平位置。
- `TextScroll` 的类 JavaDoc 提到 `support/ScrollTextHelper`，仓库内没有该名称的 Java 类型（全仓检索只命中该 JavaDoc 本身）。
- `UiTextHistory` 用 `Math.clamp` 钳制快照位置，`UiTextSelection` 则直接使用宿主 `Target` 返回的位置，不做二次钳制。
- `UiJsonText` 只做词法分类与按类别绘制，不持有颜色：类别色由宿主从主题调色板的 `code*` 项取出后传入，控件层 `UiCodeView` 是唯一调用方。
- 着色规则固定为五类（键、字符串、数字、字面量、标点），来源视图弹窗、条件树 JSON 视图与损坏规则详情共用这一套，不再为单个入口另设配色。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiTextHistory` | 控件局部文本撤销/重做 | `Snapshot`、`record`、`undo`、`redo`、`clear` |
| `UiTextSelection` | 鼠标文本选择与捕获 | `mousePressed`、`mouseDragged`、`mouseReleased`、`captureEnded`、`Target` |
| `UiHistoryShortcut` | 撤销/重做快捷键识别 | `fromKey`、`UNDO`、`REDO` |
| `TextMeasurer` | 字体度量注入 | `width`、`lineHeight`、`of`、`FontMeasurer` |
| `TextScroll` | 悬停滚动绘制与截断 | `draw`（三重载）、`trimToWidth` |
| `UiJsonText` | JSON 词法分类与按类别绘制 | `classify`、`paint`、`KIND_COUNT` |

`package-info.java` 承载包级公开面说明，见 [UI kit 入口](README.md)。

## 4. 相关文档

- [UI kit 入口](README.md)
- [输入路由、捕获与值交互](input-routing.md)
- [数值策略、滑块与区间](value-controls.md)
- [不可变树编辑与画布原语](tree-editing.md)
- [internals 索引](../README.md)
