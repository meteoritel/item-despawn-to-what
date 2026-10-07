# 双平台手动验收清单（Fabric / NeoForge）

> 读者：**不需要懂 JSON** 的验收人（含最终审阅者）与开发回归者。
> 权威依据：[plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md) §3/§5/§7、[plan-frontend-rewrite-forms.md](../plan/plan-frontend-rewrite-forms.md)（逐字段取值域）、[ADR-0018](../adr/0018-condition-tree-contract.md)–[0022](../adr/0022-client-ui-kit-adoption.md)。
> 记录日期：2026-10-03。

## 0. 状态标记与当前可验收范围（先读）

每一步都带一个标记：

| 标记 | 含义 |
|---|---|
| 【现在可验收】 | 当前代码已具备（P1–P8 全部落盘，2026-10-03 终验通过：clean build exit=0、IDE 无错误无警告），按步骤即可看到预期结果 |
| 【开发者】 | 需要调试器或改代码才能稳定复现，普通玩家可跳过 |
| 【待用户执行】 | 代码已落盘并通过 IDEA 静态检查与串行构建（`tools/dsh-build.ps1 -Tasks "build"` exit=0），但**游戏内行为本次未执行**；第 10–13 节属于此类，需用户按步骤验收 |

> 说明：清单中曾用于区分「P5 后（需要编辑界面）」「P6 后（需要草稿/撤销）」的标记已统一为【现在可验收】——这两批能力均已落盘并接线。

**当前关键事实（2026-10-03，验收前务必确认）**：

1. **编辑界面已接线**（2026-10-03 复核）：`RuleEditorOpener.bootstrap()` 已在 Fabric `fabric/src/main/java/com/meteorite/itemdespawntowhat/client/ItemDespawnToWhatClient.java:21` 与 NeoForge `neoforge/src/main/java/com/meteorite/itemdespawntowhat/client/register/RegisterEvent.java:24` 的客户端初始化中调用，内部执行 `EditorScreenHooks.setOpener(...)` 注册单例；`/idtw config edit` 取锁成功后会经 `RuleEditClientWorkspace.onOpen` → `EditorScreenHooks.open(request)` 弹出编辑器。`EditorScreenHooks` 的「未注册」日志分支只在 bootstrap 未被调用的环境（如脱离客户端初始化的调试运行）才可能出现。
2. 快捷键（`common/src/main/java/com/meteorite/itemdespawntowhat/client/key/ModKeyBindings.java`，默认未绑定）由 `common/src/main/java/com/meteorite/itemdespawntowhat/client/key/EditorShortcut.java` 处理：**已有会话**且 `EditorScreenHooks.opener()` 已注册时**只重开界面**（不重复请求授权）；**没有会话时只提示** `gui.itemdespawntowhat.edit.hint.use_command`（"使用 /idtw config edit 开始编辑"），**绝不自行发起会话**；开发环境（`DebugMode.ENABLED`）下无会话时改开 `UiPrototypeScreen` 原型屏（发布环境不存在该分支，不构成绕过锁的入口）。两平台入口：Fabric `fabric/src/main/java/com/meteorite/itemdespawntowhat/client/event/InputEvents.java`、NeoForge `neoforge/src/main/java/com/meteorite/itemdespawntowhat/client/event/InputEvents.java`。旧占位屏 `RuleEditorPlaceholderScreen` **已删除**，全仓零引用。
3. 因此：**第 2 节主链、第 4.2 节界面路径、第 6 节界面检查项现已可执行**（界面已接线；快捷键仍需在 选项 → 控制 手动绑定，见 8.5）。第 1 节（构建/启动）、第 3 节（独占锁与命令，含 3.6 写盘失败与 3.7 重载失败）、第 4.1/4.3 节（数据包规则与文件层、内置样本的游戏内效果）、第 5 节（条件/效果最小用例）、第 7 节（双平台一致性）照旧可完整验收。
4. 完整缺口清单见第 8 节；界面已接线，若按第 2 节验收时界面未弹出，先确认客户端是否完成了平台初始化（bootstrap 调用点见第 1 条），以及是否误走了未绑定的快捷键。

## 1. 前置准备【现在可验收】

### 1.1 构建

```powershell
powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"
```

- 期望：`BUILD SUCCESSFUL`，退出码 0；耗时约 40 秒（脚本内部有命名互斥体，多个构建会排队）。
- **不要裸跑 `gradlew.bat`**：其他任务/agent 可能正在并行构建，直接跑会绕过互斥体。
- 需要 Java 21。产物在每个加载器工程的 `build/libs/` 下（具体文件名以 Gradle 输出为准）。

### 1.2 在 IDEA 里跑客户端

新通知无关：Gradle 工具窗 → `itemDespawnToWhat` → `Tasks` → 对应加载器 → `runClient`，或在 `Run/Debug Configurations` 里加两条 Gradle 配置：

| 平台 | 任务 | 说明 |
|---|---|---|
| Fabric | `:fabric:runClient` | 首次启动需下载依赖，可能数分钟 |
| NeoForge | `:neoforge:runClient` | 同上 |

- 专用服务器（验证服务端权威行为、租约到期时推荐）：`:fabric:runServer` / `:neoforge:runServer`；控制台执行 `op <玩家名>` 授予权限。
- 单人世界：新建创造世界即可；**内置示例规则来自模组自带数据包**，不需要额外装数据包。
- 游戏目录：开发环境各加载器的工作目录（`run/` 下）；覆盖层规则位于 `<游戏目录>/config/itemdespawntowhat/rules/`。

### 1.3 权限与就绪

| 情形 | 行为 |
|---|---|
| 单人存档 | `/idtw config edit` **直接放行**，不需要 op（`RuleEditServerHandler.canEdit`：`server.isSingleplayer() || 权限等级 ≥ 2`） |
| 专用服务器 | 需要权限等级 2：控制台 `op <玩家名>` |
| `/idtw config edit-lock status` | **任何玩家**都可用（无需权限） |
| `/idtw config edit-lock release` | 需要权限等级 2 |
| 运行时未就绪 | 提示 `itemdespawntowhat.edit.runtime_not_ready`（正常开局不会出现） |

### 1.4 观察点

- 客户端日志（`logs/latest.log`）：logger `itemdespawntowhat-client-net` 打印开屏/未注册信息；服务端日志打印规则加载与重载结论。
- 当前"界面未接线"的证据日志就是第 0 节第 1 条那句；它**恰好证明锁已取到、载荷已送达客户端**。

## 2. 操作主链（【现在可验收】）

每步都写"操作 → 预期"，验收时逐条打勾。

### 2.1 打开与关闭

| # | 操作 | 预期 |
|---|---|---|
| 2.1.1 | 聊天栏输入 `/idtw config edit` | 聊天栏回显"已向 %1$s 发送编辑入口。"；编辑器界面打开；服务端无异常日志 |
| 2.1.2 | 再输入一次 `/idtw config edit`（自己已持有） | 仍是同一会话（不报"被他人锁定"），界面正常 |
| 2.1.3 | 按 Esc 关闭界面 | 会话立即释放（见 3.4）；重新 `/idtw config edit` 立刻成功 |
| 2.1.4 | 界面内存在未应用的改动时按 Esc | 应提示"有未应用的草稿"并允许取消（【现在可验收】） |

### 2.2 规则列表

