# 开发场景与后端性能验证

Debug用于实测真实后端线路和为性能优化提供可比较数据。游戏指令直接创建场景，开发环境实时输出日志；实现集中在 `core/debug/`。可用 `/idtw debug pipeline p0`～`p4` 一键运行阶段，自动串行、清理后冷却5秒及失败停止；参数和完整验收顺序见 [实机测试流水线](debug-test-pipeline.md)，本机压力场景源实体上限为1000。

全部功能与性能场景共用开发目录与断言器。结算示例见 [后端结算示例](debug-settlement-examples.md)，运行 `/idtw debug examples` 可查看全部命令；扩展方式见 [统一技术路线](debug-scenario-extension.md)。

## 1. 在 IDEA 中开始

1. 使用JDK 21构建，运行IDEA已有的 **Fabric Client (:fabric)** 或 **NeoForge Client (:neoforge)**。Run和Debug都可使用；判断依据是加载器的development环境，不是是否附加Java调试器。
2. 创建独立测试世界，开启作弊，使用创造模式，保持游戏正常推进。运行目录已通过IDEA MCP核对：Fabric Client为`fabric/runs/client`，NeoForge Client为`neoforge/run`。
3. 在控制台搜索或过滤`[IDTW_DEBUG]`。进入世界后出现`event=READY`，自动展示当前加载的server.json参数、平台、Minecraft与Java版本、最大堆及生效规则数。
4. 站在开阔地，执行 `/idtw debug help`，再执行 `/idtw debug run convert`。无需创建规则文件、手动summon或瞄准已有掉落物。
5. 按聊天回执的`run=<编号>`过滤控制台，观察场景全过程；自动结束出现`event=END`，聊天显示通过、未通过或未完成。默认功能场景准备与预热后测量12墙钟秒，正常20 TPS下约14秒内完成。

发布环境不注册`/idtw debug`，也不启用场景计数、逐实体过程日志或自动READY日志。`server.json`原有`debug_logging`仍控制常规运行日志，和开发场景开关不同；场景日志使用INFO，不要求该字段为true。性能场景会抑制本轮源实体的常规重试/提交日志，避免该字段开启时刷屏。

## 2. 一条命令创建一个功能场景

每轮自动准备纸张源、只在内存有效的场景规则和预期值，沿真实Codec解码、RuleValidation校验、RuleIndex索引、实体加入、年龄排期、短路求值、退避、效果派发和产物入世界链路推进。计数来自实际后端事件，不重新执行诊断条件去猜结果。

| 指令 | 实际操作及应观察到的结果 | END重点证据 |
|---|---|---|
| `/idtw debug run convert` | 1张纸在年龄2游戏秒到期，隐式消耗后产出1个海晶碎片 | CONVERT=1，OUTPUT_ITEMS=1，留存产物1，PASS |
| `/idtw debug run retry` | 源Y坐标先低于条件min，真实失败并退避；第5游戏秒后自动把源上移3格，等待下一次检查产出1个海晶碎片 | CONDITION_FALSE与RETRY>0；ACTION后实际条件成立、转化1次 |
| `/idtw debug run delay` | 第2游戏秒提交并消耗源，产出效果延迟100 tick | 提交和OUTPUT_ADDED分开，first_output_delay_ticks>=100 |
| `/idtw debug run expiry` | 年龄设为实际lifespan-1，下一次实体tick进入平台自然消失入口，再经有预算队列最终检查 | NATURAL_EXPIRY_DEFERRED>0，实际提交年龄与寿命对应，产出1个海晶碎片 |
| `/idtw debug run stack` | 源实体包含16张纸，整堆提交一次并生成16个海晶碎片 | CONVERT=1，rounds=16，OUTPUT_ITEMS及留存产物均为16 |
| `/idtw debug run priority` | 同一源有2秒低优先级和8秒高优先级规则；2秒时只应选已到期的低优先级规则 | 高优先级AGE_NOT_READY；first_rule为debug/priority；不得产出高规则的金粒 |
| `/idtw debug run excluded` | 2个源分别添加死亡锁标签和无限寿命，均不进入追踪 | EXCLUDED=2，提交与产出均为0，两个源存活 |
| `/idtw debug run reload` | 100 tick延迟效果入队后调用真实全局reload，取消结算与效果 | ACTION_RELOAD，SETTLEMENT_CANCELLED=1，产出为0，无剩余场景效果任务 |

