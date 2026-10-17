# 主题层（`client/ui/theme`）

> 层级：主题层；模块路径：`client/ui/theme`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/theme`。
> 事实来源：本文件「3. 类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 主题层 | `client/ui/theme` | 第 2 节 |

## 2. 模块：`client/ui/theme`

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.theme`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/theme`
- 覆盖：10 个 `.java` 文件（含 `package-info.java`）

### 2.2 职责

- `UiHostTheme` 定义宿主视觉规格的注入面：调色板、度量、文本色、控件样式、滑块外观、结构绘制原语、宿主外壳绘制与图标。
- `UiHostThemes` 装配并暴露两个主题实例：默认像素主题 `UiPixelTheme` 与扁平主题 `UiFlatTheme`。
- `UiPixelTheme` 把取值转发到静态主题 `UiTheme` 与像素调色板 `UiPalette`。
- `UiFlatTheme` 用 `UiFlatPalette` 的扁平配色与 `UiFlatSkin` 的扁平绘制原语实现同一注入面。
- `UiPalette` 与 `UiFlatPalette` 集中 ARGB 颜色常量；`UiTheme` 集中度量常量、像素控件样式与九宫格卡片绘制。
- `UiFlatSkin` 持有图标图集切片与扁平外壳绘制原语。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiHostTheme` | interface | 常量 `ICON_UNDO`、`ICON_REDO`、`ICON_VIEW_TREE`、`ICON_VIEW_JSON`、`ICON_HELP`、`ICON_ADD_CONDITION`、`ICON_GROUP_ALL`、`ICON_INVERT`、`ICON_REMOVE`、`ICON_MOVE_UP`、`ICON_MOVE_DOWN`、`ICON_WRAP_ALL`、`ICON_GROUP_ANY`、`ICON_WRAP_ANY`；`palette()`；`padding()`、`rowHeight()`、`textOffset()`、`headerHeight()`、`border()`、`selectMarkerWidth()`；`textColor(boolean)`、`secondaryTextColor()`、`controlTextColor(UiControlRole)`；`controlStyle(UiControlRole)`、`sliderSkin(UiSliderRole)`、`panelStyle()`、`stripStyle()`；`drawWindow`、`drawInset`、`drawHeader`、`drawDivider`、`drawBevel`、`drawFocusOutline`、`drawSelection`、`drawSelectMarker`、`drawScrollTrack`、`drawSegment`、`drawCard`、`drawPanel`、`drawChip`；`icon(int,boolean)`、`drawChrome`、`drawStatusBar`、`drawHelp`、`drawSurface` | 宿主主题契约，继承 `client/ui/kit` 的 `UiStyleSource`；共 32 个方法 |
| `UiHostThemes` | final class | `defaultTheme()`、`flatTheme()`、`require(UiHostTheme)` | 主题入口；默认主题为像素主题，扁平主题供画布页显式取用 |
| `UiHostPalette` | final class | `hostPalette()` 与 72 个颜色访问器 | 控件可见的只读语义色视图 |
| `UiPixelTheme` | final class | 实现 `UiHostTheme` 的 32 个方法 | 默认宿主主题，像素外观 |
| `UiFlatTheme` | final class | 实现 `UiHostTheme` 的 32 个方法 | 扁平宿主主题，画布页外观 |
| `UiFlatPalette` | final class | 60 个 `public static final int` 颜色常量 | 扁平配色 |
| `UiFlatSkin` | final class | 14 个 `ICON_*` 常量、`PANEL_STYLE`、`STRIP_STYLE`；`icon(int,boolean)`、`drawSurface`、`drawOutline`、`drawChrome`、`drawStatusBar`、`drawHelp`、`drawFocusOutline` | 图标图集切片与扁平绘制原语 |
| `UiPalette` | final class | 39 个 `public static final int` 颜色常量 | 灰色像素主题调色板 |
| `UiTheme` | final class | `ROW_HEIGHT`、`BORDER`、`PADDING`、`HEADER_HEIGHT`、`TEXT_OFFSET`、`SELECT_MARKER_WIDTH`；`secondaryStyle()`、`primaryStyle()`、`dangerStyle()`、`disabledStyle()`、`labelStyle()`、`sliderStyle()`、`cardSliderStyle()`、`cardSliderPainter()`；13 个绘制方法；`textColor(boolean)`、`secondaryTextColor()`、`inset(UiRect)`、`clampTo(UiRect,UiRect)` | 灰色像素主题的静态入口 |
| `package-info.java` | 包声明 | — | 声明本包依赖面：kit 与 `client/edit.TypeLabels`，不依赖 core 与平台 API |

