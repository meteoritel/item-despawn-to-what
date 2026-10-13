# 服务端配置（server.json）

这份文档说明 `server.json` 里每一项分别在做什么、默认值是多少、什么时候需要改它。它管的是**服务器怎么跑**（反应的快慢、一次能处理多少、产物的保护与冷却），不决定掉落物会变成什么——想改转化内容请看[规则基础](02-rule-basics.md)与后续各篇。

## 1. 这份文件是什么

- **位置**：`config/itemdespawntowhat/server.json`（跟随游戏实例的 `config` 目录，与存档、数据包无关）。
- **共用一份**：Fabric 与 NeoForge 读取同一份文件、同一套取值。
- **什么时候生成**：第一次启动游戏或服务器时自动生成，之后不会自动改写已有文件；你自己改过的内容不会被插件覆盖。
- **什么时候生效**：服务端启动时读取一次。**改完要重启服务端**；`/idtw config reload` 只重新读取规则，不会重新读取这份配置。
- **它和规则文件的区别**：

| | `server.json` | 规则文件 |
|---|---|---|
| 是什么 | 服务器运行参数 | 转化内容（什么变成什么） |
| 作用范围 | 全服一份，所有规则共用 | 每条规则独立 |
| 所在位置 | `config/itemdespawntowhat/server.json` | `config/itemdespawntowhat/rules/*.json`、数据包 `data/<命名空间>/idtw/rules/*.json` |
| 生效方式 | 重启服务端 | `/idtw config reload` |

- **没写的字段**：取默认值。因此只需写出想改的那几项，其余可以省略。
- **写错会怎样**：只要有一项的值超出允许范围、或类型不对（例如该填数字却填了文字），或者整个文件不是合法 JSON（括号、逗号写错，或内容根本不是一个 JSON 对象），这份文件就**整体作废、全部按默认值运行**——写对的其它项也不会生效；启动日志里会记一条错误。文件本身不会被自动改写，改完建议看一眼启动日志。
- **表里没列出的字段**不会被读取，也不会影响运行。

文件里可以自由换行缩进；游戏首次生成的内容是一行紧凑 JSON，两者等价。

## 2. 默认配置（可直接抄）

下面是首次启动时生成的完整内容，也是"全部保持默认"的等价写法：

```json
{
  "check_interval_ticks": 20,
  "backoff_max_ticks": 100,
  "max_checks_per_tick": 512,
  "overlay_directory": "itemdespawntowhat",
  "fabric_lifespan_fallback_ticks": 6000,
  "debug_logging": false,
  "server_budget_us": 2000,
  "effects_work_units_per_tick": 64,
  "check_spread_window_ticks": 20,
  "dispatch_batch_size": 64,
  "new_product_protection_seconds": 2,
  "conversion_cooldown_seconds": 5,
  "position_search_checks_per_tick": 16,
  "debug_scenario_prepare_batch_size": 128,
  "debug_scenario_prepare_budget_us": 2000,
  "nearby_products": {
    "item_limit": 1024,
    "item_radius": 6,
    "entity_limit": 128,
    "entity_radius": 6,
    "experience_orb_limit": 64,
    "experience_radius": 6
  }
}
```

注意：`max_work_units_per_tick` 默认**不出现在文件里**（不写时才用回退值，见下表）。首次生成的内容里没有它属于正常现象。

## 3. 全部字段

时间类的值单位都是**游戏刻**，20 刻 = 1 秒（正常服务器）。