“游戏秒”按20个世界tick计算；测量时长按墙钟计算，低TPS时不能用肉眼秒数直接判定年龄逻辑。expiry目前使用原版NBT short设置年龄，实际寿命超过32767时明确拒绝该场景。

## 3. 场景控制与清理验收

```mcfunction
/idtw debug status
/idtw debug mark 肉眼看到源已消失，产物尚未出现
/idtw debug stop
```

- 每个服务器最多一轮场景，重复启动会拒绝；场景名固定，Tab可补全。
- 独立场景status反馈准备、预热、测量阶段与实体生成进度；流水线活动时status反馈阶段步骤及冷却。mark记录用户观察，区别于真实后端事件。
- stop立即停止本轮负载，流水线活动时同时取消所有后续步骤，冷却期间也有效；结果为INCOMPLETE。正常结束也会自动清理本轮源、产物及尚在队列中的场景任务。
- 可在 delay 的延迟效果已入队后执行 stop：应出现 INCOMPLETE，并取消已登记的场景效果。后端结算/恢复任务独立登记，remaining_scene_tasks=0不证明全部后端任务已结束；提前停止后若有待返还残留，请重建测试存档。普通任务不会因 stop 被整体清空。
- 测完一轮后等待数秒确认没有再次产物，再运行下一轮。正常退出世界或维度卸载会结束场景并释放引用；发起者离线后结果仍写入控制台。
- 本轮实体带`idtw_debug_fixture`标签和唯一custom_data，禁止拾取、关闭重力与合并。停止前可直接看见结果，不要手工改变测试实体。自动清理后产物不会留在背包中。

## 4. 更方便的实时日志校对

日志格式固定：

```text
[IDTW_DEBUG] run=<UUID> scene=run/convert event=START {当前配置、场景规则、负载、预期}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=TRACKED {实体UUID、年龄、下一次检查、寿命}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=CANDIDATE {实际规则ID、matched}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=CONVERT {真实提交、rounds}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=OUTPUT_ADDED {实际加入世界的实体与数量}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=FRAME {当前计数、队列、目标追踪状态}
[IDTW_DEBUG] run=<UUID> scene=run/convert event=END {expected、actual_events、window、verdict、清理后队列}
```

先看START确认当前生效配置、tick_rate和场景规则；再按同一run观察CONDITION_FALSE/TRUE、RETRY、EFFECT_SCHEDULED、EFFECT_BEGIN等事件。START包括明确的通用默认值，不从旧日志或磁盘配置猜当前生效值。

EFFECT_EXECUTOR_RETURNED仅说明执行器已返回，不代表异步分批作业全部结束。产出结论使用OUTPUT_ADDED和END的OUTPUT_ITEMS、live_output_items_at_end，并校对expected与verdict。

日志每行JSON独立完整，没有需要拼接的DATA分片，没有export_id或报告文件步骤。复制同一run从START到END的日志即可交给AI；错误时附带对应ERROR和异常堆栈。正常日志仍由游戏日志系统写入运行目录的`logs/latest.log`。

END窗口含本轮实际准备数量、开始测量前已发生的提交/产出、窗口内提交/产出、最终剩余源和留存产物数量、清理后队列与剩余场景任务；年龄未到就提交或源未正确消耗也会FAIL。FAIL表示预期不匹配；INCOMPLETE表示手动中断、停服、维度卸载或错误导致未完成，不能视作验证通过。

## 5. 性能优化前后的对比方案

先通过全部功能场景，再按 [实机测试流水线](debug-test-pipeline.md) 的100→250→500→1000档位逐级测试。当前档功能或性能不达标即停止加量；1000是硬上限，不是本机必须达到的目标。

快速对照示例（逐条执行，上一轮END后至少等待5秒）：

```mcfunction
/idtw debug bench baseline 20
/idtw debug bench normal 100 20
/idtw debug bench converted 100 20
/idtw debug bench convert 100 20
/idtw debug bench retry 100 20
```

