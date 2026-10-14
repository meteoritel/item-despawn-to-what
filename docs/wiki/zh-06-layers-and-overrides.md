# 来源层与覆盖（layers & overrides）

这一页说明规则从哪三个地方被读进来、哪一层说了算、怎么停用或删除内置规则，以及多条规则同时命中一个掉落物时最终执行哪一条。

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
      "type": "itemdespawntowhat:spawn_entity",
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
      "type": "itemdespawntowhat:spawn_entity",
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
- 整个文件只含一条规则、又省略了 `id`：由文件路径推导，形如 `itemdespawntowhat:<去掉 .json 的相对路径>`；放在子目录里时子目录名也算进去，例如 `rules/mypack/chicken_to_gold.json` 推导出 `itemdespawntowhat:mypack/chicken_to_gold`。
- 文件里是一个规则数组（多条规则）：每条都必须自己写 `id`，缺 `id` 的那条会被拒载并报错。
- 同一个文件里出现两个相同 `id`：后出现的那条被拒载。
- 想覆盖内置或数据包里的规则，写下的 `id` 必须和目标完全一样；写错一个字符就是新增了一条规则，两份会同时存在。`id` 里只能用小写字母、数字和 `_`、`-`、`.`、`/`，写成大写会让这条规则被拒载。

## 覆盖控制条目：停用与删除

**覆盖控制条目**（`disabled`、`delete`）：一个只写 `id` 加控制字段的小文件，用来处理同 id 的**数据包基底**（内置数据包与世界数据包里那份规则）。它自己不携带任何规则内容。

| 字段 | 作用 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `disabled` | 停用同 id 的基底规则：内容保留、不再生效 | `true` / `false` | `false` | 规则仍会出现在 `/idtw config list` 的输出里，那一行显示 `启用=false` |
| `delete` | 删除同 id 的基底规则：从生效列表里彻底移除 | `true` / `false` | `false` | 重复声明不会有副作用；基底本来就不存在时什么都不做 |

停用一个内置示例，把文件保存为 `config/itemdespawntowhat/rules/off_arrow_rain.json`：

```json
{
  "id": "itemdespawntowhat:builtin_arrow_rain",
  "disabled": true
}
```

彻底删掉另一个内置示例，把文件保存为 `config/itemdespawntowhat/rules/remove_sapling.json`：

```json
{
  "id": "itemdespawntowhat:builtin_item_to_block",
  "delete": true
}
```

两者的差别，以及该选哪个：

- **`disabled` 只停用。** 基底规则的内容还留着（模组更新后基底参数变了，仍会跟着变），只是不再参与转化；用 `/idtw config list` 或 `/idtw rule list` 还能看到它，显示为未启用。想恢复，删掉这个控制条目就行。
- **`delete` 彻底移除。** 这条 `id` 不再出现在生效列表里，之后想恢复只能自己重新写一条完整规则。
- 两者都**只作用于同 id 的基底规则**，不会影响其它 `id`，也不会删掉数据包或模组文件本身。
- 被停用的规则在输出里显示的是控制条目所在的那一层与那个文件（也就是覆盖层里你写的这个文件），不是原来那个内置或数据包文件。
- 想「停用」时还有一种写法：在覆盖层写一份同 id 的完整规则并写 `enabled: false`。它同样不生效，区别是这份内容由你固定下来（基底以后怎么改都与你无关）。只想临时关掉就用 `disabled`，想完全接管就用整条覆盖。

写这些控制条目时要遵守：

- `disabled` 与 `delete` 只能是 `true` 或 `false`，写别的值该条被拒载。
- 同一条里不能两个都是 `true`，否则该条被拒载。
- 控制字段只能写在 config 覆盖层：写进数据包层的规则会被拒载。
- 控制条目里除 `id`、`disabled`、`delete` 之外的字段会被忽略，并出现一条告警；不要在控制条目里写 `source`、`effects`。
- `disabled` 找不到同 id 的基底规则时会被忽略，并出现一条告警；`delete` 找不到基底时什么都不做，也不算错误。

## 同一层里的应用顺序

1. **层与层之间**：严格按内置数据包 → 世界数据包 → config 覆盖层的顺序处理，与文件名的字母顺序无关。
2. **同一层内部**：先应用普通规则条目，再应用控制条目（`disabled` / `delete`）。所以控制条目写在哪个文件、文件叫什么名字都不影响结果，它总能作用到同一层里先被读到的那些规则上。
3. **同一层里同 id 出现多次**（都是普通条目）：按文件顺序后者胜——数据包层先比数据包优先级（优先级更高的包排在后面，因此胜出），同一个数据包内按文件路径；覆盖层按文件路径。
4. **合并后的先后顺序**：等于每条 `id` 第一次被读到的顺序（内置数据包 → 世界数据包 → config 覆盖层，层内按上面的文件顺序），也就是下一节说的定义顺序。

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
      "type": "itemdespawntowhat:spawn_entity",
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
- `/idtw rule show <id>`：单条规则的信息（优先级、触发秒数、效果个数、是否启用）加上它的原始 JSON；覆盖层的规则显示你文件里的原文，数据包层的规则显示等价的 JSON 文本，方便照着改。

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
4. **控制条目的写法是否合法**：`disabled` / `delete` 必须是布尔值、不能同时为 `true`、只能写在覆盖层；`disabled` 找不到同 id 基底时会被忽略，并出现一条告警。
5. **是不是被另一条规则抢走了**：同一个掉落物只执行一条规则，检查有没有优先级更高、或条件叶数更多的规则也管着这个物品；用 `/idtw config list` 看「优先级」和「出处」。
6. **改完是否重新加载过**：见 [首次使用与生效流程](zh-01-getting-started)。

## 相关

- [首次使用与生效流程](zh-01-getting-started)
- [规则基础字段](zh-02-rule-basics)
- [候选结果与结算](zh-05-outcomes-and-settlement)
- [命令参考](zh-08-commands)
