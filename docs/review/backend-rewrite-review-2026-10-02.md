# 后端重构进度与代码审查（2026-10-02）

> 本文记录修复前基线，保留作为历史审查证据；当前修复结果见 [后端收尾记录](backend-rewrite-closeout-2026-10-02.md)，不应把下列问题直接视为当前未修问题。

审查基线：分支 `1.21.1`，HEAD `ab35807`；审查开始时 Git 工作区干净。对照重构规划书（`plan-backend-rewrite.md`，现已归档至 `docs/archive/`），覆盖新模型、加载合并、运行时、效果/条件、编辑保存、平台入口和清场依赖。本次只新增审查报告，没有修改业务代码或原计划，没有启动 Minecraft，没有新增 test 文件。

## 结论与实际进度

**阶段①～⑤的主要实现已经落地，但“代码落地”尚不能等同于“验收完成”；阶段⑥尚未执行。当前新链路存在阻断切换的正确性、保存安全和性能问题，不建议直接清除旧链路。**

| 阶段 | 代码现状 | 本次复核结论 |
|---|---|---|
| ① 地基 | Rule/Effect/Condition、Codec、三层读取与覆盖合并已存在 | 已落地；同层数据包覆盖顺序错误，源引用校验不完整 |
| ② 注册体系与内置类型 | 12 个效果、10 个条件、类型分发和类型参数校验已存在 | 已落地；第三方注册 SPI、类型参数未知字段检测仍缺失 |
| ③ 运行时 | 两端 host/events、索引、分桶、退避、reload 回扫、效果执行器已存在 | 已落地；计时、排除项、零延迟任务、消失期限、源快照、延迟任务生命周期存在问题 |
| ④ 网络与 GUI | per-player 会话、版本戳、变更集、C2S 分片、新视图模型与界面已存在 | 已落地；未在写盘前校验规则，存在越界路径和数据覆盖风险，版本戳未覆盖所有写入口 |
| ⑤ 命令、数据与文档 | config/rule/debug 命令树、6 个禁用示例、docs/dev 四篇和 ADR 已存在 | 主要交付已落地；convert 存在 ID 冲突，实机验收与若干承诺未完成，计划表的“收尾中”状态也未统一 |
| ⑥ 切换与清场 | 旧初始化、旧事件、旧网络、旧命令、旧配置/GUI 类仍存在 | 尚未切换；新旧运行时同时注册；共享 GUI 组件不能按目录一起删除 |

不使用“完成 5/6 = 83%”作为质量结论：最后阶段还承接前序缺陷和实机验收，工作量不等分。

## 已确认的问题

严重程度：P1 为应在切换前修复的问题；P2 为明确的性能/校验缺口。下列结论来自代码路径与本地依赖源码；复现步骤是交给用户的验收场景，本次未进行游戏内复现。

### B01 · P1：同一来源层内的数据包覆盖顺序反了

证据：[DatapackRuleReader.java L87](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/load/DatapackRuleReader.java:87)、[DatapackRuleReader.java L99](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/load/DatapackRuleReader.java:99)、[RuleMerger.java L81](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/load/RuleMerger.java:81)。

读取器假定 `listPacks()` 按“高→低”排列，再按 rank 降序应用。原版实际是：`MultiPackResourceManager` 按传入顺序 push，并原样返回 `packs.stream()`；`FallbackResourceManager.getResource()` 从尾部向前选最高优先级资源。因此 rank 大的包优先级更高。当前排序让高优先级包先应用、低优先级包后覆盖。

复核源码：vanilla merged jar 中 `MultiPackResourceManager.class` L29/L34/L115；`FallbackResourceManager.class` L59/L67/L96/L136，通过 IDEA MCP 读取。

复现：两份 WORLD 数据包声明同一规则 ID，分别指定不同产物；将其中一份设为高优先级，检查 `config list` 与实际产物。当前低优先级规则可能胜出，文件路径不必相同。

建议：保持跨层优先级，层内按原版真实包顺序从低到高应用；同步修正注释，验证同文件与不同文件中的同 ID 两种情况。

