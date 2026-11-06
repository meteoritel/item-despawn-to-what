# 条件（conditions）

> 适用版本：**2.0.0**（Minecraft **1.21.1**）。

本页介绍如何使用 `conditions` 限制转化规则的触发环境。例如：让苹果只在夜晚腐化、让冰块只在炎热沙漠融化、或者让仪式只在雷暴天气下举行。

---

## 条件的核心概念

- **门槛作用**：条件仅用于判断「当前环境是否符合转化要求」，**不会消耗任何物品或流体**。真正扣除材料的是规则的成本配置（见 [候选结果与结算](zh-05-outcomes-and-settlement)）。
- **无条件触发**：如果希望规则在任何环境下都能触发，**直接省略 `conditions` 字段**即可。
- **自动重试机制**：如果到达触发时间但条件不满足（例如等待雨天但当前是晴天），模组**不会销毁物品**，而是会每隔一段时间自动重新检测，直到环境满足条件或物品自然消失。

---

## 条件树结构

在 JSON 中，`conditions` 是一棵由逻辑节点组成的**条件树**。每个节点通过 `op` 声明自己的逻辑类型：

| `op` 类型 | 逻辑含义 | 适用场景 | 子节点字段名 |
| --- | --- | --- | --- |
| `leaf` | **具体条件叶** | 包装一个实际的条件判定对象 | `condition` |
| `all_of` | **逻辑与（并且）** | 列表中**所有条件全部满足**时才算成立 | `terms`（数组） |
| `any_of` | **逻辑或（或者）** | 列表中**只要任一条件满足**就算成立 | `terms`（数组） |
| `inverted` | **逻辑非（取反）** | 将子条件的结果反转（如「不是雷雨天」） | `term`（单个节点） |

### 1. 单个条件（leaf）

每个 `leaf` 包含一个具体的 `condition` 对象。`type` 必须以 `idtw:` 开头：

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:overworld"]
  }
}
```

### 2. 多条件组合（all_of、any_of、inverted）

你可以自由嵌套组合。例如表示**「处于露天环境，并且不是雷暴天气」**：

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "leaf",
      "condition": { "type": "idtw:outdoor" }
    },
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "idtw:weather", "weather": "thunder" }
      }
    }
  ]
}
```

### 3. 条件规模上限

为保证服务器流畅运行，单条规则的条件树有以下上限限制：
- 最多 **128** 个条件叶（`leaf`）；
- 最多 **256** 个总节点（所有 `leaf` 与组合节点之和）；
- 条件树最大深度为 **16** 层。

---

## 10 种可用条件类型

所有条件类型的 `type` 均带有 `idtw:` 命名空间前缀。引用物品、方块、流体、群系时，均支持 `#` 开头的标签写法。

| 类型 ID | 中文名称 | 核心判定内容 |
| --- | --- | --- |
| `idtw:dimension` | 维度 | 检查掉落物是否位于指定维度列表中 |
| `idtw:biome` | 生物群系 | 精确匹配生物群系或匹配原版气候参数（温度/湿度等） |
| `idtw:time_of_day` | 时间 | 检查当前世界时间是否在指定刻数区间（支持跨昼夜） |
| `idtw:weather` | 天气 | 检查世界天气（晴天 `clear`、降雨 `rain`、雷暴 `thunder`） |
| `idtw:light_level` | 光照等级 | 检查掉落物所在方块的光照值区间（0 ~ 15） |
| `idtw:y_level` | 高度（Y 轴） | 检查所在高度是否在指定区间内（可只设上限或下限） |
| `idtw:outdoor` | 露天 | 检查掉落物正上方是否无遮挡直达天空 |
| `idtw:surrounding_blocks` | 周围方块 | 检查上下前后左右相邻方块是否匹配指定方块 |
| `idtw:fluid_present` | 存在流体 | 检查所在位置是否存在指定流体（如水、岩浆） |
| `idtw:catalyst_present` | 存在催化剂 | 检查指定范围内是否有足够数量的催化剂掉落物 |

