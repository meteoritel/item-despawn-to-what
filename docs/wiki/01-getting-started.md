# 首次使用与生效流程

这份文档解决一件事：让你写下第一条规则、把它放对位置，并用命令确认它已经生效。字段的完整含义分别见 [规则基础字段](02-rule-basics.md)、[条件](03-conditions.md)、[效果](04-effects.md) 与 [候选结果与结算](05-outcomes-and-settlement.md)。

## 这个模组做什么

ItemDespawnToWhat 让掉落物在**自然消失前**，或被**火 / 岩浆 / 仙人掌销毁**时，按你写的规则变成物品、生物、方块、经验或世界现象。

配完之后能看到什么，几个例子：

- **到期变物品**：鸡肉存活 300 秒后变成腐肉（内置示例默认启用，可直接观察）。
- **到期变生物**：鸡蛋在主世界露天放置 300 秒后孵化出幼年鸡。
- **放方块**：泥土上的树苗自动种下，种下与源物品对应的树苗方块。
- **掉战利品**：金粒 300 秒后按地牢宝箱的战利品表掉落，且有 50% 概率不产出。
- **雷击与天气**：钻石在指定高度区间外 300 秒后额外降下闪电；湿海绵在雷暴天气且高度足够时把天气改成降雨。
- **爆炸**：火药在光照不足或非晴朗天气下产生一次爆炸。

规则命中后按组结算：一组消耗声明数量的源物品，产出按整组展开；源物品不足一组时剩余部分返还。细节见 [候选结果与结算](05-outcomes-and-settlement.md)。

## 配置放在哪

规则来自两个可写性不同的位置，加载时按 id 合并：

| 位置 | 可写性 | 说明 |
| --- | --- | --- |
| `config/itemdespawntowhat/rules/**/*.json` | 可写（覆盖层） | 你自己的规则写在这里；游戏内编辑器与命令的写入目标 |
| `data/<命名空间>/idtw/rules/**/*.json` | 只读（数据包层） | 由数据包提供，含模组自带的示例规则；加进世界数据包后由原版机制同步 |

此外，首次启动会生成模组级配置文件 `config/itemdespawntowhat/server.json`。它只放服务器性能与运维参数（检查间隔、批量上限、调试日志等），与规则内容无关；字段清单见 [服务端配置（server.json）](07-server-config.md)。其中 `overlay_directory` 决定覆盖层目录名，默认值 `itemdespawntowhat`，也就是上面写的 `config/itemdespawntowhat`；没有特殊需求不要改它。

覆盖层里的规则可以放在 `rules/` 下的任意子目录，扩展名必须是 `.json`，例如 `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`。子目录只影响规则 id 的推导，不影响加载。

## 规则 id 与文件名的关系

每条规则都必须有一个 id，形如 `命名空间:路径`（例如 `myrules:chicken_to_rotten_flesh`）。id 有两种写法：

- **显式写 `id` 字段**：任何文件形状都行，也是多条目文件唯一被接受的方式。
- **省略 `id`**：仅当文件里**只有一条规则**时才允许，此时由文件路径推导。

推导规则按文件所在位置不同：

| 规则文件路径 | 推导出的 id |
| --- | --- |
| `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json` | `itemdespawntowhat:myrules/chicken_to_rotten_flesh` |
| `config/itemdespawntowhat/rules/egg_hatch.json` | `itemdespawntowhat:egg_hatch` |
| 数据包 `data/myreload/idtw/rules/crops/bean.json` | `myreload:crops/bean` |

要点：

- 覆盖层推导时，命名空间固定为模组命名空间 `itemdespawntowhat`，路径取 `rules/` 之后的相对路径去掉 `.json`；数据包层推导时，命名空间取该文件所在数据包的命名空间，路径取 `idtw/rules/` 之后的相对路径去掉 `.json`。
- 想让 id 干净可控，就在 JSON 里显式写 `id`，此时文件名可以任意取。
- 文件是**规则数组**（多条规则）时，每条都必须显式写 `id`，否则该条会被拒载。
- 同一文件内 id 不能重复；id 非法（含大写、空格、非法字符）时该条也会被拒载。

## 一条最小可用规则

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
      "type": "itemdespawntowhat:spawn_entity",
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

上面这条规则还省略了两个可以先不管的字段：`enabled`（默认就是 `true`，写 `false` 表示这条规则不参与匹配）与 `triggers`（默认只有 `natural`，即自然消失）。它们的完整说明见 [规则基础字段](02-rule-basics.md)。

**配完会发生什么**：任何 `minecraft:chicken` 掉落物存活到第 10 秒时消失，并在同一位置生成 1 个 `minecraft:rotten_flesh` 掉落物。因为没写任何消耗类效果，这一组按默认语义消耗 1 个源物品，也就是 1 个鸡肉。没有配置时，鸡肉只会等到自然消失时刻被原版机制直接删除。

## 让规则生效

完整流程只有三步：

