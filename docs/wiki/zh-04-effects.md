# 效果（执行行为）

> 适用版本：**2.0.0**（Minecraft **1.21.1**）。

本页介绍规则触发后可以执行的**效果**（在编辑器中称为「执行行为」）。当掉落物满足触发条件后，模组会依次执行你配置的效果列表。

---

## 效果类型一览

每个效果在 JSON 中都是一个独立对象，`type` 决定其行为类型：

| 效果类型 | `type` | 是否一次性演出 | 游戏内效果简述 |
| --- | --- | --- | --- |
| **实体产出** | `idtw:spawn_entity` | 否（按组生成） | 掉落物品、召唤生物、生成载具/实体或释放经验球 |
| **箭雨** | `idtw:arrow_rain` | 否（按组结算） | 在触发位置上方倾泻一阵箭矢（可附带药水效果） |
| **放置方块** | `idtw:place_block` | 否（按组摆放） | 在触发位置周围摆放方块（如树苗落地扎根、水结成冰） |
| **生成战利品** | `idtw:loot_table` | 否（按组开启） | 按照游戏战利品表（如地牢宝箱）生成随机掉落物 |
| **降下闪电** | `idtw:lightning` | **是**（一次性） | 在触发位置引下 1 ~ 16 道真实或视觉闪电 |
| **引发爆炸** | `idtw:explosion` | **是**（一次性） | 在触发位置产生指定威力的爆炸（可控是否破坏方块） |
| **改变天气** | `idtw:weather` | **是**（一次性） | 将当前世界天气转为晴天、降雨或雷暴并持续一段时间 |

*注：闪电、爆炸和天气属于「一次性演出效果」，即使一次性转化了 64 个物品，也只会表演一场，防止瞬间产生数十次爆炸或闪电导致服务器卡顿。*

---

## 通用效果字段

所有效果都可以配置以下 3 个通用字段（`type` 必填，其余可选）：

| 字段名 | 作用说明 | 取值范围 | 默认值 |
| --- | --- | --- | --- |
| `type` | 效果的唯一类型 ID | `idtw:` 开头的效果标识符 | **必填** |
| `delay_ticks` | 规则触发后，等待多少游戏刻再执行本效果 | 非负整数（20 刻 = 1 秒） | `0`（立即执行） |
| `chance` | 本效果执行时的成功概率 | `0.0` 到 `1.0` 的浮点数 | `1.0`（100% 触发） |

```json
{
  "type": "idtw:lightning",
  "delay_ticks": 40,
  "chance": 0.5
}
```
*上例表示：触发 2 秒（40 刻）后，有 50% 的几率引下闪电。*

---

## 7 大效果详解与范例

### 1. 实体产出（spawn_entity）

通过 `variant` 区分产出子类型：`item`（物品）、`mob`（生物）、`entity`（其他实体）、`experience`（经验）。

#### A. 产出物品（`variant: "item"`）
- `item`：物品 ID 或标签（填标签时随机挑选一个成员）；
- `count`：生成数量（1 ~ 64）。

```json
{
  "type": "idtw:spawn_entity",
  "variant": "item",
  "item": "minecraft:diamond",
  "count": 1
}
```
*特性说明*：如果源物品在销毁前正在飞行（例如玩家丢出），生成的产物会自带轻微的反向初速度，**朝抛出方向回飞**，更方便玩家顺手捡回。

#### B. 召唤生物（`variant: "mob"`）
- `entity`：生物类型 ID 或标签；
- `count`：生成数量（1 ~ 64）；
- `age`：幼年/成年年龄（-24000 为刚出生幼年，0 为成年）。

```json
{
  "type": "idtw:spawn_entity",
  "variant": "mob",
  "entity": "minecraft:cow",
  "count": 1,
  "age": -24000
}
```
*安全生成保护*：默认启用安全寻找落脚点机制，自动在周围寻找平整地面，避免生物直接卡进墙壁窒息或掉入岩浆。

#### C. 生成载具与其他实体（`variant: "entity"`）
- 用于矿车、船等载具实体。

```json
{
  "type": "idtw:spawn_entity",
  "variant": "entity",
  "entity": "minecraft:minecart",
  "count": 1
}
```