| # | 操作 | 预期 |
|---|---|---|
| 2.2.1 | 打开界面看列表 | 列出全部规则；每行显示 显示名（未设置则 id）、id、来源（`overlay` / `datapack` / `mixed`）、状态（`active` / `disabled` / `masked` / `invalid`）、可编辑标记 |
| 2.2.2 | 观察内置示例规则 | 10 个内置规则条目可见（清单见 4.1）；来源为数据包；显示名为中英对照（如"鸡肉腐化 / Chicken to Rotten Flesh"） |
| 2.2.3 | 搜索框输入"鸡肉" | 命中 `builtin_item_to_item` |
| 2.2.4 | 搜索框输入 "chicken" | 命中同一条（按技术 id 搜索） |
| 2.2.5 | 搜索框输入 "itemdespawntowhat:" | 命中全部本模组规则（按命名空间搜索） |
| 2.2.6 | 刷新/重新打开列表 | 列表内容与状态保持一致，不出现重复条目 |

### 2.3 新建 / 复制 / 编辑 / 停用 / 删除 / 恢复

| # | 操作 | 预期 |
|---|---|---|
| 2.3.1 | 新建：选模板（10 种内置效果及实体三子类的入口） | 新建一条只含该效果的规则草稿，未填必填项时有明确提示 |
| 2.3.2 | 填规则 id、源物品（用**选择器按名字挑**，不手输 id）、效果参数 → 应用 | 回执成功；列表出现新规则；来源 `overlay` |
| 2.3.3 | 复制一条既有规则 | 生成新的草稿（id 需改），原规则不变 |
| 2.3.4 | 编辑：改 `display_name` 与优先级 → 应用 | 列表显示名与排序（优先级 desc）随之变化；id 不变 |
| 2.3.5 | 停用：`enabled=false` → 应用 | 列表状态变 `disabled`，内容仍可见可编辑；游戏内该规则不触发 |
| 2.3.6 | 删除覆盖层规则 → 应用 | 条目从列表消失；覆盖层文件被删除 |
| 2.3.7 | 对**数据包规则**删除/覆盖后再"恢复原始" | 恢复后列表回到数据包原规则，覆盖条目消失（详见 4.2） |

### 2.4 页签与条件树

| # | 操作 | 预期 |
|---|---|---|
| 2.4.1 | 切换 基本信息 / 源物品 / 触发条件 / 效果 页签 | 切换不丢改动；脏标记保留（【现在可验收】显示撤销/重做按钮状态） |
| 2.4.2 | 条件树：添加 `all_of` / `any_of` / `inverted` / 条件叶 | 树按层级缩进显示，可折叠/展开；每层有明确的"全部/任一/非"文字标签（不能只靠颜色或图标） |
| 2.4.3 | 删除某个节点 | 删除后父节点结构仍然合法可显示；空 `all_of`/`any_of` 允许短暂存在（编辑中间态）并显示"未完成"提示 |
| 2.4.4 | 尝试在叶子上找"取反"勾选框 | **不存在**。取反只能用 `inverted` 节点（界面文案"非/NOT"）；草稿 JSON 中不得出现 `negated` 字段 |
| 2.4.5 | 深度超过 6 层 | 显示"层级较深"提示（不阻止编辑） |
| 2.4.6 | 制造未完成的空 `all_of` 并点应用 | 本地拦截，提示定位到该节点（不是笼统失败） |
| 2.4.7 | 制造超限（>128 叶 / >256 节点 / 深度 >16） | 本地拦截并给出字段路径；上限**按逐表达式**计（规则级与每个效果级各自独立） |

### 2.5 效果列表

| # | 操作 | 预期 |
|---|---|---|
| 2.5.1 | 添加/删除/上移/下移效果 | 顺序即执行顺序；界面即时反映 |
| 2.5.2 | 设置 `delay_ticks` = 0 | 显示"立即"；可输入 0..72000 |
| 2.5.3 | 设置 `chance` | 以百分比呈现（0%..100%）；未命中概率时仍按隐式消耗扣源物品（见 5.2 说明） |
| 2.5.4 | 给单个效果加"附加条件" | 该效果级条件树独立成树，不与其他效果或规则级条件混用 |
| 2.5.5 | 依次打开 10 种内置效果及实体三子类表单 | 每种都有可填字段，无空白表单、无异常 |

### 2.6 应用与错误定位

| # | 操作 | 预期 |
|---|---|---|
| 2.6.1 | 点"应用" | 提交变更集；成功后回执为成功（文案来自 messageCode，客户端不解析文案） |
| 2.6.2 | 故意留一个未完成节点再应用 | 校验失败回执 + 问题列表：每条含 规则 id、字段路径（如 `conditions.terms[0].condition.min`）、可读说明；点击问题应跳到对应字段/节点 |
| 2.6.3 | 制造版本冲突（见 3.5 的技巧） | 回执"版本冲突"，提示刷新后重试，**不落盘** |
| 2.6.4 | 制造写盘失败（见 3.6） | 回执区分"写盘失败"；界面提示可重试；列表仍可刷新 |
| 2.6.5 | 制造重载失败（见 3.7，需先做一次 Windows ACL 操作） | 回执"已写盘但未重载"；运行时继续用旧规则；界面明确提示"规则已保存但未生效" |
| 2.6.6 | 应用成功后刷新列表 | 改动生效、脏标记清除；【现在可验收】草稿与撤销历史清空 |

## 3. 独占编辑会话（锁）【现在可验收】

### 3.1 两个客户端互斥

准备：单人世界 + "对局域网开放"，或用专用服务器；两个客户端各自连入（第二个客户端需要 op 才能执行 `/idtw config edit`）。

| # | 操作 | 预期 |
|---|---|---|
| 3.1.1 | 客户端 A 执行 `/idtw config edit` | A 看到"已向 A 发送编辑入口。"；服务端日志无异常 |
| 3.1.2 | 客户端 B 执行 `/idtw config edit` | B 看到"规则编辑器已被 A 锁定，可用 `/idtw config edit-lock status` 查看。"；【现在可验收】B 不弹出编辑器（或弹出被占用提示） |
| 3.1.3 | A 关闭界面（【现在可验收】按 Esc）后 B 再执行 | B 立刻成功（锁已释放，见 3.4） |

### 3.2 `edit-lock status` 的预期输出

| 执行者 | 预期回显 |
|---|---|
| 持有者 A | "你正持有规则编辑器：状态=%1$s，会话号=%2$s。"（状态为 `opening` / `active` / `applying`） |
| 无权限的 B | "规则编辑器当前被其他玩家持有。"（**不泄露**会话号） |
| 有权限（op）的 B | "%1$s 正持有规则编辑器：状态=%2$s。" |
| 任何人（空闲时） | "规则编辑器空闲，当前无人持有编辑锁。" |

### 3.3 `edit-lock release`

| # | 操作 | 预期 |
|---|---|---|
| 3.3.1 | op 执行 `/idtw config edit-lock release` | "已强制释放 A 持有的规则编辑器锁。"；A 立刻收到"会话失效"回执（【现在可验收】界面自动关闭并提示） |
| 3.3.2 | 空闲时执行 release | "规则编辑器本来就是空闲的。" |
| 3.3.3 | 无权限玩家执行 release | 命令不可用/被拒（需要权限等级 2） |

### 3.4 释放时机

