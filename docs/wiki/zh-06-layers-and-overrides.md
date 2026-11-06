# 来源层与覆盖（layers & overrides）

> 适用版本：**2.0.0**（Minecraft **1.21.1**）。

本页说明世界数据包与 config 覆盖层的加载顺序、同 id 覆盖、本存档的启停设置，以及多条规则匹配时的选择顺序。

规则字段见[规则基础字段](zh-02-rule-basics)，条件见[条件](zh-03-conditions)，产出见[效果](zh-04-effects)与[候选结果与结算](zh-05-outcomes-and-settlement)。文件修改后的生效步骤见[首次使用与生效流程](zh-01-getting-started)。

## 来源与优先级

2.0.0 的自定义规则可以放在以下位置。同 id 时，**config 覆盖层优先于世界数据包**。

| 来源层 | 规则文件位置 | 编辑方式 | 优先级 |
| --- | --- | --- | --- |
| 世界数据包（`world`） | `<世界存档>/datapacks/<数据包>/data/<命名空间>/idtw/rules/*.json` | 编辑数据包文件；GUI 将其作为只读基底 | 低 |
| config 覆盖层（`overlay`） | `config/itemdespawntowhat/rules/` 下的 `*.json`，可以有子目录 | GUI 或手动编辑 JSON | 高 |

- 两个来源的规则一起合并；不同 `id` 的规则各自保留。
- 显式填写 `id` 时，规则身份由该字段决定；仅含一条规则的文件也可省略 `id`，由路径推导。
- 数据包规则文件放在 `data/<命名空间>/idtw/rules/` 下，以 `.json` 结尾。同一个路径在多个数据包中存在时，优先级较高的数据包后应用，同 id 时覆盖前面的内容。

## 同 id 覆盖：整条替换

**同 id 覆盖**：不同来源层里的规则写出完全相同的 `id` 时，只有优先级最高的那一份生效，而且是**整条替换**，不是逐字段拼合。合并按 `id` 进行：

- **不同 `id`**：各自独立叠加，互不影响。
- **同 `id`**：只保留优先级最高的那一份。

下面用自行创建的规则演示覆盖。先在一个已启用的世界数据包中创建 `data/mypack/idtw/rules/chicken_to_rotten_flesh.json`，写入以下内容，让鸡肉掉落物存活 300 秒后变成腐肉：

