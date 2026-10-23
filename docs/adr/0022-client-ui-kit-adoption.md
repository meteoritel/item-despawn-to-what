# ADR-0022：客户端 UI kit 的同源引入与来源记录

- 状态：已实施；公开面见 [UI kit 模块文档](../modules/ui-kit.md)。
- 依据：kit 引入契约、客户端 UI 契约与视觉验收基线。

## 背景

新前端要做「灰色像素风」的完整编辑器：窗口、滚动、布局、焦点、控件组、九宫格边框、图标、弹窗等基础能力如果从零写起，会重复投入且难保证视觉一致；引入第三方 UI 库又会带来依赖、体积与许可证问题。同一作者另有一个 Minecraft 1.21.1 多加载器模组 **unsuspiciousBlock**，其中已有一套成熟的客户端 UI kit。

## 决策

1. **同源复制**：从 `D:/system_default/desktop/mc_mod_project/unsuspiciousBlock-1.21.1-multi/common/src/main/java/com/meteorite/unsuspiciousblock/client/ui/kit/` 复制整套 kit 包（含 `package-info` 与全部公开类）到 `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`，源提交 `625747e1f34ea957841d7136be4635469687c227`。
2. **只做机械改写**：仅改 `package` 声明（`com.meteorite.unsuspiciousblock.client.ui.kit` → `com.meteorite.itemdespawntowhat.client.ui.kit`）与 javadoc 中的全限定名；**不改**类名、字段名、方法签名、实现逻辑、常量值与注释语义。
3. **保持 kit 通用**：kit 不得 import 本项目其他包，不得依赖 `core.model`，不得 import 平台 API，也不得引用 `Constants.MOD_ID`；仅依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL 与 JetBrains annotations，原版渲染类按 [ADR-0025](0025-reusable-client-ui-kit-boundary.md) 的白名单精确放行（`platform.Lighting`、`vertex.PoseStack`、`vertex.VertexConsumer`）。
4. **不复制上游贴图资源**：`UiNineSlice` 与 `UiIcon.Sprite` 使用的纹理 id 由调用方提供；本模组自带的九宫格底图与图标图集放在 `textures/gui/editor/`，图标图集由 `tools/image_generator/generate-editor-icons.py` 生成；主题层（`client/ui/theme/`）的窗口、面板与内凹区的凸凹边由 `chrome.png` 的九宫格掩码按主题色染色绘制，滚动条轨道与滑块同样取自该图集，均由 `tools/image_generator/generate-editor-chrome.py` 生成。
5. **主题与控件分层**：kit 只提供通用布局/交互原语；灰色像素主题在 `client/ui/theme/`，本模组控件在 `client/ui/widget/`，业务屏幕在 `client/ui/screen/`。
6. **视觉验收基线**：最低 320×240 GUI 逻辑坐标；小尺寸页签可滚动、参数转单列；大屏扩宽但不留大片空白；颜色必须辅以文字或图标。圆角卡片的取舍与画布页的扁平皮肤见 [ADR-0028](0028-canvas-page-and-flat-skin.md)。

## 理由

- 同源复用没有引入任何第三方库依赖，因此不存在新的许可证与体积负担，也不必为外部库的版本漂移买单。
- 保持 kit 通用（不依赖 `core.model`、不依赖平台 API）可以让它继续可回流上游、可被其他模组复用，避免本模组业务渗入基础设施。
- 只做包名与 javadoc 改写，复制件可与源提交逐文件比对，后续升级有据可查。
- 不复制上游贴图资源避免引入许可不明的美术资产；底图与图标集按本模组调色板自制，图标图集随仓附带生成脚本，视觉资产来源可核查。
- 主题与控件分层让「视觉规范」与「基础能力」解耦：换主题不影响 kit，加控件不污染 kit。

## 后果

- `client/ui/kit/` 按 [ADR-0025](0025-reusable-client-ui-kit-boundary.md) 作为**可回流的维护副本**：改动以记录来源与差异的方式维护，公开面扩张见 [ADR-0028](0028-canvas-page-and-flat-skin.md)。
- 主题层承担视觉规范（灰色像素风、状态色 + 文字/图标双编码），控件层承担本模组交互（输入框、列表、条件树等）。
- 九宫格底图与图标图集集中在 `textures/gui/editor/`，由主题层统一装配；新增底图沿用同一路径与切分方式。
- 尺寸与缩放验收（320×240 与 1920×1080、缩放 1x/2x/3x、无文本溢出与控件重叠）由主题与控件层共同承担。