### 2.4 依赖关系

- 依赖本模组：`client/ui/kit`（5 个文件 import，共 31 处：`UiControlStyle`、`UiIcon`、`UiIconSheet`、`UiRect`、`UiStyleSource`、`UiControlRole`、`UiSliderRole`、`UiSliderSkin`、`UiSliderStyle`、`UiSliderPainter`、`PixelSliderPainter`、`UiNineSlice`）
- 依赖本模组：`client/edit`（1 个文件 import，共 1 处：`UiFlatSkin` import `TypeLabels`）
- 依赖外部：`net.minecraft`（5 个文件 import，共 7 处：`GuiGraphics`、`ResourceLocation`）
- 依赖外部：`com.mojang.blaze3d.systems`（1 个文件 import，共 1 处：`UiTheme` 的 `RenderSystem`）
- 依赖外部：`java.util`（1 个文件 import，共 1 处：`UiHostThemes` 的 `Objects`）
- 被依赖：`client/ui/widget`（21 个文件 import，共 37 处）
- 被依赖：`client/ui/screen`（17 个文件 import，共 30 处）
- 被依赖：`client/ui/screen/form`（12 个文件 import，共 21 处）
- 被依赖：`client/ui/shared`（1 个文件 import，共 2 处：`CatalogPreviewIcons` 取 `UiHostTheme`、`UiHostThemes`）
- 被依赖：`client/ui/prototype`（1 个文件 import，共 2 处：`UiPrototypeScreen` 取 `UiPalette`、`UiTheme`）

### 2.5 关键实现位置

- `UiHostThemes#defaultTheme`：返回静态单例 `UiPixelTheme.INSTANCE`。
- `UiHostThemes#flatTheme`：返回静态单例 `UiFlatTheme.INSTANCE`。
- `UiHostThemes#require`：对入参执行 `Objects.requireNonNull(theme, "theme")`，控件构造期用它校验注入主题。
- `UiHostPalette#hostPalette`：返回静态单例；调色板按定义不可变，两种皮肤共用同一实例。
- `UiPixelTheme#palette`：返回 `UiHostPalette.hostPalette()`。
- `UiPixelTheme#controlStyle`：按 `UiControlRole` 分派到 `UiTheme` 的 `SECONDARY`、`PRIMARY`、`DANGER`、`DISABLED`、`LABEL` 五个静态样式。
- `UiPixelTheme#sliderSkin`：`COMPACT` 取 `UiTheme#sliderStyle` 配 `UiTheme#cardSliderPainter`；`ROOMY` 取 `UiTheme#cardSliderStyle` 配 `UiTheme#cardSliderPainter`。
- `UiPixelTheme#controlTextColor`：`PRIMARY` 与 `DANGER` 取 `palette().textOnDark()`；`DISABLED` 取 `textDisabled()`；`SECONDARY` 与 `LABEL` 取 `textPrimary()`。
- `UiPixelTheme#panelStyle` 与 `#stripStyle`：返回本类静态 `PANEL_STYLE`、`STRIP_STYLE`，两者都由 `UiPalette` 常量构造。
- `UiPixelTheme#icon`、`#drawChrome`、`#drawStatusBar`、`#drawHelp`、`#drawSurface`：转发 `UiFlatSkin` 的同名静态方法。
- `UiPixelTheme` 的其余绘制方法与文本色：转发 `UiTheme` 的同名静态方法。
- `UiFlatTheme#controlStyle`：`SECONDARY` 取 `CONTROL_STYLE`，`PRIMARY` 取 `PRIMARY_STYLE`，`DANGER` 取 `DANGER_STYLE`，`DISABLED` 取 `DISABLED_STYLE`，`LABEL` 取 `LABEL_STYLE`。
- `UiFlatTheme#sliderSkin`：两档都使用 `PixelSliderPainter.INSTANCE`；`COMPACT` 配 `COMPACT_SLIDER`，`ROOMY` 配 `ROOMY_SLIDER`。
- `UiFlatTheme#drawBevel`：只用 `dark` 参数描 1 像素同色外框，不读取 `light` 参数。
- `UiFlatTheme#drawSegment` 与 `#drawChip`：按 `enabled`、`selected`、`hovered` 组合选择填充色，描边统一取 `UiFlatPalette.PANEL_BORDER`。
- `UiFlatTheme#panelStyle` 与 `#stripStyle`：返回 `UiFlatSkin.PANEL_STYLE`、`UiFlatSkin.STRIP_STYLE`。
- `UiFlatSkin#icon`：从 `ICON_SHEET_TEXTURE`（`textures/gui/editor/icons_dark.png`）构造的 `UiIconSheet` 取第 `row` 行、列 0 或列 1 的单元格；图集单元格 16×16、2 列、14 行。
- `UiFlatSkin#drawOutline`：矩形宽或高不超过 2 时整块填描边色，否则描四边各 1 像素。
- `UiFlatSkin#drawChrome`、`#drawStatusBar`、`#drawHelp`：以 `UiFlatPalette` 的底色与描边调用 `drawSurface`。
- `UiTheme#surface`：按名构造 `UiNineSlice`，贴图路径为 `textures/gui/editor/<name>.png`，参数 `size = 32`、`corner = 8`。
- `UiTheme#drawCard`、`#drawChip`、`#drawSegment`：以九宫格贴图绘制；`drawSegment` 的取值顺序为禁用、选中、悬停、常态。
- `UiTheme#cardSliderPainter`：先调用 `PixelSliderPainter.INSTANCE`，再给手柄画 `WINDOW_BORDER_LIGHT` 与 `PANEL_BORDER_DARK` 立体边，手柄宽不小于 5 且高不小于 6 时画中央刻痕。
- `UiTheme#drawSelectMarker`：在矩形左侧画三级三角，颜色 `TEXT_PRIMARY`。
- `UiTheme#inset`：宽或高小于 2 时原样返回，否则内缩 1 像素。
- `UiTheme#clampTo`：取两矩形交集；无交集时返回容器原点上的零尺寸矩形。