| # | 情形 | 预期 |
|---|---|---|
| 3.4.1 | 【现在可验收】关闭编辑界面 | 客户端发关闭载荷，服务端**立即**把会话置为空闲（`RuleEditService.close` → `sessions.release()`，回执 SUCCESS）；**不需要等 60 秒** |
| 3.4.2 | 客户端断线（杀进程/拔网线） | 客户端工作区重置；服务端在**租约 60 秒**内无心跳即释放（按服务端活动 tick 计） |
| 3.4.3 | 客户端打开界面后 15 秒内没有确认握手 | 会话在 OPENING 窗口（15 秒）后自动释放 |
| 3.4.4 | 单人世界打开暂停菜单 | 服务端 tick 不推进 ⇒ **租约不流逝**（这是设计行为，不是 bug）；要测到期请用专用服务器 |

### 3.5 版本冲突（【现在可验收】）

让两个客户端先后写入：A 取锁改一条规则但**不提交**；B 用 op 执行 `release` 后取锁并成功保存一条；A 再提交 ⇒ 预期回执"版本冲突"、整批不落盘、收到最新快照。

### 3.6 写盘失败（【现在可验收】的开发者路径）

用另一个进程独占锁定覆盖层里将被写入的文件（Windows 资源管理器预览/文本编辑器打开并保持写锁，或 `handle.exe`），再通过界面/命令触发保存。
预期：回执"写盘失败"（`WRITE_FAILED`），运行时索引不变，列表仍可刷新。

### 3.7 重载失败（"已写盘但未重载"）——【现在可验收，需先做一次 Windows ACL 操作】

**先看一条纠正（2026-10-03 实测代码路径）**：**把已存在的覆盖层 JSON 写坏不会**造成"已写盘但未重载"。JSON 解析失败只产生一条问题记录（`common/src/main/java/com/meteorite/itemdespawntowhat/core/load/RuleFileParser.java:39-40`：`JSON 解析失败: ...`），加载照常成功，保存回执仍是成功；单个文件读取失败同样只记问题（`OverlayRuleReader.java:65-71`）。

`SAVED_NOT_RELOADED` 只在**重载过程抛异常**时出现（两个平台行为现已一致）：

- `fabric/src/main/java/com/meteorite/itemdespawntowhat/runtime/RuleRuntimeHost.java:147-152` 与 `neoforge/src/main/java/com/meteorite/itemdespawntowhat/runtime/RuleRuntimeHost.java:149-151`：`rebuildAndRescan` 在 `applyReload(...) == null` 时抛 `IllegalStateException("保存后的运行时重载失败")`；
- 两侧 `reload`/`applyReload` 仅在捕获 `RuntimeException` 时返回 null（此时**保留上一版索引**继续服务）；
- **不需要改代码的诱因**：覆盖层目录无法列举 —— `common/src/main/java/com/meteorite/itemdespawntowhat/core/load/OverlayRuleReader.java:47-48` 抛 `UncheckedIOException("列举覆盖层规则文件失败，保留现有规则")`。

可操作做法（任选其一）：

| 方式 | 步骤 | 预期 |
|---|---|---|
| **目录 ACL 拒绝列举（推荐，不改代码）** | 保存**前**在 Windows 对 `config/itemdespawntowhat/rules/` 下的一个**子目录**执行 `icacls "<子目录>" /deny %USERNAME%:(RD)`（确保即将写入的文件不在该子目录内；RD = 拒绝列出目录），然后通过命令/界面触发保存 | 回执"已写盘但未重载"（`SAVED_NOT_RELOADED`，`writtenToDisk=true, reloaded=false`，`RuleEditService.java:370-371`）；运行时继续用旧规则；【现在可验收】界面提示"已保存但未生效"，列表可刷新（回执附带新快照）；验收后 `icacls "<子目录>" /remove:d %USERNAME%` 还原 |
| 调试器注入 | 在 `RuleRuntimeHost` 的 `loadRules(...)` 调用处设断点，命中一次后抛 `new IllegalStateException(...)`（或 `Force Return null`） | 同上 |

无论哪种方式，验收要求：① 回执状态为"已写盘但未重载"；② **不**把这次保存当成完全成功；③ 运行时行为仍是旧规则；④ 列表仍可刷新；⑤ 下一次成功重载/重启后新规则生效。

> 常规玩家流程里不会自然遇到该分支（见 8.9）；本条属**故障注入验收**，按上表操作即可完成。

## 4. 数据包规则与文件层

### 4.1 内置示例规则（第 5 节的最小用例直接引用它们）

全部位于模组自带数据包 `data/itemdespawntowhat/idtw/rules/` 下，**文件为只读的顶层规则对象或规则数组**（8 个文件共 10 条规则；多规则文件用数组），`trigger_after_seconds` 均为 300 秒：

| 文件 | 规则 id | 源物品 | 内容 | 默认启用 |
|---|---|---|---|---|
| builtin_item_to_item.json | `itemdespawntowhat:builtin_item_to_item` | `minecraft:chicken` | 无 `conditions`（恒真）→ `spawn_entity / item`（腐肉）；隐式消耗 1 | ✅ |
| builtin_item_to_entity.json | `itemdespawntowhat:builtin_item_to_entity` | `minecraft:egg` | `outdoor` + `dimension`(主世界) → `spawn_entity`（幼年鸡） | ✅ |
| builtin_item_to_block.json | `itemdespawntowhat:builtin_item_to_block` | `#minecraft:saplings`（排除 `minecraft:oak_sapling`） | `surrounding_blocks`(下=`#minecraft:dirt`) + `outdoor` → `place_block` | ❌（默认停用） |
| builtin_multi_effect.json | `itemdespawntowhat:builtin_multi_effect` | `minecraft:diamond` | `inverted`(y_level) 条件 → `spawn_entity / experience` + `consume_source` + `lightning`（带效果延迟） | ✅ |
| builtin_loot_and_chance.json | `itemdespawntowhat:builtin_loot_and_chance` | `minecraft:gold_nugget` | `loot_table` + 50% `chance` | ✅ |
| builtin_conditions.json | `itemdespawntowhat:builtin_catalyst_and_fluid` | `minecraft:redstone` | `fluid_present`(流动水) + `catalyst_present`(骨粉) → `spawn_entity / item` + `consume_catalyst` | ✅ |
| builtin_conditions.json | `itemdespawntowhat:builtin_biome_or_time` | `minecraft:apple` | `any_of`[ `biome`(forest), `time_of_day` ] → `spawn_entity / item` | ❌ |
| builtin_weather_and_light.json | `itemdespawntowhat:builtin_thunder_condensation` | `minecraft:wet_sponge` | `weather`(thunder) + `y_level`(60..320) → `weather`(rain,6000t,雷) + `consume_fluid`(水源) | ✅ |
| builtin_weather_and_light.json | `itemdespawntowhat:builtin_dark_blast` | `minecraft:gunpowder` | `any_of`[ `light_level`(0..7), `inverted`(`weather` clear) ] → `explosion`(power 2, 起火) | ❌ |
| builtin_arrow_rain.json | `itemdespawntowhat:builtin_arrow_rain` | `minecraft:arrow` | `dimension`(主世界) + `weather` → `arrow_rain` | ❌ |

覆盖完整性：以上样本合计覆盖**全部 10 个条件**（dimension / biome / weather / outdoor / surrounding_blocks / catalyst_present / fluid_present / time_of_day / y_level / light_level）与**全部 10 个内置效果**（spawn_entity〔三子类〕 / place_block / loot_table / lightning / arrow_rain / weather / explosion / consume_source / consume_catalyst / consume_fluid）。

