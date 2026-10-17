# 原型层（`client/ui/prototype`）

> 层级：原型层；模块路径：`client/ui/prototype`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/prototype`。
> 事实来源：本文件「3. 类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 原型层 | `client/ui/prototype` | 第 2 节 |

## 2. 模块：`client/ui/prototype`

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.prototype`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/prototype`
- 覆盖：2 个 `.java` 文件（含 `package-info.java`）

### 2.2 职责

- 提供开发用的原型屏 `UiPrototypeScreen`，在一屏内展示灰色像素主题、四种按钮变体、单行文本输入框、可滚动列表、可展开树、模态弹窗与多层弹窗栈。
- 按窗口宽度切换两套布局：小于 460 逻辑像素为单列，其余为左右两栏。
- `package-info.java` 声明本包只用于开发期验证，不属于生产界面。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiPrototypeScreen` | final class | 构造 `UiPrototypeScreen()`；重写 `init()`（protected）、`render(GuiGraphics,int,int,float)`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`charTyped`、`tick()`、`isPauseScreen()` | 继承原版 `Screen`；无参构造，标题键 `gui.itemdespawntowhat.prototype.title`；控件构建与布局方法为包内私有 |
| `package-info.java` | 包声明 | — | 声明本包不得注册快捷键，也不得被绕过独占锁的入口打开 |

### 2.4 依赖关系

- 依赖本模组：`client/ui/widget`（1 个文件 import，共 9 处：`UiButton`、`UiButtonVariant`、`UiListView`、`UiModal`、`UiModalStack`、`UiTextInput`、`UiTreeNode`、`UiTreeView`、`UiWidget`）
- 依赖本模组：`client/ui/kit`（1 个文件 import，共 3 处：`UiFocusManager`、`UiFocusTarget`、`UiRect`）
- 依赖本模组：`client/ui/theme`（1 个文件 import，共 2 处：`UiPalette`、`UiTheme`）
- 依赖外部：`net.minecraft`（1 个文件 import，共 3 处：`GuiGraphics`、`Screen`、`Component`）
- 依赖外部：`org.jetbrains.annotations`（1 个文件 import，共 1 处：`@NotNull`）
- 依赖外部：`org.lwjgl.glfw`（1 个文件 import，共 1 处：`GLFW`）
- 依赖外部：`java.util`（1 个文件 import，共 2 处：`ArrayList`、`List`）
- 被依赖：`dev.guiscreenshot`（1 个文件 import，共 1 处：`ScreenshotScreens`，位于 `neoforge/src/guiScreenshot` 源集）

### 2.5 关键实现位置

