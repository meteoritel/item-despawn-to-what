# ADR-0022：客户端 UI kit 的同源引入与来源记录

- 状态：已实施（P1，2026-10-02）；来源声明见 [NOTICE-kit.md](../../NOTICE-kit.md)，契约见 [实施契约 §4](../plan/plan-frontend-rewrite-contract.md)。
- 后续决策（2026-10-03）：[ADR-0025](0025-reusable-client-ui-kit-boundary.md)已接受、待实施，将机械改写/冻结副本方向替换为记录差异的维护副本；本 ADR 仍保留原引入记录，其它来源与隔离约束继续成立。
- 依据：[契约 §4](../plan/plan-frontend-rewrite-contract.md)（kit 引入契约）、[契约 §5](../plan/plan-frontend-rewrite-contract.md)（客户端 UI 契约）、[规划书 §3.1](../plan/plan-frontend-rewrite.md)（kit 来源）、[规划书 §9.4](../plan/plan-frontend-rewrite.md)（kit 扩展边界与视觉验收）。

## 背景

新前端要做「灰色像素风」的完整编辑器：窗口、滚动、布局、焦点、控件组、九宫格边框、图标、弹窗等基础能力如果从零写起，会重复投入且难保证视觉一致；引入第三方 UI 库又会带来依赖、体积与许可证问题。同一作者另有一个 Minecraft 1.21.1 多加载器模组 **unsuspiciousBlock**，其中已有一套成熟的客户端 UI kit。

## 决策

1. **同源复制**：从 `D:/system_default/desktop/mc_mod_project/unsuspiciousBlock-1.21.1-multi/common/src/main/java/com/meteorite/unsuspiciousblock/client/ui/kit/` 复制 23 个文件到 `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`，源提交 `625747e1f34ea957841d7136be4635469687c227`。
2. **只做机械改写**：仅改 `package` 声明（`com.meteorite.unsuspiciousblock.client.ui.kit` → `com.meteorite.itemdespawntowhat.client.ui.kit`）与 javadoc 中的全限定名；**不改**类名、字段名、方法签名、实现逻辑、常量值与注释语义。
3. **新增来源声明 `NOTICE-kit.md`**：记录源仓库路径、源提交、23 个文件清单与行数（共 2824 行）、重命名规则、已知差异（无）。
4. **保持 kit 通用**：kit 不得 import 本项目其他包，不得依赖 `core.model`，不得 import 平台 API，也不得引用 `Constants.MOD_ID`；仅依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL 与 JetBrains annotations。
5. **不复制贴图资源**：`UiNineSlice` 与 `UiIcon.Sprite` 使用的纹理 id 由调用方提供；主题层（`client/ui/theme/`）当前用纯色像素边框绘制，需要美术资源的位置以 `TODO(美术)` 注释标出。
6. **主题与控件分层**：kit 只提供通用布局/交互原语；灰色像素主题在 `client/ui/theme/`，本模组控件在 `client/ui/widget/`，业务屏幕在 `client/ui/screen/`。
7. **视觉验收基线**（契约 §5）：最低 320×240 GUI 逻辑坐标；小尺寸页签可滚动、参数转单列；大屏扩宽但不留大片空白；禁止现代网页式圆角卡片；颜色必须辅以文字或图标。

## 理由

- 同源复用没有引入任何第三方库依赖，因此不存在新的许可证与体积负担，也不必为外部库的版本漂移买单。
- 保持 kit 通用（不依赖 `core.model`、不依赖平台 API）可以让它继续可回流上游、可被其他模组复用，避免本模组业务渗入基础设施。
- 只做包名与 javadoc 改写、并把 23 个文件与行数写进 NOTICE，使复制件可逐文件核对，后续升级有据可查。
- 不复制贴图资源避免引入许可不明的美术资产；用纯色像素边框先满足「灰色像素风」验收，美术资源后补。
- 主题与控件分层让「视觉规范」与「基础能力」解耦：换主题不影响 kit，加控件不污染 kit。

## 后果

- `client/ui/kit/` 成为**冻结复制件**：任何改动都偏离上游，必须记录在 NOTICE 的「已知差异」中；升级需重新比对源提交。
- 主题层承担视觉规范（灰色像素风、状态色 + 文字/图标双编码），控件层承担本模组交互（输入框、列表、条件树等）。
- 贴图缺失期间，相关位置以 `TODO(美术)` 标记，验收时按「无贴图不阻塞功能」处理。
- 契约 §5 的尺寸与缩放验收（320×240 与 1920×1080、缩放 1x/2x/3x、无文本溢出与控件重叠）由主题与控件层共同承担。