- normal 无规则，验证原版掉落物实体成本；converted 在5游戏秒后转化；convert 在10游戏秒后转化；retry 持续条件失败，验证检查和退避。
- 非空性能场景默认1000源实体。normal/converted默认10秒，convert/retry默认60秒；baseline默认0实体/60秒。实体数允许1～1000，时长10～300墙钟秒。
- 所有场景通过统一 checks 校验。转化负载要求实际提交与留存产物均为请求数量，normal/retry要求零提交且源全部保留；PASS不自动表示性能达标。
- 保持同一平台、JVM/堆、位置、视距、模拟距离、规则参数及20 TPS。性能窗口锁定发起者视角，保持静止以避免额外纠正包；不在测量中mark、reload或改配置。
- 准备按配置分批（默认每tick最多128个源、2ms软预算），预热20个世界tick；准备与清理不混入测量窗口。固定0.25格网格，1000源约8×8格，唯一组件防合并，关闭重力。
- 短窗口和低TPS可能导致未完成转化；可以降低数量并用60秒确认机制。延长窗口不会消除CPU过载。
- 优化前后在相同、已达标档位各测至少3轮，保留全部run及构建标识，比较均值、分位数、积压和延期；不挑最快一轮，不直接相减p95/p99推断纯模组分位。

| END字段 | 解释与用途 |
| --- | --- |
| window.server_tick_cost | 服务端总tick耗时（微秒），含世界与其它模组工作 |
| window.checks / window.effects | 场景维度检查/效果队列的 mean_us、p95_us、p99_us 等 |
| window.observed_ticks_per_second | 实测完整服务端tick数/墙钟窗口 |
| window.world_ticks_advanced | 场景维度实际推进刻数 |
| window.index_changes | 测量期全局规则索引变更次数，性能测试应为0 |
| window.sample_limit_reached | 样本上限是否提前结束窗口 |
| conversions/output_items_in_window | 窗口内真实提交与产出，另有窗口开始前计数 |
| remaining_scene_tasks | 本轮登记任务清理结果，不包括全部后端结算/恢复任务 |
| checks/effects_pending_after_cleanup | 清理后的全局维度队列，可包含普通任务 |

分位数使用有界真实样本和nearest-rank计算，最多12000个tick样本；空窗口没有统计意义。队列成本含开发观测开销，前后对比需使用相同诊断版本。完整性能门槛与失败分流以流水线文档为准。

## 6. AI反馈模板

```text
平台：Fabric / NeoForge，IDEA运行配置：
源码commit或本轮构建标识：
执行的场景命令：
run编号：
肉眼观察的预期与实际差异：
优化前/后的对比轮次：
附件：同run的START到END控制台日志，有错误时附异常堆栈。
请先核对场景参数、实际事件、expected和verdict；区分准备/测量/清理，
再比较server_tick_cost、checks/effects、积压、延期与窗口吞吐，给出后端优化建议和证据。
```

## 7. 当前覆盖与限制

- 场景实际走统一后端和现有平台事件，不新增Mixin。场景规则通过正常Codec、校验与RuleIndex，但只在内存中绑定本轮UUID，不覆盖磁盘规则或替换用户全局索引，测试产物也不进入用户规则链。
- excluded验证死亡锁标签和无限寿命的后端排除语义，不验证真实玩家死亡掉落的两平台标签注入。资源加载/三层覆盖、标签重载、GUI编辑协议、真实区块卸载和第三方模组组合仍需对应实机验收。
- 性能窗口的队列范围为场景维度，服务端总体耗时范围为整个服务器；其他普通实体和模组仍可能污染基线。受控纸张转海晶碎片负载不代表爆炸、战利品、催化剂等复杂效果性能。
- 正常停止、停服与维度卸载会清理；进程强杀不能保证清理。若测试世界重启后有残留fixture，可在该测试世界执行 `/kill @e[type=minecraft:item,tag=idtw_debug_fixture]`，不会匹配普通未标记掉落物。
- 清理只查询已加载实体，不强制加载区块或修改卸载区块的实体存档；若测试期间离开使区块卸载，回来后按fixture标签清理残留。为保持受控工况，整轮留在生成区域。
- 压力场景最多1000源实体、64条mark；mark最多160个UTF-16单元并保留完整代理对。IDEA没有实时推进游戏时不会虚构成功结果。
- 本轮游戏验证交由用户完成；本页“预期”是验收条件，不是已经观测到的游戏结果。

## 8. 开发验证与反馈标识

验证顺序为 IDEA MCP 工作区检查，再执行 `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`，不直接运行Gradle。游戏内验收交由用户，本页预期不代表已经实测通过。

反馈时附当前源码commit、未提交改动和同run的START～END日志。jar哈希如需使用，应从本次实际构建产物计算，不复用历史构建哈希。
