# 服务端配置（server.json）

> 适用版本：**2.0.0**（Minecraft **1.21.1**）。

本页说明 `server.json` 服务端性能配置中各项参数的含义、默认值与典型调优方案。

这份配置文件主要用于控制服务端的运行节奏与性能预算（如检测频率、单刻处理上限、产物安全保护等），**不负责定义转化产物**。如果需要增加或修改规则内容，请参阅 [规则基础字段](zh-02-rule-basics)。

---

## 配置文件特性与生效规则

- **文件位置**：`config/itemdespawntowhat/server.json`，位于服务端（或单人客户端）的全局 `config` 目录下；
- **平台通用**：Fabric 与 NeoForge 读取同一份文件，配置格式与字段完全一致；
- **自动生成与安全修改**：首次启动游戏时自动生成；后续不会自动覆盖你修改过的内容；未填写的字段自动采用官方默认值；
- **生效方式**：**修改后必须重启服务端（或重启单人游戏）才能生效**。指令 `/idtw rule reload` 仅用于重载规则文件，不会重载服务端配置。

### 与规则文件的区别速查

| 对比维度 | 服务端配置（server.json） | 规则文件（rules/*.json） |
| --- | --- | --- |
| **主要功能** | 服务器运行性能与防卡顿保护 | 具体转化内容（输入什么、产出什么） |
| **作用范围** | 全服唯一，影响所有规则 | 单条规则独立生效 |
| **存放路径** | `config/itemdespawntowhat/server.json` | `config/itemdespawntowhat/rules/` 或数据包 |
| **生效方式** | **重启游戏/服务器** | 执行 `/idtw rule reload` 立即生效 |

---

## 默认配置参考

以下为模组首次生成的默认配置内容：

<!-- config-reference:defaults:begin -->
```json
{
  "check_interval_ticks": 20,
  "backoff_max_ticks": 100,
  "max_checks_per_tick": 512,
  "server_budget_us": 10000,
  "effects_work_units_per_tick": 64,
  "check_spread_window_ticks": 20,
  "dispatch_batch_size": 64,
  "rescan_entities_per_step": 16,
  "cancels_per_step": 1,
  "prepare_groups_per_step": 8,
  "settlement_recovery_max_attempts": 3,
  "fabric_lifespan_fallback_ticks": 6000,
  "new_product_protection_seconds": 2,
  "conversion_cooldown_seconds": 5,
  "position_search_checks_per_tick": 16,
  "max_place_block_radius": 8,
  "max_effect_offset_x": 6,
  "max_effect_offset_y": 6,
  "max_effect_offset_z": 6,
  "debug_logging": false,
  "debug_scenario_prepare_batch_size": 128,
  "debug_scenario_prepare_budget_us": 2000,
  "overlay_directory": "itemdespawntowhat",
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
<!-- config-reference:defaults:end -->

---

## 核心参数详解

时间字段的单位见表；游戏正常运行时，20 刻 = 1 秒。

<!-- config-reference:fields:begin -->
| 字段 | 作用 | 类型 / 范围 | 缺省值 | 什么时候需要改 |
| --- | --- | --- | --- | --- |
| `check_interval_ticks` | 条件暂时不满足时，隔多久再检查一次；第一次重试用这个间隔，之后每次翻倍 | 整数 [1, 1200] 刻 | `20` | 希望条件一满足就尽快转化就调小；掉落物很多、想减少检查次数就调大 |
| `backoff_max_ticks` | 上面那个翻倍的上限：条件长期不满足时，最多等这么久再检查一次 | 整数 [1, 72000] 刻 | `100` | 调小可让过一会儿就会满足的规则更快再次尝试；调大可进一步省性能，但反应更迟钝 |
| `max_checks_per_tick` | 每刻检查次数上限，但只在 `max_work_units_per_tick` 没写时用得上 | 整数 [1, 100000] | `512` | 只在明确要控制每刻检查次数上限时才动，通常不用改 |
| `server_budget_us` | 每个游戏刻最多花多少时间处理本模组的工作（微秒）；一次做不完的部分顺延到之后几刻继续 | 整数 [100, 100000] 微秒 | `10000` | 服务器吃紧、想更保险就调小；转化延迟明显、服务器还有余力就调大 |
| `max_work_units_per_tick` | 每刻处理量总上限，与 `server_budget_us` 同时生效，先到哪个算哪个 | 整数 [1, 100000] | 不填写时跟随 `max_checks_per_tick` | 通常保持不写；只有需要单独设定每刻处理量总上限时才显式填写 |
| `effects_work_units_per_tick` | 每刻专门留给执行效果的额度上限（生成物品、放置方块、给经验等），避免大批产出把检查工作挤掉 | 整数 [1, 100000] | `64` | 一次要产出大量东西、却迟迟交不出来时调大；想让检查更及时就调小 |
| `check_spread_window_ticks` | 大量掉落物同时到期时，把这些检查摊到这么多个游戏刻里完成 | 整数 [1, 1200] 刻 | `20` | 一批东西同时到期造成卡顿时调大（更平缓，但个别转化会稍晚一点）；希望转化尽量准时、单刻压力可接受就调小 |
| `dispatch_batch_size` | 每刻最多推进多少条排队中的转化，也限制一轮返还交付的数量 | 整数 [1, 100000] | `64` | 大批量转化处理不过来、积压明显时调大 |
| `rescan_entities_per_step` | 启动、维度加载与规则重载后重建追踪时，每一步最多处理多少个已加载掉落物；剩余部分留到之后几刻继续 | 整数 [1, 4096] | `16` | 上万掉落物的服务器在启动或重载后仍感觉单刻卡顿就调小；想更快完成重建就调大，但单刻负担随之上升 |
| `cancels_per_step` | 规则重载、维度加载卸载与停服前摘除任务时，每一步最多取消多少个排队任务；剩余部分留到之后几刻继续 | 整数 [1, 4096] | `1` | 规则重载或维度卸载后单刻出现取消尖峰就保持或调小；希望旧队列更快清理完可以调大，但单刻取消回调随之变多 |
| `prepare_groups_per_step` | 结算的准备阶段每步最多为多少组转化固定效果输入（例如随机产出的具体物品与落点），把整堆的准备工作摊到多个游戏刻 | 整数 [1, 4096] | `8` | 大批转化同时开始、单刻准备造成卡顿时调小；整堆很大、希望更快开始产出时调大，但单刻负担随之上升 |
| `settlement_recovery_max_attempts` | 运行期结算中途失败时，本次服务器运行内最多重试几次；超过后保留账本，等下次启动扫描再恢复 | 整数 [1, 100] | `3` | 某个维度长期不可用、希望直接交给下次重启处理时调小；只有失败原因会在几秒内自行消失时才需要调大 |
| `fabric_lifespan_fallback_ticks` | 仅 Fabric 端：当前服务端尚未采到掉落物参考寿命时，使用的自然寿命刻数 | 整数 [1, 72000] 刻 | `6000` | 默认对应原版掉落物的自然消失时间；只有服务器改过掉落物寿命规则时才需要调整 |
| `new_product_protection_seconds` | 刚转化出来的产物（掉落物、生物与其他实体）在多少秒内不会被火、岩浆、仙人掌销毁 | 整数 [0, 3600] 秒 | `2` | 产物常常一出现就被火销毁（例如在岩浆旁转化）就调大；想让产物立刻可被销毁就设 `0` |
| `conversion_cooldown_seconds` | 刚转化出来的物品在多少秒内不会再次启动转化 | 整数 [0, 3600] 秒 | `5` | 出现产物立刻又被另一条规则转化的连锁时调大；想让产物马上能继续参与转化就设 `0` |
| `position_search_checks_per_tick` | 每刻最多尝试多少个候选位置，用来给产物或返还物找落点 | 整数 [1, 4096] | `16` | 出现东西一直没交出来、落点难找（地形拥挤）时调大；想省性能可调小 |
| `max_place_block_radius` | 单条规则里 `place_block` 的 `radius` 允许的最大值；超过的规则在加载与保存时被拒载 | 整数 [1, 2147483647] | `8` | 想让一条规则一次铺得更远就调大，但单次放置开销按半径的立方增长，不要调得过大；调小后超出新上限的既有规则会被拒载，需要同时把规则里的 `radius` 改小 |
| `max_effect_offset_x` | 单个行为 X 轴触发点偏移的绝对值上限，单位为整格，默认 6；0 将该轴固定为零 | 整数 [0, 2147483647] | `6` | 调整后重新打开编辑器；加载和保存均按当前上限校验，超限规则需改小偏移 |
| `max_effect_offset_y` | 单个行为 Y 轴触发点偏移的绝对值上限，单位为整格，默认 6；0 将该轴固定为零 | 整数 [0, 2147483647] | `6` | 调整后重新打开编辑器；加载和保存均按当前上限校验，超限规则需改小偏移 |
| `max_effect_offset_z` | 单个行为 Z 轴触发点偏移的绝对值上限，单位为整格，默认 6；0 将该轴固定为零 | 整数 [0, 2147483647] | `6` | 调整后重新打开编辑器；加载和保存均按当前上限校验，超限规则需改小偏移 |
| `debug_logging` | 是否把运行过程写进日志（例如某个掉落物条件未满足、多少刻后重试） | 布尔 | `false` | 排查为什么没转化时临时设为 `true`；平时保持 `false`，一直打开会刷出大量日志 |
| `debug_scenario_prepare_batch_size` | 只影响开发环境测试场景的准备阶段，与正常游玩无关 | 整数 [1, 100000] | `128` | 普通玩家不用改 |
| `debug_scenario_prepare_budget_us` | 同上，准备阶段的时间上限（微秒） | 整数 [1, 100000] 微秒 | `2000` | 普通玩家不用改 |
| `overlay_directory` | 规则覆盖层目录名，相对 `config/`，即规则放在 `config/<这个值>/rules/` | 字符串 | `"itemdespawntowhat"` | 只有想把覆盖层目录改名时才动；改完要把整个目录一起搬过去，否则覆盖层规则会读不到（`server.json` 自身位置不受影响） |
| `nearby_products` | 产物邻近上限：按附近已有的产物数量与每组声明的产出折算这批能开出几组，详见下一节 | 对象 | 见子字段 | 产物堆积过多导致卡顿时调低；想解除数量限制时把对应上限设 `0` |
<!-- config-reference:fields:end -->

---

## 产物邻近数量保护（nearby_products）

当一个区域内的产物堆积过多时，模组会主动暂停该区域新的产物生成，未被转化的源物品会自动返还给地面，防止刷怪塔或自动化设施造成严重掉 TPS。

<!-- config-reference:nearby-products:begin -->
| 字段 | 作用 | 类型 / 范围 | 缺省值 |
| --- | --- | --- | --- |
| `item_limit` | 附近同类物品的总件数上限（按要产出的物品或物品标签统计件数） | 整数 [0, 1000000] | `1024` |
| `item_radius` | 上一项的统计半径（方块）：以行为产出原点所在方块为中心的立方体，边长 = 2 × 半径 + 1 格 | 整数 [1, 32] | `6` |
| `entity_limit` | 附近同类实体的个数上限（按要产出的实体产物或实体标签统计） | 整数 [0, 1000000] | `128` |
| `entity_radius` | 实体统计半径（方块），含义同上 | 整数 [1, 32] | `6` |
| `experience_orb_limit` | 保留字段：只保留在配置文件里，不再影响任何转化 | 整数 [0, 1000000] | `64` |
| `experience_radius` | 保留字段：只保留在配置文件里，不对应任何实际统计半径 | 整数 [1, 32] | `6` |
<!-- config-reference:nearby-products:end -->

*提示：将 `item_limit` 或 `entity_limit` 设为 `0` 可以解除对应的邻近数量限制。*

---

## 常见调优场景示例

以下片段只需按需加入 `config/itemdespawntowhat/server.json` 中即可，未出现的字段会自动保持默认值。**改完请重启服务端或游戏生效**。

### 场景 1：低配服务器或刷怪塔掉落物极多，优先保障流畅度

调小单刻处理预算并拉长重试间隔，平缓消耗计算量：

```json
{
  "server_budget_us": 2000,
  "check_interval_ticks": 40,
  "backoff_max_ticks": 200,
  "check_spread_window_ticks": 40,
  "dispatch_batch_size": 32
}
```

### 场景 2：希望环境变化时规则能更快响应

调小检查间隔，环境满足时更快触发：

```json
{
  "check_interval_ticks": 10,
  "backoff_max_ticks": 40
}
```

### 场景 3：防止物品在岩浆或火焰旁生成时立刻被烧毁

将新产物保护时间调大（例如 10 秒）：

```json
{
  "new_product_protection_seconds": 10
}
```

### 场景 4：防止产物立刻被另一条规则无休止转化（死循环连锁）

调大转化冷却时间：

```json
{
  "conversion_cooldown_seconds": 15
}
```

---

## 相关指引

- [首次使用与生效流程](zh-01-getting-started)：文件位置与快速开始。
- [规则基础字段](zh-02-rule-basics)：规则属性与触发时机设定。
- [命令参考](zh-08-commands)：查阅全部在服命令。