1. **写到哪**：把 JSON 放进 `config/itemdespawntowhat/rules/`，或放进数据包的 `data/<命名空间>/idtw/rules/`。改完文件必须先保存到磁盘。
2. **看错误**：执行 `/idtw config validate`。它按当前磁盘内容重新读取全部来源做一次检查，先回显一条统计行（共几条、各来源层各几条、错误数与告警数），随后逐条列出问题，每条形如 `[error] 消息 来源#字段路径`。它只检查，不改变已经生效的规则，也不会把改动应用进去。
3. **应用**：执行 `/idtw config reload`。它重新读取全部规则并让新内容立刻生效，回显与 `validate` 相同的统计行与问题清单。

必须记住的两条：

- **改完文件必须 reload**（或重载数据包、重启服务器），否则改动不会被游戏读取。
- **有问题时只拒载出问题的那部分**：整个文件 JSON 语法错误、顶层既不是规则对象也不是规则数组时，该文件全部内容都不生效；单条规则的字段错误只让该条不生效，同一文件里其它规则照常加载。所以 `validate` 报了某个文件的错，对应规则就不会生效。

命令需要单人世界，或权限等级 ≥2。其余命令见 [命令参考](08-commands.md)。

想用图形界面编辑规则，`/idtw config edit` 是等价的替代入口（由玩家执行，取得编辑锁后打开编辑器）。

## 内置示例规则

模组自带 **8 个示例文件、共 10 条规则**，随内置数据包提供。其中 6 条默认启用，方便直接观察效果：

| 默认启用 | 规则 id | 效果 |
| --- | --- | --- |
| 是 | `itemdespawntowhat:builtin_item_to_item` | 鸡肉 300 秒后变腐肉 |
| 是 | `itemdespawntowhat:builtin_item_to_entity` | 鸡蛋在主世界露天孵化出幼年鸡 |
| 是 | `itemdespawntowhat:builtin_catalyst_and_fluid` | 红石在流动水与骨粉旁变荧石粉 |
| 是 | `itemdespawntowhat:builtin_loot_and_chance` | 金粒按战利品表掉落（50% 概率） |
| 是 | `itemdespawntowhat:builtin_multi_effect` | 钻石在指定高度区间外给经验并降下闪电 |
| 是 | `itemdespawntowhat:builtin_thunder_condensation` | 湿海绵在雷暴天气且高度足够时改天气并消耗水 |
| 否 | `itemdespawntowhat:builtin_item_to_block` | 树苗自动种下 |
| 否 | `itemdespawntowhat:builtin_arrow_rain` | 主世界下雨时降下箭雨 |
| 否 | `itemdespawntowhat:builtin_biome_or_time` | 森林或白天时苹果变金苹果 |
| 否 | `itemdespawntowhat:builtin_dark_blast` | 暗处或非晴朗天气下火药产生爆炸 |

**怎么查看**：用 `/idtw config list` 逐条列出生效规则，每行给出 id、来源层、优先级、触发秒数、效果数与是否启用；也可以直接打开数据包里的 `data/itemdespawntowhat/idtw/rules/` 阅读示例原文。

**怎么停用或删除**：内置规则只读，不能直接改它；要在 `config` 覆盖层用同 id 的控制条目停用或删除，具体写法与合并规则见 [来源层与覆盖](06-layers-and-overrides.md)。

## 规则不生效？按顺序排查

1. **文件位置对不对**：必须在 `config/itemdespawntowhat/rules/` 之下（或数据包的 `data/<命名空间>/idtw/rules/` 之下），扩展名是 `.json`。放错目录就等于没放。
2. **JSON 合法吗**：语法错误、多余逗号、顶层既不是规则对象也不是规则数组，都会让整个文件不生效，`validate` 会报“JSON 解析失败”或“规则文件顶层必须是规则对象或规则数组”。
3. **`enabled` 是不是 `false`**：为 `false` 的规则仍在加载清单里（`list` 会显示“启用=false”），但不参与匹配；被覆盖层停用过的内置规则同样显示为未启用。
4. **`source` 匹配得上吗**：物品 id 的拼写、命名空间、标签名都要正确，写错的静态物品 id 会让该条被拒载；也别忘了 `exclude` 可能正把你要的物品排除掉。
5. **消失方式与时间对不对**：`triggers` 没写就只有 `natural`；要观察被火烧、被岩浆或仙人掌销毁，必须把对应取值写进 `triggers`。`trigger_after_seconds` 超过该掉落物的实际存活时间时，它会先自然消失，等不到触发。
6. **条件满足了吗**：`conditions` 里任何一个条件不成立，这条规则就不会被选中。条件类型与语义见 [条件](03-conditions.md)。
7. **有没有被更高优先级的规则抢先**：同一个掉落物一次只执行一条规则。候选按“优先级高的先看、优先级相同则条件叶数多的先看、再相同按定义顺序”排序，取第一条消失方式相符且条件成立者。多条规则抢同一个物品时，把想要的调高 `priority`，或缩小 `source`、加严条件。

排查时优先看 `/idtw config validate` 的输出，它会直接告诉你问题出在哪个来源、哪个字段路径。

