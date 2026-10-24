---
title: 规则 JSON 契约
layer: 5
audience: 编写规则文件、扩展规则字段或规则类型的开发者
authority: common/src/main/java/com/meteorite/itemdespawntowhat/core/api/RuleFields.java、core/model/RuleCodecs.java、core/model/RuleValidation.java、core/load/RuleFileParser.java、core/load/RuleMerger.java
---

# 规则 JSON 契约

## TL;DR

- 一条规则是一个 JSON 对象；规则文件可以是单个对象，也可以是对象数组。
- 来源层按 内置数据包 → 世界数据包 → config 覆盖层 合并，同 `id` 由后面一层覆盖；覆盖层用控制条目停用或删除规则。
- 字段名以 `RuleFields` 为唯一来源；新增字段要同时改字段常量、Codec 与语义校验。

## 适用对象

手写规则文件、做数据包、实现规则编辑器或给规则模型加字段的开发者。

## 文件与位置

| 来源层 | 位置 | 说明 |
|---|---|---|
| 内置数据包 | 模组自带的 `idtw/rules/` 资源 | 随模组分发，作为默认规则基底 |
| 世界数据包 | 存档或数据包提供的 `idtw/rules/` 资源 | 同一路径可叠加多个包，按包的优先级取后者 |
| config 覆盖层 | `config/<overlay_directory>/rules/` | 运行时可直接编辑的一层，也是编辑器写盘的唯一落点 |

`overlay_directory` 的取值见 [server.json 配置](../ops/configuration.md)。规则加载读取数据包与覆盖层，按层优先级合并后再解码，合并与拒载粒度见 [错误与状态码](errors.md)。

文件以 `.json` 结尾，内容可以是单个规则对象，也可以是规则对象数组。同一文件内多条规则共享文件路径，因此每条必须显式声明 `id`；单个对象的文件在缺 `id` 时可由文件路径推导，命名空间取默认命名空间，路径去掉扩展名并转成资源路径。

规则缺省 ID 的命名空间为 `idtw`。显式声明的规则 `id` 可使用任意合法命名空间。

```mermaid
flowchart TB
    A[内置数据包条目] --> D[层内排序]
    B[世界数据包条目] --> D
    C[config 覆盖层条目] --> D
    D --> E[按 id 合并: 后者覆盖前者]
    E --> F[逐条解码与校验]
    F -->|通过| G[进入运行时索引]
    F -->|ERROR| H[拒载该条目]
```

## 覆盖控制条目

覆盖层的条目除了完整规则，还可以只声明控制意图：`id` 加上 `disabled` 或 `delete`。控制条目只识别这三个键，其余键会被忽略并记一条 WARN；两个控制键同时为 `true`、或控制键出现在数据包层，都会被拒绝。

- `disabled`：保留基底规则的完整内容，仅把它标记为停用；找不到同 `id` 的基底时记 WARN 并忽略该声明。
- `delete`：从合并结果中移除同 `id` 的规则。

层内先处理普通条目、再处理控制条目；因此控制条目总能作废同层或更底层的同名规则。编辑器提交控制条目时要求条目只包含 `id` 与一个值为 `true` 的控制键，这条约束在提交校验中执行。

## 规则对象的分区

规则对象按键的语义分区。每个分区的键名全部登记在 `RuleFields`，本节只说明分区职责与代表键，不复制完整字段表。

| 分区 | 回答的问题 | 代表键 | 校验落点 |
|---|---|---|---|
| 标识与展示 | 这条规则叫什么、优先级多高、是否启用 | `id`、`display_name`、`priority`、`enabled` | 解码档 + 语义校验 |
| 来源匹配 | 哪些物品会触发这条规则 | `source` | 语义校验（物品引用与 tag） |
| 触发与时机 | 在什么消失方式下判定、存活多久后才判定 | `triggers`、`trigger_after_seconds` | 语义校验 + 运行时选择 |
| 条件 | 判定成立还需要满足什么 | `conditions` | 解码档（形状与预算）+ 语义校验（规模） |
| 效果与候选 | 转化产出什么 | `effects` 或 `outcomes` | 语义校验 + 类型注册表参数校验 |
| 成本与催化剂 | 消耗多少源物品、是否需要催化剂 | `source_cost`、`catalyst_cost`、`consume_fluid` | 语义校验 |
| 组合 | 多个候选如何轮流出现 | `combination` | 解码档（枚举取值） |
| 版本与分类 | 规则格式版本、编辑器中的分类 | `schema_version`、`category` | 解码档 + 快照装配 |

