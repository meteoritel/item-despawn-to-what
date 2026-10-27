# 来源层与覆盖（layers & overrides）

这一页说明规则从哪三个地方被读进来、哪一层说了算、怎么用覆盖层接管内置规则、怎么按存档停用一条规则，以及多条规则同时命中一个掉落物时最终执行哪一条。

模组会把三个位置里的规则一起读进来，这三个位置叫**来源层**（`builtin`、`world`、`overlay`）：**内置数据包**（`builtin`）是模组自带的规则，随模组更新被替换；**世界数据包**（`world`）是你放进存档的数据包里的规则；**config 覆盖层**（`overlay`）是 `config/itemdespawntowhat/rules/` 下的文件，也是三层里唯一由你手写的一层。三者的优先级固定为 config 覆盖层 > 世界数据包 > 内置数据包。

规则的基本字段（`id`、`priority`、`source` 等）见 [规则基础字段](zh-02-rule-basics)；能不能转化由条件决定，见 [条件](zh-03-conditions)；转化出什么见 [效果](zh-04-effects) 与 [候选结果与结算](zh-05-outcomes-and-settlement)；改完文件怎么生效见 [首次使用与生效流程](zh-01-getting-started)。

## 三层来源与优先级

| 来源层 | 规则文件位置 | 你能直接改吗 | 优先级 |
| --- | --- | --- | --- |
| 内置数据包（`builtin`） | 模组自带的 `data/<命名空间>/idtw/rules/*.json` | 不能，会随模组更新被替换 | 最低 |
| 世界数据包（`world`） | 存档里 `datapacks/<数据包名>/data/<命名空间>/idtw/rules/*.json` | 可以，通过数据包提供 | 中 |
| config 覆盖层（`overlay`） | `config/itemdespawntowhat/rules/` 下的 `*.json`（可以有子目录） | 可以，命令与规则编辑器也只写这一层 | 最高 |

- 三层**同时被读取并合并**；优先级只决定同 id 时谁说了算，不会把低层其它 `id` 的规则顶掉。
- 覆盖层的文件名随意，规则的身份由文件里写的 `id` 决定；放在子目录里同样会被读到。
- 数据包层的文件必须放在 `data/<命名空间>/idtw/rules/` 下、扩展名为 `.json`。同一个文件路径在多个数据包里都存在时，数据包优先级更高的那份更靠后应用，因而在同 id 上胜出（合并时后者胜，见下一节）。

## 同 id 覆盖：整条替换

**同 id 覆盖**：不同来源层里的规则写出完全相同的 `id` 时，只有优先级最高的那一份生效，而且是**整条替换**，不是逐字段拼合。合并按 `id` 进行：

- **不同 `id`**：各自独立叠加，互不影响。
- **同 `id`**：只保留优先级最高的那一份。

例子：内置规则 `itemdespawntowhat:builtin_item_to_item` 是「鸡肉 300 秒后变腐肉」（下面省略了它只影响显示的 `display_name` / `notes` 两个字段）：