| 字段 | 作用 | 取值范围 | 默认值 | 什么时候需要改 |
|---|---|---|---|---|
| `check_interval_ticks` | 条件暂时不满足时，隔多久再检查一次；第一次重试用这个间隔，之后每次翻倍 | 1 ~ 1200 刻 | `20`（1 秒） | 希望"条件一满足就尽快转化"就调小；掉落物很多、想减少检查次数就调大 |
| `backoff_max_ticks` | 上面那个翻倍的上限：条件长期不满足时，最多等这么久再检查一次 | 1 ~ 72000 刻 | `100`（5 秒） | 调小可让"过一会儿就会满足"的规则更快再次尝试；调大可进一步省性能，但反应更迟钝 |
| `max_checks_per_tick` | 每刻处理总量的上限，但只在 `max_work_units_per_tick` **没写**时用得上 | 1 ~ 100000 | `512` | 只在明确要控制"每刻处理量"时才动，通常不用改 |
| `overlay_directory` | 规则覆盖层目录名，相对 `config/`，即规则放在 `config/<这个值>/rules/` | 任意目录名（字符串） | `"itemdespawntowhat"` | 只有想把配置目录改名时才动；改完要把整个目录一起搬过去，否则覆盖层规则会读不到（`server.json` 自身位置不受影响） |
| `fabric_lifespan_fallback_ticks` | 仅 Fabric 端：读不到某个掉落物的实际自然寿命时，用这个刻数当作"它还能活多久" | 1 ~ 72000 刻 | `6000`（5 分钟） | 默认对应原版掉落物的自然消失时间；只有服务器改过掉落物寿命规则时才需要调整 |
| `debug_logging` | 是否把运行过程写进日志（例如某个掉落物条件未满足、多少刻后重试） | `true` / `false` | `false` | 排查"为什么没转化"时临时设为 `true`；平时保持 `false`，一直打开会刷出大量日志 |
| `server_budget_us` | 每个游戏刻最多花多少时间处理本模组的工作（微秒）；一次做不完的部分顺延到之后几刻继续 | 100 ~ 100000 微秒 | `2000`（约 2 毫秒） | 服务器吃紧、想更保险就调小；转化延迟明显、服务器还有余力就调大 |
| `max_work_units_per_tick` | 每刻处理量的总上限（与 `server_budget_us` 同时生效，先到哪个算哪个） | 1 ~ 100000 | 不写（按 `max_checks_per_tick` 的值 `512`） | 通常保持不写；只有需要单独设定"每刻处理量"时才显式填写 |
| `effects_work_units_per_tick` | 每刻专门留给"执行效果"（生成物品、放置方块、给经验等）的额度上限，避免大批产出把检查工作挤掉 | 1 ~ 100000 | `64` | 一次要产出大量东西、却迟迟交不出来时调大；想让检查更及时就调小 |
| `check_spread_window_ticks` | 大量掉落物同时到期时，把这些检查摊到大约这么多个游戏刻里完成 | 1 ~ 1200 刻 | `20`（1 秒） | 一批东西同时到期造成卡顿时调大（更平缓，但个别转化会稍晚一点）；希望转化尽量准时、单刻压力可接受就调小 |
| `dispatch_batch_size` | 每刻最多向前推进多少条待办，也限制一次批量交付的数量 | 1 ~ 100000 | `64` | 大批量转化处理不过来、积压明显时调大 |
| `new_product_protection_seconds` | 刚转化出来的物品在多少秒内不会被火、岩浆、仙人掌销毁 | 0 ~ 3600 秒 | `2` | 产物常常一出现就被烧掉（例如在岩浆旁转化）就调大；想让产物立刻可被销毁就设 `0` |
| `conversion_cooldown_seconds` | 刚转化出来的物品在多少秒内**不会再次启动转化** | 0 ~ 3600 秒 | `5` | 出现"产物立刻又被另一条规则转化"的连锁时调大；想让产物马上能继续参与转化就设 `0` |
| `position_search_checks_per_tick` | 每刻最多尝试多少个候选位置，用来给产物或返还物找落点 | 1 ~ 4096 | `16` | 出现"东西一直没交出来"、落点难找（地形拥挤）时调大；想省性能可调小 |
| `debug_scenario_prepare_batch_size` | 只影响开发环境测试场景的准备阶段，与正常游玩无关 | 1 ~ 100000 | `128` | 普通玩家不用改 |
| `debug_scenario_prepare_budget_us` | 同上，准备阶段的时间上限（微秒） | 1 ~ 100000 微秒 | `2000` | 普通玩家不用改 |
| `nearby_products` | 产物邻近上限（按附近已有的产物数量决定一组转化能不能开始），详见下一节 | 对象 | 见下一节 | 产物堆积过多导致卡顿时调低；想解除数量限制时把对应上限设 `0` |

`new_product_protection_seconds` 与 `conversion_cooldown_seconds` 允许写 `0`；其余字段不能写 `0`。另外 `server_budget_us` 的下限是 `100` 而不是 `1`。任何一项写出范围都会让整份文件回退默认值并在日志报错（见第 1 节）。

## 4. nearby_products：产物邻近上限

一组转化**开始之前**，模组会先看产物落点附近已经有多少同类东西。已经达到上限，这一组就不开（对应的源物品不会被消耗，见下方要点）。**已经在进行中的一组不会因为数量变多被砍掉**，可以正常完成并超过上限。

这些上限对**所有规则通用**（不是每条规则单独设置）。

| 子字段 | 作用 | 取值范围 | 默认值 | 什么时候需要改 |
|---|---|---|---|---|
| `item_limit` | 附近同类**物品**的总件数上限（按要产出的物品或物品标签统计件数） | 0 ~ 1000000 | `1024` | 物品类产物大量堆积时调低；`0` 关闭这项检查（不再限制） |
| `item_radius` | 上面这项的统计半径（方块）：以源所在方块为中心的立方体，边长 = 2 × 半径 + 1 格 | 1 ~ 32 | `6`（13 格边长） | 想扩大或缩小"附近"的范围时调整 |
| `entity_limit` | 附近同类**实体**的个数上限（按要产出的实体类型或实体标签统计） | 0 ~ 1000000 | `128` | 生物等实体类产物堆积时调低；`0` 关闭这项检查 |
| `entity_radius` | 实体统计半径（方块），含义同上 | 1 ~ 32 | `6` | 同 `item_radius` |
| `experience_orb_limit` | 附近**经验球**的个数上限（统计范围内全部经验球，不分类） | 0 ~ 1000000 | `64` | 经验类产物堆积时调低；`0` 关闭这项检查 |
| `experience_radius` | 经验球统计半径（方块），含义同上 | 1 ~ 32 | `6` | 同 `item_radius` |