`category` 的取值是规则的分类标识，未知取值按原文成组展示；编辑器列表的「未完成」与「有问题」分组只按草稿状态和校验问题展示，分类器不产出这两个值。

`triggers` 的取值是消失方式枚举，`combination` 的取值是组合模式枚举，两者都由模型层的枚举定义；新增取值需要同时改枚举与解码档的取值校验。`trigger_after_seconds` 缺省时按模型层默认值补齐。

## 条件树

`conditions` 是一棵以 `op` 分派的条件树，允许的 `op` 集合由 `RuleFields` 的 `OP_*` 常量给出：组合节点（`all_of` / `any_of`）、取反节点（`inverted`）、叶节点（`leaf`）。

- 组合节点用 `terms` 承载子节点，`all_of` 要求全部成立、`any_of` 要求任一成立，`terms` 为空会被当作写法错误拒绝，空条件应整体省略 `conditions`。
- 取反节点用单个 `term` 承载子节点，用来表达叶级取反。
- 叶节点用 `condition` 承载一个条件实例，类型由类型注册表解析，参数校验由注册的校验入口执行。
- 完整规则的空条件表达式在编码时省略 `conditions`；解码缺省还原为无条件，显式空组合节点、空对象与数组写法按结构错误拒绝。

条件树的形状与嵌套规模有独立预算：解析前先做文本嵌套深度预检，解码时再按条件深度与节点数检查，超限按错误处理而不是抛异常。预算常量集中在 `core/api/RuleDecodeBudget.java`，调整口径在那里改。

解码器对已废弃的写法给出带改写提示的明确错误（例如旧的分组键、把取反写在叶节点内、把节点字段写在错误层级），因此升级规则文件时按报错提示改写即可，不需要猜测。

## 版本与校验

`schema_version` 标记规则格式版本，当前取值为 1（`RuleCodecs.DEFAULT_SCHEMA_VERSION`）；取值不匹配的条目会被拒载。缺省时按当前版本补齐。

规则进入运行时索引前先过解码档、再过语义档，全部通过 Issue 报告问题，ERROR 导致拒载：

| 档位 | 入口 | 负责什么 |
|---|---|---|
| 解码档 | `RuleCodecs` 的 decoder | JSON 形状、字段名、枚举取值、条件树结构、条件预算、废弃写法 |
| 语义档 | `RuleValidation` | 必填项、来源为空、效果与候选的二选一、成本合法性、候选 id 唯一、效果参数、规模上限 |

带注册表的语义校验会逐效果、逐条件叶调用类型自带的参数校验，并叠加服务端配置换算出的运行期上限（当前是 `place_block` 的 `radius` 上限，来自服务端配置 `max_place_block_radius`）；动态引用（例如战利品表、方块、实体 ID）由引用校验在此之后再查，服务端不可用时跳过这一档。

未知顶层字段只记 WARN 并保留该条目，便于向前兼容；候选结果对象与催化剂成本对象里的未知字段按错误拒绝，避免把拼写错误当成合法配置放过。

## 扩展点

| 要做的事 | 改哪里 |
|---|---|
| 新增规则字段 | 在 `RuleFields` 加键常量，在 `RuleCodecs` 的 decoder/encoder 加入解码与编码，在 `RuleValidation` 加语义约束，需要时在模型 record 加分量 |
| 新增条件节点类型 | 在 `RuleFields` 加 `op` 常量，在 `RuleCodecs` 的条件分派里加分支，并补条件树的规模统计口径 |
| 新增效果或条件类型 | 走类型注册表，见[类型系统模块](../modules/type-system.md) |
| 调整默认值 | 模型层的默认常量，不要在解码器里散落字面量 |

## 相关文档

- [规则模型模块](../modules/rule-model.md)
- [规则加载模块](../modules/rule-loading.md)
- [错误与状态码](errors.md)
- [server.json 配置](../ops/configuration.md)
- [故障排查](../ops/troubleshooting.md)