`UiPalette` 的 39 个常量：

| 常量 | 值 | 源码注释给出的用途 |
|---|---|---|
| `TRANSPARENT` | `0x00000000` | 完全透明 |
| `SHADOW` | `0xFF000000` | 窗口外描边 |
| `MODAL_DIM` | `0xB0000000` | 弹窗背景遮罩 |
| `WINDOW_FILL` | `0xFFC6C6C6` | 窗口底色 |
| `WINDOW_BORDER_LIGHT` | `0xFFFFFFFF` | 窗口高光边 |
| `WINDOW_BORDER_DARK` | `0xFF555555` | 窗口阴影边 |
| `PANEL_FILL` | `0xFFB0B0B0` | 面板底色 |
| `PANEL_BORDER_LIGHT` | `0xFFE0E0E0` | 面板高光边 |
| `PANEL_BORDER_DARK` | `0xFF4A4A4A` | 面板阴影边 |
| `HEADER_FILL` | `0xFF8E8E8E` | 分区标题栏底色 |
| `HEADER_TEXT` | `0xFF1E1E1E` | 分区标题栏文字 |
| `SLOT_FILL` | `0xFF8B8B8B` | 内凹区域底色 |
| `SLOT_INNER` | `0xFF2E2E2E` | 内凹区域最深处 |
| `SLOT_BORDER_LIGHT` | `0xFFE0E0E0` | 内凹区域高光边（下、右） |
| `SLOT_BORDER_DARK` | `0xFF4A4A4A` | 内凹区域阴影边（上、左） |
| `LIST_ROW_ODD` | `0xFF959595` | 列表奇数行条纹 |
| `LINK` | `0xFF2B4C8C` | 行内链接文字 |
| `CONTROL_FILL` | `0xFFC6C6C6` | 控件常态 |
| `CONTROL_HOVER` | `0xFFDCDCDC` | 控件悬停 |
| `CONTROL_PRESSED` | `0xFF9A9A9A` | 控件按下 |
| `CONTROL_SELECTED` | `0xFF9FB6CE` | 控件选中 |
| `CONTROL_DISABLED` | `0xFFA0A0A0` | 控件禁用 |
| `CONTROL_BEVEL_LIGHT` | `0xFFFFFFFF` | 控件外凸高光边 |
| `CONTROL_BEVEL_DARK` | `0xFF555555` | 控件外凸阴影边 |
| `FOCUS_OUTLINE` | `0xFFFFFFFF` | 键盘焦点轮廓 |
| `ACCENT` | `0xFF5B708D` | 主要操作 |
| `ACCENT_HOVER` | `0xFF607591` | 主要操作悬停 |
| `ACCENT_PRESSED` | `0xFF4A5D78` | 主要操作按下 |
| `DANGER` | `0xFFA44A44` | 危险操作 |
| `DANGER_HOVER` | `0xFFAA5650` | 危险操作悬停 |
| `DANGER_PRESSED` | `0xFF8B3B36` | 危险操作按下 |
| `SUCCESS` | `0xFF5F9460` | 成功 |
| `WARNING` | `0xFFB8913C` | 警告 |
| `TEXT_PRIMARY` | `0xFF1E1E1E` | 主要文本 |
| `TEXT_SECONDARY` | `0xFF4A4A4A` | 次要文本 |
| `TEXT_DISABLED` | `0xFF7A7A7A` | 禁用文本 |
| `TEXT_ON_DARK` | `0xFFFFFFFF` | 深色底上的文本 |
| `SCROLL_TRACK` | `0xFF8B8B8B` | 滚动条轨道 |
| `SCROLL_THUMB` | `0xFFD0D0D0` | 滚动条滑块 |

