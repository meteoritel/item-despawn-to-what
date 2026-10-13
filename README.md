# ItemDespawnToWhat

让掉落物在**自然消失前**，或被**火 / 岩浆 / 仙人掌销毁**时，按规则变成物品、实体、方块、经验或世界效果。

支持 Minecraft **1.21.1 / Fabric / NeoForge**，开发与构建使用 **JDK 21**。

后端使用统一的 `core/**` 规则链路。**规则编辑器已落地**：`/idtw config edit` 在服务端取得独占编辑锁后打开，支持浏览 / 新建 / 复制 / 编辑 / 停用 / 删除 / 恢复规则、递归条件树、草稿持久化与撤销重做；也可以直接用 JSON 与命令管理规则。

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
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

规则可用 `triggers` 声明触发方式（缺省仅 `natural` 自然消失；也可加 `fire` / `lava` / `cactus`，被对应方式销毁时即时触发）。命中后按**固定源成本整组结算**：每组消耗 `source_cost` 个源物品（未声明时为 1），产出按“完整组”展开，输入不足一组时剩余源物品返还；一组内多个效果共同执行，也可用 `outcomes` 声明多个候选结果、按 `combination`（`round_robin` 轮询为默认 / `priority` 优先）选取。未声明 `consume_*` 时每轮默认消耗一个源物品。条件不满足时退避重试，计时依据实际存活年龄。

规则优先级为 **config 覆盖层 > 世界数据包 > 内置数据包**。同 id 覆盖；不同 id 的候选按优先级、条件叶数与定义顺序选择，最终执行一条。内置数据包含 **8 个示例文件（共 10 条规则）**，其中若干条默认启用以便直接观察效果（如鸡肉 300 秒后变腐肉）；如需纯净环境，可在 `config` 覆盖层停用或移除内置数据包。
