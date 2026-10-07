# Kit 实体预览提取方案

日期：2026-10-07。状态：用户 Q22 已确认采用方案 B，包括职责分层与必要的精确渲染类白名单调整；已实施 `UiEntityPreview` 与 `UiIcon.Rendered`，对象策略由宿主提供；游戏内模型与性能验收待用户执行。

关联：[UI 实施方案](plan-ui-alignment-implementation-2026-10-07.md)、[讨论台账](grill-ui-alignment-2026-10-06.md)、[Kit 维护边界 ADR-0025](../adr/0025-reusable-client-ui-kit-boundary.md)。

## 已确认的功能

- 实体使用自身 3D 模型作为图标，在框内绕竖直中轴旋转，等比适框，整圈尺度稳定。
- 只绘制可见项，缓存样例实体，不逐帧创建，不加入世界或运行玩法 tick。
- 按碰撞尺寸先估计，补明显偏差的原版模型修正；模组模型在 UI 优化后实测，不保证任意模型的精确可视边界。
- 无模型、创建/渲染失败或缺 renderer 时，用屏障方块的物品图标兜底，并显示本地化“预览不可用”；保留对象名称，不禁止选择。

## 实施前的调查证据（历史快照，非当前 API）

源码均相对于 `common/src/main/java/com/meteorite/itemdespawntowhat/`。

| 证据 | 结论 |
|---|---|
| `client/ui/kit/UiIcon.java:10–22` | sealed，仅 Item/Sprite；宿主不能直接实现自定义 UiIcon |
| `client/ui/kit/UiNode.java:22/44`、`UiDocument.java:194–217/289–305` | 行首/行内图标已有尺寸排版、tooltip、命中与绘制路径 |
| `client/ui/widget/UiCatalogGrid.java:50/363–366` | 目录条目直接接收 UiIcon，可复用适配 |
| `client/ui/widget/UiTreeView.java:31/317`、`UiListView.java:30–32/268` | 行渲染器可绘制预览图标；行高由宿主同步设置 |
| `client/ui/screen/RuleEditorEditPages.java:1050/1063` | 方案/动作列表有真实行渲染接点 |
| `client/ui/kit/UiImageView.java:12–25/69–94` | 纹理图片视图有独立尺寸/适框/缺图职责，但不能直接承担实体模型渲染 |
| `client/ui/kit/package-info.java:51–62`、ADR-0025 | Kit 面向 Minecraft 客户端，允许原版通用类型；禁止反向依赖项目业务、主题、widget、loader、规则 JSON |
| `tools/check-ui-kit-boundaries.ps1:44` | 当前 import 白名单不含 com.mojang.blaze3d；原版 Lighting 等不能按现状直接引入 Kit |

目录、管理树、方案/动作行、选中规则预览共四个展示接点。未来其他 Minecraft 客户端宿主也有同类需求；无需假设额外业务才能证明复用价值。

## 方案对比

| 方案 | 复用范围 | 成本与代价 |
|---|---|---|
| A：渲染留宿主，Kit 只增加自定义图标适配 | 本项目四处共享宿主 renderer | Kit 改动最少，其他宿主仍需重做 Entity 渲染与状态恢复 |
| B：Kit 提供通用实体绘制核心和图标适配，宿主提供对象与策略 | 本项目与未来 Kit 宿主均复用矩阵/适框/旋转/裁剪/状态恢复 | 增加小规模公开 API、文档与依赖白名单维护；Q22 已采用 |
| C：Kit 接管类型解析、实例创建、缓存、修正与兜底的完整系统 | 接入最少 | 涉及客户端世界、资源、第三方样例及业务状态生命周期，公开契约过大，当前无需全部固化 |

## 已确认方案 B 的职责