`UiFlatPalette` 的 60 个常量：

| 常量 | 值或来源 | 源码注释给出的用途 |
|---|---|---|
| `CHROME_FILL` | `0xFFB4B4B4` | 功能栏与工具列底色 |
| `CHROME_BORDER` | `0xFF3C3C3C` | 外壳描边 |
| `STATUS_FILL` | `0xFF8E8E8E` | 状态栏底色 |
| `STATUS_TEXT` | `UiPalette.TEXT_PRIMARY` | 状态栏文本 |
| `STATUS_TEXT_MUTED` | `UiPalette.TEXT_SECONDARY` | 状态栏次要文本 |
| `STATUS_TEXT_DANGER` | `UiPalette.DANGER` | 状态栏错误文本 |
| `STATUS_TEXT_WARNING` | `UiPalette.WARNING` | 状态栏警告文本 |
| `PANEL_FILL` | `0xFFCFCFCF` | 调板主体底色 |
| `PANEL_HEADER_FILL` | `0xFF8E8E8E` | 调板标题栏底色 |
| `PANEL_BORDER` | `0xFF3C3C3C` | 调板 1 像素描边 |
| `PANEL_TEXT` | `UiPalette.TEXT_PRIMARY` | 调板正文 |
| `PANEL_INSET_FILL` | `0xFFC0C0C0` | 调板内容区内凹底色 |
| `PANEL_CONTROL_FILL` | `0xFFDCDCDC` | 调板控件常态底色 |
| `PANEL_CONTROL_HOVER` | `0xFFEAEAEA` | 调板控件悬停底色 |
| `PANEL_CONTROL_PRESSED` | `0xFFA8A8A8` | 调板控件按下底色 |
| `PANEL_CONTROL_SELECTED` | `0xFF9FB6CE` | 调板控件选中底色 |
| `PANEL_CONTROL_DISABLED` | `0xFFBEBEBE` | 调板控件禁用底色 |
| `CANVAS_FILL` | `UiPalette.SLOT_FILL` | 画布底色 |
| `CANVAS_BORDER` | `UiPalette.SLOT_BORDER_DARK` | 画布 1 像素描边 |
| `CANVAS_GRID_DOT` | `UiPalette.SLOT_BORDER_DARK` | 点阵网格 |
| `CANVAS_HINT_TEXT` | `UiPalette.TEXT_SECONDARY` | 空态提示文字 |
| `CANVAS_CARD_FILL` | `UiPalette.CONTROL_FILL` | 条件叶卡片底色 |
| `CANVAS_GROUP_FILL` | `UiPalette.PANEL_FILL` | 容器卡片底色 |
| `CANVAS_ALL_FILL` | `0xFFD9E7FF` | 「且」运算符底色 |
| `CANVAS_ALL_TEXT` | `0xFF234EA0` | 「且」运算符文字 |
| `CANVAS_ANY_FILL` | `0xFFD9F6E4` | 「或」运算符底色 |
| `CANVAS_ANY_TEXT` | `0xFF22643D` | 「或」运算符文字 |
| `CANVAS_NOT_FILL` | `0xFFEADFF8` | 「非」运算符底色 |
| `CANVAS_NOT_TEXT` | `0xFF674193` | 「非」运算符文字 |
| `CANVAS_FLOW_LINK` | `0xFF315E69` | 流程连线 |
| `CANVAS_CARD_HOVER_FILL` | `UiPalette.CONTROL_HOVER` | 悬停中的卡片底色 |
| `CANVAS_CARD_SELECTED_FILL` | `UiPalette.CONTROL_SELECTED` | 选中卡片底色 |
| `CANVAS_CARD_BORDER` | `UiPalette.CONTROL_BEVEL_DARK` | 未选中卡片描边 |
| `CANVAS_CARD_BORDER_SELECTED` | `UiPalette.ACCENT` | 选中卡片描边 |
| `CANVAS_LINK` | `UiPalette.PANEL_BORDER_DARK` | 父子连线 |
| `CANVAS_LINK_SELECTED` | `UiPalette.ACCENT` | 父子连线（任一端被选中） |
| `CANVAS_TEXT` | `UiPalette.TEXT_PRIMARY` | 卡片主行文字 |
| `CANVAS_TEXT_MUTED` | `UiPalette.TEXT_SECONDARY` | 卡片次行文字 |
| `CANVAS_TEXT_DANGER` | `UiPalette.DANGER` | 问题卡片主行文字 |
| `CANVAS_TEXT_ON_ACCENT` | `UiPalette.TEXT_ON_DARK` | 强调底色上的文字 |
| `CANVAS_ACCENT` | `UiPalette.ACCENT` | 强调色 |
| `CANVAS_ACCENT_HOVER` | `UiPalette.ACCENT_HOVER` | 强调色悬停态 |
| `CANVAS_HOTSPOT_BORDER` | `UiPalette.CONTROL_BEVEL_LIGHT` | 热区描边 |
| `CANVAS_DRAG_GHOST_FILL` | `UiPalette.CONTROL_SELECTED` | 拖动浮影底色 |
| `CANVAS_RUBBER_FILL` | `0x305F7FB0` | 框选矩形填充（半透明） |
| `CANVAS_PICKER_FILL` | `UiPalette.WINDOW_FILL` | 类型选择浮层底色 |
| `CANVAS_PICKER_BORDER` | `UiPalette.WINDOW_BORDER_DARK` | 类型选择浮层描边 |
| `CANVAS_PICKER_HEADER_FILL` | `UiPalette.HEADER_FILL` | 类型选择浮层标题栏底色 |
| `CANVAS_PICKER_HEADER_TEXT` | `UiPalette.HEADER_TEXT` | 类型选择浮层标题栏文字 |
| `CANVAS_PICKER_INPUT_FILL` | `UiPalette.SLOT_INNER` | 类型选择浮层输入框底色 |
| `CANVAS_PICKER_INPUT_BORDER` | `UiPalette.SLOT_BORDER_DARK` | 类型选择浮层输入框描边 |
| `CANVAS_PICKER_ROW_SELECTED` | `UiPalette.CONTROL_SELECTED` | 浮层中选中或悬停的行 |
| `CANVAS_PICKER_TEXT` | `UiPalette.TEXT_PRIMARY` | 浮层正文 |
| `CANVAS_PICKER_TEXT_MUTED` | `UiPalette.TEXT_SECONDARY` | 浮层次要文字 |
| `CANVAS_PICKER_TEXT_DISABLED` | `UiPalette.TEXT_DISABLED` | 浮层占位文字 |
| `HELP_FILL` | `0xFFE4E4E4` | 帮助浮层底色 |
| `HELP_BORDER` | `0xFF3C3C3C` | 帮助浮层描边 |
| `HELP_TEXT` | `UiPalette.TEXT_PRIMARY` | 帮助浮层标题 |
| `HELP_TEXT_MUTED` | `UiPalette.TEXT_SECONDARY` | 帮助浮层正文 |
| `FOCUS_OUTLINE` | `UiPalette.FOCUS_OUTLINE` | 键盘焦点轮廓 |

