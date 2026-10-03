# 开发环境后端结算示例

在 IDEA 中启动 **Fabric Client (:fabric)** 或 **NeoForge Client (:neoforge)**，进入开启作弊的测试世界。Run 和 Debug 都可以；是否启用由加载器的 development 环境决定。

站在已加载的空旷区域，保持世界正常推进，运行：

```mcfunction
/idtw debug examples
/idtw debug run source_cost
```

`examples` 在聊天栏列出全部功能和性能命令及预期；`run` 的场景名支持 Tab 补全。源、催化剂、产物都自动准备，无需手动扔物品。场景在玩家视线方向约3格处开始；4源场景再向 X 正方向展开3格。

每轮默认测量12秒，结束后自动校对并清理实体，每个服务器同时只能有一轮。低 TPS 时可延长，例如 `/idtw debug run multi_effect 60`；允许10～300秒。计时使用墙钟，规则与效果延迟使用世界 tick，请勿暂停世界或执行 tick freeze。

| 命令（前缀 `/idtw debug run `） | 自动准备 | 预期实物与结算 |
| --- | --- | --- |
| `source_cost` | 12张纸，来源成本3 | 4组，消耗12张纸，4个海晶碎片 |
| `rebate` | 10张纸，来源成本3 | 3组，消耗9张纸，3个海晶碎片，返还1张纸 |
| `insufficient` | 2张纸，来源成本3 | 0组，0产出，完整返还2张纸 |
| `outcome_priority` | 一堆4张纸，两个候选 | 选择 first，4个海晶碎片，0金粒 |
| `round_robin` | 4个独立纸张实体，两个候选 | first/second 各选择2次，海晶碎片和金粒各2个 |
| `multi_effect` | 2张纸，候选含两个效果 | 2个海晶碎片；40 tick延迟后共4个金粒；消耗2张纸 |
| `chance_skip` | 4张纸，效果 chance=0 | 消耗4张纸，0产出，4次 EFFECT_CHANCE_SKIPPED |
| `condition_tree` | 1张纸，all_of/any_of/inverted 条件树 | 正常世界高度下条件通过，1个海晶碎片 |
| `catalyst` | 4张纸、4个绿宝石；每组催化剂成本1 | 4组，4个海晶碎片，绿宝石全部消耗 |
| `catalyst_limited` | 4张纸、2个绿宝石 | 只执行2组，2个海晶碎片，返还2张纸，绿宝石全部消耗 |

催化剂场景会拒绝周围3格已有绿宝石掉落物的区域；请勿在运行时向区域额外扔绿宝石、攻击或手动删除测试实体。测试实体禁止拾取、关闭重力，产物在结束时清理。

## 日志怎么看

IDEA 控制台过滤 `[IDTW_DEBUG]`，再按聊天栏显示的 `run=<UUID>` 筛选同一轮。

1. `START`：完整规则、当前配置、输入规模、`expected`；`rule_source=development-memory`、`builtin_datapack=false` 明确来源。
2. `SOURCE_ADDED` / `CATALYST_ADDED`：实际创建的实体、物品 ID、初始数量。
3. `OUTCOME_PLANNED`：实际选中的候选与组数；`CATALYST_PLANNED`：实际预留件数。
4. `OUTPUT_ADDED`：成功加入世界的物品 ID 与数量；`EFFECT_CHANCE_SKIPPED`：真实概率跳过。
5. `SETTLEMENT_COMPLETED`：真实消耗与返还账目；`RETURN_OBSERVED`：实物返还及永久保护、永久禁转状态。
6. `END`：`checks` 每项包含 `name`、`expected`、`actual`、`pass`，总体 `verdict` 为 PASS、FAIL 或 INCOMPLETE。失败项还单独输出 `CHECK_FAILED`，可直接过滤。

例如 `rebate` 应出现：

```json
{"name":"consumed_sources","expected":9,"actual":9,"pass":true}
{"name":"live_returned_source_items","expected":1,"actual":1,"pass":true}
{"name":"protected_returned_source_items","expected":1,"actual":1,"pass":true}
```

产出类型、实际件数、留存件数、返还状态、候选选择次数、完成结算数和剩余场景效果任务都会交叉检查。`settlement_snapshot.pending_units` 是结算完成事件当时的账目快照；效果可能之后才执行，因此不把该快照当作最终产出断言。最终结果以成功生成的实物及效果队列为准。

FAIL 表示实际与预期不一致，也可能是测量窗口不足；先查看 `CHECK_FAILED`，确认世界没有暂停，再增加秒数重跑。INCOMPLETE 表示手动停止、异常、维度卸载或停服，不算测试通过。

```mcfunction
/idtw debug status
/idtw debug mark 我看到金粒出现了
/idtw debug stop
```

尽量等待 END 再启动下一轮。已有 stop 机制取消登记的检查和效果任务，但后端结算任务独立登记；在结算尚未完成时提前 stop，仍可能留下待返还记录。若要刻意测试这种中断，请使用可丢弃的存档并保留整轮日志。

## 与内置数据包的边界

示例 JSON 位于 `common/src/main/resources/idtw-debug/scenarios/run/`，不位于 `data/<namespace>/...`，不会被数据包管理器加载，也不会出现在普通规则列表。它们不是 builtin rules，也不会写入用户 overlay 或替换全局规则索引。

只有开发环境会注册 debug 命令；统一目录再次检查 development 标志及 manifest 场景白名单。规则仍经过项目真实 Codec、语义校验和 RuleIndex，只对本轮 UUID 绑定的测试源生效，测试催化剂和产物使用空规则索引。发布环境不能触发这些示例，即使发布包中包含 JSON 资源。

本轮真实转换仍使用现有后端持久化结算账本与候选游标，轮询候选首个选择可能受历史游标影响；4个源的两候选总数保持各2次。请在独立测试存档运行，这些示例不承诺清空后端账本历史。

基础与性能场景说明见 [debug-validation-guide.md](debug-validation-guide.md)，统一资源格式和扩展流程见 [debug-scenario-extension.md](debug-scenario-extension.md)。reload 场景已经改为验证延迟效果入队后重载、取消结算及零产出；它会执行真实全局重载，请在独立测试存档运行。