**加速技巧**：`trigger_after_seconds=300` 与掉落物 5 分钟生命周期几乎重合，肉眼验证要等 5 分钟。建议复制一条规则到覆盖层并把 `trigger_after_seconds` 改成 5（【现在可验收】用界面；【现在】用文本编辑器写覆盖层并 `/idtw config reload`）。

### 4.2 覆盖 / 停用 / 屏蔽 / 恢复（【现在可验收】走界面；命令路径现在可用）

以 `itemdespawntowhat:builtin_item_to_item` 为例：

| # | 操作 | 预期 |
|---|---|---|
| 4.2.1 | 对数据包规则点"自定义覆盖" | 生成覆盖层文件 `config/itemdespawntowhat/rules/itemdespawntowhat_builtin_item_to_item.json`（文件名由 id 派生：`:` 与 `/` 换成 `_`，追加 `.json`）；列表中该条来源变 `overlay`/`mixed` |
| 4.2.2 | 改内容后应用 | 覆盖层生效；数据包原文件**不被修改** |
| 4.2.3 | 把 `enabled` 关掉并应用 | 列表状态 `disabled`；游戏内该规则不触发 |
| 4.2.4 | 用"屏蔽/删除覆盖条目"（delete 覆盖） | 覆盖文件只含控制条目（`id` + `delete: true`）；列表状态 `masked`；数据包规则完全不生效 |
| 4.2.5 | 删除该覆盖条目 / 点"恢复原始" | 覆盖条目消失，列表回到数据包原规则（来源 `datapack`，状态 `active`） |
| 4.2.6 | 【现在可验收】尝试直接改纯数据包规则的字段 | 不得修改数据包文件：界面应引导「先创建覆盖条目」再编辑；未注册编辑器描述的类型回退为只读 JSON 摘要，且**原样保留**未识别字段 |

**`editable` 语义（Lead 裁决，契约 §3.6）**：`editable = 该条目存在规则内容（base 或 overlay 非空）`。因此**纯数据包规则同样 `editable=true`**（可以基于它生成覆盖）；只有"仅控制条目、无实体内容"（覆盖文件只有 `id` + `delete: true`/`enabled: false`）才是 `editable=false`。`editable` **不表示"能否表单编辑"**，`status=invalid`（解码失败）的条目也可以是 true，客户端按 `status`/`issues` 自行决定是否只读展示。

**结论**：4.2 这一节的「覆盖 → 停用 → 屏蔽 → 恢复原始」**现在就能验收**——用文本编辑器改覆盖层文件并 `/idtw config reload`，或在命令路径可用时走命令；不依赖 P5 编辑界面。

### 4.3 文件层与服务端命令【现在可验收】

| # | 命令/操作 | 预期 |
|---|---|---|
| 4.3.1 | `/idtw config validate` | 离线体检全部来源，输出问题清单（含来源与字段路径）与统计；无问题时为 0 条 |
| 4.3.2 | `/idtw config list` | 逐条输出 `id &#124; 来源层= &#124; 优先级= &#124; 触发= 秒 &#124; 效果= &#124; 启用= &#124; 出处=` |
| 4.3.3 | `/idtw config reload` | 重建索引 + 全维度回扫，输出条数与问题数 |
| 4.3.4 | 覆盖层目录 | `config/itemdespawntowhat/rules/`；**一文件一规则**，保存形态是**顶层对象**（数组只可能是只读的原始/手写文件）；`.edit_version` 在上一层目录，不会被当作规则读取 |
| 4.3.5 | 旧格式必须报错（零兼容） | 手写 `"conditions": [ { "conditions": [ ... ] } ]`（旧数组）或叶子里的 `negated`，然后 `/idtw config reload`：应出现**明确报错**（列出允许的 op、提示改用 inverted），该文件不生效，**不静默兼容、不崩溃** |
| 4.3.6 | 空组合节点 | 文件里 `{"op":"all_of","terms":[]}` 解码期即报错（`terms` 至少 1 项）；编辑器中间态允许存在并由保存前校验拦截 |
| 4.3.7 | 超限 | 深度 17 层 / 129 叶 / 257 节点 / `display_name` 超 128 码点的规则被校验拦下并给出字段路径 |
| 4.3.8 | 坏 JSON 文件 | 只产生问题记录，**不删除文件**；其它规则照常生效 |

### 4.4 旧链路转换（已退役）

`/idtw config convert` 已按 P8 结论**退役**（命令与 `RuleConvertService` 已删除），旧 v1.2.1 配置不再加载，需用 `/idtw config edit` 手工重建；见 `docs/guide/update-notes.md` 与 `docs/plan/v1.2.1-migration-evaluation.md`（第 8 节 8.6 同口径）。

## 5. 10 个条件 + 10 个内置效果的最小用例

每条都能用 4.1 的样本规则直接验收（把 `trigger_after_seconds` 改小即可）。"肉眼判据"是**不需要看 JSON** 也能判断的现象。

### 5.1 条件（10 个）

| 条件 | 最小设置 | 肉眼判据 | 样本 |
|---|---|---|---|
| `dimension` | 主世界 | 同样的物品丢到**下界**不触发 | builtin_item_to_entity |
| `biome` | 精准匹配 `#minecraft:is_forest` | 森林触发，草原不触发 | builtin_biome_or_time |
| `weather` | `thunder` | 用 `/weather thunder` 后触发，`/weather clear` 后不触发 | builtin_thunder_condensation |
| `outdoor` | 无参数 | 露天触发；在 2 格高封顶的室内不触发 | builtin_item_to_entity |
| `surrounding_blocks` | 下方 `#minecraft:dirt` | 下方是泥土触发，换成石头不触发；周围区块未全加载时**不触发**（UNAVAILABLE，而不是判定为"不满足"） | builtin_item_to_block |
| `catalyst_present` | 骨粉，count=1 | 半径内没有骨粉时不触发；有则触发 | builtin_catalyst_and_fluid |
| `fluid_present` | `minecraft:water`，require_source=false | 流动水也算；require_source=true 时必须有水源 | builtin_catalyst_and_fluid |
| `time_of_day` | 0..11999 | `/time set day` 触发，`/time set midnight` 不触发 | builtin_biome_or_time |
| `y_level` | 60..320 | 地面（y≥60）触发；挖到 y=30 丢同样的物品不触发；用 `inverted` 可表示"不在该区间" | builtin_thunder_condensation / builtin_multi_effect |
| `light_level` | 0..7 | 暗处触发；插火把后不触发 | builtin_dark_blast |

### 5.2 效果（10 个内置类型，实体三子类分行验收）

