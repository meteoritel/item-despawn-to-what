# 命令参考（/idtw）

这一页列出模组的全部玩家命令、它们要求的权限，以及输出该怎么读。命令只负责检查和重载配置，规则内容本身写在 JSON 文件里。

## 命令一览

| 命令 | 作用 | 权限 | 会改变正在生效的规则吗 |
| --- | --- | --- | --- |
| `/idtw config validate` | 按磁盘上的当前内容检查全部来源，只报问题 | 需要权限 | 否 |
| `/idtw config reload` | 重新读取全部来源并立即生效 | 需要权限 | 是 |
| `/idtw config list` | 逐条列出生效规则，含优先级、触发秒数、效果数 | 需要权限 | 否 |
| `/idtw rule list` | 生效规则的紧凑清单 | 需要权限 | 否 |
| `/idtw rule show <id>` | 输出某条规则当前生效内容的 JSON | 需要权限 | 否 |
| `/idtw config edit` | 打开图形编辑器的入口 | 需要权限，且只能由玩家执行 | 保存后是 |
| `/idtw config edit-lock status` | 查看编辑锁现在在谁手里 | 所有玩家 | 否 |
| `/idtw config edit-lock release` | 强制释放编辑锁 | 权限等级 ≥2 | 否，只释放锁 |

## 权限怎么算

- **需要权限**：单人世界直接放行；多人服务器要求权限等级 ≥2，也就是常说的管理员 / OP 等级 2。
- 权限不足时，这条命令不会被识别为可用，客户端按原版提示「未知或不完整命令」，模组不会额外说明原因。
- `/idtw config edit-lock status` 不要求权限，任何玩家都能查。
- `/idtw config edit-lock release` 只看权限等级是否 ≥2，不看是不是单人世界；单人存档没开作弊时权限等级为 0，所以这条命令用不了，其它命令不受影响。
- 服务端还没启动完成时，命令会提示「……尚未就绪，请等待服务端启动完成」，稍后再试即可。

## 一次检查回显的通用格式

`validate`、`reload`、`list` 与 `rule list` 都以同一条统计行开头，后面按需跟问题清单。下面是 `validate` 报出一个错误时的样子：

```text
规则加载[validate]：共 10 条（内置数据包 10 / 世界数据包 0 / 覆盖层 0），错误 1，告警 0
[error] trigger_after_seconds 不能为负数: -1 config 覆盖层:rules/paper.json#trigger_after_seconds
```

（上面的第二行是示意，实际文案随问题不同。）

- **统计行**：`规则加载[阶段]` 里的阶段是本次执行的名字，四个取值分别是 `validate`、`reload`、`list`、`rule_list`；`共 N 条` 是这次成功读入的规则数，括号里按三个来源层分别计数；`错误` 与 `告警` 是问题条数。
- **问题行**：形如 `[级别] 问题描述 来源#字段路径`。
  - 级别只有 `error` 与 `warn` 两种。`error` 会让出问题的那一条规则或整个文件不生效；`warn` 只是提醒，不影响加载。
  - 来源形如 `config 覆盖层:rules/paper.json`（覆盖层路径相对于 `config/itemdespawntowhat`）、`世界数据包[数据包标识]:data/<命名空间>/idtw/rules/<文件>.json`、`内置数据包[数据包标识]:data/<命名空间>/idtw/rules/<文件>.json`。
  - 字段路径定位到具体字段，例如 `#trigger_after_seconds`、`#source.items`；只有来源、没有 `#字段` 时，问题出在整个文件，比如 JSON 语法错误。
- 问题行只列问题，`共 N 条` 里不会算上被拒载的条目，所以「条数比预期少」本身就是一条线索。

## /idtw config validate

语法：`/idtw config validate`

按磁盘当前的规则文件重新读一遍全部来源做检查，并输出上面那条统计行与问题清单。

- **只检查，不应用**：它不会改变正在生效的规则，所以你可以先看结果再决定要不要 `reload`。
- 它读的是磁盘现状：刚保存的文件会被读进来，还没 `reload` 也照样能查。
- 打开游戏后先跑一次 `validate`，是最省事的排错方式；排查清单见 [首次使用与生效流程](zh-01-getting-started.md)。

## /idtw config reload

语法：`/idtw config reload`

重新读取全部来源的规则并立即生效，回显与 `validate` 相同的统计行与问题清单。

- 已经在地上的掉落物也会按新规则重新判断，不需要重新丢一次。
- 改完 JSON 文件必须执行它，或重载数据包、重启服务器，否则改动不会被游戏读取。
- 它只重载规则，不会重载 `server.json`：改服务器参数要重启服务端，见 [服务端配置](zh-07-server-config.md)。

## /idtw config list

语法：`/idtw config list`

逐条列出生效规则，一行一条：

```text
规则加载[list]：共 2 条（内置数据包 0 / 世界数据包 0 / 覆盖层 2），错误 0，告警 0
myrules:chicken_to_rotten_flesh | 来源层=overlay | 优先级=0 | 触发=10 秒 | 效果=1 | 启用=true | 出处=config 覆盖层:rules/myrules/chicken_to_rotten_flesh.json
```

