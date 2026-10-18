# ADR-0020：来源目录、覆盖层语义与停用/屏蔽控制条目

- 状态：已实施；实现见 `core/load/RuleSourceIndex.java`、`core/service/RuleSnapshotAssembler.java`、`core/network/protocol/RuleSnapshotEntry.java`、`core/network/protocol/RuleSourceView.java` 与[编辑协议](../api/edit-protocol.md)。
- 依据：加载与合并链路沿用 [ADR-0014](0014-three-layer-scope-and-overlay-merge.md)；来源目录、应用流程与覆盖文件形态为本决策内容。
- 相关术语：`GLOSSARY.md` 中的「来源层」「覆盖层」「数据包基底」「覆盖控制条目」「来源标识」。

## 背景

规则来源分三层（config 覆盖层 > 世界数据包 > 内置数据包），最终生效状态是合并结果。旧链路只保留合并后的运行索引，导致：

- 基底规则被覆盖后**看不出原始内容与来源**，无法区分「数据包原始」「自定义覆盖」「自建」；
- 停用/屏蔽基底后**无法恢复**，因为原始条目信息已经丢失；
- 「删除覆盖条目」与「屏蔽基底规则」在界面上混为一谈，容易误删数据包内容。

加载服务因此保留**按 ID 聚合的来源目录**：原始基底、原始覆盖、控制条目、加载问题与最终状态。

## 决策

1. **来源目录按 ID 聚合**，至少保留：原始基底 JSON、原始覆盖 JSON、控制条目、加载问题、最终生效状态与服务端排序序号；不得仅由合并后的运行索引反推。快照条目 `RuleSnapshotEntry` 只承载 `effective`、`origin`、`status`、`editable`、`category`、`hasBase`、`hasOverlay` 与 `issues`；基底与覆盖原文经按需请求的 `RuleSourceView` 单独下发（见 [ADR-0040](0040-on-demand-snapshot-source-views.md)）。
2. **状态区分**：`origin` 取 `overlay` / `datapack` / `mixed`；`status` 取 `active` / `disabled` / `masked` / `invalid`。界面据此区分数据包原始（`datapack` + `hasBase`）、仅覆盖（`overlay`，`hasBase=false`）、覆盖与基底共存（`mixed` + `hasBase`/`hasOverlay`）、停用、屏蔽、非法（`issues` 定位）；`editable` 表示来源目录持有该条目的原始内容，可据此决定是否请求来源视图。
3. **不可解析或没有可用 ID 的文件只以问题列表呈现**，不制造可编辑的假规则。
4. **覆盖层语义表**（逐行即行为规范）：

| GUI 操作 | 覆盖层写入 |
|---|---|
| 新建 / 复制 | 新 ID 完整规则 UPSERT |
| 自定义数据包规则 | 相同 ID 写入完整覆盖规则 |
| 停用普通自建 / 覆盖规则 | 保存 `enabled=false`，保留内容 |
| 停用原始基底 | 控制条目 `disabled=true`（加载时只读 `id`/`disabled`/`delete`） |
| 删除覆盖条目 | 协议 `DELETE` 移除该 ID 的覆盖/控制条目，重新显示当前基底 |
| 标记删除 | 写入带 `delete=true` 的覆盖条目，同 ID 基底随之退出生效集合 |

5. **两种「删除」不可混称**：协议 `DELETE` 表示删除覆盖条目；JSON `delete=true` 表示屏蔽基底。`disabled` 保留内容、最终来源记为覆盖层；`delete` 直接删除同 ID 基底规则。
6. **同层内顺序固定**：普通条目先合并、控制条目后应用（见 [GLOSSARY.md](../../GLOSSARY.md) 「覆盖控制条目」）。
7. **编辑器保存形态**：顶层 JSON **对象**，一个文件一条规则，写入总带显式 `id`；文件路径由 id 派生为 `<namespace>/<path>.json`（命名空间为一级目录，路径可含子目录）。读取同时接受顶层对象与数组：多条目文件必须逐条声明显式 id，仅含一条且未声明 id 时由相对路径推导。
8. **冲突与坏 JSON 保留数据、阻止有歧义的修改**：同 ID 多文件重复定义、路径冲突或坏 JSON 时保留原数据并给出来源定位，由用户按定位手动消歧或修复。

## 理由

- 「按 ID 聚合的来源目录」是**可恢复性**的前提：只有保留基底原始 JSON，删除覆盖条目后才能重新显示基底内容，无需复制基底。
- 控制条目（只读取 `id` + `disabled`/`delete`）让覆盖层能够影响基底规则，而无需复制基底内容，避免基底更新后被陈旧副本覆盖。
- 协议层把「删除覆盖条目」与「屏蔽基底」分成两种写入，直接对应两种不同后果：前者只影响覆盖层，后者影响最终生效集合；混用会造成数据包规则被误删。
- 编辑器写文件采用「一文件一条规则」并由 id 派生文件名，使覆盖层与「按 ID 逐条应用」的权威落盘模型一致，避免整文件覆盖丢失未涉及条目。
- 顶层数组仍被读取，是为了兼容数据包原始文件（一个文件可写多条规则），但它不是编辑器的输出形态。

## 后果

- 加载服务保留原始 JSON、来源、排序序号与问题列表；快照条目只带生效视图与存在标记，基底/覆盖原文按需请求，单条上限 4 MiB，快照与来源视图都走分片传输（见 [ADR-0019](0019-exclusive-edit-session-and-target-lock.md) 与 [ADR-0040](0040-on-demand-snapshot-source-views.md)）。
- GUI 必须展示控制状态（停用/屏蔽/非法/基底缺失），并据此决定可用操作；不可解析条目以问题定位呈现，编辑入口在规则可解码后出现。
- 覆盖层文件名与 id 的映射成为约定：重命名规则 id 等于「新建 + 删除」，界面需按 [ADR-0021](0021-display-name-and-stable-ids.md) 的规则提示用户。
- 删除动作必须区分协议 DELETE 与 JSON `delete=true`，命令、网络与界面文案都要分别表述，禁止使用同一个「删除」标签。
- 停用与屏蔽都通过覆盖控制条目表达；删除对应的覆盖条目后，基底内容重新生效。
