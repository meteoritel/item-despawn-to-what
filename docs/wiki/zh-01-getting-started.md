# 首次使用与生效流程

> 适用版本：**2.0.0**（Minecraft **1.21.1**）。

先用游戏内 GUI 创建第一条规则。2.0.0 是一次破坏性更新，版配置需要重新编写。

## 用 GUI 创建第一条规则

下面创建一条「鸡肉掉落 10 秒后变成腐肉」的规则。截图展示英文界面的操作位置，示例中请分别选择鸡肉和腐肉。

先安装模组并进入世界。多人游戏中，服务器和使用编辑器的玩家客户端都需安装模组。单人存档房主可直接使用；其他玩家需要权限等级 ≥2。同一时间只允许一位玩家编辑。

### 1. 打开编辑器

在聊天栏输入并执行：

```text
/idtw editor open
```

也可以使用简写 `/idtw editor`。

![在聊天栏输入打开编辑器的命令](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-open-command.webp)

### 2. 新建规则

点击「新建规则」（**New rule**），将名称改为 `myrules:chicken_to_rotten_flesh`，点击「确认」（**Confirm**）。规则 ID 用来区分规则；使用小写字母、数字和下划线，冒号前是命名空间。

![新建规则弹窗与规则名称输入框](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-new-rule.webp)

### 3. 选择输入物品

在「输入」（**Input**）页的「源物品」（**Source items**）中点击「添加」（**Add**），选择鸡肉（`minecraft:chicken`）。将鸡肉的「源物品消耗」（**Source item consumption**）设为 `1`，表示每组转化消耗 1 个鸡肉。

![输入页中的源物品选择与消耗数量](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-input.webp)

### 4. 设置触发时间

切换到「触发」（**Trigger**）页，保留「自然（物品消失）」（**Natural (item despawn)**），将「触发时间（秒）」（**Trigger time (seconds)**）设为 `10`。这条入门规则的条件保持为空。

### 5. 设置转化结果

切换到「结果」（**Results**）页，点击左侧「执行行为」（**Actions**）区域的 `+`，添加「实体」（**Entities**）行为。

![结果页的添加行为按钮](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-add-action.webp)

在右侧将「产物」（**Product**）设为「物品」（**Items**），点击「物品」（**Item**）旁的「添加」（**Add**），选择腐肉（`minecraft:rotten_flesh`），将「数量」（**Count**）设为 `1`。

![实体行为中的物品选择与产物数量](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-item-output.webp)

### 6. 应用并验证

点击底部「应用更改（1 条）」（**Apply changes (1 rules)**），保存期间保持界面打开，等待保存成功。GUI 保存成功后会自动重载规则。

回到游戏，往地上丢出 1 个鸡肉，等待约 10 秒，原位置会生成 1 个腐肉。也可以执行 `/idtw rule list` 查看规则，或用 `/idtw rule show myrules:chicken_to_rotten_flesh` 查看摘要。

### 按需添加条件

想让规则只在特定天气、维度或环境下触发，在「触发」页打开条件树，点击 `+` 选择条件类型，再填写参数。下图展示天气条件的选择入口。条件类型与组合方式见[条件](zh-03-conditions)。