### B02 · P1：最短候选计时会让其它规则提前执行

证据：[ConversionRuntime.java L210](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:210)、[ConversionRuntime.java L279](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:279)。

首次排期使用全部候选的最短触发时间，但到期选择仅检查优先级和条件，未检查每条规则自身是否已到期。比如同源 A：高优先级、300 秒；B：低优先级、1 秒；A 条件成立时，A 会在 B 的 1 秒到期时被执行。

此外，排期后直接相信 gameTime；未复核实时 age。实体暂停 ticking、合并改变 age 等情况下，绝对年龄契约同样缺少保护。后者属于需游戏内验证的具体场景，前者可直接由代码推导。

建议：明确“已到期候选”再选择最高优先级规则，按实时 age 和 lifespan 判断；尚未到期的候选应排到下一有效时刻，避免用退避替代规则时钟。秒转 tick 使用 long，当前 int 乘法也可能溢出。

### B03 · P1：直接物品索引绕过 source.exclude

证据：[RuleIndex.java L66](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuleIndex.java:66)、[RuleIndex.java L103](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuleIndex.java:103)、[RuleIndex.java L110](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuleIndex.java:110)；完整匹配契约见 [SourceMatcher.java L39](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/model/SourceMatcher.java:39)。

直接命中的规则直接进入候选列表；只有标签规则遍历调用 `source.matches()`，且标签遍历不会移除已加入的直接命中。

复现：`source.items=["minecraft:oak_log"]`，`source.exclude=["#minecraft:logs"]`。这两个表达式不同，能够通过当前重复项校验，但 oak_log 仍会转化。混合“直接项 + tag 项”的规则也受影响。

建议：直接索引只作粗筛，首次生成 item 候选缓存时对所有命中规则统一执行完整 SourceMatcher；保持 exclude 优先。

### B04 · P1：执行中的零延迟任务进入过去桶，永不执行

证据：[TickScheduler.java L28](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/TickScheduler.java:28)、[TickScheduler.java L37](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/TickScheduler.java:37)、[RuntimeEffectContext.java L78](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeEffectContext.java:78)、[LightningExecutor.java L32](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/LightningExecutor.java:32)、[ArrowRainExecutor.java L47](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/ArrowRainExecutor.java:47)。

`runDue(now)` 先移除 now 桶。执行器在该过程中 `schedule(now, 0)`，重新创建 now 桶；以后只读取新 tick 的精确桶，旧桶一直滞留。EffectContext 的注释还明确要求 delay=0 立即执行。

复现：lightning.count=1 或 arrow_rain.count=1。源物品可被消耗，但唯一的闪电/箭矢不执行；pending 增加且不下降。count>1 时至少第一个任务漏执行。运行时执行期间生成触发时间已经到达的新源实体，也可能排入这一过去桶。

建议：明确当前执行轮次的零延迟入队语义，采用可继续出队且受预算约束的就绪队列；至少不得遗留 past-due 桶。避免简单无限递归执行导致新的性能问题。

### B05 · P1：预算顺延和退避可能越过原版 discard

证据：[TickScheduler.java L45](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/TickScheduler.java:45)、[ConversionRuntime.java L218](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:218)、[ConversionRuntime.java L245](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:245)、[ConversionRuntime.java L260](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:260)；两端都在 level tick 结束时运行。

当前把自然期限安排在 lifespan-1，但预算超出后顺延到下一 tick。原版 `ItemEntity.tick()` 先增加 age，并在 age>=6000 时 discard，随后才到 level tick 结束回调。因此被顺延的物品可能已经消失，`attempt()` 只能移除追踪。失败重试也没有把 backoff 限定到剩余自然期限内。

复现：大量分散、不会合并的物品同时在 age=5999 到期，数量超过默认 512。第一批可转化，其余在下一 tick 判定前自然消失。另一个场景是第一次条件失败后，重试时刻超过剩余寿命，最后一次机会也会丢失。