要点：

- **`0` 表示关闭对应检查**（等价于"不限量"），只有三个 `*_limit` 能写 `0`，三个 `*_radius` 不能。
- 上限只决定**新的转化组能不能开始**，不是硬性存量上限，也不保证"附近永远不超过这个数"。
- 达到上限而被拦下的转化不会消耗源物品：开不出的那部分源物品走返还流程（见[结果与结算](05-outcomes-and-settlement.md)），返还物退回世界后不再参与转化——也就是说，达到上限时多出来的那些不会排队等位置。
- 上限调低会让转化更容易被推迟，甚至在大批产物堆积时长期不产出；三项上限都设为非 `0` 时以最紧的那一项为准，比只留一项更严格。

例：解除所有邻近数量限制（其余字段保持默认）：

```json
{
  "nearby_products": {
    "item_limit": 0,
    "entity_limit": 0,
    "experience_orb_limit": 0
  }
}
```

## 5. 新产物保护与转化冷却

这两个字段只作用于**转化刚产出的物品**（返还物另有规则，见下）：

| 字段 | 实际效果 | 默认值 | 写 `0` 的含义 |
|---|---|---|---|
| `new_product_protection_seconds` | 刚产出的物品在这段时间内不会被**火、岩浆、仙人掌**销毁（其它伤害方式不影响） | `2` 秒 | 不授予保护：产物一出现就可以被烧掉、被岩浆吞掉或被仙人掌破坏 |
| `conversion_cooldown_seconds` | 刚产出的物品在这段时间内不会**再次启动转化**，避免"产物马上被另一条规则接着转化"的连锁 | `5` 秒 | 不授予冷却：产物只要命中某条规则就可以立刻再次被转化 |

两者互相独立，可以分别设置。举例：

- 产物经常掉在岩浆旁被烧掉：把 `new_product_protection_seconds` 调大（例如 `10`）。
- 看到产物刚出来就被立刻变成别的东西：把 `conversion_cooldown_seconds` 调大。
- 完全不希望有任何额外保护、要求产物立刻可被销毁和再次转化：

```json
{
  "new_product_protection_seconds": 0,
  "conversion_cooldown_seconds": 0
}
```

补充一条与本配置无关的现象：**返还物**（已经退回给世界的源物品）在整个生命周期内永久保护且永久不再转化，不受这两个字段影响。

## 6. 按场景改配置

下面几段都是 `server.json` 的一部分，未写到的字段保持默认值，直接并入文件即可。改完都要重启服务端。

**服务器较弱 / 掉落物非常多（优先保证流畅度）**

```json
{
  "server_budget_us": 1000,
  "effects_work_units_per_tick": 32,
  "check_interval_ticks": 40,
  "backoff_max_ticks": 200,
  "check_spread_window_ticks": 40,
  "dispatch_batch_size": 32
}
```

效果：模组每刻占用的时间更少、检查更稀、大批同时到期更平缓；代价是转化整体变慢，条件满足后可能要等一会儿才发生。

**希望转化反应更快**

```json
{
  "check_interval_ticks": 10,
  "backoff_max_ticks": 40
}
```

效果：条件第一次不满足后更快重试，等待上限从 5 秒降到 2 秒；代价是同样时间内检查次数更多。

**单独设定每刻处理量**（默认不写此键时用 `max_checks_per_tick` 的值）

```json
{
  "max_checks_per_tick": 512,
  "max_work_units_per_tick": 1024
}
```

**把规则覆盖层搬到别的目录**

```json
{
  "overlay_directory": "my_idtw"
}
```

效果：规则改从 `config/my_idtw/rules/` 读取。必须先把原 `config/itemdespawntowhat/` 目录下的内容搬到 `config/my_idtw/`，否则原有覆盖层规则会全部读不到；`server.json` 本身仍留在 `config/itemdespawntowhat/`。

## 7. 正常情况下不用动的字段

- `overlay_directory`：只有想改目录结构时才动，改错会让覆盖层规则全部失效。
- `max_checks_per_tick`、`max_work_units_per_tick`、`effects_work_units_per_tick`、`dispatch_batch_size`、`position_search_checks_per_tick`：默认值适合大多数服务器，只有出现卡顿或明显积压时才调。
- `fabric_lifespan_fallback_ticks`：只有 Fabric 端读不到寿命时才会用上。
- `debug_logging`：只在排查问题时临时开启。
- `debug_scenario_prepare_batch_size`、`debug_scenario_prepare_budget_us`：只服务于开发环境的测试场景，正常游玩永远不用改。
- `nearby_products` 的默认上限很宽松，正常玩法几乎不会触发。

## 8. 相关

- 文件位置与第一次启动的流程：[开始使用](01-getting-started.md)
- 规则顶层字段（`trigger_after_seconds` 等）：[规则基础](02-rule-basics.md)
- 覆盖层目录与来源优先级：[来源层与覆盖](06-layers-and-overrides.md)
- 返还流程与结算：[结果与结算](05-outcomes-and-settlement.md)
- 命令参考：[命令](08-commands.md)
