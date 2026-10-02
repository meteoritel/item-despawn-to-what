# 开发场景与后端性能验证

Debug用于实测真实后端线路和为性能优化提供可比较数据。游戏指令直接创建场景，开发环境实时输出日志；实现集中在 `core/debug/`。

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
| `/idtw debug run reload` | 提交100 tick延迟产出后调用真实规则reload并回扫；已提交效果继续完成 | ACTION_RELOAD，index_changes>0，延迟产出1次，没有重复提交 |

“游戏秒”按20个世界tick计算；测量时长按墙钟计算，低TPS时不能用肉眼秒数直接判定年龄逻辑。expiry目前使用原版NBT short设置年龄，实际寿命超过32767时明确拒绝该场景。

## 3. 场景控制与清理验收

```mcfunction
/idtw debug status
/idtw debug mark 肉眼看到源已消失，产物尚未出现
/idtw debug stop
```

- 每个服务器最多一轮场景，重复启动会拒绝；场景名固定，Tab可补全。
- status只反馈准备、预热、测量阶段与实体生成进度；mark记录用户观察，区别于真实后端事件。
- stop立即停止本轮负载，结果为INCOMPLETE；不能把手动提前停止当作PASS。正常结束也会自动清理本轮源、产物及尚在队列中的场景任务。
- 再启动一轮delay，在源提交后立刻stop：应出现INCOMPLETE、remaining_scene_tasks=0，后续不再生成本轮延迟产物。普通实体、普通规则的延迟任务不会被清空。
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

先完成全部功能场景，再开始性能测量。保持同一平台、JVM参数、堆大小、地图位置、模拟距离、实体数、规则参数与tick_rate。默认20 TPS；不得用提高刻率或跳tick来宣称达到20 TPS目标。

在开阔约30×30格且区块已加载的位置逐轮执行，等上一轮END与清理结束后再执行下一条：

```mcfunction
/idtw debug bench baseline 60
/idtw debug bench convert 1000 60
/idtw debug bench retry 1000 60
/idtw debug bench convert 10000 60
/idtw debug bench retry 10000 60
```

baseline不添加实体，测量当前世界背景成本；convert生成真实年龄10游戏秒后到期的转化负载；retry生成相同数量、年龄10游戏秒到期、条件始终不满足的负载，持续测试退避和检查成本。

### 5.1 10000 实体对照：normal 对比 converted

用于隔离“模组对掉落物的处理开销”。两组都在生成高峰之后测连续10秒窗口（沿用准备→预热20tick→测量相位，生成耗时不混入窗口）。

```mcfunction
/idtw debug bench normal 10000 10
/idtw debug bench converted 10000 10
```

- `normal`生成10000个纸源但**不绑定任何规则**，实体不被追踪，只承担原版实体tick成本，作为对照组。
- `converted`生成10000个纸源并绑定转化规则（trigger为5游戏秒，比默认10秒更早到期，为10秒窗口留足完成裕度），被追踪并在窗口内完成转化与产物生成，作为转化组。
- 两组默认10000实体、10秒；可用 `[实体数] [秒数]` 覆盖（实体1～10000，秒数10～300）。原 `convert`/`retry`/`baseline` 语义不变。
- 测量窗口内发起者视角被固定（位置与朝向都会纠正），并在动作栏显示剩余秒数倒计时，结束时自动清除。玩家持续移动鼠标/按键时每tick会收到纠正包，`server_tick_cost` 会略含这部分开销；玩家静止时该开销为零。
- 完成判定沿用 `verify()`：`converted` 要求窗口内10000次转化全部完成且留存产物10000，`normal` 要求0次转化、10000源全部存活。若实测TPS过低（约低于12），10秒墙钟对应的世界tick不足，转化未全部完成会使END判FAIL，此时应延长窗口或降低实体数。
- 对比口径：同平台、同JVM/堆/视距/位置/规则参数下各跑≥3轮，比较 `server_tick_cost` 与 checks/effects 队列的均值与分位数，以及 `observed_ticks_per_second`；两组之间只差“是否被模组追踪并转化”，不要把单轮极值当作稳定工况。