| 效果 | 最小设置 | 肉眼判据 | 样本 |
|---|---|---|---|
| `spawn_entity / item` | 腐肉 ×1 | 原地出现腐肉 | builtin_item_to_item |
| `spawn_entity / entity` | 幼年鸡 ×1（age=-24000 或预设"幼年"） | 出现幼年鸡实体 | builtin_item_to_entity |
| `place_block` | 留空（由源物品决定），方形 | 原地出现对应方块 | builtin_item_to_block |
| `spawn_entity / experience` | 5 点 | 出现经验球/玩家获得经验 | builtin_multi_effect |
| `loot_table` | `minecraft:chests/simple_dungeon` | 按战利品表随机产出 | builtin_loot_and_chance |
| `lightning` | count=1 | 命中位置落雷 + 雷声 | builtin_multi_effect |
| `arrow_rain` | count=10，pickup=allowed | 天降箭并可拾取 | builtin_arrow_rain |
| `weather` | rain / 6000 tick / 雷电开 | 天气变雨或雷暴 | builtin_thunder_condensation |
| `explosion` | power=2，起火开，仅视觉关 | 爆炸粒子 + 方块破坏 | builtin_dark_blast |
| `consume_source` | count=1 | 源物品被扣减（不再转化出别的） | builtin_multi_effect |
| `consume_catalyst` | 骨粉 ×1，半径 2 | 附近骨粉被消耗 | builtin_catalyst_and_fluid |
| `consume_fluid` | 水源，require_source=true | 附近水源被消耗 | builtin_thunder_condensation |

补充验收（放在任一效果上）：把 `delay_ticks` 设成 20（1 秒）→ 效果应在触发后约 1 秒出现；把 `chance` 设成 50% → 多次触发约半数产出，**未命中概率时源物品仍被隐式消耗**（样本 `builtin_loot_and_chance` 的注释即此语义）；给效果加一个 `light_level` 附加条件 → 条件下不满足时该效果不执行，规则内其它效果不受影响。

## 6. 显示与可访问性（【现在可验收】）

| # | 检查项 | 预期 |
|---|---|---|
| 6.1 | 窗口缩到 320×240 | 界面自动缩放，关键控件仍可点击，无重叠 |
| 6.2 | GUI 缩放 1x / 2x / 3x | 均无重叠、无越界；列表可滚动 |
| 6.3 | 中文 ↔ English 切换（含长文本，如 "Thunder Condensation"） | 无缺失键（不显示原始 key）、无截断导致语义不清；过长时用省略号且有 tooltip |
| 6.4 | 键盘：Tab / Shift+Tab | 焦点按视觉顺序移动，焦点有清晰描边 |
| 6.5 | Enter / Esc | Enter 激活当前控件；Esc 返回上级或关闭（【现在可验收】有关闭前的草稿提示） |
| 6.6 | 滚轮 | 列表/树/表单滚动，不越界滚动 |
| 6.7 | 禁用态 | 只读或不可用的控件有明确禁用样式 + tooltip 说明原因 |
| 6.8 | tooltip 不越界 | 鼠标停在最右/最下控件上，tooltip 自动换边，不出屏 |
| 6.9 | 颜色不是唯一信息 | 启用/停用/只读/错误状态都同时有文字或图标 |
| 6.10 | 无会话时 | 提示用 `/idtw config edit` 打开（不静默失败） |

## 7. 双平台一致性

Fabric 与 NeoForge **各跑一遍**，结果应一致：

| # | 检查项 | Fabric | NeoForge |
|---|---|---|---|
| 7.1 | 构建成功、客户端可启动 | ☐ | ☐ |
| 7.2 | `/idtw config edit` / `edit-lock status` / `release` 行为一致 | ☐ | ☐ |
| 7.3 | `validate` / `list` / `reload` 输出一致 | ☐ | ☐ |
| 7.4 | 覆盖层写入路径一致（`config/itemdespawntowhat/rules/`） | ☐ | ☐ |
| 7.5 | 保存后立即重载生效（两者都不得静默把"重载失败"当成功） | ☐ | ☐ |
| 7.6 | 客户端心跳/tick 装配（Fabric 侧已确认 `ItemDespawnToWhatClient.java:17-18` 与 `InputEvents.java:19`，NeoForge 侧同项核对） | ☐ | ☐ |
| 7.7 | 编辑器界面（【现在可验收】）开屏与关闭释放锁 | ☐ | ☐ |

## 8. 已知缺口表（照实写，2026-10-03）

| # | 缺口 | 现状/证据 | 用户可见影响 | 归属 |
|---|---|---|---|---|
| 8.1 | ~~**编辑界面未接线**~~ **已解决**（2026-10-03 复核） | `RuleEditorOpener.bootstrap()` → `EditorScreenHooks.setOpener(...)` 已在 `fabric/src/main/java/com/meteorite/itemdespawntowhat/client/ItemDespawnToWhatClient.java:21` 与 `neoforge/src/main/java/com/meteorite/itemdespawntowhat/client/register/RegisterEvent.java:24` 调用（旧占位屏已删除） | 无（第 2 节主链、4.2 界面路径、第 6 节现已可执行） | P5 已完成 |
| 8.2 | ~~语言文件缺 43 个界面键~~ **已解决**（2026-10-03 收口） | 两个语言文件各 **698 键**、键集合完全一致、无空值无重复；源码字面量 key 全命中（含 `…edit.list.*`、`…edit.preset.*`、`…edit.undo.*` 15 个、`…edit.enum.*` 13 个、`…edit.field.*`） | 无 | P5 已完成 |
| 8.3 | ~~`client/ui` 未读取 `EditorField.enumGroup()`~~ **不成立**（2026-10-03 复核） | `client/ui/screen/form/FormControl.java:596` 读取 `field.enumGroup()` 并拼 `gui.itemdespawntowhat.edit.enum.<组>.<值>`（组名为空才回退 `field.name()`）；5 组 group 定义在 `client/edit/BuiltinEditorDescriptors.java`（`biome_mode` / `weather_kind` / `place_block_shape` / `arrow_rain_pickup` / `weather_effect_mode`） | 无 | P5 已完成 |
| 8.4 | ~~**P6 未实现**~~ **已解决**（task-9 落盘 + task-17 收口） | 草稿落 `config/itemdespawntowhat/client/editor-drafts/`（临时文件 + 原子改名、写盘失败自动重排）、跨界面/断线/重启恢复、撤销重做上限 100（含删除标记与「恢复原始版本」）、多规则统一变更集、整条规则三态冲突（`TARGET_MISSING` / `TARGET_EXISTS` / `REMOTE_CHANGED`）与冲突弹窗 | 无（2.1.4 / 2.6.6 / 6.5 的草稿预期可验收）；主线程同步写盘见 8.10 | P6 已完成 |
| 8.5 | 快捷键**默认未绑定** | `client/key/ModKeyBindings.java:11-15`：`InputConstants.UNKNOWN`；处理逻辑在 `client/key/EditorShortcut.java`（有会话才重开界面，无会话提示 `gui.itemdespawntowhat.edit.hint.use_command`，开发环境开原型屏）。界面接线已完成（见 8.1） | 需在 选项 → 控制 手动绑定；无会话时不会弹编辑器（符合契约 §5.3，不是缺陷） | 发布说明需注明默认按键 |
| 8.6 | **旧配置无迁移路径** | **【已定案：破坏性更新】**`/idtw config convert` 与 `RuleConvertService` 已删除（P8 结论，见 `docs/plan/v1.2.1-migration-evaluation.md`）；旧 v1.2.1 配置不再加载 | 旧链路配置用户需用 `/idtw config edit` 手工重建（发布说明见 `docs/guide/update-notes.md`） | P8 已定案 |
| 8.7 | ~~状态效果（`mob_effect`）不在选择目录里~~ **已解决**（2026-10-03，task-13） | 枚举末尾已追加 `MOB_EFFECT`（`core/network/protocol/RuleCatalogType.java:20`）；数据源 `core/catalog/RuleCatalogSources.java:154-162` 的 `mobEffects(server)` 取 `BuiltInRegistries.MOB_EFFECT.keySet()`，label 用 `MobEffect.getDescriptionId()`（形如 `effect.minecraft.poison`），icon 空串；不改协议版本（双端同包） | 无（服务端数据源 + 客户端选择器映射 task-13/14 均已完成） | P5 增量已完成 |
| 8.8 | ~~客户端尚无 messageCode 解析~~ **已解决** | `client/net/RuleEditClientWorkspace.java:254` 归一 `messageArgs`/`fallbackMessage`，`client/edit/LiveEditorWorkspace.java:125` 暴露 `lastMessageArgs()`；`client/ui/screen/RuleEditorScreen.java:1939-1944` 用 `RuleSaveStatus.messageKey()` + 参数渲染回执（SUCCESS 绿 / SAVED_NOT_RELOADED 黄 / 其余红并带参数） | 无 | P5/P6 已完成 |
| 8.9 | "已写盘但未重载"在常规玩家流程里不会自然出现 | 见 3.7：JSON 写坏只会产生问题记录；需目录列举失败（`OverlayRuleReader.java:47-48`）或调试器注入 | 无自然诱因；可按 3.7 的 `icacls` 拒绝列举步骤做故障注入验收 | 验收限制（非代码缺陷） |
| 8.10 | 草稿写盘在**主线程同步**执行，草稿数量大时的帧时间未实测 | `client/edit/draft/DraftStore.java` 的 `save` 由界面线程直接写文件；写盘有 2 秒节流（`DraftJournal.SAVE_DELAY_MS`）、临时文件 + 原子改名落地，失败会自动重排重试并给出界面提示 | 极端情况下（大量脏草稿同时到期）可能出现一次掉帧；属已知限制，规划书 §15 未要求本版优化 | P6（已知限制，不修） |

