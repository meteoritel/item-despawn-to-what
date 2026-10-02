# ItemDespawnToWhat

让掉落物在自然消失前，按规则变成物品、实体、方块、经验或世界效果。

支持 Minecraft **1.21.1 / Fabric / NeoForge**，开发与构建使用 **JDK 21**。

后端已切换到统一的 `core/**` 规则链路，旧配置模型、执行器、网络与编辑器已退役。**当前 GUI 是占位页**，可由配置快捷键或 `/idtw config edit` 打开；完整前端编辑器将在下一轮重构。目前通过 JSON 与命令管理规则。

## 开始使用

1. 启动一次游戏或服务器，生成 `config/itemdespawntowhat/server.json`。
2. 在 `config/itemdespawntowhat/rules/` 下新建 JSON；也可用数据包的 `data/<命名空间>/idtw/rules/**.json` 提供只读规则。
3. 执行 `/idtw config validate` 查看错误，再执行 `/idtw config reload` 应用规则。

例如，鸡肉存活 10 秒后变为腐肉：

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "source": { "items": ["minecraft:chicken"] },
  "trigger_after_seconds": 10,
  "effects": [
    { "type": "itemdespawntowhat:spawn_item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

未声明 `consume_*` 时，每轮默认消耗一个源物品，按整堆数量展开轮次。规则命中后只提交一次；多个效果按列表顺序启动，大量产出分批完成。条件不满足时退避重试，计时依据实际存活年龄。

规则优先级为 **config 覆盖层 > 世界数据包 > 内置数据包**。同 id 覆盖；不同 id 的候选按优先级、条件叶数与定义顺序选择，最终执行一条。内置 6 个示例文件均默认停用，不自动改变世界行为。

## 命令

单人世界或具有 OP 等级 2 的玩家可使用：

| 命令 | 用途 |
|---|---|
| `/idtw config validate` | 校验所有来源、类型参数与当前服务端引用 |
| `/idtw config reload` | 重载规则并回扫已加载掉落物 |
| `/idtw config list` | 查看规则来源与启用状态 |
| `/idtw config convert` | 备份并显式转换旧配置，报告无法映射的条目 |
| `/idtw config edit` | 打开前端占位页 |
| `/idtw rule list`、`/idtw rule show <id>` | 查询规则 |
| `/idtw debug inspect`、`why`、`biome`、`stats` | 检查掉落物、条件与队列积压 |

旧 JSON 保留在原目录，但运行时不再加载。迁移前请备份配置与存档，并阅读[迁移指南](docs/dev/migration-guide.md)。

## 开发与验收

```powershell
.\gradlew.bat build
```

- [配置字段与运行边界](docs/dev/config-reference.md)
- [架构与数据流](docs/dev/architecture.md)
- [新增效果、条件与第三方 SPI](docs/dev/extension-guide.md)
- [后端收尾记录与游戏验收步骤](docs/review/backend-rewrite-closeout-2026-10-02.md)
- [重构规划与历史阶段记录](docs/plan/plan-backend-rewrite.md)

构建通过与游戏行为验收是两项独立结论。双平台死亡掉落、自然消失、区块边界、重载及 1 万掉落物性能需要按收尾记录在游戏内验证。