建议：设计独立的到期保护/期限处理。常规检查保留预算，自然消失边界需要可靠事件或最小平台入口保障；重试不能越过期限。该修复涉及平台/API/Mixin 选择，实施前按项目复杂任务规则拆分并确认方案。

### B06 · P1：先消耗源物品，后续 use_source_block 失去物品信息

证据：[ConversionRuntime.java L302](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:302)、[RuntimeEffectContext.java L58](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeEffectContext.java:58)、[ConsumeSourceExecutor.java L38](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/ConsumeSourceExecutor.java:38)、[PlaceBlockExecutor.java L72](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/PlaceBlockExecutor.java:72)。

上下文没有保存触发时源堆叠；`sourceStack()` 每次从实时实体复制，removed 时返回 EMPTY。只配置 `place_block(use_source_block=true)` 的规则会先隐式消耗整堆、discard，再从 EMPTY 推导方块，得到 AIR 并报错。显式 consume_source 放在 place_block 之前、或源已消失的延迟放置，同样失效。

建议：在任何效果执行前保存不可变的源 ItemStack 快照；源信息读取使用快照，实时消耗另用实体/UUID。这样才能满足“延迟效果不依赖源实体存活”的契约。

### B07 · P1：新链路忽略死亡掉落锁

证据：[ConversionRuntime.java L108](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:108)、[ConversionRuntime.java L245](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:245)；旧保护见 [ConversionTracker.java L34](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/server/conversion/ConversionTracker.java:34)；标记入口见 [EntityMixin.java L21](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/fabric/src/main/java/com/meteorite/itemdespawntowhat/mixin/EntityMixin.java:21)、[ItemConversionEvent.java L35](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/neoforge/src/main/java/com/meteorite/itemdespawntowhat/server/event/ItemConversionEvent.java:35)。

平台仍会写 CHECK_LOCK_TAG，但新运行时不读取该 tag。TrackedState.locked 只防止转化重入，与死亡掉落保护无关。因此命中新规则的玩家死亡掉落可参与转化；旧链路的两端标记范围差异也没有真正闭环。

建议：把掉落物是否可参与追踪的策略移入新链路，并统一玩家死亡掉落保护的两端入口。清场时不能连 NeoForge 的死亡标记逻辑一起删掉而不迁移。

### B08 · P1：服务端先落盘、后校验，非法修改可以覆盖有效规则

证据：[RuleEditChangeSet.java L39](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/protocol/RuleEditChangeSet.java:39)、[RuleEdit.java L63](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/protocol/RuleEdit.java:63)、[RuleEditServerHandler.java L113](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/transport/RuleEditServerHandler.java:113)、[RuleEditServerHandler.java L189](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/transport/RuleEditServerHandler.java:189)。

协议解析只验证 edit 的 ID/action/JSON 对象形状。服务端没有调用规则 Codec 和注册表感知语义校验，就写覆盖层并 bumpVersion；随后 reload 才拒载非法规则。覆盖一个原本有效的 ID 时，旧内容已替换，新规则却不生效，回执仍使用 save_success。文件写入失败也继续推进版本并发送成功类型回执，只附问题数量。

复现：通过 GUI 填入格式合法但不存在的产物 ID，或提交 count 越界、空 source 等变更，修改已有有效规则；核对磁盘、validate 与回执。

建议：落盘前逐条解码与语义校验，控制条目走独立校验；非法变更不得覆盖旧数据。约定整批拒绝还是逐条拒绝并明确回执。写入失败/部分成功要返回实际结果，版本只随成功提交推进。

### B09 · P1：合法 ResourceLocation 不等于安全文件路径

证据：[RuleEdit.java L67](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/protocol/RuleEdit.java:67)、[RuleOverlayWriter.java L117](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:117)。

新规则目标路径直接拼接 `id.getPath()`，未拒绝 `..`、绝对路径，也未检查 normalize 后仍在 rules 根内。原版 ResourceLocation.validPathChar 允许 `.` 与 `/`，不拒绝 `../`。