#### D. 释放经验球（`variant: "experience"`）
- `amount`：经验点数（1 ~ 10000）。

```json
{
  "type": "idtw:spawn_entity",
  "variant": "experience",
  "amount": 20
}
```

---

### 2. 放置方块（place_block）

在物品附近将方块摆放到世界中。

| 字段 | 说明 | 取值范围 | 默认值 |
| --- | --- | --- | --- |
| `block` | 要放置的方块 ID 或标签 | 方块 ID/标签 | 与源物品方块相同（需开启 `use_source_block`） |
| `use_source_block` | 是否直接使用源物品对应的方块 | `true` / `false` | `false` |
| `shape` | 摆放区域形状 | `circle`（圆形）、`square`（方形）、`cube`（立方体） | `circle` |
| `radius` | 摆放半径 | 1 ~ 8 | `1` |
| `count` | 摆放数量 | 1 ~ 64 | `1` |

```json
{
  "type": "idtw:place_block",
  "block": "minecraft:ice",
  "shape": "circle",
  "radius": 2,
  "count": 4
}
```
*在周围 2 格圆形范围内随机放置最多 4 块冰方块。*

---

### 3. 生成战利品（loot_table）

按照 Minecraft 原版的战利品表随机爆出一批物资。

- `table`：战利品表资源 ID；
- `seed`：随机种子（可选，省略表示真随机）。

```json
{
  "type": "idtw:loot_table",
  "table": "minecraft:chests/simple_dungeon"
}
```
*按地牢宝箱的掉落规则爆出随机物资。*

---

### 4. 降下闪电（lightning）

- `visual_only`：是否仅为视觉闪电（为 `true` 时不造成伤害、不点火）；
- `count`：闪电道数（1 ~ 16）。

```json
{
  "type": "idtw:lightning",
  "visual_only": false,
  "count": 1
}
```

---

### 5. 引发爆炸（explosion）

- `radius`：爆炸威力半径（0.0 ~ 32.0，TNT 威力为 4.0）；
- `causes_fire`：是否在地面引燃火苗；
- `block_interaction`：方块破坏模式：`keep`（不破坏方块）、`destroy`（破坏方块并掉落）、`trigger`（破坏并触发连锁）。

```json
{
  "type": "idtw:explosion",
  "radius": 3.0,
  "causes_fire": false,
  "block_interaction": "keep"
}
```
*在原地产生一次威力为 3 的爆炸，击退周围生物但不会炸坏地形。*

---

### 6. 改变天气（weather）

- `weather`：目标天气，支持 `clear`（晴朗）、`rain`（降雨）、`thunder`（雷暴）；
- `duration_ticks`：天气持续刻数（例如 6000 刻 = 5 分钟）。

```json
{
  "type": "idtw:weather",
  "weather": "clear",
  "duration_ticks": 12000
}
```

---

### 7. 箭雨（arrow_rain）

从触发位置上方落下一阵箭矢。

- `count`：箭矢总数（1 ~ 128）；
- `pickup`：玩家拾取权限：`allowed`（允许拾取）、`disallowed`（不可拾取）、`creative_only`（仅创造模式可拾取）；
- `potion_effects`：附带药水效果列表（可选）。

```json
{
  "type": "idtw:arrow_rain",
  "count": 16,
  "pickup": "disallowed"
}
```

---

## 防卡顿与安全保护机制

为了保障服务器性能和良好的游玩体验，模组内置了以下保护机制：
1. **防卡顿分刻投递**：一次性转化大堆物品时，产物会自动分批次在几个游戏刻内陆续生成，防止瞬间卡服；
2. **生物安全生成**：召唤生物时优先寻找不会掉落虚空或岩浆的平坦地面；
3. **转化冷却保护**：刚转化出来的产物默认拥有数秒的保护时间，不会刚生成就立即被火烧毁或再次被其他规则二次转化。

---

## 下一步

- [候选结果与结算](zh-05-outcomes-and-settlement)：多产物轮换、消耗催化剂与整组返还机制。
- [规则基础字段](zh-02-rule-basics)：顶层配置与触发时机设定。