## 9. 验收记录模板

| 日期 | 平台 | 步骤号 | 结果（通过/失败） | 现象/截图 | 备注 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

判定口径：任何"界面显示原始 key"、"保存后未生效却回报成功"、"两个客户端能同时编辑"、"条件下不满足却触发了效果"都是**失败**，需记录复现步骤与日志片段。

## 10. 新 GUI 与后端前置人工验收（B1/B2/B3、P1、P2、P3、P3-B、P4）【待用户执行】

> 本节覆盖 2026-10-03 冻结方案（`plan-gui-rule-update.md` 及其字段 / kit 规格）的实施验收。**本次未执行任何游戏内步骤**，下列每一项都由用户按步骤验收；静态核对与构建证据见各节说明与 `docs/plan/*` 头部状态行。

### 10.1 后端前置 B1 / B2 / B3【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.1.1 | B1 逐组候选选择：造一条规则，源量 4、每组成本 1，两个候选 A/B 都有容量，从 A 开始连续触发 | 依次得到 A、B、A、B；后续请求接续同规则同维度的持久化游标，不从头开始 |
| 10.1.2 | B1 容量不足 | 容量不足的候选被跳过；全部候选都不可用时停止并**返还尚未开始的库存**，不消耗、不重复扣款 |
| 10.1.3 | B1 概率落空 | 落空后**不重选**候选、不免费重试已开始的组 |
| 10.1.4 | B2 混合一次性效果：4 个可供消费的源、成本 1，候选包含「生成物品」+「一次闪电」 | 生成物品执行 4 组；闪电在同一源请求中**最多尝试一次** |
| 10.1.5 | B2 纯一次性候选 | 仍最多一组（不因含可重复效果就放宽） |
| 10.1.6 | B2 预算续跑 / 恢复 | 预算中断、重载、断线后继续：不重放已开始的组、不重复扣款；催化剂预留与返还账本一致 |
| 10.1.7 | B3 过时告警 | 只声明 `outcomes`（不写 `effects`）的规则**不再**出现「当前版本不会执行」告警，且能正常执行 |
| 10.1.8 | B3 描述符域 | 见 10.4.2 的数值项：priority / trigger_after_seconds / source_cost / delay_ticks / spawn_entity.age 支持后端完整域；药水 duration_ticks / amplifier 可省略 |

### 10.2 P1 可复用 kit【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.2.1 | 标量滑块：精确输入一个窗口外的合法值后回填 | 回填**不吸附**；拖动才按步长吸附；Shift 细调；方向键连按在抬起时合并为一次提交 |
| 10.2.2 | 双端区间：拖动两端使其重叠、再分别关闭某一端 | 端点身份不交换；可选端点独立于 0；重叠端点可切换；关闭端点按字段语义省略而不是写 0 |
| 10.2.3 | 周期区间（time_of_day 时间条） | 周期 / 刻度由宿主注入，界面不读取世界时间；from > to 表示跨零点而不是错误 |
| 10.2.4 | （静态）依赖边界 | kit 不 import 本项目其它包与两端 loader API；本期**未**拆包发布独立库 |

### 10.3 P2 数据层（client/edit）【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.3.1 | 打开一条规则，不改任何内容，关闭 / 应用 | 磁盘 JSON **不变**（无操作不写 JSON） |
| 10.3.2 | 在嵌套路径上编辑：规则级条件 `conditions.terms[i].term…`、效果级条件、`outcomes[i].effects[j].字段` | 修改落到正确对象；重排候选 / 效果后路径随之重算，不跳错对象 |
| 10.3.3 | 对一条只写顶层 `effects` 的规则添加第二个候选 | 一次操作内：删除 `effects`、写入 `outcomes`、**原效果对象逐字保留**；Ctrl+Z 一次完整还原（一条撤销记录） |
| 10.3.4 | 打开省略了 `priority` / `trigger_after_seconds` / `count` / `luck` / `power` / `pickup` / `shape` / `radius` 等字段的合法规则 | 各页**不出现 required 误报**（task-10 修复后）；清空这些字段也不会被阻断 |
| 10.3.5 | 规则内保留未知 / 第三方字段（如 `"x_foo": 1`），编辑其它字段后保存 | 该字段原样保留，不被已知字段 DTO 重建丢掉 |
| 10.3.6 | 自动命名：填 `display_name` → 再清空 | 有别名时显示别名；清空后回落自动标题（单结果 / 多结果轮询 / 多结果优先 / 候选 / 多效果 / 多输入 / 未完成）；`notes` 不参与命名；标题不写入 JSON、不影响覆盖文件名 |
| 10.3.7 | 错误定位：提交一条服务端校验失败的规则 | 按 VALIDATION_FAILED 的问题路径定位到规则 → 页签 → 候选 → 效果 → 条件 → 字段；未知路径至少落到对应规则与问题区，不阻止界面打开 |

