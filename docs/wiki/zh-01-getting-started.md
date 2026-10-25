# 首次使用与生效流程

这一页带你写完第一条规则、把它放到正确的位置，再用命令确认它已经生效。

## 这个模组做什么

ItemDespawnToWhat 会在掉落物的**消失方式**发生时，按你写的规则把它变成物品、生物、方块、经验或世界现象。

**掉落物**：被丢出或方块被破坏后，落在地上等待处理的那一份物品。**消失方式**（`triggers`）：掉落物被销毁的途径，分为自然消失、火烧、岩浆销毁与仙人掌销毁。

| 消失方式 | 什么时候判定 | 要不要等 |
| --- | --- | --- |
| `natural`（自然消失） | 掉落物活到寿命上限的那一刻 | 等 `trigger_after_seconds` |
| `fire`（火烧） | 被烧毁的当刻 | 不等 |
| `lava`（岩浆销毁） | 被岩浆吞掉的当刻 | 不等 |
| `cactus`（仙人掌销毁） | 被仙人掌扎掉的当刻 | 不等 |

配上规则后能看到什么，几个例子：

| 规则做出来的效果 | 说明 |
| --- | --- |
| 到期变物品 | 鸡肉活到 300 秒后变成腐肉（内置示例默认启用，可直接观察） |
| 到期变生物 | 鸡蛋在主世界露天放置 300 秒后孵出幼年鸡 |
| 摆放方块 | 种下与源物品对应的树苗方块 |
| 掉战利品 | 按地牢宝箱的战利品表掉落，并有 50% 概率不产出 |
| 打雷与改天气 | 在指定高度区间外降下闪电；把雷暴天气改成降雨 |
| 爆炸 | 在光照不足或非晴朗天气下炸一次 |

规则命中后按组结算：一组消耗声明数量的源物品，产出按整组展开；源物品不足一组时，剩余部分会**返还**，也就是退回给玩家或留在原地成为掉落物。细节见 [候选结果与结算](zh-05-outcomes-and-settlement)。

## 配置放在哪

规则来自两个可写性不同的位置，加载时按 id 合并：

| 位置 | 可写性 | 说明 |
| --- | --- | --- |
| `config/itemdespawntowhat/rules/**/*.json` | 可写（config 覆盖层） | 你自己的规则写在这里，它也是编辑器与 `/idtw config edit` 的写入目标 |
| `data/<命名空间>/idtw/rules/**/*.json` | 只读（数据包层） | 由数据包提供，含模组自带的示例规则；放进世界数据包后按原版机制同步 |

**来源层**：规则来源的分层，决定了同名规则最终用哪一份。合并顺序从低到高是内置数据包、世界数据包、config 覆盖层，同 id 时高优先级的那份整体替换低优先级的那份。

此外，首次启动会生成模组级配置文件 `config/itemdespawntowhat/server.json`。它只放服务器性能与运维参数（检查间隔、批量上限、调试日志等），与规则内容无关；字段清单见 [服务端配置](zh-07-server-config)。其中 `overlay_directory`（覆盖层目录名）默认是 `itemdespawntowhat`，也就是上面写的 `config/itemdespawntowhat`；没有特殊需求不要改它。

覆盖层里的规则可以放在 `rules/` 下的任意子目录，扩展名必须是 `.json`，例如 `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`。子目录只影响规则 id 的推导，不影响加载。

## 规则 id 与文件名的关系

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

## 让规则生效

完整流程只有三步：

1. **写到哪**：把 JSON 放进 `config/itemdespawntowhat/rules/`，或放进数据包的 `data/<命名空间>/idtw/rules/`。改完文件必须先保存到磁盘。
2. **看错误**：执行 `/idtw config validate`。它按磁盘当前内容重新读取全部来源做一次检查，先回显一条**统计行**（共几条、各来源层各几条、错误数与告警数），随后逐条列出**问题行**，每条形如 `[error] 消息 来源#字段路径`。它只检查，不改变已经生效的规则，也不会把改动应用进去。
3. **应用**：执行 `/idtw config reload`。它重新读取全部规则并让新内容立刻生效，回显与 `validate` 相同的统计行与问题清单。

必须记住的两条：

- **改完文件必须重载**（执行 `/idtw config reload`，或重载数据包、重启服务器），否则改动不会被游戏读取。
- **有问题时只拒载出问题的那部分**：整个文件 JSON 语法错误、或顶层既不是规则对象也不是规则数组时，该文件全部内容都不生效；单条规则的字段错误只让该条不生效，同一文件里其它规则照常加载。

