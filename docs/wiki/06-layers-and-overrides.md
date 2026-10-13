# 来源层与覆盖（layers & overrides）

规则可以从三个地方被读进来，它们会一起生效：**内置数据包**（模组自带）→ **世界数据包**（你放进存档的数据包）→ **config 覆盖层**（`config/itemdespawntowhat/rules/`）。这份文档说明这三层的优先级、怎么用一条同 id 的规则替换掉另一层里的规则、覆盖层的"停用/删除"控制条目，以及同一个掉落物被多条规则同时命中时到底听谁的。

规则的基本字段（`id`、`priority`、`source` 等）见 [规则基础字段](02-rule-basics.md)；"能不能转化"由条件决定，见 [条件配置](03-conditions.md)；转化出什么由效果与结果方案决定，见 [效果](04-effects.md) 与 [候选结果与结算](05-outcomes-and-settlement.md)。改完文件怎么生效见 [首次使用与生效流程](01-getting-started.md)。

## 三层来源与优先级

| 来源层 | 规则文件位置 | 你能手改吗 | 优先级 |
|---|---|---|---|
| 内置数据包（builtin） | 模组自带的 `data/<命名空间>/idtw/rules/*.json` | 不能（随模组更新被替换） | 最低 |
| 世界数据包（world） | 存档的 `datapacks/<数据包名>/data/<命名空间>/idtw/rules/*.json` | 可以通过数据包提供 | 中 |
| config 覆盖层（overlay） | `config/itemdespawntowhat/rules/**/*.json` | 可以，且命令与规则编辑器也只写这一层 | 最高 |

- 三层**同时被读取并合并**，优先级高的只是"同 id 时说了算"，不会把低层的其它规则顶掉。
- 覆盖层可以放在子目录里（`rules/mypack/xxx.json` 同样会被读到），文件名随意，规则的身份由文件里写的 `id` 决定。
- 数据包层的文件必须放在 `data/<命名空间>/idtw/rules/` 下、扩展名为 `.json`；同一个文件路径在多个数据包里都存在时，数据包优先级更高的那份在同 id 上更靠后、更容易胜出（合并时是"后者胜"，见下一节）。

## 同 id 覆盖：整条替换

合并按规则的 `id` 进行：

- **不同 id**：各自独立叠加，互不影响。
- **同 id**：只有优先级最高的那一份生效，而且它是**整条替换**，不是逐字段拼合。

例子：内置规则 `itemdespawntowhat:builtin_item_to_item` 是"鸡肉 300 秒后变腐肉"（下面省略了它只影响显示的 `display_name` / `notes` 两个字段）：

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

把下面这份保存为 `config/itemdespawntowhat/rules/chicken_to_gold.json`，它写的是**同一个 id**：

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
- 覆盖层里这份是**唯一生效**的：内置那份的内容不再参与，只有你写的字段说了算。
- 你没写的字段会回到默认值，而不是继承基底：`conditions` 省略是"恒真"、`trigger_after_seconds` 省略是 300、`enabled` 省略是 `true`、`combination` 省略是 `round_robin`、`schema_version` 省略是 1。**所以覆盖时要把整条规则写全**，不能只写想改的那几个字段。
- 想撤销覆盖：删掉覆盖层这个文件（或把这个文件里的 `id` 改成别的，它就变成一条新规则）。

### 覆盖层文件的 `id` 从哪来

- 文件里显式写了 `id`：就用它。
- 整个文件只含一条规则且省略了 `id`：用文件名推导，形如 `itemdespawntowhat:<文件名去掉 .json>`。
- 文件里是一个规则数组（多条规则）：每条都必须自己写 `id`，缺 `id` 的那条会被拒载并报错。
- 同一个文件里出现两个相同 `id`：后出现的那个被拒载。
- 想覆盖内置或数据包里的规则，写下的 `id` 必须和目标**完全一样**（命名空间、大小写都不能差）；写错一个字符就是"新增了一条规则"，两条会同时存在。

