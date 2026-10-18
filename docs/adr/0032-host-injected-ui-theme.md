# ADR-0032：UI 主题与样式由宿主注入

- 状态：已实施；kit 依赖边界见 [UI kit 模块文档](../modules/ui-kit.md)。
- 依据：kit 的库化目标，以及用户对「主题是否改为宿主注入」的答复。

## 背景

[客户端 UI 模块文档](../modules/client-ui.md)把主题定位为「把灰色像素与扁平两套外观映射成 kit 的样式值，并以 `UiHostTheme` 作为唯一注入入口」；kit 的目标是可独立发布的库 API（见 [ADR-0029](0029-client-dependency-boundary-and-shared-subpackage.md)、[ADR-0030](0030-ui-kit-as-standalone-library-api.md)），不能硬编码本模组的主题与调色板——控件若自行读取全局调色板或自带主题，换主题就要改 kit 或逐个控件改。

## 决策

1. **kit 只提供接口**：kit 定义样式与主题接口（`UiStyleSource`、`UiSliderSkin`、`UiControlRole`、`UiSliderRole`），不实现本模组视觉规范。
2. **宿主注入主题**：宿主侧主题实现接口（`UiHostTheme`）并经注入传给控件与消费点，控件取色只经注入的主题与调色板。
3. **宿主入口默认提供现行主题**：屏幕与控件从 `UiHostThemes.defaultTheme()` 取默认主题，既有画面不变。

## 考虑过的替代方案

- **保留静态主题访问**：kit 依赖全局状态，无法独立发布，也无法在测试中替换主题。
- **让 kit 自带主题**：宿主视觉规范渗入通用库，换主题要改 kit。
- **一次性全量迁移所有控件**：涉及控件层与界面层的全部取色点，单次迁移风险不可控。

## 后果

- 控件取色经注入接口，主题与视觉规范归宿主侧。
- kit 不依赖本模组主题实现，具备独立发布的边界条件。
- 控件与页面的取色都经注入的主题实例，kit 侧不持有全局静态主题。
