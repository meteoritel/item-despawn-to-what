# 实体产物与共用生成阈值

2026-10-07 开发期新格式。原 `spawn_item`、`spawn_xp` 及不含 variant 的旧 `spawn_entity` 不再支持；本轮不提供迁移。项目内置规则/模板和开发运行目录样例已重写。

## 规则中的三个子类

动作类型统一为 `itemdespawntowhat:spawn_entity`。专用字段与 type 同级，不使用 product 包装，不混用其他子类字段。

```json
{
  "id": "example:chicken_to_rotten_flesh",
  "source": {"items": ["minecraft:chicken"]},
  "trigger_after_seconds": 1,
  "effects": [
    {"type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1}
  ]
}
```

以下分别是可以放进 effects 的另外两种动作：

```json
{"type":"itemdespawntowhat:spawn_entity","variant":"entity","entity":"minecraft:chicken","count":1,"age":-24000}
```

```json
{"type":"itemdespawntowhat:spawn_entity","variant":"experience","amount":5,"per_source_item":true}
```

| 子类 | 数量单位 | 参数约束 |
|---|---|---|
| item | 物品件数 | item 必填，可用物品 #tag；count 1..64，默认 1 |
| entity | 实体个数 | entity 必填，可用实体 #tag；count 1..64，默认 1；age 为整数，默认 0，仅可成长生物生效 |
| experience | 经验点数 | amount 1..65536，默认 1；per_source_item 默认 false |

通用实体子类支持生物与其他实体；minecraft:item、minecraft:experience_orb 以及含任一类型的标签必须改用专用子类。其他实体的默认状态由实体自身定义；预览不可用不表示禁止生成，也不保证实体在世界中一定能以理想方式显示。

所有子类共享 chance（默认 1）、delay_ticks（默认 0）、conditions（可选条件树）。经验 false 表示每组 amount 点，true 表示 amount × 本组实际消耗源件数；例如每组源成本 3、amount 5，true 为 15 点。最终点数使用原版经验球拆分与合并，不为每个点创建实体。

UI 在“结果”页先选方案、再选动作与子类。切换子类保留概率、延迟、条件并重置专用参数，一次撤销恢复；管理页按所有方案的产出分类，每条规则只出现一次，分类不移动文件。

## 共用服务端配置

生成邻近阈值由所有规则共享，写在 config/itemdespawntowhat/server.json 的 nearby_products 中：

```json
{
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

这是可添加到已有 server.json 的字段片段，保留其它服务器设置。limit 为 0 关闭对应检查；limit 上界 1000000、radius 为 1..32 方块。修改后重启服务器。规则 reload 不重新读取 server.json。

阈值只检查完整组启动：容量不足不支付该组成本，并保留/返还源物品；已开始组完成时可以超过阈值，不会因阈值截断或删除产物。下一组继续检查容量。物品按同类件数、通用实体按同类个数、经验按邻域球实体数统计；经验需求使用原版拆分球数保守估计，真实合并可能减少球数。阈值不覆盖战利品/世界事件间接创建的所有实体，也不保证任意重叠标签形成硬上限。

方块放置的 limit/radius 是玩法范围，继续保留在 place_block 动作中。已有预算、区块门禁、概率、条件与异常中断机制继续生效。

## 需要实测的项目

默认阈值没有性能压测保证；3D 预览使用碰撞尺寸估计及已知原版修正，模组模型仍可能裁剪。无模型/创建或渲染失败使用屏障物品和“预览不可用”。按 [人工验收 §13](manual-acceptance.md#13-本轮-ui-与实体统一验收2026-10-07待用户执行) 分别在 NeoForge 与 Fabric 测试。