本次只读路径演算已确认：ID `itemdespawntowhat:../../server` 对应目标会从 `rules/itemdespawntowhat/../../server.json` 归一化为覆盖层根的 `server.json`。更多上跳可越过覆盖层。需要有编辑权限才能走该入口，但 OP 游戏权限不应赋予覆盖其它服务器文件的能力。

建议：拒绝根路径、空/点/上跳路径段，normalize 后检查 startsWith 预期根目录；考虑已存在符号链接的真实路径边界。不要实际写越界文件来验证。

### B10 · P1：写盘器仍可能删除未编辑的数据或覆盖坏文件

证据：[RuleOverlayWriter.java L159](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:159)、[RuleOverlayWriter.java L166](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:166)、[RuleOverlayWriter.java L117](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:117)。

读取规则数组时只保存 JsonObject，非对象条目被丢掉。编辑同文件其它规则后，这些未编辑原始条目永久消失。语法损坏文件虽然跳过，但没有保留占用路径；新规则 ID 对应到同一目标路径时，会按“新文件”整份覆盖损坏文件。

复现：已有数组 `[有效规则, 42]`，修改有效规则；或已有损坏的 `rules/test/a.json`，新建 ID=test:a。备份会留下上一份内容，但权威文件仍被覆盖，不能称 A1 无条件闭环。

建议：保留完整 JsonElement 数组，只修改明确选中的对象；把无法解析文件记录为不可写占用路径，目标冲突显式拒绝。备份是补救，不代替保留未编辑数据的契约。

### B11 · P1：延迟效果未实现“区块卸载保留”，reload 还会清空待执行效果

证据：[RuntimeEffectContext.java L78](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeEffectContext.java:78)、[ConversionRuntime.java L87](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:87)、[ConversionRuntime.java L137](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:137)、[PlaceBlockExecutor.java L51](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/PlaceBlockExecutor.java:51)。

延迟任务是直接捕获 level/item/context 的 Runnable，没有目标区块状态检查或卸载暂存逻辑。任务到期会执行；读写方块的路径可能请求目标区块，生成实体的路径可能失败，也不会等待区块重新加载。

另外 `replaceRules()` 和 `rescan()` 均 clear 整个 scheduler；已经扣掉源物品、等待产出的任务会在 GUI 保存或 /reload 时无条件丢失。源已经被消耗，回扫无法恢复。规划只允许服务器重启丢失延迟任务，没有授权 reload 丢失。

建议：分离“源检查排期”与“已提交延迟效果”；任务以维度 key、目标位置和必要数据组织，区块不可执行时保留，加载后继续。规则 reload 重建检查索引，不清除已发生转化的后续效果。维度整体卸载与服务器停止的行为另行明确。

### B12 · P1：convert 的 ID 未包含旧文件来源，会相互冲突或覆盖已有新规则

证据：[RuleConvertService.java L90](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/command/RuleConvertService.java:90)、[RuleConvertService.java L97](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/command/RuleConvertService.java:97)、[RuleOverlayWriter.java L85](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:85)、[RuleOverlayWriter.java L108](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/RuleOverlayWriter.java:108)。

每个旧文件 index 从 0 开始，输出一律是传入 namespace:type_index。不同目录下同名类型文件产生同一 ID，writer 跳过后者；再次 convert 或已经存在相同新 ID 时又会 UPSERT 覆盖已有编辑。Report.converted 用的是 edits.size，包含未实际写成的冲突项。

复现：旧裸路径与命名空间路径同时保留同类型文件；或者先转换、编辑新规则，再执行 convert。核对条数和新规则内容。

建议：ID 纳入旧文件来源、旧命名空间与稳定序号；目标 ID 已存在时拒绝覆盖或提供显式受控策略。报告区分映射成功、写入成功、重复、拒绝，备份失败也应如实计数。

### B13 · P1：版本戳只覆盖 GUI 保存，convert/外部 reload 可以绕过冲突检测

证据：[EditSessionManager.java L75](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/service/EditSessionManager.java:75)、[RuleEditServerHandler.java L117](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/network/transport/RuleEditServerHandler.java:117)、[RuleConfigCommands.java L103](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/command/RuleConfigCommands.java:103)；IDEA Incoming Calls 确认 bumpVersion 只有 handleChangeSet 一个调用入口。

