# ADR-0020：来源目录与覆盖层合并语义

- 状态：已实施；实现见 `core/load/RuleSourceIndex.java`、`core/service/RuleSnapshotAssembler.java`、`core/service/edit/RuleSnapshotEntry.java`、`core/service/edit/RuleSourceView.java` 与[编辑协议](../api/edit-protocol.md)。
- 依据：加载与合并链路沿用 [ADR-0014](0014-three-layer-scope-and-overlay-merge.md)；来源目录、应用流程与覆盖文件形态为本决策内容。
- 相关术语：`GLOSSARY.md` 中的「来源层」「覆盖层」「数据包基底」「来源标识」。
- 现行决策：启用语义见 [ADR-0044](0044-rule-enable-state-layering.md)。

## 背景

规则来源分三层（config 覆盖层 > 世界数据包 > 内置数据包），最终生效状态是合并结果。旧链路只保留合并后的运行索引，导致：

- 基底规则被覆盖后**看不出原始内容与来源**，无法区分「数据包原始」「自定义覆盖」「自建」；
- 删除覆盖条目后**无法恢复**，因为原始条目信息已经丢失。

加载服务因此保留**按 ID 聚合的来源目录**：原始基底、原始覆盖、加载问题与最终生效状态。

## 决策

1. **来源目录按 ID 聚合**，至少保留：原始基底 JSON、原始覆盖 JSON、加载问题、最终生效状态与服务端排序序号；不得仅由合并后的运行索引反推。快照条目 `RuleSnapshotEntry` 只承载 `effective`、`origin`、`source`、`status`、`editable`、`category`、`defaultEnabled`、`enabled`、`enabledOverride`、`hasBase`、`hasOverlay` 与 `issues`；基底与覆盖原文经按需请求的 `RuleSourceView` 单独下发（见 [ADR-0040](0040-on-demand-snapshot-source-views.md)）。
2. **状态区分**：`origin` 取 `overlay` / `datapack` / `mixed`；`source` 在数据包内部继续区分 `builtin` / `world`，覆盖与混合来源取 `overlay` / `mixed`；`status` 取 `active` / `disabled` / `invalid`。界面据此区分数据包原始（`datapack` + `hasBase`）、仅覆盖（`overlay`，`hasBase=false`）、覆盖与基底共存（`mixed` + `hasBase`/`hasOverlay`）、停用与非法（`issues` 定位）；`editable` 表示来源可写且持有可打开的规则内容，`source` 为 `builtin` / `world` 的条目为 false，界面禁用内容编辑与删除。
3. **不可解析或没有可用 ID 的文件只以问题列表呈现**，不制造可编辑的假规则。
4. **覆盖层语义表**（逐行即行为规范）：

| GUI 操作 | 覆盖层写入 |
|---|---|
| 新建 / 复制 | 新 ID 完整规则 upsert |
| 自定义数据包规则 | 相同 ID 写入完整覆盖规则 |
| 删除覆盖条目 | 协议 `delete` 移除该 ID 的覆盖条目，重新显示当前基底 |
| 切换本服启停 | 写本服启用状态，不动覆盖层文件 |

5. **覆盖层条目承载完整规则内容**：同 ID 覆盖条目整条替换基底内容，合并结果由最后生效的那一层整条决定；本服启停状态不占覆盖层条目，见 [ADR-0044](0044-rule-enable-state-layering.md)。
6. **编辑器保存形态**：顶层 JSON **对象**，一个文件一条规则，写入总带显式 `id`；文件路径由 id 派生为 `<namespace>/<path>.json`（命名空间为一级目录，路径可含子目录）。读取同时接受顶层对象与数组：多条目文件必须逐条声明显式 id，仅含一条且未声明 id 时由相对路径推导。
7. **冲突与坏 JSON 保留数据、阻止有歧义的修改**：同 ID 多文件重复定义、路径冲突或坏 JSON 时保留原数据并给出来源定位，由用户按定位手动消歧或修复。

## 理由

- 「按 ID 聚合的来源目录」是**可恢复性**的前提：只有保留基底原始 JSON，删除覆盖条目后才能重新显示基底内容，无需复制基底。
- 覆盖层条目以整条规则为单位：撤销一次覆盖就是删除该条目，不必把参数逐项改回基底值，也不会出现只改一半的陈旧副本。
- 编辑器写文件采用「一文件一条规则」并由 id 派生文件名，使覆盖层与「按 ID 逐条应用」的权威落盘模型一致，避免整文件覆盖丢失未涉及条目。
- 顶层数组仍被读取，是为了兼容数据包原始文件（一个文件可写多条规则），但它不是编辑器的输出形态。

## 后果

- 加载服务保留原始 JSON、来源、排序序号与问题列表；快照条目只带生效视图与存在标记，基底/覆盖原文按需请求，单条上限 4 MiB，快照与来源视图都走分片传输（见 [ADR-0019](0019-exclusive-edit-session-and-target-lock.md) 与 [ADR-0040](0040-on-demand-snapshot-source-views.md)）。
- GUI 展示每条规则的生效状态（生效 / 停用 / 非法 / 基底缺失）与本服覆盖标注，并据此决定可用操作；不可解析条目以问题定位呈现，编辑入口在规则可解码后出现。
- 覆盖层文件名与 id 的映射成为约定：重命名规则 id 等于「新建 + 删除」，界面需按 [ADR-0021](0021-display-name-and-stable-ids.md) 的规则提示用户。
- 删除动作只删除覆盖条目，基底规则保持可恢复；启停动作改写本服状态，不产生覆盖层文件。