### 2.6 代码现状（可核对）

- 跨层 import：`UiFlatSkin` import `client/edit` 的 `TypeLabels`，用途是取 `TypeLabels.OWN_NAMESPACE` 拼接图标图集路径；本包其余文件不 import `client/edit`。
- 接口常量来自扁平皮肤：`UiHostTheme` 的 14 个 `ICON_*` 常量全部直接赋值 `UiFlatSkin.ICON_*`，主题接口因此引用同包的具体皮肤类。
- 像素主题转发扁平皮肤：`UiPixelTheme#icon`、`#drawChrome`、`#drawStatusBar`、`#drawHelp`、`#drawSurface` 调用 `UiFlatSkin` 的静态方法；其余绘制方法与文本色转发 `UiTheme`。
- 宿主调色板跨两套调色板取色：`UiHostPalette` 的 `chromeBorder`、`statusText*`、`helpText*`、`panelText`、`panelControlFill` 与 38 个 `canvas*` 访问器取自 `UiFlatPalette`，其余访问器取自 `UiPalette`。
- 图标资源有两张图集：`common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor/` 下存在 `icons_dark.png` 与 `icons_light.png`；`UiFlatSkin` 只引用 `icons_dark.png`。
- 调色板注释与常量命名不一致：`UiPalette` 类注释只列出 `WINDOW`、`PANEL`、`CONTROL`、`TEXT`、`SCROLL` 五个前缀；源码中另有 `SLOT_`、`HEADER_`、`LIST_`、`LINK`、`FOCUS_OUTLINE`、`ACCENT_`、`DANGER_`、`SUCCESS`、`WARNING`、`MODAL_DIM`、`TRANSPARENT`、`SHADOW` 等未列出的命名。
- 缺少 JavaDoc：`UiHostTheme` 的 32 个方法与 14 个常量只有行注释；`UiHostPalette` 只有类级 JavaDoc，72 个颜色访问器无注释；`UiPixelTheme`、`UiFlatTheme` 的重写方法无注释；`UiFlatPalette`、`UiFlatSkin`、`UiPalette`、`UiTheme` 的成员为行注释。
- 未引用常量：`UiHostTheme.ICON_GROUP_ANY`、`UiHostTheme.ICON_WRAP_ANY` 带 `@SuppressWarnings("unused")`，源码中没有调用点；其余 12 个 `ICON_*` 由 `ConditionTreeOverlay` 消费。
- 纯色绘制：`UiTheme#drawBevel` 与 `UiTheme#drawScrollTrack` 的方法注释写明用纯色填充，当前不使用贴图。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiHostTheme` | 宿主主题契约，控件与屏幕消费的视觉规格注入面 | `palette`、`controlStyle`、`sliderSkin`、`panelStyle`、`stripStyle`、18 个绘制与图标方法 |
| `UiHostThemes` | 主题入口，装配默认像素主题与扁平主题 | `defaultTheme`、`flatTheme`、`require` |
| `UiHostPalette` | 控件可见的只读语义色视图 | `hostPalette` 与 72 个颜色访问器 |
| `UiPixelTheme` | 默认宿主主题，转发 `UiTheme` 与 `UiPalette` | `controlStyle`、`sliderSkin`、`panelStyle`、`stripStyle` |
| `UiFlatTheme` | 扁平宿主主题，用 `UiFlatPalette` 与 `UiFlatSkin` | `controlStyle`、`sliderSkin`、`drawBevel`、`drawSegment` |
| `UiFlatPalette` | 扁平配色常量 | 60 个 ARGB 常量 |
| `UiFlatSkin` | 图标图集切片与扁平绘制原语 | `icon`、`drawSurface`、`drawOutline`、`drawChrome`、`drawStatusBar`、`drawHelp` |
| `UiPalette` | 灰色像素主题调色板 | 39 个 ARGB 常量 |
| `UiTheme` | 灰色像素主题的静态入口与度量常量来源 | `ROW_HEIGHT`、`PADDING`、`drawWindow`、`drawCard`、`sliderStyle`、`clampTo` |
| `package-info.java` | 包声明与依赖面说明 | — |

## 4. 相关文档

- [UI kit](ui-kit/README.md)
- [展示工具层](ui-shared.md)
- [原型层](ui-prototype.md)
- [客户端层](../layers/client.md)
- [ADR-0028：条件树画布页、扁平皮肤与 kit 公开面扩张](../../adr/0028-canvas-page-and-flat-skin.md)
- [ADR-0032：UI 主题与样式由宿主注入](../../adr/0032-host-injected-ui-theme.md)
- [文档索引](README.md)