convert 会修改规则并 reload，但没有推进版本。手工修改 JSON 后 reload、数据包变更同样没有快照修订号推进。玩家拿旧草稿保存时仍能携带相同版本，服务端判为一致并覆盖更新。

附带 GUI 问题：[RuleListScreen.java L74](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen/RuleListScreen.java:74) 只比较 version；同版本刷新返回了新内容，session 已更新，列表却不重建。

建议：所有权威写入统一走提交入口；版本绑定编辑快照实际内容/修订，涵盖会影响编辑视图的 reload。客户端使用独立的快照到达序号刷新列表，不把持久化版本兼任 UI 刷新信号。

### B14 · P1：任务数量预算无法限制单任务的大量世界操作

证据：[TickScheduler.java L44](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/TickScheduler.java:44)、[SpawnEntityExecutor.java L37](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/SpawnEntityExecutor.java:37)、[PlaceBlockExecutor.java L46](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/PlaceBlockExecutor.java:46)、[SpawnXpExecutor.java L25](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/SpawnXpExecutor.java:25)。旧安全边界参考 [ConversionLimits.java L11](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/config/ConversionLimits.java:11)。

一个 attempt 消耗一个预算，却可顺序执行任意数量效果。合法配置 spawn_entity.count=64、默认隐式 consume_source、64 个源物品，可在一个任务内生成 4096 个实体。spawn_xp.amount=65536 同样会按 rounds 放大。place_block(radius=32,limit 已配置) 的上限统计最坏读取 65³=274625 个方块，随后放置仍在单次执行中完成。

因此“max_checks_per_tick=512”不是工作量或时间上限，单个规则就能突破一整个 tick 的可接受成本。实际毫秒数尚未测量，不能仅凭静态分析宣称 TPS 达标。

建议：把生成/放置等重操作拆成可预算执行的小批；限制每次转化总产物/扫描工作量，并增加时间预算/延期策略。迁移旧链路的性能保护意图，不能只保留参数范围并乘 rounds。避免为了性能静默截断产物；剩余工作要可继续完成且可观察。

### B15 · P2：全量 prune 和无法取消的任务抵消了到期调度收益

证据：[ConversionRuntime.java L126](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:126)、[ConversionRuntime.java L383](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:383)、[ConversionRuntime.java L221](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/ConversionRuntime.java:221)、[TickScheduler.java L43](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/TickScheduler.java:43)。

默认每 20 tick 全量遍历 tracked，逐项 getEntity 和查候选，不受检查预算约束。10000 个长计时物品也会周期性形成 10000 项清理尖峰。拾取/合并/区块卸载后，清理只移除 tracked，不取消队列里的 Runnable；lambda 中的 entity.getUUID() 捕获的是实体本身，实体仍可能被保留到原到期时刻。

过载时 runDue 还会扫描全部 due，并逐个复制未执行任务到下一桶；预算只限制 task.run 次数，不限制搬运成本。连续积压下会重复搬运。

建议：实体离开入口及时移除/取消，使用 UUID 与追踪代数校验过期任务；清理分批或按桶进行。检查和延迟效果队列分开，超额就绪任务保留游标/队列，避免每 tick 重抄积压任务。

### B16 · P2：气候缓存无限增长，并以比实际采样更细的键缓存

证据：[RuntimeClimateSampler.java L24](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeClimateSampler.java:24)、[RuntimeClimateSampler.java L36](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeClimateSampler.java:36)、[RuntimeClimateSampler.java L42](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/RuntimeClimateSampler.java:42)。

缓存只在 reload 或维度释放时清空，长期服探索新坐标会持续增长。采样输入实际是 QuartPos（4×4×4），缓存却以每个 BlockPos 为键；同一 quart cell 最多重复保存 64 份等价采样值。此项是计划已知遗留，当前仍未修复。