命令要求单人存档的房主，或权限等级 ≥2。其余命令见 [命令参考](zh-08-commands)。

想用图形界面编辑规则，`/idtw config edit` 是等价的替代入口：由玩家执行，取得编辑锁后打开编辑器。

## 内置示例规则

模组自带一组示例规则，随内置数据包提供。其中一部分默认启用，进游戏就能直接观察效果：

| 默认启用 | 规则 id | 效果 |
| --- | --- | --- |
| 是 | `itemdespawntowhat:builtin_item_to_item` | 鸡肉 300 秒后变腐肉 |
| 是 | `itemdespawntowhat:builtin_item_to_entity` | 鸡蛋在主世界露天孵出幼年鸡 |
| 是 | `itemdespawntowhat:builtin_catalyst_and_fluid` | 红石在流体与催化剂旁变荧石粉 |
| 是 | `itemdespawntowhat:builtin_loot_and_chance` | 金粒按战利品表掉落，50% 概率 |
| 是 | `itemdespawntowhat:builtin_multi_effect` | 钻石在指定高度区间外给经验并降下闪电 |
| 是 | `itemdespawntowhat:builtin_thunder_condensation` | 湿海绵在雷暴天气且高度足够时改天气并消耗水 |
| 否 | `itemdespawntowhat:builtin_item_to_block` | 树苗自动种下 |
| 否 | `itemdespawntowhat:builtin_arrow_rain` | 主世界下雨时降下箭雨 |
| 否 | `itemdespawntowhat:builtin_biome_or_time` | 森林或白天时苹果变金苹果 |
| 否 | `itemdespawntowhat:builtin_dark_blast` | 暗处或非晴朗天气下火药产生爆炸 |

**怎么查看**：用 `/idtw config list` 逐条列出生效规则，每行给出 id、来源层、优先级、触发秒数、效果数与是否启用；也可以直接打开数据包里的 `data/itemdespawntowhat/idtw/rules/` 阅读示例原文。

**想改哪条的内置行为**：内置规则只读，改它要在 config 覆盖层写一份同 `id` 的完整规则整条接管；只想临时关掉某条则用 `/idtw rule <id> disable`，按存档生效。写法与合并规则见 [来源层与覆盖](zh-06-layers-and-overrides)。

## 规则不生效？按顺序排查

1. **文件位置对不对**：必须在 `config/itemdespawntowhat/rules/` 之下，或数据包的 `data/<命名空间>/idtw/rules/` 之下，扩展名是 `.json`。放错目录等于没放。
2. **JSON 合法吗**：语法错误、多余逗号、顶层既不是规则对象也不是规则数组，都会让整个文件不生效，`validate` 会报「JSON 解析失败」或「规则文件顶层必须是规则对象或规则数组」。
3. **这条规则是不是被关掉了**：规则里的 `enabled` 是默认启用，为 `false` 的规则仍加载但不参与匹配（`config list` 显示「启用=false」）；本存档也可以单独停用某条，生效值同样显示在「启用」列，用 `/idtw rule <id> default` 回退到默认。
4. **`source` 匹配得上吗**：物品 id 的拼写、命名空间与标签名都要正确，写错的静态物品 id 会让该条被拒载；也别漏看 `exclude` 是不是把你想要的物品排除了。
5. **消失方式与时间对不对**：`triggers` 没写就只有 `natural`；要观察被火烧、被岩浆或仙人掌销毁，必须把对应取值写进 `triggers`。`trigger_after_seconds` 超过该掉落物的实际存活时间时，它会先自然消失，等不到触发。
6. **条件满足了吗**：`conditions` 里任何一个条件不命中，这条规则就不会被选中。条件类型与语义见 [条件](zh-03-conditions)。
7. **有没有被更高优先级的规则抢先**：同一个掉落物一次只执行一条规则。候选按优先级高的先看、优先级相同则条件叶数多的先看、再相同按定义顺序排，取第一条消失方式相符、条件也命中的规则。多条规则抢同一个物品时，把想要的调高 `priority`，或缩小 `source`、加严条件。

排查时优先看 `/idtw config validate` 的输出，它会直接告诉你问题出在哪个来源、哪个字段路径。

## 相关

- [规则基础字段](zh-02-rule-basics)：每条规则顶层能写哪些字段、默认值是多少。
- [条件](zh-03-conditions)：什么时候才让规则生效。
- [效果](zh-04-effects)：触发后具体做什么。
- [命令参考](zh-08-commands)：检查与应用改动用到的全部命令。