```json
{
  "id": "mypack:chicken_to_rotten_flesh",
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
  "id": "mypack:chicken_to_rotten_flesh",
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

保存两份文件并执行 `/idtw rule reload` 后：

- 掉落物仍然是鸡肉（`source` 是你写的这一份）。
- 等待时间从 300 秒变成 60 秒，产物从腐肉变成金锭，`priority` 变成 10。
- 同 id 的规则采用覆盖层这一份完整内容。
- 你没写的字段回到默认值，而不是继承基底：`conditions` 省略是「总是命中」、`trigger_after_seconds` 省略是 300、`enabled` 省略是 `true`、`combination` 省略是 `round_robin`、`schema_version` 省略是 1。**覆盖时要把整条规则写全**，不要只写想改的那几个字段。
- 想撤销覆盖：删掉覆盖层里的这个文件，或把文件里的 `id` 改成别的，它就变成一条新规则。

### 覆盖层文件的 id 从哪来

- 文件里显式写了 `id`：就用它。
- 整个文件只含一条规则、又省略了 `id`：由文件路径推导，形如 `idtw:<去掉 .json 的相对路径>`；放在子目录里时子目录名也算进去，例如 `rules/mypack/chicken_to_gold.json` 推导出 `idtw:mypack/chicken_to_gold`。
- 文件里是一个规则数组（多条规则）：每条都必须自己写 `id`，缺 `id` 的那条会被拒载并报错。
- 同一个文件里出现两个相同 `id`：后出现的那条被拒载。
- 想覆盖世界数据包里的规则，写下的 `id` 必须和目标完全一样；写错一个字符就是新增了一条规则，两份会同时存在。`id` 里只能用小写字母、数字和 `_`、`-`、`.`、`/`，写成大写会让这条规则被拒载。

## 覆盖层条目：整条接管

覆盖层里的每条条目都是一条**完整规则**，它与世界数据包规则走同一套字段与校验：

- 写同 id 的规则就是接管：整条替换基底规则的内容，未写的字段取默认值（见上一节）。
- 撤销接管就是删掉这条覆盖：删掉整个文件，或从规则数组里删掉那一条，世界数据包里的原版规则原样恢复；把 `id` 改成别的，它就变成一条新规则。
- 配置文件里出现未登记的顶层字段时，加载会记一条告警并原样保留该字段，规则按完整规则对象解析，其它字段照常生效。

## 按存档启停：本服启用状态

一条规则是否执行，由两层决定：

- 规则内容里的 `enabled` 是**默认启用**：作者希望这条规则默认是开还是关。
- 实际生效值 = 本存档对这条规则的设置 ?? 默认启用。本存档的设置叫**本服启用状态**，按存档保存，不写进规则文件。

用命令改（需要权限，单人存档的房主直接可用）：

| 命令 | 作用 |
| --- | --- |
| `/idtw rule disable <id>` | 只在本存档停用这条规则，规则内容不动 |
| `/idtw rule enable <id>` | 在本存档重新启用这条规则 |
| `/idtw rule reset <id>` | 删掉本存档的设置，回退到规则里的默认启用 |

- 停用只影响「执不执行」：规则内容、优先级、条件都按文件原样读取，模组或数据包更新后内容照常跟着变。
- 设置跟着存档走：换一个存档或换一个服务器，启停各自独立；多人服务器上所有玩家共享本服状态。
- 停用规则仍出现在 `/idtw rule list` 中，行内显示「停用」；存在本服覆盖时附带「本服」标记。
- 规则内容撤掉后（例如删掉了覆盖层文件、数据包不再提供这个 id），它在本存档的设置就没有对应规则了，加载时忽略；`/idtw rule orphans` 列出这些记录，`/idtw rule orphans clear` 清理。规则后来又出现时，未清理的旧记录会重新起作用，清理过则按默认启用。

想做的事与对应的做法：

| 想做的事 | 用什么 |
| --- | --- |
| 换掉一条世界数据包规则的全部内容 | 覆盖层写同 id 的完整规则 |
| 撤销接管、回到世界数据包原版 | 删掉覆盖层里的那个文件或数组条目 |
| 临时关掉一条规则，内容保留、随时可恢复 | 本服启停命令 |
| 改这条规则在新存档里的默认开关 | 规则 JSON 的 `enabled` |

## 同一层里的应用顺序

1. **层与层之间**：按世界数据包 → config 覆盖层的顺序处理，与文件名的字母顺序无关。
2. **同一层里同 id 出现多次**：按文件顺序后者胜——数据包层先比数据包优先级（优先级更高的包排在后面，因此胜出），同一个数据包内按文件路径；覆盖层按文件路径。
3. **合并后的先后顺序**：等于每条 `id` 第一次被读到的顺序（世界数据包 → config 覆盖层，层内按上面的文件顺序），也就是下一节说的定义顺序。
4. **启用状态不参与合并**：先按上面的顺序合并出规则内容，再在装配末端把本存档的启停设置套到每条规则上。

## 多条规则都能命中同一个掉落物时

一个掉落物一次只执行**一条**规则。规则之间按三个关键字排队，取第一条同时满足「声明了本次**消失方式**（`triggers`，掉落物被销毁的途径：`natural`、`fire`、`lava`、`cactus`；不写时只对 `natural` 生效）」与「条件命中」的规则：

1. **优先级**（`priority`，整数，默认 0）：数值越大越靠前，字段说明见 [规则基础字段](zh-02-rule-basics)。
2. **条件叶数**（条件树里的叶节点个数）：越多越靠前——`all_of` 下两个叶算 2，`inverted` 包一个叶算 1，组合节点本身不计数。条件写得更具体的那条更容易胜出，写法见 [条件](zh-03-conditions)。
3. **定义顺序**（每条 `id` 第一次被读到的先后）：以上都相同时，先被读到的先执行。

排队靠前但条件不命中的规则会被跳过，继续往下找，直到找到一条能执行的；一条都不命中时，这次不转化，规则会按**退避**节奏继续重试（间隔从 1 秒起逐步拉长，上限由 `backoff_max_ticks` 控制，见 [服务端配置](zh-07-server-config)）。

下面是一条优先级为 10 的钻石转金锭规则。将它保存为 `config/itemdespawntowhat/rules/diamond_to_gold.json`。当它与另一条优先级为 0 的规则都满足触发时机和条件、可处理同一掉落物时，先执行这一条：

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

重载后，满足触发时机和条件的钻石掉落物会按这条规则生成金锭。优先级较低、id 不同的规则仍保留在列表中。

来源层优先级用于决定**同 id** 采用哪份内容；**不同 id** 的规则按 `priority`、条件叶数和定义顺序选择。要修改世界数据包中某条规则的行为，在覆盖层使用相同 id。

## 查看一条规则来自哪一层、哪个文件

使用 `/idtw rule list [页码] [筛选词]` 分页查询已发布规则。行内显示名称、ID、生效启用和本服覆盖标记；悬停查看来源，点击预填详情命令。

`/idtw rule show <id>` 展示已发布来源、默认启用、本服覆盖、生效启用、优先级与结果结构。`/idtw rule show <id> raw [页码]` 按需读取当前覆盖层与已加载数据包基底中的胜出原始内容，保留未知字段。

手动修改文件后先运行 `/idtw rule validate` 检查，再运行 `/idtw rule reload` 发布。列表与普通详情在重载后更新，读取原始来源保持运行状态。具体语法、补全与交互见 [命令参考](zh-08-commands)。

## 排查：覆盖或停用看起来没生效

按这个顺序查：

1. **文件位置和扩展名**：覆盖层必须落在 `config/itemdespawntowhat/rules/` 下（可以有子目录）且以 `.json` 结尾；数据包必须落在 `data/<命名空间>/idtw/rules/` 下。
2. **`id` 是否和目标完全一致**：不一致就是新增了一条规则，两份同时存在，覆盖的意思就没有了。
3. **有没有加载成功**：用 `/idtw rule validate` 看文件校验的错误与告警——它们会带上出问题的来源与字段路径。文件里 JSON 语法错误、文件为空、顶层既不是规则对象也不是规则数组，都会让整份文件被拒载；单条规则写错只是那一条不生效。
4. **本服有没有停用它**：规则里的 `enabled` 只是默认启用，本存档的启停设置优先。看 `/idtw rule list` 的生效状态标记；被停用过就用 `/idtw rule reset <id>` 回退到默认。
5. **是不是被另一条规则抢走了**：同一个掉落物只执行一条规则，检查有没有优先级更高、或条件叶数更多的规则也管着这个物品；用 `/idtw rule show <id>` 看优先级和发布来源。
6. **改完是否重新加载过**：见 [首次使用与生效流程](zh-01-getting-started)。

## 相关

- [首次使用与生效流程](zh-01-getting-started)
- [规则基础字段](zh-02-rule-basics)
- [候选结果与结算](zh-05-outcomes-and-settlement)
- [命令参考](zh-08-commands)