### 10.4 P3 四页流程与候选 / 成本区【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.4.1 | 四页导航：基本信息 / 输入与成本 / 触发与条件 / 结果，加上一步 / 下一步与常驻短摘要 | 自由切换；窄窗口单列分层、宽窗口并排；返回恢复选择、滚动、折叠与焦点 |
| 10.4.2 | 数值与单位：priority 填 2147483647；trigger_after_seconds 填 100000；delay_ticks 填大值；spawn_entity.age 填 100000；概率填 12.3456% | 保存后 JSON 为对应原值；触发秒数**不 ×20**；避免把旧 UI 上限当硬限额 |
| 10.4.3 | 成本区：source_cost 留空（=后端推导）与填 64；催化剂成本开关 + items/count/radius | 留空不写字段；编辑已有 consume_source / consume_catalyst 时只改**原效果路径**，不生成对应固定成本字段、不静默删公共字段 |
| 10.4.4 | 触发与条件：四种消失方式多选（不可全不选）；取消 natural 后重新选中；time_of_day 跨零点；biome climate 六维 | 隐藏 trigger_after_seconds 不清值；跨零点合法；气候六维全空**禁止提交** |
| 10.4.5 | 结果区：添加 / 复制 / 删除 / 拖动 / 上下移候选与效果 | 一次排序一次撤销；删除后焦点落邻项；32 候选或 32 效果总数上限生效 |
| 10.4.6 | 第三方未注册效果 | 显示明确的「只读保留」说明，不改写、不丢未知字段（task-12） |

### 10.5 P3-B 表单控件数值精度与焦点【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.5.1 | 概率回填：`chance = 0.123456` 打开界面不编辑 | 显示 **12.3456%**（不是 12.3%）；不改写舍入后的值 |
| 10.5.2 | 药水等级：amplifier 255 | 界面显示 256（当前为阿拉伯数字 + 级 / Lv）；写回 255，可往返 |
| 10.5.3 | 刻数秒 / 刻切换：输入不能整除 20 的秒数 | 拒绝并提示，而不是静默取整；保存仍是完整整数 tick |
| 10.5.4 | 焦点路由 | Tab / Shift+Tab 遍历；方向键由控件优先消费；Enter 确认、Space 切换、Esc 逐级取消；modal 关闭恢复焦点 |

### 10.6 P4 目录 / 区间 / 时间条 / 图解 / 会话 / 朗读【待用户执行】

| # | 步骤 | 期望 |
|---|---|---|
| 10.6.1 | 目录面板：9 类目录、翻页与搜索、快速连续切换 | 分页上限 200；过时响应被丢弃、不串页；无图标用文字 / 默认槽；目录未加载时用缓存或 id，不阻塞渲染 |
| 10.6.2 | 区间与时间条：双端区间各端开关；昼夜条全天 / 白天 / 夜间预设 | 端点可单独不约束；from = to 表示该刻而不是全天 |
| 10.6.3 | 结构图解 | 只显示配置结构（源、配置成本、候选分支、效果与实际配置量），不含示例源数量、组数、余数或运行模拟 |
| 10.6.4 | 会话恢复：编辑中隐藏窗口 / 失权 / 断线后重开 | 未完成预览被取消、已落盘草稿保留；恢复选择、滚动、折叠与焦点 |
| 10.6.5 | 键盘与朗读、中英切换 | tooltip / 朗读走本地化 Component；中英切换不出现原始 key；过长文本省略但 tooltip 有全文 |

## 11. 双平台新特性验收清单（Fabric / NeoForge 各跑一遍）【待用户执行】

平台薄注册的静态结论：Fabric 侧 13 个 Java 文件、NeoForge 侧 9 个 Java 文件，均为 loader 事件 / 注册器 / 平台实现 / mixin 入口（如 `client/ItemDespawnToWhatClient.java`、`client/event/InputEvents.java`、`network/registrar/RuleEditPayloadRegistrar.java`、`runtime/RuleRuntime*.java`）；后端 B1/B2/B3、GUI 四页、表单控件与 client/edit 数据层**全部在 common**，无平台分叉，因此下列行为应在两端一致。

| # | 检查项 | Fabric | NeoForge |
|---|---|---|---|
| 11.1 | B1 逐组候选选择与轮询（10.1.1–10.1.3） | ☐ | ☐ |
| 11.2 | B2 混合一次性效果与一次性只尝试一次（10.1.4–10.1.5） | ☐ | ☐ |
| 11.3 | B2 预算续跑与恢复：不重放已开始组、不重复扣款（10.1.6） | ☐ | ☐ |
| 11.4 | 容量 / 催化剂不足时的跳过与返还（10.1.2、10.1.6） | ☐ | ☐ |
| 11.5 | 规则编辑与应用全链（10.3–10.5：原子结构转换、数值往返、错误定位） | ☐ | ☐ |
| 11.6 | 四页与 P4 交互（10.4、10.6） | ☐ | ☐ |
| 11.7 | 平台薄注册核对：两端仅注册 / 事件 / 平台实现，无 GUI 或结算逻辑分叉 | ☐ | ☐ |

## 12. 本期已知限制（2026-10-03）

| # | 已知限制 | 现状 / 证据 | 用户可见影响 | 归属 |
|---|---|---|---|---|
| 12.1 | **`UiInputRouter` 未接入** | kit 规格 §8.5 要求「没有真实使用的抽象要么接入要么延后」；全仓引用仅出现在自身、`client/ui/kit/package-info.java` 公开入口列表与 `UiSliderExamples` 说明注释中，宿主控件仍自行分发事件 | 无直接可见影响；事件路由顺序（modal → 捕获 → 聚焦 → 容器导航）目前由宿主各自实现 | P1/P4 已知限制，见 `NOTICE-kit.md`「P4 之后」 |
| 12.2 | 等级**罗马数字辅助显示未实现** | 界面显示为阿拉伯数字 + 级 / Lv（写回仍为 amplifier − 1，可往返） | 与规格「罗马数字与阿拉伯数字辅助」有差距，不影响数值往返 | P3-B 已知限制 |
| 12.3 | 小滑块不能靠像素覆盖全部精细值 | 滑块窗口只覆盖常用区间，窗口外合法值须用行内精确输入 | 极大 / 极小值需键盘精确输入 | 设计限制（kit 规格已接受） |
| 12.4 | 静态结构图不知道实时容量 / 位置 / 游标 | 图解只描述配置结构，不读取运行时状态 | 图解不代表实际结算结果 | 设计限制 |
| 12.5 | 候选**无人工别名字段** | 候选只有自动命名（结果 N：…），JSON 不写 `display_name` / `notes` | 无法给候选起人工别名 | 冻结方案决定 |
| 12.6 | 独立 kit 库**本期未发布** | 只冻结公开边界与依赖检查方式；独立 Gradle 模块与发布脚本需另立拆包任务 | 第三方暂不能以独立依赖方式使用 kit | P1 范围外 |
| 12.7 | 语言文件键集合 | `en_us.json` 与 `zh_cn.json` 各 **895** 键、键集合完全一致、无空值（2026-10-07 静态校验；其中 `gui.itemdespawntowhat.edit.*` 595 键） | 无（不一致会导致显示原始 key） | P5 文档同步 |
| 12.8 | ~~文档引用与仓库现状的偏差~~ **已解决（P5-e）** | `docs/dev/internals/ui-kit-api.md` 与 `tools/check-ui-kit-boundaries.ps1` 已建为真实文件，`package-info.java` 已指向后者；脚本对当前 kit 实跑 0 违规/exit 0，对违规样本 exit 1 | 无（遗留至修复前的读者困惑已消除） | 已修复：P5-e（task-14） |

## 13. 本轮 UI 与实体统一验收（2026-10-07，待用户执行）

**用户已有 NeoForge 截图反馈；本轮修正尚未游戏复测，不能据构建判定游戏内通过。** NeoForge、Fabric 各执行一轮，在中文/英文和常用 GUI Scale 下记录截图、操作顺序、预期/实际及频率。最低目标为 320×240 GUI 逻辑尺寸；原第 10 节中被本轮重排替代的页面形状以本节为准。