```json
{
  "id": "itemdespawntowhat:builtin_item_to_item",
  "enabled": true,
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 300,
  "triggers": [
    "natural"
  ],
  "source_cost": {"minecraft:chicken": 1},
  "combination": "round_robin",
  "schema_version": 1,
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:rotten_flesh",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

把下面这份保存为 `config/itemdespawntowhat/rules/chicken_to_gold.json`，它写的是**同一个 `id`**：

```json
{
  "id": "itemdespawntowhat:builtin_item_to_item",
  "enabled": true,
  "priority": 10,
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 60,
  "triggers": [
    "natural"
  ],
  "source_cost": {"minecraft:chicken": 1},
  "combination": "round_robin",
  "schema_version": 1,
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:gold_ingot",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

配完会发生什么：

- 掉落物仍然是鸡肉（`source` 是你写的这一份）。
- 等待时间从 300 秒变成 60 秒，产物从腐肉变成金锭，`priority` 变成 10。
- 覆盖层里这份**唯一生效**：内置那份的内容不再参与，只有你写的字段说了算。
- 你没写的字段回到默认值，而不是继承基底：`conditions` 省略是「总是命中」、`trigger_after_seconds` 省略是 300、`enabled` 省略是 `true`、`combination` 省略是 `round_robin`、`schema_version` 省略是 1。**覆盖时要把整条规则写全**，不要只写想改的那几个字段。
- 想撤销覆盖：删掉覆盖层里的这个文件，或把文件里的 `id` 改成别的，它就变成一条新规则。

### 覆盖层文件的 id 从哪来

- 文件里显式写了 `id`：就用它。
- 整个文件只含一条规则、又省略了 `id`：由文件路径推导，形如 `idtw:<去掉 .json 的相对路径>`；放在子目录里时子目录名也算进去，例如 `rules/mypack/chicken_to_gold.json` 推导出 `idtw:mypack/chicken_to_gold`。
- 文件里是一个规则数组（多条规则）：每条都必须自己写 `id`，缺 `id` 的那条会被拒载并报错。
- 同一个文件里出现两个相同 `id`：后出现的那条被拒载。
- 想覆盖内置或数据包里的规则，写下的 `id` 必须和目标完全一样；写错一个字符就是新增了一条规则，两份会同时存在。`id` 里只能用小写字母、数字和 `_`、`-`、`.`、`/`，写成大写会让这条规则被拒载。

## 覆盖层条目：整条接管

覆盖层里的每条条目都是一条**完整规则**，它与内置、数据包规则走同一套字段与校验：

- 写同 id 的规则就是接管：整条替换基底规则的内容，未写的字段取默认值（见上一节）。
- 撤销接管就是删掉这条覆盖：删掉整个文件，或从规则数组里删掉那一条，内置或数据包里的原版规则原样恢复；把 `id` 改成别的，它就变成一条新规则。
- 配置文件里出现未登记的顶层字段时，加载会记一条告警并原样保留该字段，规则按完整规则对象解析，其它字段照常生效。

## 按存档启停：本服启用状态

一条规则是否执行，由两层决定：

- 规则内容里的 `enabled` 是**默认启用**：作者希望这条规则默认是开还是关。
- 实际生效值 = 本存档对这条规则的设置 ?? 默认启用。本存档的设置叫**本服启用状态**，按存档保存，不写进规则文件。

用命令改（需要权限，单人存档的房主直接可用）：

| 命令 | 作用 |
| --- | --- |
| `/idtw rule <id> disable` | 只在本存档停用这条规则，规则内容不动 |
| `/idtw rule <id> enable` | 在本存档重新启用这条规则 |
| `/idtw rule <id> default` | 删掉本存档的设置，回退到规则里的默认启用 |

- 停用只影响「执不执行」：规则内容、优先级、条件都按文件原样读取，模组或数据包更新后内容照常跟着变。
- 设置跟着存档走：换一个存档或换一个服务器，启停各自独立；多人服务器上所有玩家共享本服状态。
- 停用的规则仍出现在 `/idtw config list` 与 `/idtw rule list` 里，那一行显示「启用=false」。
- 规则内容撤掉后（例如删掉了覆盖层文件、数据包不再提供这个 id），它在本存档的设置就没有对应规则了，加载时忽略；`/idtw rule orphans` 列出这些记录，`/idtw rule orphans clear` 清理。规则后来又出现时，未清理的旧记录会重新起作用，清理过则按默认启用。

想做的事与对应的做法：

| 想做的事 | 用什么 |
| --- | --- |
| 换掉一条内置或数据包规则的全部内容 | 覆盖层写同 id 的完整规则 |
| 撤销接管、回到内置或数据包原版 | 删掉覆盖层里的那个文件或数组条目 |
| 临时关掉一条规则，内容保留、随时可恢复 | 本服启停命令 |
| 改这条规则在新存档里的默认开关 | 规则 JSON 的 `enabled` |

## 同一层里的应用顺序

1. **层与层之间**：严格按内置数据包 → 世界数据包 → config 覆盖层的顺序处理，与文件名的字母顺序无关。
2. **同一层里同 id 出现多次**：按文件顺序后者胜——数据包层先比数据包优先级（优先级更高的包排在后面，因此胜出），同一个数据包内按文件路径；覆盖层按文件路径。
3. **合并后的先后顺序**：等于每条 `id` 第一次被读到的顺序（内置数据包 → 世界数据包 → config 覆盖层，层内按上面的文件顺序），也就是下一节说的定义顺序。
4. **启用状态不参与合并**：先按上面的顺序合并出规则内容，再在装配末端把本存档的启停设置套到每条规则上。

## 多条规则都能命中同一个掉落物时

一个掉落物一次只执行**一条**规则。规则之间按三个关键字排队，取第一条同时满足「声明了本次**消失方式**（`triggers`，掉落物被销毁的途径：`natural`、`fire`、`lava`、`cactus`；不写时只对 `natural` 生效）」与「条件命中」的规则：

1. **优先级**（`priority`，整数，默认 0）：数值越大越靠前，字段说明见 [规则基础字段](zh-02-rule-basics)。
2. **条件叶数**（条件树里的叶节点个数）：越多越靠前——`all_of` 下两个叶算 2，`inverted` 包一个叶算 1，组合节点本身不计数。条件写得更具体的那条更容易胜出，写法见 [条件](zh-03-conditions)。
3. **定义顺序**（每条 `id` 第一次被读到的先后）：以上都相同时，先被读到的先执行。

排队靠前但条件不命中的规则会被跳过，继续往下找，直到找到一条能执行的；一条都不命中时，这次不转化，规则会按**退避**节奏继续重试（间隔从 1 秒起逐步拉长，上限由 `backoff_max_ticks` 控制，见 [服务端配置](zh-07-server-config)）。

用 `priority` 控制结果：内置规则 `itemdespawntowhat:builtin_multi_effect`（钻石雷鸣）已经管着钻石且优先级是 0。如果你想让钻石改成别的产物，又不想覆盖那条内置规则，可以新增一条自己 `id` 的规则并给它更高的优先级：

```json
{
  "id": "mypack:diamond_to_gold",
  "priority": 10,
  "source": {
    "items": [
      "minecraft:diamond"
    ]
  },
  "trigger_after_seconds": 60,
  "triggers": [
    "natural"
  ],
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:gold_ingot",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

配完会发生什么：钻石掉落物这次走你的规则（优先级 10 > 0），内置那条不再被选中，但它还在生效列表里，只是永远轮不到。

注意：覆盖层**并不自动**压过内置或数据包的规则，只有同 id 时才是层说了算。写的是新 `id` 时，要靠 `priority` 或更具体的条件才能抢到执行权；想改内置规则的行为，写同 id 覆盖最稳。

## 查看一条规则来自哪一层、哪个文件

下面三条命令都能回答这个问题，它们都要求单人世界或**权限等级 ≥2**（单人世界直接放行）：

- `/idtw config list`：逐条列出生效规则（`id`、来源层、优先级、触发秒数、效果个数、启用状态、出处），第一行是**统计行**（总条数，以及内置数据包、世界数据包、覆盖层各多少条，还有错误与告警数）。
- `/idtw rule list`：更紧凑的清单（`id`、来源层、是否启用、出处），适合规则很多时快速扫一遍。
- `/idtw rule show <id>`：先受理请求，再异步输出胜出来源、本服启用覆盖和原始 JSON；覆盖层优先，否则使用数据包基底，保留未知字段与默认声明。

列表命令里的「启用」都是**本存档的生效值**（本服设置 ?? 规则里的 `enabled`）。改启用状态用 `/idtw rule <id> enable|disable|default`，完整语法见 [命令参考](zh-08-commands)。

`/idtw config list` 里的一行长这样：

```text
mypack:diamond_to_gold | 来源层=overlay | 优先级=10 | 触发=60 秒 | 效果=1 | 启用=true | 出处=config 覆盖层:rules/diamond_to_gold.json
```

其中来源层取值为 `builtin` / `world` / `overlay`；**来源标识**（`出处`）写成「层名[数据包名]:文件路径」，只有数据包层带方括号里的数据包名，例如 `世界数据包[mypack]:data/mypack/idtw/rules/xxx.json`，覆盖层显示相对 `config/itemdespawntowhat` 的路径。

改完文件要重新加载才生效，命令的完整语法见 [命令参考](zh-08-commands)。

## 排查：覆盖或停用看起来没生效

按这个顺序查：

1. **文件位置和扩展名**：覆盖层必须落在 `config/itemdespawntowhat/rules/` 下（可以有子目录）且以 `.json` 结尾；数据包必须落在 `data/<命名空间>/idtw/rules/` 下。
2. **`id` 是否和目标完全一致**：不一致就是新增了一条规则，两份同时存在，覆盖的意思就没有了。
3. **有没有加载成功**：用 `/idtw config validate` 或 `/idtw config list` 看错误与告警——它们会带上出问题的来源与字段路径。文件里 JSON 语法错误、文件为空、顶层既不是规则对象也不是规则数组，都会让整份文件被拒载；单条规则写错只是那一条不生效。
4. **本服有没有停用它**：规则里的 `enabled` 只是默认启用，本存档的启停设置优先。看 `/idtw config list` 的「启用」列（这就是生效值）；被停用过就用 `/idtw rule <id> default` 回退到默认。
5. **是不是被另一条规则抢走了**：同一个掉落物只执行一条规则，检查有没有优先级更高、或条件叶数更多的规则也管着这个物品；用 `/idtw config list` 看「优先级」和「出处」。
6. **改完是否重新加载过**：见 [首次使用与生效流程](zh-01-getting-started)。

## 相关

- [首次使用与生效流程](zh-01-getting-started)
- [规则基础字段](zh-02-rule-basics)
- [候选结果与结算](zh-05-outcomes-and-settlement)
- [命令参考](zh-08-commands)