建议：用 quart 坐标的压缩键，设置容量上限或按区块卸载失效；debug biome 当前每次新建 sampler，缓存复用的边界也应明确。记录命中率与容量，防止用无界缓存换 CPU。

### B17 · P2：validate 仍无法发现不存在的源物品和战利品表

证据：[SourceEntry.java L16](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/model/SourceEntry.java:16)、[RuleValidation.java L149](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/model/RuleValidation.java:149)、[LootTableEffect.java L44](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/LootTableEffect.java:44)、[LootTableEffect.java L60](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/LootTableEffect.java:60)。

source 只解析 TaggedId 的字符串格式，语义校验只检查空列表/重复/匹配排除冲突，不检查源物品注册表存在性。loot_table 参数使用 ResourceLocation.CODEC，仅校验非空与 luck；“数据包机制保证存在”的注释不适用于这套自定义 JSON 规则引用。

复现：有效规则的 source 写成格式合法但不存在的 `minecraft:does_not_exist`，或引用不存在的 loot table；validate 不能给出对应引用错误。具体运行结果需用户实机验证，但校验缺口已确认。

建议：加载期以实际注册表/服务端 reloadable registries 验证直接引用；缺失/空 tag 保持原先有意设计的告警策略，避免误拒载尚未绑定的标签。类型参数未知字段检测也仍属于未完成的统一校验工作。

## 性能取舍：已有收益与需要补齐的部分

已确认值得保留：按物品缓存候选列表、源 tag 懒展开、DNF 短路、标签缓存、效果级单次 limit 查询、整堆 rounds 计算、没有候选时不追踪。A8 中“同一结果上限在选择/提升/执行反复扫描”的旧结构已明显减少，但密集场景的查询成本尚未消除。

| 热点 | 当前成本与问题 | 下一步可执行优化 |
|---|---|---|
| 等待到期的 tracked | 每 20 tick 全量 prune，且未取消陈旧任务 | 事件移除 + 有预算的增量清理 + 任务代数失效 |
| 同 tick 大量到期 | 重复遍历/搬运 backlog；自然期限会丢任务 | 就绪队列保留游标，期限任务独立保障 |
| catalyst_present / consume_catalyst | 先完整 AABB 查询；consume 对含无关物品的列表全量排序，再筛匹配项 | 查询前/查询回调中筛选目标；必要时建立本 tick 可更新的空间统计，消耗后及时失效 |
| spawn_item / spawn_entity 的 limit | getEntities… 先收集全列表，“到 limit 早停”只减少后面的 Java 累加，未停止原版空间查询 | 使用可中止的实体查询/统计，按真实 API 能力选方案；缓存必须在本 tick 生成/消耗后更新，避免越过 limit |
| place_block | 立方体统计 O(r³)，layerPositions 反复扫描每层方形包围盒并分配列表 | 预算扫描/放置；预计算有上限的相对偏移并按需迭代；不要缓存世界状态而不处理变化 |
| climate | 无界 BlockPos Map，相同 quart 输入重复缓存 | quart key + 上限/失效策略 |
| 保存/请求快照 | 服务端主线程多次读全目录、解码、回扫；没有内容修订关联缓存 | 修订级快照缓存；prepare/apply 分工；保证文件写入和运行时提交的先后契约 |

旧的 [PositionCachedConditionChecker.java L17](D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult/common/src/main/java/com/meteorite/itemdespawntowhat/condition/checker/PositionCachedConditionChecker.java:17) 在实体位置不变时永久复用结果，不能直接搬到新链路：周边方块、遮挡等世界状态仍会变化。宜用同 tick 复用、明确失效事件或严格定义的短 TTL；对 TTL 带来的可见延迟需明确约定。

另需区分**已选择的效果语义**与**优化缺口**：新链路按源数量计算 rounds，旧 computeActualRounds 同时受催化剂与产物容量限制；当前先扣源、后产物 limit 收敛可消耗整堆而只产出少量，催化剂不足也不减少其它效果 rounds。规划允许有序非事务效果，所以本报告不擅自把这种设计当成必须回滚的 bug；但“恢复旧 computeActualRounds 的语义”表述不准确，切换前应明确接受哪些新行为。吞掉异常/静默截断产物同样不能作为性能优化。

