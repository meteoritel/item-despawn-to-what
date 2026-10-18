# ADR-0029：客户端依赖边界与中立共享子包

- 状态：已实施；依赖方向登记见[依赖规则](../architecture/dependency-rules.md)与[客户端 UI 模块文档](../modules/client-ui.md)。
- 依据：客户端 UI 分层审查的实测结论，以及用户对「传输载荷类型」范围与反向依赖两项待裁决问题的答复。

## 背景

层内方向登记为单向 `client/net`（协议）→ `client/edit`（草稿与注册表）→ `client/ui`（界面），见[依赖规则](../architecture/dependency-rules.md)。把只读 DTO 也算进「传输载荷」，就与源码用法冲突：界面层多个文件直接使用 `core.network.protocol` 的快照、问题与目录类型，承担隔离职责的只读工作区视图也导出这些类型；`client/edit` 引用 kit 的树编辑契约（条件节点适配与树编解码）；`ui/screen` 单向使用 `ui/screen/form` 的表单与条件树编辑支持；界面层在调试观测里组装 `JsonObject`。

不写清范围，「界面层不得直接接触传输载荷」就会被各自解释成「现状允许」，协议 DTO 增删会穿透界面，草稿层与界面层的依赖方向也失去判断依据。因此先界定「传输载荷类型」的范围，再固定三处依赖的方向。

## 决策

1. **传输载荷类型只指传输通道类型**：传输通道由 `client/net` 收敛（`RuleEditClientWorkspace` 是 `client/net` 对界面层的唯一入口，上行发送器由客户端初始化注入、在包内单点持有），界面层不接触；`core.network.protocol` 的只读 DTO（快照与条目、问题、目录、保存状态）视为客户端可用契约，界面层可直接使用。界面层仍不得直接构造或发送上行载荷。
2. **`client/edit` 不依赖界面表现层**：草稿与撤销栈持有的校验问题类型落在 `client/edit` 自身（`FormIssue` 与 `RuleIssueLocator`）；条件节点经 `ConditionTreeNodes` 实现 kit 的 `UiTreeEditor.Adapter`，树编解码经 `ConditionTreeJsonCodec` 实现 `UiTreeCodec`，因此 `client/edit` 只依赖 kit 的树契约，不依赖 `ui/theme`、`ui/widget` 与 `ui/screen`。
3. **`ui/screen` 与 `ui/screen/form` 单向**：`ui/screen` 使用 `ui/screen/form` 的表单、叶面板与条件树编辑支持，`ui/screen/form` 不引用 `ui/screen`；两者共享的纯值与展示类型放在 `ui/shared`，两侧只向下依赖 `ui/shared`、`ui/theme`、`ui/widget` 与 kit。
4. **界面层的 JSON 用法限定在调试观测**：模型 JSON 的解析与构造收敛到 `client/edit`（`ConditionTreeJsonCodec` 等），界面只消费模型或转换结果；界面层用 Gson 组装调试事件负载（`DebugProfile` 的 `JsonObject`），不参与模型读写。

## 考虑过的替代方案

- **维持「界面层不接触任何协议类型」，在 `client/edit` 增加视图模型层**（快照、问题、目录各一份镜像类型）：隔离更彻底，但每个协议 DTO 都要维护镜像与转换，协议演进须两处同改；只读工作区视图已承担传输通道隔离，收益与外扩成本不成比例。
- **承认 `ui/screen` 与 `ui/screen/form` 同层双向**：类级环不消除，改动影响面仍无法判断，两个子包也不能独立测试。
- **保留界面层 Gson 用法**：解析与布局继续交织，结构变化直接穿透界面。

## 后果

- [依赖规则](../architecture/dependency-rules.md)与[客户端 UI 模块文档](../modules/client-ui.md)按本决策登记方向：界面层可直接使用 `core.network.protocol` 的只读 DTO，但不得直接构造上行载荷；`client/edit` → `client/ui/kit` 的树契约作为已知例外登记。
- 传输通道仍由 `client/net` 收敛，端隔离与平台隔离不变。
- `ui/screen/form` 不引用 `ui/screen`；`client/edit` 不依赖界面表现层；界面层的 Gson 用法只剩调试观测负载。
- 契约细节由实现与依赖规则文档承载；本 ADR 不复述类清单。