| 放入 Kit | 保留在客户端宿主 |
|---|---|
| 绘制传入 Entity 的通用核心 | 对象类型/ID/规则字段解析与实例创建 |
| 以 UiRect 为边界的居中、等比适框与旋转 | 预览对象与失败缓存、容量、失效与重试 |
| 接受宿主传入角度、中心/尺寸修正和留边参数 | 按预览参数更新样例、原版/模组特殊模型修正 |
| 局部矩阵、裁剪、光照、缓冲提交与状态恢复 | 可见项调度、切服/断线/资源重载生命周期 |
| 返回可判定的成功/不可用绘制状态 | 识别已知无模型对象，解释不可预览原因 |
| 可被 UiIcon 消费端使用的受控绘制适配 | 屏障 ItemStack、名称和本地化“预览不可用” |

Kit 不加载目录、不读取规则 JSON、不发网络请求、不决定方案分类。屏障兜底是本项目选择，由宿主实现或注入，不固定为其他宿主必须采用的样式。

## 最小 API 职责草案

### 实体预览核心

实际类型为 `UiEntityPreview`，接受 GuiGraphics、目标 UiRect、已准备的 Entity、旋转角度与适框参数，返回绘制结果。类型名称及准确参数由实施时的调用点决定，不让用户逐项选择方法名。

- 调用在客户端渲染线程；Kit 不保存全局世界、实体或资源缓存。
- 角度由宿主使用单调时间计算，不按帧计数，不随 FPS 改变旋转速度，也不依赖世界 tick。
- 尺寸参数代表估计的可视范围与中心；Kit 执行适框计算，不声称传入碰撞尺寸就是实际模型边界。
- 处理可判定的缺 renderer/绘制异常并恢复状态；静默不产生任何可视几何的第三方 renderer 无通用判定保证，不能用“调用成功”证明图标一定有模型。
- 宿主收到失败状态后先恢复到普通 GUI 绘制，再画屏障并提供提示；同一失败对象避免逐帧重试/刷日志，生命周期变化后可重试。

### UiIcon 绘制适配

保留 Item/Sprite，增加受控的 Rendered 图标实现，接受固定宽高与绘制回调。宿主通过回调调用预览核心或画兜底，同一图标可进入目录、UiNode 行首与列表行。

- 不把 Entity 硬塞进 UiImageView/LightboxImage 的纹理图片契约。
- 不为每个页面复制 3D 渲染代码，也不为每一帧重建内容/排版。
- 调整树/列表行高和目录格尺寸，以容纳 3D 图标与文字；UiDocument 已支持图标自然尺寸，普通树/列表需要宿主显式设置行高。
- 新增 sealed 子类型可能影响依赖者的穷尽 switch；同步公开 API 变更说明，不能无依据宣称所有外部源码均兼容。

## 必要的依赖边界调整

已精确放行 `com.mojang.blaze3d.platform.Lighting`，脚本/package-info/API/NOTICE 同步；没有引入 RenderSystem/PoseStack 的额外依赖，也没有放开整个 com.mojang。

现有禁止项目业务、Gson 与 Fabric/NeoForge API 的边界继续有效。此调整扩展 Minecraft 渲染能力，不引入 loader 依赖或第三方 UI 库。

## 实施与验证

- 纳入已确认的第三个 UI 任务，先完成通用核心/图标适配，再接四处展示点与宿主 provider；不新增第四个独立发布任务。
- 更新 Kit package-info、`docs/dev/internals/ui-kit-api.md` 的最小接入示例、边界检查脚本和 NOTICE-kit.md 差异记录；当前源码说明在实现时更新，不提前写成已具备能力。
- 实现后先 IDEA 检查，核对 Kit 依赖边界，再按项目包装脚本串行 build；不新增 test 文件。当前已包含 Java 实现，按项目顺序做静态检查与构建。
- UI 优化后由用户核验各实体整圈适框、非生物实体、屏障兜底、切服/重载、滚动帧率以及预览后普通物品/文字渲染是否受污染。

最终结论：Q22 已采用方案 B。纳入第三个 UI 任务，用户已确认实施，源码已落盘，验证记录见 UI 实施方案。