建议用户实测至少覆盖：10000 个长计时等待物品、10000 个同时到期物品、同格高密度催化剂/limit 条件、较大 radius 放置、spawn_entity 高倍率、climate 长时间移动采样。记录 mod 检查/世界效果/清理分别的耗时、P95/P99、最大 backlog 与最老延期，而不是只观察平均 TPS。当前 debug stats 只提供数量与配置，没有这些耗时指标。

## A1～A10 的复核状态

| 原缺陷 | 当前结论 |
|---|---|
| A1 保存误删 | 变更集和按磁盘编辑修复了旧快照丢 disabled 的核心原因；B08/B10 仍可损害权威文件，不能称完全闭环 |
| A2 秒单位错误 | 已改为秒×20；B02/B05 表明每条规则实际触发契约尚未闭环 |
| A3 reload 不回扫 | 已有两端全维度回扫，静态路径成立；B11 提醒 reload 清队列会误伤已提交效果 |
| A4 ServerLevel 泄漏 | 已有卸载/停止清理，这是实际改进；“完全不持有 ServerLevel”的文字不成立，缓存和任务捕获仍持有引用，B04/B15 会延长留存 |
| A5 死亡掉落两端差异 | 新链路不读取死亡锁，B07 未闭环；旧 Fabric 全生物标记也仍存在 |
| A6 lifespan 差异 | 已有平台 helper；NeoForge 使用 ItemStack#getEntityLifespan，Fabric 用 server.json 兜底，第三方寿命兼容仍属已知限制 |
| A7 引用身份比较 | 新链路直接从当前 index 选择，不再使用旧 DTO 引用比较；但选择时需补 B02 |
| A8 反复上限扫描 | 去掉旧选择/提升阶段的重复查询；单次查询、密集场景和重操作预算仍需 B14/B15 及空间查询优化 |
| A9 加载写回 | 新规则读取链路不做旧式迁移写回；server.json 缺失生成默认配置属于独立明确行为 |
| A10 校验策略不一 | 加载统一入口已落地；保存入口 B08、引用完整性 B17、未知字段遗留仍未闭环 |

## 切换前的其它待办与文档差异

1. **清场按类和职责执行。** 新 RuleEditScreen 仍 import FormRenderer、FormFieldContext、FormListPanel；RuleFormBuilder 复用 FormDefinition/FormField；ConditionParameterInput 继承 FormFieldInput。规划阶段⑥把 client/ui/form、panel 整目录列为删除目标，直接照做会破坏新 GUI。先迁出/保留通用组件，再删 BuiltinFormDefinitions、旧 DTO 适配器、旧 list/presentation 等旧绑定。Constants 与平台 helper 同样先查仍被使用的能力。
2. **第三方 SPI 未交付。** BuiltinConditionTypes/BuiltinEffectTypes 构造后立即 freeze，没有对外插入时机。普通后端新增类型约 3 处；包含 GUI 支持并不总能满足全工程 ≤3 处，EffectParams/ConditionParams/模板/i18n 还需同步，指南与计划口径应统一。
3. **自定义 idtw 规则 JSON 不会因为放在 data/ 下就自动作为原版动态注册表同步。** 当前 GUI 实际依靠服务端 RuleSnapshotAssembler 编码并发送基底，因而存在可用同步路径；“基底由原版自动同步”的架构表述与当前实现不同。切换前确认实际协议契约，勿移除这部分快照而期待原版代发。
4. **未知字段与 i18n 未完全完成。** 规则顶层只告警；类型参数缺少字段集检测。编辑错误/Issue.format 仍有中文直出，两端语言文件 key 一致不能证明所有用户可见内容均本地化。
5. **包层判定仍是 substring 启发式。** byPackIdToken 把任意包 ID 含 itemdespawntowhat 的世界包视为 BUILTIN；例如带该词的世界包文件名可能被误分类。应从平台包来源准确判定。此项计划已列为实机验收遗留，不能因新增示例 JSON 就视为验证完成。
6. **重载异常保留旧索引的说明不完全可靠。** 两端 host 的 loadRules 捕获 RuntimeException 后返回空集合，applyReload 会安装空索引；其外层“保留旧索引”的 catch 捕捉不到已经转为空结果的错误。需区分正常逐条拒载与整次加载失败。
7. **退避首步为 2 秒。** attempt 先 failureCount++，再传 backoffTicks；默认实际 40→80→100 tick，而不是约定 20→40→80→100。修正计数基准即可。
8. **承诺的实机验收未完成。** 专用服务端连接、数据包层级、世界操作、GUI 行为/视觉冻结、10000 掉落物性能都没有本次实测证据。GUI 类已经替换，不能仅以 build 成功宣称视觉无变化。