- `来源层` 是 `builtin`、`world`、`overlay` 三个英文层名之一；`出处` 是层名加上来源数据包标识与文件路径，覆盖层则给出相对 `config/itemdespawntowhat` 的路径。
- `优先级` 是规则里的 `priority`，没写就是 `0`；`触发` 是 `trigger_after_seconds` 的秒数；`效果` 是最外层 `effects` 列表里的效果个数（只写 `outcomes` 的规则这里是 `0`，写在候选结果里的效果不计入这一列）。
- `启用=false` 的规则仍在清单里，只是不参与匹配。
- 同一个 id 在多层都有时，只显示最终生效的那一条；想看合并规则见 [来源层与覆盖](zh-06-layers-and-overrides.md)。

## /idtw rule list 与 /idtw rule show

### /idtw rule list

语法：`/idtw rule list`

比 `config list` 少几列，只给 id、来源层、是否启用与出处：

```text
规则加载[rule_list]：共 2 条（内置数据包 0 / 世界数据包 0 / 覆盖层 2），错误 0，告警 0
myrules:chicken_to_rotten_flesh | 来源层=overlay | 启用=true | 出处=config 覆盖层:rules/myrules/chicken_to_rotten_flesh.json
```

### /idtw rule show <id>

语法：`/idtw rule show <id>`，其中 `<id>` 写成与 JSON 里 `id` 字段相同的 `命名空间:路径`，例如 `itemdespawntowhat:builtin_item_to_item`。它必须是小写的合法资源位置（可用字母、数字、`_`、`-`、`.`、`/`）。

输出两行：先是这条规则的摘要，再是它当前生效内容的 JSON（一整行）。

```text
规则 myrules:chicken_to_rotten_flesh：优先级=0，触发=10 秒，效果=1，启用=true
原始 JSON：{"id":"myrules:chicken_to_rotten_flesh", ... }
```

- 摘要的字段与 `config list` 一致，只是排成一句话。
- 第二行的 JSON 是模组按当前规则内容整理的，可能与磁盘文件不是逐字相同（例如省略掉默认值、字段顺序不同）；想核对「游戏到底读到了什么」，看它最准。
- 拿不到可输出的内容时，第二行会换成提示「<id> 没有可输出的原始 JSON（由模型编码或不可编辑）。」。
- id 写错或不存在时提示「未找到规则 <id>」，本次命令按失败返回。

## /idtw config edit 与编辑锁

`/idtw config edit` 是打开图形编辑器的入口，服务端会先取得独占的编辑锁再打开它；编辑器里的具体操作不在本页范围。

- 只能由游戏内玩家执行，控制台执行会提示「该子命令只能由玩家执行。」。
- 没有编辑权限时提示「你没有编辑覆盖层规则的权限。」。
- 锁已被别人持有时，会提示「规则编辑器已被 <玩家> 锁定，可用 /idtw config edit-lock status 查看。」；取到锁时提示「已向 <玩家> 发送编辑入口。」。

### /idtw config edit-lock status

语法：`/idtw config edit-lock status`，任何玩家都能用。按情况给出四种结果：

| 情况 | 回显含义 |
| --- | --- |
| 没人持有 | 规则编辑器空闲，当前无人持有编辑锁 |
| 自己持有 | 「你正持有规则编辑器：状态=…，会话号=…」，状态为 `opening`（已取到锁、等你确认）、`active`（可编辑）、`applying`（正在写入）之一 |
| 别人持有，你有权限 | 「<玩家> 正持有规则编辑器：状态=…」 |
| 别人持有，你没有权限 | 只提示「规则编辑器当前被其他玩家持有」，看不到是谁，也看不到会话号 |

编辑锁不是永久的：持有者超过 60 秒没有心跳（按服务端活动时间算，游戏暂停不计时）会自动释放；取到锁后一直没有确认进入编辑器时，15 秒后也会自动释放。停服或持有人主动关闭编辑器同样会释放。

### /idtw config edit-lock release

语法：`/idtw config edit-lock release`，需要权限等级 ≥2。

- 锁本来就是空闲时提示「规则编辑器本来就是空闲的。」，不会有副作用。
- 锁被别人持有时强制释放，并提示「已强制释放 <玩家> 持有的规则编辑器锁。」；对方如果在线，他的编辑器会立即收到会话失效，需要重新获取才能继续。

## 几种常见用法

- **改完文件后**：先 `/idtw config validate` 看有没有报错，再 `/idtw config reload` 让改动生效。
- **确认某条规则生效成什么样**：`/idtw rule list` 找到 id，再用 `/idtw rule show <id>` 看它当前的内容。
- **报错看不懂**：看问题行的级别与 `来源#字段路径`，`error` 说明这条不生效，先按字段路径回去改对应的 JSON。
- **多人服务器上编辑锁被占住**：`/idtw config edit-lock status` 看持有人，必要时 `/idtw config edit-lock release` 强制释放。

## 相关

- [首次使用与生效流程](zh-01-getting-started.md)：文件放哪、改完怎么应用。
- [来源层与覆盖](zh-06-layers-and-overrides.md)：`来源层` 与 `出处` 背后的三层合并规则。
- [服务端配置](zh-07-server-config.md)：`reload` 不会重载的那些参数。