- `UiPrototypeScreen#UiPrototypeScreen`：以 `gui.itemdespawntowhat.prototype.title` 作为标题调用 `Screen` 构造；把 `baseFocus` 的回车与空格激活开关都打开。
- `UiPrototypeScreen#init`：清空基础控件与焦点，依次执行 `buildInput`、`buildButtons`、`buildList`、`buildTree`、`layout`、`refreshBaseFocus`。
- `UiPrototypeScreen#buildInput`：文本输入框最大长度 64，提交回调把值写入状态行。
- `UiPrototypeScreen#buildButtons`：四个变体按钮加一个弹窗按钮；禁用态按钮用 `setEnabled(false)` 关闭。
- `UiPrototypeScreen#buildList`：12 条示例条目，行渲染用 `UiTheme.TEXT_OFFSET` 与 `UiTheme#textColor`。
- `UiPrototypeScreen#buildTree`：根节点下挂「且」与「或」两个分组，分组下各挂叶子；根与「且」默认展开。
- `UiPrototypeScreen#refreshBaseFocus`：只登记实现 `UiFocusTarget` 且 `canFocus()` 为真的控件，随后聚焦第一个。
- `UiPrototypeScreen#layout`：常量 `NARROW_WIDTH = 460` 决定分支；窄屏把输入框放一行、五个按钮按两列三行排布，列表高度取可用高度的 2/5；宽屏把输入框宽度按可用宽度的 1/3 计算，按钮从右向左排布，列表宽度按可用宽度的 2/5 计算；两种布局都为分区标签留出 `LABEL_HEIGHT = 10` 的高度。
- `UiPrototypeScreen#layout`：底部留出 `HINT_HEIGHT = 10` 的高度，并把弹窗栈边界设置为整屏。
- `UiPrototypeScreen#render`：先绘制背景，再用 `UiTheme.drawWindow` 画整屏窗口、用 `UiTheme.drawHeader` 画标题栏；随后绘制标题、状态行、分区标签、基础控件、弹窗栈与底部提示。
- `UiPrototypeScreen#render`：基础控件渲染前用 `UiPointer.gated(modalStack.isEmpty(), mouseX, mouseY)` 计算可见指针，弹窗打开时基础控件拿不到指针坐标。
- `UiPrototypeScreen#mouseClicked`：先交给弹窗栈；未消费时按控件列表逆序命中，命中可聚焦控件后把焦点移到该控件。
- `UiPrototypeScreen#mouseReleased`、`#mouseDragged`、`#mouseScrolled`：先交给弹窗栈，再按顺序分发给基础控件。
- `UiPrototypeScreen#keyPressed`：先交给弹窗栈，再处理 `GLFW_KEY_ESCAPE` 关闭屏幕，随后交给 `baseFocus`，最后交给当前聚焦控件。
- `UiPrototypeScreen#charTyped`：先交给弹窗栈，再交给当前聚焦控件。
- `UiPrototypeScreen#openDemoModal` 与 `#openNestedModal`：用 `UiModal.create` 构造弹窗并 `modalStack.push`；嵌套弹窗在已有弹窗之上再压一层。
- `UiPrototypeScreen#tick`：只把 `tick` 转发给弹窗栈。
- `UiPrototypeScreen#isPauseScreen`：返回 `false`，原型屏不暂停游戏。
- `ScreenshotScreens#ScreenshotScreens`：在 `neoforge/src/guiScreenshot` 源集中以 `register("prototype", UiPrototypeScreen::new)` 登记原型屏工厂。

### 2.6 代码现状（可核对）

- 可达性证据：`common` 源码中没有构造 `UiPrototypeScreen` 的类型，游戏内不存在打开原型屏的入口。
- 唯一构造路径：`neoforge/src/guiScreenshot` 源集的 `ScreenshotScreens#ScreenshotScreens` 直接登记 `UiPrototypeScreen::new`，该源集只在开发截图工具中启动。
- 主题取用方式不一致：`UiPrototypeScreen` import `UiPalette` 与 `UiTheme`，直接用静态主题类绘制窗口与标题栏，并直接读 `UiTheme.PADDING`、`UiTheme.HEADER_HEIGHT`、`UiTheme.TEXT_OFFSET`；本包不 import `UiHostTheme` 或 `UiHostThemes`。
- 焦点与指针：基础层用 `UiFocusManager` 管理，弹窗层用 `UiModalStack` 管理；弹窗打开时基础控件的指针被 `UiPointer.gated` 置空。
- 缺少 JavaDoc：`UiPrototypeScreen` 只有类级 JavaDoc；构造与全部重写方法（`init`、`render`、鼠标与键盘方法、`tick`、`isPauseScreen`）无 JavaDoc，只有行注释或没有注释。
- 布局字面量：`NARROW_WIDTH = 460`、`LABEL_HEIGHT = 10`、`HINT_HEIGHT = 10` 是本类私有常量；`layout` 内的输入框高度 18、间距 4、按钮最小宽度 40、宽屏输入框最小宽度 120、列表宽度下限 140 与树宽度下限 60 都是方法内字面量。
- 包声明与入口的边界：`package-info.java` 声明本包不得注册快捷键；本包内没有 `KeyMapping` 注册，客户端也不存在按键入口。
- 依赖方向：本层依赖组件层 `client/ui/widget`（9 处），并依赖基础组件层 `client/ui/kit`（3 处）与主题层 `client/ui/theme`（2 处）；没有其它客户端包 import 本层。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiPrototypeScreen` | 开发用原型屏，展示主题与控件的组合效果 | `init`、`layout`、`render`、`mouseClicked`、`keyPressed`、`isPauseScreen` |
| `package-info.java` | 包声明：开发期用途与入口边界 | — |

## 4. 相关文档

- [主题层](ui-theme.md)
- [UI kit](ui-kit/README.md)
- [入口层文档：平台客户端接入](client-entry.md)
- [NeoForge 截图工具](gui-screenshot-tool.md)
- [客户端层](../layers/client.md)
- [文档索引](README.md)