## 本次验证及限制

- IDEA MCP Git 状态：审查开始时工作区干净，没有未提交工作区文件可供“变更文件检查”。
- 扩展抽查 23 个新链路关键 Java 文件：**0 ERROR、52 WARNING**，未报告超时。包含 runtime、loader、writer、handler、平台 host/events、payload registrar、命令、GUI/session。警告多为未使用参数/import、可简化代码和可空分析；不把所有提示都当作真实运行时缺陷，也不忽略项目“零警告”验收条件。
- 在 IDEA 检查后运行 `.\gradlew.bat build`：**BUILD SUCCESSFUL in 9s**；30 actionable tasks，3 executed、27 up-to-date，common/Fabric/NeoForge 均通过。这是正常增量构建；没有执行 clean/强制重编译。各模块 test 为 NO-SOURCE。
- 首次沙箱构建因 C:\.gradle 锁文件不可写失败；经工具授权在同一命令下重试通过。两次没有并发；已确认运行 session 结束。
- common resources 的 8 个 JSON 可解析，en_us/zh_cn key 集合一致；两端最终 jar 均包含 6 个内置规则文件。
- 静态 import 检查：common 未发现 Fabric/NeoForge import；core 及扫描的服务端/旧网络/旧命令路径未发现客户端 import。该检查针对源码直接引用，未进行专用服务端运行验证。
- 用 IDEA 的调用层级核对 schedule 与 bumpVersion 调用者；用本地 vanilla merged jar 源码核对数据包优先级、ItemEntity age/discard 与 ResourceLocation 字符约束。
- **已知限制**：未运行游戏、未测 TPS/MSPT、未实际发送特殊保存包、未验证包 ID 的平台实值。本报告中的复现场景均是待用户执行的验收步骤。
- **假设**：继续以规划书的有序非事务效果、死亡掉落保护、到期前执行、区块卸载保留和三层覆盖契约作为目标；如用户已有新决定，应先更新目标，再调整对应建议。

## 建议执行顺序

1. **先做保存安全闭环：B08/B09/B10/B12/B13。** 验收：非法修改保留原文件；路径越界被拒；坏文件不被无关操作覆盖；convert 不冲突、不覆盖已编辑新规则；所有写入口产生可靠版本变更。
2. **修规则选择与基础调度：B01/B02/B03/B04/B06。** 验收：包优先级正确、逐规则计时正确、exclude 生效、单发闪电/箭雨有效、消耗后仍可按源方块放置。
3. **处理平台和生命周期：B05/B07/B11。** 这是跨平台/可能涉及 Mixin 的复杂子任务；按项目规则先确定拆分与入口。验收覆盖自然期限超预算、玩家死亡、区块卸载再加载、转化后 reload 的延迟效果。
4. **完成性能闭环：B14/B15/B16 与热点表。** 增加真实工作量/时间预算和观测，再交用户执行上述负载场景；同时补 B17 和计划遗留的参数未知字段/i18n/SPI。
5. **最后执行阶段⑥。** 按类迁移共享组件和保护逻辑，停用旧入口，删除旧专属实现，保留显式迁移能力；核对无旧模型引用，再按 IDEA→build 顺序验证，最后做两端实机验收。旧配置清理不能把用户唯一存档/备份直接删掉。
