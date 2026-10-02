# NOTICE：客户端 UI kit 来源声明

本模组客户端 UI 基础设施（`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`）取自同作者的 Minecraft 模组 **unsuspiciousBlock**（1.21.1 多加载器项目）的客户端 UI kit，属同源复用，不引入任何第三方库依赖。

## 来源

- 源仓库路径：`D:/system_default/desktop/mc_mod_project/unsuspiciousBlock-1.21.1-multi`
- 源目录：`common/src/main/java/com/meteorite/unsuspiciousblock/client/ui/kit/`
- 源提交：`625747e1f34ea957841d7136be4635469687c227`
- 复制日期：2026-10-02
- 目标目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`

## 重命名规则

复制过程为机械文本替换，仅改动以下两类内容：

1. `package` 声明：`com.meteorite.unsuspiciousblock.client.ui.kit` → `com.meteorite.itemdespawntowhat.client.ui.kit`；
2. javadoc 中的全限定名（仅 `package-info.java` 的示例代码块内）。

未改动：类名、字段名、方法签名、实现逻辑、常量值、注释语义。

## 文件清单（23 个，共 2824 行）

| 文件 | 行数 |
| --- | --- |
| `LightboxImage.java` | 46 |
| `package-info.java` | 61 |
| `TextMeasurer.java` | 18 |
| `TextScroll.java` | 131 |
| `UiAction.java` | 7 |
| `UiControl.java` | 204 |
| `UiControlGroup.java` | 187 |
| `UiControlStyle.java` | 40 |
| `UiDocument.java` | 380 |
| `UiFocusManager.java` | 199 |
| `UiFocusTarget.java` | 33 |
| `UiIcon.java` | 40 |
| `UiImageView.java` | 229 |
| `UiLightbox.java` | 517 |
| `UiLinearLayout.java` | 212 |
| `UiMetrics.java` | 24 |
| `UiNavigationHistory.java` | 44 |
| `UiNineSlice.java` | 33 |
| `UiNode.java` | 83 |
| `UiRect.java` | 21 |
| `UiScrollView.java` | 197 |
| `UiTarget.java` | 17 |
| `UiTransform.java` | 101 |

## 依赖与约束

- kit 仅依赖 Minecraft 客户端通用类型、Java 标准库、JOML、LWJGL 与 JetBrains annotations；
- 不 import 本项目其他包，不 import Fabric / NeoForge 平台 API，不引用 `Constants.MOD_ID`；
- kit 不依赖 `core.model`，可在 `common` 内独立编译；`client/` 之外不得引用该包。

## 贴图资源

源项目 kit 自带贴图资源（`UiNineSlice` 与 `UiIcon.Sprite` 使用的纹理）。**本次未复制任何贴图资源**：两者的贴图 id 由调用方提供；主题层（`client/ui/theme/`）当前使用纯色像素边框绘制，需要美术资源接入的位置均以 `TODO(美术)` 注释标出。

## 已知差异

无。目标目录文件与源文件内容等价（仅包名重定位）。