| # | 操作 | 预期 |
|---|---|---|
| 13.1 | 管理页展开各组，搜索规则名/ID，再清空搜索 | 每条规则只出现一次；组合产出分类正确；搜索命中展开，清空恢复折叠与稳定选中 |
| 13.2 | 宽屏选规则，再切 320×240 并打开/关闭“详情” | 宽屏树与预览并排；窄屏详情占正文，可滚动且切回树；操作和底栏可到达 |
| 13.3 | 新建空白规则，点缺失项，选择源物品/排除项/催化剂/标签 | 优先输入页，缺失提示能定位；每个目录写入目标正确；连续添加不覆盖前项 |
| 13.4 | 省略 enabled、chance、delay_ticks、count、trigger_after_seconds 后只打开查看 | 有效默认值与默认标记正确（enabled=true、chance=100%、等待=300秒等）；不产生脏草稿 |
| 13.5 | 三种实体子类填参数，切换再撤销 | 共同字段保留，专用字段重置；一次撤销恢复切换前状态；数量单位清楚 |
| 13.6 | 单方案→多方案、多动作，再在窄屏逐层进入/返回 | 方案/动作层级清楚，单方案隐藏多方案策略；参数、高级、安全/填原点只在适用时出现 |
| 13.7 | 目录用中文名称及 ID 搜索；首次加载、失败重试、切语言/重载/切服 | 搜索完整缓存，加载未完明确可能缺项；追逐点阵与文本可见，失败可重试，生命周期失效有效 |
| 13.8 | 同名物品/方块标签及通用实体标签选择 | 标签按字段真实注册表归属过滤；通用入口不提供 item/xp 或包含它们的标签 |
| 13.9 | 查看鸡/幼体/经验球/箭、龙/恶魂/鱿鱼及模组实体完整旋转 | 可见项 3D 竖直旋转、整圈尺度稳定；检查裁剪及滚动帧率，无逐帧实例创建 |
| 13.10 | marker/interaction/空 display 等不可预览类型 | 屏障物品+“预览不可用”，名称完整、仍可选；后续物品/文字绘制无污染 |
| 13.11 | 多条规则修改、查看改动、应用、放弃；英文长正文/按钮弹窗 | 改动清单有名称/类别，管理展示隐藏规则注册名；明确应用所有脏草稿；正文可滚动、按钮不越界，撤销与只读/冲突仍有效 |
| 13.12 | 分别验证 item/entity/experience JSON，故意填别的子类字段、旧类型和规则邻近 limit/radius | 合法三子类被接受；不兼容字段/旧生成格式被明确拒绝；place_block 自身范围仍可编辑 |
| 13.13 | 测试经验每组成本 3、amount 5、per_source_item true/false | true 每组 15 点，false 每组 5 点；实际拾取总点数准确，原版拆分/合并保留 |
| 13.14 | 重启服务端使用低共享阈值；容量不足/刚好/已开始组中途新增邻近产物 | 不足不开组不付成本；已开始组完成允许超额，之后新组继续检查；源物品守恒 |
| 13.15 | 大量源物品、延迟动作与跨区块/重启返还，随后恢复正常配置 | 预算/中断/返还契约不回退；记录实体数和帧率，不把软阈值当硬性能保证 |

已知限制：默认阈值未压测；碰撞尺寸不能精确代表任意模组可视模型，第三方静默无几何 renderer 可能无法自动识别；目录 revision 不覆盖所有资源内容变更；重叠不同 tag 不形成硬存量保证。假设：TAG 的接口归属缺失暂由客户端已同步注册表处理，后端结构调整仍需另行讨论。

### 13.16–13.18：实测反馈修正（2026-10-07）

| # | 操作 | 预期 |
|---|---|---|
| 13.16 | 首次打开管理页，点分类左侧三角，搜索再清空，并调整 GUI Scale | 分类默认折叠；右三角折叠、下三角展开；子级缩进，叶子没有短横线或虚假展开按钮；已操作的折叠状态在刷新/缩放中保留 |
| 13.17 | 查看无催化剂、多个催化剂、多个动作/方案的规则 | 原注册名位置显示对象图标关系：源物品 -> 结果，或源物品 + 催化剂 -> 结果；催化剂使用 +，结果使用逗号，均保持原数组顺序；管理行、提示、预览不展示规则注册名；窄行用图标关系，长关系悬停滚动并有完整说明 |
| 13.18 | 正向 catalyst_present、取反条件、consume_catalyst 与 catalyst_cost；设置源成本及产出数量 | 正向条件计入催化剂，奇数层取反不计入；同名条件/消耗引用去重并保留首次位置；消耗催化剂右上角为透明底橙色星标，数量在所有对象图标右下角且无背景填充；不同条件/方案的数量以 / 保留原序，提示列出消耗件数。经验 per_source_item 的角标表示每源物品点数，提示解释倍率 |

截图证据：neoforge/run/screenshots/2026-10-07_09.49.42.png。用户确认将正向存在条件计入催化剂，并要求消耗/数量分别用右上/右下角标。配方只读展示沿用当前规则语义；正向条件是否实际必需仍由条件树决定，列表条目可能是择一匹配，多方案每组只选一个，悬停说明不将所有配置误写为同时执行。消耗-only 合法规则显示“无产出”，不误标为缺少设置。此补充尚待用户复测。

### 13.19–13.23：第二轮实测反馈修正（2026-10-07，待用户执行）

截图证据：`neoforge/run/screenshots/2026-10-07_10.33.35.png`。目录空白、双击无效、冗余选中三角、提示未分行、角标黑底和 resize 重置均按客户端路径修正，未改后端接口或结算语义。截图证明修复前现象，不能作为修复后游戏验证。

| # | 操作 | 预期 |
|---|---|---|
| 13.19 | 展开包含目录和配方的树，滚动；使用上下键、PageUp/PageDown 后悬停各行 | 目录按文字高度加上下留白，规则按名称与配方图标实际高度测量；没有统一高目录造成的大块空白；渲染、点击、提示命中及翻页共用实际行高 |
| 13.20 | 单击三角、双击目录名称、再次双击；双击规则行；选中后查看左侧 | 三角单击、目录名称双击均切换展开/折叠；叶子双击进入编辑；只画目录展开三角，选中只用行高亮，不出现额外选中三角 |
| 13.21 | 悬停普通规则、带催化剂规则、多方案、不同数量要求及经验按源计规则，中英文分别查看 | 明确分行显示输入、催化剂、产出及数量；消耗件数跟随对应催化剂；只在适用时解释择一源匹配、方案选择、数量 / 和经验倍率；长名称按窗口宽度换行，无超长单行 |
| 13.22 | 查看消耗/仅存在催化剂，以及物品、实体、经验数量角标 | 只有消耗催化剂右上有橙色五角星；数量统一位于右下，白字带阴影；两类角标均无矩形背景；3D 模型与后续图标绘制无污染 |
| 13.23 | 管理页搜索、筛选、展开目录、选中规则并滚动后 resize；编辑页选择非首方案/动作、输入尚未提交或无效的数值、打开弹窗后 resize | resize 不重建控件、不提交输入、不改变草稿；保留搜索/筛选、展开/选中、页签、方案/动作、窄屏层级、输入缓冲、弹窗和焦点；滚动偏移仅随新视口正常钳制，缩放后点击命中正确 |
