# ItemDespawnToWhat

让掉落物在**自然消失前**，或被**火 / 岩浆 / 仙人掌销毁**时，按规则变成物品、实体、方块、经验或世界效果。

支持 Minecraft **1.21.1 / Fabric / NeoForge**，开发与构建使用 **JDK 21**。

后端已切换到统一的 `core/**` 规则链路，旧配置模型、执行器、网络与编辑器已退役。**规则编辑器已落地**：`/idtw config edit` 在服务端取得独占编辑锁后打开，支持浏览 / 新建 / 复制 / 编辑 / 停用 / 删除 / 恢复规则、递归条件树、草稿持久化与撤销重做；也可以直接用 JSON 与命令管理规则。

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

规则可用 `triggers` 声明触发方式（缺省仅 `natural` 自然消失；也可加 `fire` / `lava` / `cactus`，被对应方式销毁时即时触发）。命中后按**固定源成本整组结算**：每组消耗 `source_cost` 个源物品（未声明时为 1），产出按“完整组”展开，输入不足一组时剩余源物品返还；一组内多个效果共同执行，也可用 `outcomes` 声明多个候选结果、按 `combination`（`round_robin` 轮询为默认 / `priority` 优先）选取。未声明 `consume_*` 时每轮默认消耗一个源物品。条件不满足时退避重试，计时依据实际存活年龄。

规则优先级为 **config 覆盖层 > 世界数据包 > 内置数据包**。同 id 覆盖；不同 id 的候选按优先级、条件叶数与定义顺序选择，最终执行一条。内置数据包含 **8 个示例文件（共 10 条规则）**，其中若干条默认启用以便直接观察效果（如鸡肉 300 秒后变腐肉）；如需纯净环境，可在 `config` 覆盖层停用或移除内置数据包。

## 命令

单人世界或具有 OP 等级 2 的玩家可使用：

| 命令 | 用途 |
|---|---|
| `/idtw config validate` | 校验所有来源、类型参数与当前服务端引用 |
| `/idtw config reload` | 重载规则并回扫已加载掉落物 |
| `/idtw config list` | 查看规则来源与启用状态 |
| ~~`/idtw config convert`~~（已退役 2026-10-03） | 旧 v1.2.1 配置不再自动迁移；用 `/idtw config edit` 手工重建，见 [破坏性更新说明](docs/guide/update-notes.md) |
| `/idtw config edit` | 打开规则编辑器（服务端取独占编辑锁，需权限等级 2） |
| `/idtw rule list`、`/idtw rule show <id>` | 查询规则 |
| `/idtw debug run <场景>` | 开发环境自动准备真实后端功能场景，过程与校对结果实时输出到 IDEA |
| `/idtw debug bench baseline [秒数]` | 开发环境采集当前世界的无新增负载基线 |
| `/idtw debug bench convert\|retry [实体数] [秒数]` | 开发环境自动创建转化/失败重试负载，默认1000实体、60秒 |
| `/idtw debug mark <现象>`、`status`、`stop` | 标记肉眼观察、查看场景进度、停止并清理本轮负载 |

Debug由加载器的开发环境判断自动启用，IDEA Run/Debug均可使用，发布环境不注册debug指令。执行 `/idtw debug run convert` 即可生成测试源并走真实后端；在 IDEA 控制台过滤 `[IDTW_DEBUG]` 或回执的 `run=<编号>`，校对 START 参数、过程事件、FRAME 和 END 的预期/实际。无需手写测试配置或开启 `debug_logging`。功能场景、性能对比流程和AI反馈模板见 [Debug 实机验证与反馈指南](docs/dev/debug-validation-guide.md)。

旧 JSON 保留在原目录，但运行时不再加载。旧 v1.2.1 配置**不再自动迁移**（`/idtw config convert` 已于 2026-10-03 按 P8 结论退役），迁移前请备份配置与存档，改用 `/idtw config edit` 手工重建，见[破坏性更新说明](docs/guide/update-notes.md)；字段与运行边界见[后端开发者文档地图](docs/dev/backend/README.md)。

## 开发与验收

```powershell
powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"
```

构建脚本内部有命名互斥体，多个构建会排队；不要裸跑 `gradlew.bat`，以免与其他任务并行时争抢同一 `build` 目录。

- [后端开发者文档地图](docs/dev/backend/README.md)（功能模块 / 横向系统 / 纵向系统）
- [Debug 实机验证与反馈指南](docs/dev/debug-validation-guide.md)
- [后端收尾记录与游戏验收步骤](docs/review/backend-rewrite-closeout-2026-10-02.md)
- [前端重构契约与表单规格](docs/plan/plan-frontend-rewrite-contract.md)（现行权威契约）

构建通过与游戏行为验收是两项独立结论。双平台死亡掉落、自然消失、区块边界、重载及 1 万掉落物性能需要按收尾记录在游戏内验证。