![条件树中的条件类型选择器](https://raw.githubusercontent.com/meteoritel/item-despawn-to-what/1.21.1/docs/wiki-assets/2.0.0/editor-conditions.webp)

调整完成后再次点击「应用更改」。字段和按钮的用途也可通过悬停提示查看。

## 这个模组做什么

ItemDespawnToWhat 会在掉落物的**消失方式**发生时，按你写的规则把它变成物品、生物、方块、经验或世界现象。

**掉落物**：被丢出或方块被破坏后，落在地上等待处理的那一份物品。**消失方式**（`triggers`）：掉落物被销毁的途径，分为自然消失、火烧、岩浆销毁与仙人掌销毁。

| 消失方式 | 什么时候判定 | 要不要等 |
| --- | --- | --- |
| `natural`（自然消失） | 掉落物活到寿命上限的那一刻 | 等 `trigger_after_seconds` |
| `fire`（火烧） | 被烧毁的当刻 | 不等 |
| `lava`（岩浆销毁） | 被岩浆吞掉的当刻 | 不等 |
| `cactus`（仙人掌销毁） | 被仙人掌扎掉的当刻 | 不等 |

以下是可以自行配置的效果示例：

| 规则做出来的效果 | 说明 |
| --- | --- |
| 到期变物品 | 鸡肉掉落物存活 300 秒后变成腐肉 |
| 到期变生物 | 鸡蛋在主世界露天放置 300 秒后孵出幼年鸡 |
| 摆放方块 | 种下与源物品对应的树苗方块 |
| 掉战利品 | 按地牢宝箱的战利品表掉落，并有 50% 概率不产出 |
| 打雷与改天气 | 在指定高度区间外降下闪电；把雷暴天气改成降雨 |
| 爆炸 | 在光照不足或非晴朗天气下炸一次 |

规则命中后按组结算：一组消耗声明数量的源物品，产出按整组展开；源物品不足一组时，剩余部分会**返还**，也就是退回给玩家或留在原地成为掉落物。细节见 [候选结果与结算](zh-05-outcomes-and-settlement)。

## 配置放在哪

规则可以通过 config 文件或世界数据包提供，加载时按 id 合并。以下路径均位于服务端；单人游戏使用本地游戏目录和世界存档。

| 位置 | 编辑方式 | 说明 |
| --- | --- | --- |
| `config/itemdespawntowhat/rules/**/*.json` | GUI 或手动编辑 JSON | config 覆盖层，GUI 保存规则的位置 |
| `<世界存档>/datapacks/<数据包>/data/<命名空间>/idtw/rules/**/*.json` | 编辑数据包文件 | 世界数据包中的规则，作为 GUI 的只读基底 |

**来源层**决定同 id 规则使用哪一份。世界数据包先加载，config 覆盖层后加载；同 id 时，覆盖层的完整规则替换数据包中的规则。不同 id 的规则各自保留。

此外，首次启动会生成模组级配置文件 `config/itemdespawntowhat/server.json`。它只放服务器性能与运维参数（检查间隔、批量上限、调试日志等），与规则内容无关；字段清单见 [服务端配置](zh-07-server-config)。其中 `overlay_directory`（覆盖层目录名）默认是 `itemdespawntowhat`，也就是上面写的 `config/itemdespawntowhat`；没有特殊需求不要改它。

覆盖层里的规则可以放在 `rules/` 下的任意子目录，扩展名必须是 `.json`，例如 `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`。子目录只影响规则 id 的推导，不影响加载。

## 可选：手动编写 JSON

需要直接管理文件或制作世界数据包时，可以手动编写规则。下面的 JSON 与上方 GUI 示例效果相同。

### 规则 id 与文件名的关系

每条规则都必须有一个 id，形如 `命名空间:路径`，例如 `myrules:chicken_to_rotten_flesh`。写 id 有两种方式：

- **显式写 `id` 字段**：任何文件形状都可以，也是多条目文件唯一被接受的方式。
- **省略 `id`**：仅当文件里只有一条规则时才允许，此时 id 由文件路径推导出来。

推导方式按文件所在位置不同：

| 规则文件路径 | 推导出的 id |
| --- | --- |
| `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json` | `idtw:myrules/chicken_to_rotten_flesh` |
| `config/itemdespawntowhat/rules/egg_hatch.json` | `idtw:egg_hatch` |
| 数据包 `data/myreload/idtw/rules/crops/bean.json` | `myreload:crops/bean` |

要点：

- 覆盖层推导时，命名空间固定为规则命名空间 `idtw`，路径取 `rules/` 之后的相对路径去掉 `.json`。
- 数据包层推导时，命名空间取该文件所在数据包的命名空间，路径取 `idtw/rules/` 之后的相对路径去掉 `.json`。
- 想让 id 干净可控，就在 JSON 里显式写 `id`，这时文件名可以任意取。
- 文件里是一个**规则数组**（多条规则）时，每条都必须显式写 `id`，否则该条会被**拒载**（读取时被拒绝、不参与加载）。
- 同一个文件里 id 不能重复；id 非法（含大写字母、空格或其它非法字符）时该条也会被拒载。

### 一条最小可用规则

把下面内容整份保存为 `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`：

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 10,
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "variant": "item",
      "item": "minecraft:rotten_flesh",
      "count": 1
    }
  ]
}
```

逐字段说明：

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | 这条规则的身份 | `命名空间:路径` | 省略时由文件路径推导（仅单条文件） | 见上一节 |
| `source` | 这条规则认哪些掉落物 | 对象；`items` 必填且非空，可选 `exclude` | 无，必填 | `items` 里可写物品 id 或物品标签，如 `"#minecraft:saplings"` |
| `trigger_after_seconds` | 掉落物存活多少秒后触发 | 非负整数 | `300` | 实际触发不晚于该掉落物的自然消失时刻 |
| `effects` | 触发时做什么 | 非空数组；与 `outcomes` 二选一 | 无，必填（二者至少一个） | 每个元素是一个**效果**，必须带 `type` |

上面这条规则还省略了两个可以先不管的字段：`enabled`（默认就是 `true`，写 `false` 表示这条规则不参与匹配）与 `triggers`（默认只有 `natural`，即自然消失）。它们的完整说明见 [规则基础字段](zh-02-rule-basics)。

**配完会发生什么**：任何 `minecraft:chicken` 掉落物存活到第 10 秒时消失，并在同一位置生成 1 个 `minecraft:rotten_flesh` 掉落物。因为没写源成本，这一组按默认语义消耗 1 个源物品，也就是 1 个鸡肉。没有配置时，鸡肉只会等到自然消失时刻被原版机制直接删除。

### 手动改文件后如何生效

完整流程只有三步：

1. **写到哪**：把 JSON 放进 `config/itemdespawntowhat/rules/`，或放进数据包的 `data/<命名空间>/idtw/rules/`。改完文件必须先保存到磁盘。
2. **看错误**：执行 `/idtw rule validate`。它按磁盘当前内容重新读取全部来源做一次检查，先回显校验结论与统计（有效规则、各来源层数量、错误和告警），随后分页列出带来源与字段路径的问题。它只检查，不改变已经生效的规则，也不会把改动应用进去。
3. **应用**：执行 `/idtw rule reload`。它重新读取全部规则并让新内容立刻生效，回显应用结果、统计与问题预览；更多问题通过 `validate` 分页查看。

必须记住的两条：

- **改完文件必须重载**（执行 `/idtw rule reload`，或重载数据包、重启服务器），否则改动不会被游戏读取。
- **有问题时只拒载出问题的那部分**：整个文件 JSON 语法错误、或顶层既不是规则对象也不是规则数组时，该文件全部内容都不生效；单条规则的字段错误只让该条不生效，同一文件里其它规则照常加载。

命令要求单人存档的房主，或权限等级 ≥2。其余命令见 [命令参考](zh-08-commands)。

## 查看与管理已添加的规则

执行 `/idtw rule list` 分页查看已发布规则，悬停查看来源。`/idtw rule show <id>` 显示规则摘要，`/idtw rule show <id> raw` 读取原始 JSON。

通过 `/idtw rule enable <id>`、`/idtw rule disable <id>` 按存档启停规则。要替换世界数据包提供的规则，在 config 覆盖层写入同 `id` 的完整规则，详见[来源层与覆盖](zh-06-layers-and-overrides)。

## 规则不生效？按顺序排查

1. **文件位置对不对**：必须在 `config/itemdespawntowhat/rules/` 之下，或数据包的 `data/<命名空间>/idtw/rules/` 之下，扩展名是 `.json`。放错目录等于没放。
2. **JSON 合法吗**：语法错误、多余逗号、顶层既不是规则对象也不是规则数组，都会让整个文件不生效，`validate` 会报「JSON 解析失败」或「规则文件顶层必须是规则对象或规则数组」。
3. **这条规则是不是被关掉了**：规则里的 `enabled` 是默认启用，为 `false` 的规则仍加载但不参与匹配（`rule list` 显示「停用」）；本存档也可以单独停用某条，已发布生效值同样显示在状态标记中，用 `/idtw rule reset <id>` 回退到默认。
4. **`source` 匹配得上吗**：物品 id 的拼写、命名空间与标签名都要正确，写错的静态物品 id 会让该条被拒载；也别漏看 `exclude` 是不是把你想要的物品排除了。
5. **消失方式与时间对不对**：`triggers` 没写就只有 `natural`；要观察被火烧、被岩浆或仙人掌销毁，必须把对应取值写进 `triggers`。`trigger_after_seconds` 超过该掉落物的实际存活时间时，触发时刻会提前到自然消失那一刻，不会拖到设定秒数。
6. **条件满足了吗**：`conditions` 里任何一个条件不命中，这条规则就不会被选中。条件类型与语义见 [条件](zh-03-conditions)。
7. **有没有被更高优先级的规则抢先**：同一个掉落物一次只执行一条规则。候选按优先级高的先看、优先级相同则条件叶数多的先看、再相同按定义顺序排，取第一条消失方式相符、条件也命中的规则。多条规则抢同一个物品时，把想要的调高 `priority`，或缩小 `source`、加严条件。

排查时优先看 `/idtw rule validate` 的输出，它会直接告诉你问题出在哪个来源、哪个字段路径。

## 相关

- [规则基础字段](zh-02-rule-basics)：每条规则顶层能写哪些字段、默认值是多少。
- [条件](zh-03-conditions)：什么时候才让规则生效。
- [效果](zh-04-effects)：触发后具体做什么。
- [命令参考](zh-08-commands)：检查与应用改动用到的全部命令。