## 覆盖层控制条目：停用与删除

覆盖层还能写一种"只有 `id` + 控制字段"的小文件，用来处理同 id 的基底规则（内置或数据包里的那份），自己不带任何内容：

| 字段 | 作用 | 取值 | 默认 | 说明 |
|---|---|---|---|---|
| `disabled` | 停用同 id 的基底规则：内容保留、不再生效 | `true` / `false` | `false` | 生效列表里仍能看到它，只是"启用=false" |
| `delete` | 删除同 id 的基底规则：从生效列表里彻底移除 | `true` / `false` | `false` | 重复声明无副作用；基底本来就不存在时什么都不做 |

停用一个内置示例（`config/itemdespawntowhat/rules/off_arrow_rain.json`）：

```json
{
  "id": "itemdespawntowhat:builtin_arrow_rain",
  "disabled": true
}
```

彻底删掉另一个内置示例（`config/itemdespawntowhat/rules/remove_sapling.json`）：

```json
{
  "id": "itemdespawntowhat:builtin_item_to_block",
  "delete": true
}
```

两者的差别，以及该选哪个：

- **`disabled`：只停用。** 基底规则的内容还留着（模组更新后基底参数变了，仍会跟着变），只是不再参与转化；用 [查看规则来自哪一层](#查看一条规则来自哪一层哪个文件) 的命令还能看到它、显示为未启用。想恢复，删掉这个控制条目就行。
- **`delete`：彻底移除。** 这条 id 不再出现在生效列表里，恢复了也没有"原来那份"，要恢复只能自己重新写一条完整规则。
- 两者都**只作用于同 id 的基底规则**，不会影响其它 id，也不会删掉数据包或模组文件本身。
- 想"停用"时还有一种写法：在覆盖层写一份同 id 的完整规则并写 `enabled: false`。它同样不生效，区别是这份内容由你固定下来（基底以后怎么改都与你无关）。只想临时关掉就用 `disabled`，想完全接管就用整条覆盖。

写这些控制条目时的规则：

- `disabled` / `delete` 只能是 `true` 或 `false`，写别的值该条被拒载。
- 同一条里不能两个都是 `true`，否则该条被拒载。
- 控制字段**只能写在 config 覆盖层**：写进数据包层的那条规则会被拒载。
- 控制条目里除 `id`、`disabled`、`delete` 之外的字段会被忽略（并出现一条告警），所以别在里面写 `source`、`effects`。
- `disabled` 找不到同 id 的基底规则时会被忽略，并出现一条告警；`delete` 找不到基底时什么都不做，也不算错误。

## 同一层里的应用顺序

1. **层与层之间**：严格按 内置数据包 → 世界数据包 → 覆盖层 处理，和文件名的字母顺序无关。
2. **同一层内部**：先应用普通规则条目，再应用控制条目（`disabled` / `delete`）。所以控制条目写在哪个文件、文件叫什么名字都不影响结果，它总能作用到同一层里先被读到的那些规则上。
3. **同一层里同 id 出现多次**（都是普通条目）：按文件顺序"后者胜"——数据包层先比数据包优先级（优先级更高的包排在后面，因此胜出），同一个数据包内按文件路径；覆盖层按文件路径。
4. 合并后规则的先后顺序 = 每条 `id` **第一次**被读到的顺序（内置 → 世界数据包 → 覆盖层，层内按上面的文件顺序）。这个顺序就是下面"定义顺序"那一项的依据。

## 多条规则都能命中同一个掉落物时

一个掉落物一次只执行**一条**规则。候选规则按下面的顺序排队，取第一条"声明了本次消失方式、并且条件成立"的规则：

1. **优先级降序**：规则字段 `priority`（整数，默认 0）越大越靠前，字段说明见 [规则基础字段](02-rule-basics.md)。
2. **条件叶数降序**：条件树里的叶节点越多越靠前——`all_of` 下两个叶算 2，`inverted` 包一个叶算 1，组合节点本身不计数。也就是"条件写得更具体的那条更容易胜出"。条件的写法见 [条件配置](03-conditions.md)。
3. **定义顺序**：以上都相同时，按上一节说的读取先后，先被读到的先执行。

排队靠前但条件不成立的规则会被跳过，继续往下找，直到找到一条能执行的；一条都不成立时，掉落物这次不转化（规则会按退避节奏继续重试，见 [条件配置](03-conditions.md)）。

用 `priority` 控制结果：内置规则 `itemdespawntowhat:builtin_multi_effect`（钻石雷鸣）已经管着钻石且优先级是 0。如果你想让钻石改成别的产物，又不想覆盖那条内置规则，可以新增一条自己 id 的规则并给它更高的优先级：

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

配完会发生什么：钻石掉落物这次走你的规则（优先级 10 > 0），内置那条不再被选中，但它还在生效列表里（只是永远轮不到）。

注意：覆盖层**并不自动**压过内置或数据包的规则，只有"同 id"时才是层说了算。写的是新 id 时，要靠 `priority`（或更具体的条件）才能抢到执行权；所以想改内置规则的行为，写同 id 覆盖最稳。

## 查看一条规则来自哪一层、哪个文件

- `/idtw config list`：第一行是加载汇总（总条数 + 内置 / 世界数据包 / 覆盖层各多少条 + 错误与告警数），之后每行一条规则，形如
  ```
  mypack:diamond_to_gold | 来源层=overlay | 优先级=10 | 触发=60 秒 | 效果=1 | 启用=true | 出处=config 覆盖层:rules/diamond_to_gold.json
  ```
  其中"来源层"取值为 `builtin` / `world` / `overlay`；"出处"是"层名[:数据包名]:文件路径"，覆盖层显示相对 `config/itemdespawntowhat` 的路径，数据包层显示 `data/...` 路径并带上数据包名（例如 `世界数据包[mypack]:data/mypack/idtw/rules/xxx.json`）。
- `/idtw rule list`：更紧凑的清单（id / 来源层 / 是否启用 / 出处），适合条目很多时快速扫一遍。
- `/idtw rule show <id>`：单条规则的信息（优先级、触发秒数、效果个数、是否启用）加上它的原始 JSON。覆盖层的规则显示的就是你文件里的原文；数据包层的规则显示等价的 JSON 文本，方便照着改。
- 以上命令需要单人世界或权限等级 2。改完文件要重新加载才生效，命令完整语法见 [命令参考](08-commands.md)。

## 排查：覆盖或停用看起来没生效

按这个顺序查：

1. **文件位置和扩展名**：覆盖层必须落在 `config/itemdespawntowhat/rules/`（可以有子目录）且以 `.json` 结尾；数据包必须落在 `data/<命名空间>/idtw/rules/` 下。
2. **`id` 是否和目标完全一致**：不同 = 新增了一条规则，两条同时存在，覆盖的意思就没有了。
3. **有没有加载成功**：用 `/idtw config validate` 或 `/idtw config list` 看错误与告警行——它们会带上出错的来源与字段位置。文件里 JSON 语法错误会导致整份文件被拒载；单条规则写错只是那一条不生效。
4. **控制条目的写法是否合法**：`disabled` / `delete` 必须是布尔值、不能同时为 `true`、只能写在覆盖层；`disabled` 找不到同 id 基底时会被忽略（有告警）。
5. **是不是被另一条规则抢走了**：同一个掉落物只执行一条规则，检查有没有优先级更高（或条件叶数更多）的规则也管着这个物品——用 `/idtw config list` 看"优先级"和"出处"。
6. **改完是否重新加载过**：见 [首次使用与生效流程](01-getting-started.md)。