- 实体数1～10000，测量10～300墙钟秒。默认1000实体和60秒；基线默认60秒。推荐60秒，短窗口/严重低TPS可能在结束前尚未全部到期，END会按实际判定FAIL。
- 持续失败负载仍遵循真实自然寿命；默认寿命300游戏秒下，测量300秒再加准备/预热可能使部分源自然消失、固定负载校对FAIL。使用60秒或在寿命范围内选时长，并对照remaining_source_items_at_end。
- 自动分批准备，每tick最多128个源且有2ms软预算；准备后预热20个世界tick再采样，准备与清理成本不混入窗口。准备或预热异常明确ERROR；准备不会强制加载区块。
- 生成固定0.25格间距的网格，单个源count=1，以唯一组件防止合并。10000实体约25×25格；关闭重力以固定位置。若要测正常落地/合并成本，应另外设计负载，不能将当前受控密度结果视为所有自然场景。
- benchmark关闭逐实体过程日志，仅保留每秒FRAME和最后END；期间不要mark、reload或运行其他会扫描配置的指令。END仍记录mark数量与index_changes，避免把变更配置前后的数据当作稳定工况。
- 优化前、优化后至少各跑3轮上述基线和负载，保留每轮run及构建/commit标识，不只挑最快一轮。先比功能结果，再比相同负载下的窗口均值、p95/p99、最大耗时、积压和延期。

| END字段 | 解释与用途 |
|---|---|
| window.server_tick_cost | 原版已完成tick的总体耗时，微秒；包含世界、玩家等工作，不等于模组耗时 |
| window.checks / window.effects | 场景维度实际检查/效果队列的耗时，含普通实体工作；samples、mean_us、max_us、p50/p95/p99_us |
| visited_in_window | 窗口内任务访问次数，包含取消任务的访问，不是成功转化数 |
| max/mean_pending_at_tick_end | 本窗口tick末队列深度，不是tick内瞬时峰值或生命周期峰值 |
| max_oldest_ready_delay_ticks | 就绪任务最大观测延期，协助判断预算是否形成积压 |
| observed_ticks_per_second | 完整服务端tick数/单调墙钟窗口；不是纯模组吞吐，暂停、GC、其他模组都会影响 |
| world_ticks_advanced | 场景维度实际推进刻数；冻结时不重复采集同一个世界tick的队列耗时 |
| conversions/output_items_in_window | 测量窗口内实际提交次数与实际产出数量；另列开始测量前计数 |
| checks/effects_pending_after_cleanup | 清理后的维度队列深度；若还有普通实体任务不要求为0 |
| remaining_scene_tasks | 本轮待执行任务的清理结果，正常结束应为0 |

分位数来自有界原始样本、按nearest-rank计算，仅在结束时排序；最多12000个服务端样本，达到上限会结束并标注。空窗口samples=0，没有统计意义。

TPS≥19.5或检查开销<0.5ms/tick的目标需由实测窗口支持；2ms软预算不能证明达标。READY、FRAME与结束日志在EndServerTick之后输出，不属于当前原版tick数组记录，但会影响墙钟间隔；队列耗时包含开发计数和场景任务管理开销。优化前后使用相同诊断版本，基线不能消除所有观测开销，也不要直接相减p95/p99来推断纯模组分位数。

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
- 单场景最多10000源实体、64条mark；mark最多160个UTF-16单元并保留完整代理对。IDEA没有实时推进游戏时不会虚构成功结果。
- 本轮游戏验证交由用户完成；本页“预期”是验收条件，不是已经观测到的游戏结果。

## 8. 本轮开发验证记录

- IDEA MCP工作区检查：Java与JSON无错误，debug包无告警。其余5条提示涉及已有冗余判断、Math.clamp建议、返回值使用、框架持有的ServerLevel资源以及固定参数；没有关闭框架持有的世界。
- 按IDEA检查后构建的顺序执行 `./gradlew build`（Windows命令为 `.\gradlew.bat build`）：`BUILD SUCCESSFUL in 25s`，退出码0，两端编译和打包通过。没有新增test文件，也没有启动游戏代替实机验收。
- 已检查语言JSON合法性、双语场景key与占位符一致性、平台/客户端依赖隔离和 `git diff --check`。
- 已核对Fabric与NeoForge主jar：均包含新的debug场景类和双语文本，不含旧报告/导出类或旧command包的RuleDebugCommands。
- 后续新增压力对比场景 `normal`/`converted` 及测量期锁视角、动作栏倒计时；改动后重新执行 `.\gradlew.bat build` 通过，下表哈希为本次最终产物。

本轮生成产物的SHA-256可用于反馈时标识发布jar；IDEA运行源码时仍应附当前commit及未提交改动信息：

| 产物 | SHA-256 |
|---|---|
| `fabric/build/libs/itemdespawntowhat-fabric-1.21.1-1.2.1.jar` | `59849cd96e94014bf4228e0879bf99a540c287a91fdec3541bc91555c3820322` |
| `neoforge/build/libs/itemdespawntowhat-neoforge-1.21.1-1.2.1.jar` | `fbf98258f099138729bbed3e9680cd04e8e8e9916da215243de34f4f630e9138` |