---

## 详细参数与使用示例

### 1. dimension（维度）

检查掉落物所在维度。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:the_nether"]
  }
}
```
*仅在下界生效。*

### 2. biome（生物群系）

支持两种模式：
- `mode: "exact"`：按群系 ID 或 `#` 标签匹配；
- `mode: "climate"`：按温度、湿度等气候参数区间匹配。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_forest"]
  }
}
```
*仅在森林类群系中生效。*

### 3. time_of_day（世界时间）

Minecraft 一天为 24000 刻（清晨 0，正午 6000，日落 12000，午夜 18000）。区间包含首尾：

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:time_of_day",
    "from": 13000,
    "to": 23000
  }
}
```
*仅在夜晚（13000 ~ 23000）生效。若 `from` 大于 `to`（如 `22000` 到 `2000`），表示跨越零点的午夜区间。*

### 4. weather（天气）

检查维度当前天气，可选值：`clear`（晴朗）、`rain`（降雨/下雪）、`thunder`（雷暴）。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:weather",
    "weather": "rain"
  }
}
```

### 5. light_level（光照等级）与 y_level（高度）

- `light_level`：取值 0 ~ 15，包含 `min` 和 `max`。
- `y_level`：世界高度，包含 `min` 和 `max`（可只写其中一个）。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:light_level",
    "min": 0,
    "max": 4
  }
}
```
*仅在昏暗环境中（光照 ≤ 4）生效。*

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:y_level",
    "min": 64
  }
}
```
*仅在地表或高空（Y ≥ 64）生效。*

### 6. outdoor（露天）

无额外参数。掉落物正上方直到建筑限高均无不透明方块遮挡时成立。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:outdoor"
  }
}
```

### 7. surrounding_blocks（周围方块）

检查物品六个相邻方向（`up`、`down`、`north`、`south`、`east`、`west`）上的方块。支持方块 ID 或标签。未指定的方向不作限制。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:surrounding_blocks",
    "down": "#minecraft:dirt"
  }
}
```
*仅当掉落物下方是泥土类方块时生效。*

### 8. fluid_present（存在流体）

检查物品当前方块或脚下是否有指定流体。`fluid` 填单个流体，`fluids` 填数组。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:fluid_present",
    "fluid": "minecraft:water"
  }
}
```

### 9. catalyst_present（存在催化剂掉落物）

检查附近地面上是否存在辅助转化的掉落物。
- `items`：催化剂物品 ID 或标签；
- `count`：所需数量。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:catalyst_present",
    "items": ["minecraft:redstone"],
    "count": 4
  }
}
```
*仅当周围地面上有至少 4 个红石粉掉落物时生效。*

---

## 完整规则应用范例

下面是一条**「只有在雨夜且露天的森林群系中，苹果才会变成附魔金苹果」**的完整配置：

```json
{
  "id": "myrules:mystic_apple",
  "display_name": "神秘苹果转化",
  "source": { "items": ["minecraft:apple"] },
  "trigger_after_seconds": 60,
  "conditions": {
    "op": "all_of",
    "terms": [
      {
        "op": "leaf",
        "condition": { "type": "idtw:outdoor" }
      },
      {
        "op": "leaf",
        "condition": { "type": "idtw:weather", "weather": "rain" }
      },
      {
        "op": "leaf",
        "condition": { "type": "idtw:time_of_day", "from": 13000, "to": 23000 }
      },
      {
        "op": "leaf",
        "condition": {
          "type": "idtw:biome",
          "mode": "exact",
          "biomes": ["#minecraft:is_forest"]
        }
      }
    ]
  },
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "variant": "item",
      "item": "minecraft:enchanted_golden_apple",
      "count": 1
    }
  ]
}
```

---

## 下一步

- [效果](zh-04-effects)：配置物品、生物、方块、箭雨、爆炸与天气演出效果。
- [候选结果与结算](zh-05-outcomes-and-settlement)：多产物轮换、消耗催化剂与整组返还。
