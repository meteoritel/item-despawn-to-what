---
title: NeoForge 后端开发探针
layer: 5
audience: 修改后端链路、需要自行验证结果的开发者与 Agent
authority: tools/run-probes.ps1、neoforge/build.gradle、common/src/dev、neoforge/src/probe
---

# NeoForge 后端开发探针

## TL;DR

- 运行 `powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1`，在独立存档验证精选核心后端链路，报告失败时退出非零。
- 探针只在 NeoForge 开发类目录装载；正式 `main` 保留空观测契约，发布物由打包检查约束。
- GUI、客户端交互和显示由用户手测。
- `-Chain performance` 运行空基线、千实体空白对照、转化与重载回扫，生成可直接打开的 `performance.html`，显示阶段耗时、CPU、heap、GC 和调度分类曲线。

## 适用对象

需要验证规则加载、物品转化、结算恢复、调度或覆盖层落盘的开发者与 Agent。运行环境是项目现有的 JDK 21、Gradle 与 NeoForge 1.21.1 开发环境。

## 按改动选择验证

先确定改动影响的业务结果，再选择能观察该结果的最小集合；链路与判据见下文核心链路表。

| 改动或目标 | 选择与执行时机 |
|---|---|
| 单条核心后端链路的行为修改或问题修复 | 改动稳定并完成 IDEA 与构建检查后，运行对应 `-Chain`；涉及数条独立链路时取相关专项的并集 |
| 跨链路公共逻辑，如共享调度、服务端生命周期或观测接线 | 运行一轮 `core`，复用其中已包含的专项结果 |
| 后端性能排查或优化 | 复用相关功能通过报告，按性能工作流采集同配置基线与修改后结果；需要区分运行波动与变化趋势时再重复采样 |
| 探针实现或工作流维护 | 运行能触达改动的最小集合；重启编排选 `recovery`，性能采样或曲线报告选 `performance`；修改断言或退出码处理时加一次 `-Negative` |
| 纯文档、语言或 GUI 改动，以及只影响 Fabric 的平台改动 | 执行对应机械校验或平台验证；GUI 交由用户手测 |

所选集合满足报告通过条件、获得任务所需证据后结束验证；负控以预期失败和非零退出为完成条件。代码、配置、负载和验证目标未变化时复用已有报告。后续修改影响已验证链路时重跑相关集合；出现新失败、影响范围扩大或用户明确要求时再扩测。交付节点本身不增加 `core` 或性能运行。

## 运行工作流

在仓库根目录运行：

```powershell
# 全部精选核心链路，恢复验证会自动停服并用同一存档启动第二个进程
powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1

# 修改某条链路时先跑相关专项
powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1 -Chain settlement

# 验证失败通道：第一步附加一个故意错误的预期，期望进程退出 1
powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1 -Chain save -Negative

# 发布前构建；check 会编译开发探针并扫描发布 jar
powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks build
```

`-Chain` 接受 `core`、`natural`、`settlement`、`damage`、`recovery`、`loading`、`budget`、`state`、`save`、`performance`。`-TimeoutSeconds` 设置每次服务端进程的等待上限，默认 900 秒。Gradle 始终经已有构建互斥脚本串行运行。

每次运行创建 `neoforge/build/probe-runs/<运行编号>/`，写入专用平坦世界配置、EULA 和本地服务端配置。服务端绑定 `127.0.0.1`，端口由系统分配；夹具只操作该次运行的存档和 config。该目录保留现场，使用脚本自动生成的目录进行验证。

探针失败时查看失败断言和真实日志，修复后重跑相关链路。新增场景先写明它能发现的具体故障，再补最少输入与后置断言。

## 核心链路、埋点与判据

按故障对物品守恒和规则生效的影响排序。每条都核对业务结果，命令成功、Gradle 成功和无异常日志分别只作为运行条件。

| 优先级 / 链路 | 探针类型与输入 | 入口与埋点位置 | 观测指标与通过条件 |
|---|---|---|---|
| P0 / `natural` | 声明场景：`priority`、`expiry` | 实体真实入世界 → `RuleRuntimeEvents` / `ConversionRuntime`；`RuntimeObserver.converted`、`outputAdded`；NeoForge `ItemExpireEvent` | 选中预期优先规则；转化和实物数量精确匹配；零提前、重复转化；寿命场景至少一次 `NATURAL_EXPIRY_DEFERRED`，首次转化年龄达到寿命边界 |
| P0 / `settlement` | 声明场景：`source_cost`、`catalyst_limited`、`delay` | 正式候选判定 → `ConversionSettlement` → `SettlementItemOutputs` / `EffectTask`；`SETTLEMENT_COMPLETED` 与产物加入事件 | 已消费源、完整组数、返还与剩余催化剂均匹配声明；世界实物与输出事件双重核对；`pending_delivery=0`、待完成效果为零；延迟产物距接管至少 100 tick |
| P0 / `damage` | 正式文件规则 + 原版实体伤害调用 | 真实 `ItemEntity.hurt` 的火、岩浆、仙人掌致死分支 → NeoForge `ItemEntityMixin` → 环境转化入口；最终枚举世界实物 | 三个源各毁掉，产物总数精确为三；非致死伤害后源仍存在；其它致死类型的源被毁掉且总产物数仍为三 |
| P0 / `recovery` | 真实准备/步骤异常 + SavedData 跨进程夹具 | `effectPreparing` / `taskStepStarting` 故障接缝 → 生产补偿和 `SettlementRecovery`；停服前经同包开发夹具登记中断账本，重启走正式恢复器 | 故障必须实际注入；准备失败保留源，结算失败实物返还且具永久保护/禁转；跨重启待返还七件全部交付，账目 `initial=consumed+delivered+pending`；重复重载后仍为七件、pending 为零 |
| P1 / `loading` | 开发数据包基底 + 真实 config 覆盖文件 + 一个损坏文件 | `RuleRuntimeHost.reloadRules` → 正式读取/解码/发布 → 已存在实体回扫；`reloadApplying` 在发布前注入一次异常 | 正常文件继续应用、损坏文件产生 ERROR；覆盖同 id 后旧实体按新规则产出；随后加载新内容但发布失败时回执拒绝，旧索引继续产出；最终 shard 四件、diamond 零件、源零件 |
| P1 / `budget` | 小预算生产调度器的有界任务；正式宿主调度器跨主世界/下界的有限实物负载 | `ServerScheduler.tick`、`scheduleAt`，混合 EFFECT / CONDITION_CHECK；读取任务句柄终态与真实实体 | 到期前零执行；小预算首 tick 留有积压，同 tick 再推进不增加完成量；两个 realm 最终各完成二十个任务；正式宿主两个真实维度各交付二十件、所有句柄 DONE、零提前产出 |
| P1 / `state` | 原版同组件可合并实体 + 临时/永久实体状态 | `DropStateStore`；真实伤害免疫事件；NeoForge 实体 NBT 持久化、原版合并与 Mixin | 临时保护生效且在到期刻失效；冷却前源存活、到期后产出；永久返还豁免伤害且不转化；永久标志不同保持两堆；兼容堆合并为两件并取较晚到期刻；NBT round trip 状态一致 |
| P1 / `save` | 后端组件集成：真实文件提交与真实会话状态机 | `RuleOverlayWriter.apply` 的 WRITING 进度回调；`EditSessionManager` 取锁、确认、APPLYING、释放 | 首批两个文件写入；第二个文件提交前故障实际命中，失败回执明确、回滚成功、整个目录文件集合与字节完全复原（含备份/临时文件）；重试两个文件成功；独占锁拒绝第二人，APPLYING 延迟释放并在收尾归零 |

声明场景只对本轮源覆盖候选索引；规则仍走正式 Codec、校验和运行时。`loading`、`damage`、`state` 的实体使用正式文件索引，候选观测器返回 `null`，这三条负责验证真实装配。文件保存探针覆盖 writer 与会话状态机，协议分片、客户端取锁和保存回执显示由手动链路验证。

跨重启夹具构造的是已中断的待返还库存；故障发生到中断落账这一段由前面的真实异常场景负责。进程通过正常停服保存账本，第二个 JVM 从同一存档恢复，断言读取 SavedData 和世界实体。持久化边界在实际重启处验证。

预算探针使用两层证据：小预算实例验证共享配额及同刻幂等；正式宿主验证跨维度排期、真实 tick 推进与最终实物。它的判据是延期、排空和终态，不把经过钳制的 `usedWorkUnits` 或固定为零的 dropped 指标单独作为成功依据。

## 报告与定位

目录中保留：

- `prepare-report.json`：首个进程的完整链路结果。
- `resume-report.json`：恢复链路第二个进程的结果。
- `probe-report.json`：最近一次进程的检查点和终态。
- `prepare-gradle.log` / `resume-gradle.log`、对应 stderr、游戏 `logs/` 和存档。

报告格式为 `idtw-backend-probe-v1`。`results` 每步含 `verdict` 和 `checks`，每项断言给出 `name`、`expected`、`actual`、`pass`。声明场景还带真实事件、账目和实物指标；世界探针含 `metrics`。`remaining` 列出尚未完成的步骤。串行场景的账本累计值以启动时 `COUNTER_BASELINE` 记录的基线计算预期。

成功要求：进程退出零、报告身份正确、结果与断言非空、每步及每项断言通过、`remaining` 为空。恢复首进程的 `RESTART_REQUIRED` 会进入第二进程。断言失败立即封存报告并停服；异常、启动失败、超时、缺报告和未完成状态均退出非零。预期的注入异常保留在日志中，使用注入次数及业务结果判定。

`-Negative` 追加 `negative_control/intentionally_wrong`：预期 1、实际 0。它走相同断言与退出路径，用于检查 Agent 会看到明确失败。

## 开发隔离与维护入口

| 位置 | 职责 |
|---|---|
| `common/src/dev/java/.../core/debug`、`common/src/dev/resources` | 复用开发场景、手动命令、观测实现、调试语言与声明资源 |
| `neoforge/src/probe` | 后端控制器、平台事件接线、真实世界探针、开发数据包与同包账本夹具 |
| `neoforge/build.gradle` 的 `probe` 源集 | 编译时依赖正式代码；开发运行合并到同一 mod 类目录，符合 NeoForge 模块包隔离约束 |
| `RuntimeObserver`、`DevelopmentDiagnostics` | 生产侧空观测契约，开发入口在服务端装配并在停止后释放 |
| `tools/run-probes.ps1` | 隔离运行、重启编排、超时、报告校验与退出码 |
| `DebugProbePerformance` | 自动运行逐步启停的独立 JMX 采样、阶段计时、主线程 tick 与分类耗时记录 |
| `BackendProbeSuite` / `BackendProbeReport` | 精选集合目录、环境配置与结构化检查点；控制器只编排步骤和终态 |
| `BackendProbeFixtures` | 正式文件规则、源/产物登记、字节快照与精确清理；世界探针只保留业务步骤与断言 |
| `tools/probe-performance-report.py` | 原始 CSV、离线 HTML、测量窗口汇总和历史目录对比 |
| `tools/check-probe-packaging.py`、根 `checkProbePackaging` 任务 | 扫描 common / Fabric / NeoForge 的发布 jar、sources jar、javadoc jar；拒绝探针类、夹具资源与开发语言 key |

`main` 的编译类路径只含生产代码；两平台的 jar 任务只使用 `main`。`check` 同时编译 NeoForge 探针、组装各端发布物并检查打包边界。`check-isolation.py` 还拒绝 `src/main` 对开发探针包的 import。

## 性能工作流

```powershell
# 首次保存基线，目录由脚本输出的 artifacts 给出
powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1 -Chain performance

# 修改后沿用相同集合，与已保存的运行目录比较
powershell -ExecutionPolicy Bypass -File tools/run-probes.ps1 -Chain performance -CompareTo neoforge/build/probe-runs/<基线目录>

# 仅重绘已有结果，无需再次启动服务端
python tools/probe-performance-report.py neoforge/build/probe-runs/<本次目录> --compare-to neoforge/build/probe-runs/<基线目录>
```

每次运行使用固定 seed 的独立平坦存档。`run-metadata.json` 记录 Git revision、工作区变更、集合与基线目录。完整的 `core` 和单条功能链路也采集性能，适合定位保存、准备或恢复的阶段成本；吞吐比较使用 `performance`，避免逐实体功能日志扰动。

| 代表负载 | 规模与测量窗口 | 核心业务判据 |
|---|---|---|
| `bench:baseline` | 0 个源，60 秒 | 零转化、零产物，观察空服务端与采样本身的背景成本 |
| `bench:unmatched` | 1000 个圆石实体，60 秒；规则只匹配纸 | 零转化、零产物、源库存完整、追踪与检查任务为零；作为物品实体自身成本对照 |
| `bench:condition_miss` | 1000 个纸实体，60 秒；10 秒后检查的高度条件恒不满足 | 零转化、零产物、源库存与追踪完整，`CONDITION_FALSE` 至少 1000 次；作为持续检查但不结算的对照 |
| `bench:convert` | 1000 个独立源，每堆 1 件，60 秒；触发年龄 10 秒 | 千次结算完成，消费源与生成的实物精确匹配，无待交付余额 |
| `bench:reload_storm` | 1000 个源，30 秒；五次正式重载 | 五次动作实际执行，回扫排空、源仍完整追踪，步长与工作量符合声明上限 |

场景规模、动作、规则与预期取自 `common/src/dev/resources/idtw-debug/scenarios/bench/`。新增代表负载时先写声明并登记 manifest，再加入 `BackendProbeSuite`；阶段、资源采样与报告无需各场景重复实现。功能场景与性能场景共用生命周期和断言器。

两组空白对照沿用转化场景的实体数量、堆叠数、网格密度、防合并组件与测量时长。先看 `unmatched` 相对空载的进程 CPU 与原版 tick，再看 `condition_miss` 的检查次数、runtime 和调度分类，最后结合 `convert` 评估结算成本。CPU 均值可作同负载差异观察；分位数分别展示，避免把不同场景的 p95 相减当作独立净开销。

### 时间轴与观测口径

| 指标 | 埋点与计算 | 阅读方式 |
|---|---|---|
| 阶段墙钟 / 服务端 CPU ms | 步骤启动前开始，`DebugScenarioRun` 切换 SETUP / WARMUP / MEASURE / ASSERT / CLEANUP；世界链路区分准备、等待、检查与清理；保存另外记录初写、回滚、重试及会话；重载用 RELOAD 段 | 墙钟包含跨 tick 等待，CPU 使用服务端线程累计时间差值；阶段互斥，短操作结束恢复父阶段 |
| JVM 进程 CPU % | 独立线程每 200 ms 读取 `OperatingSystemMXBean.getProcessCpuTime` 的增量，除以实际墙钟间隔 | 100% 为占用一个逻辑核，包含 JIT、GC、后台 I/O 与探针线程；可超过 100% |
| 服务端线程 CPU % | 同间隔读取 `ThreadMXBean` 中服务端线程累计 CPU 时间 | 与进程 CPU 并列定位主线程和后台成本；Windows CPU 时钟粒度会使短阶段出现 0 ms |
| heap / non-heap MiB | `MemoryMXBean` 的 heap used、committed、max 以及 non-heap used | 曲线显示 used / committed / non-heap，容量上限单列；heap 口径用于比较 JVM 堆占用 |
| GC 次数 / ms | 所有 GC MXBean 的累计差值，以步骤开始为基线 | 结合 heap 回落和 tick 尖峰观察回收成本 |
| 原版 / runtime / 总 tick ms | 正式运行时完成后、场景推进前读取已有 tick 槽与 `runtimeTickNanos` | 总 tick 使用原版 + IDTW；准备、断言等开发工作通过阶段表和 CPU 单独展示 |
| 调度分类 ms | 同 tick 读取正式 scheduler 的各 kind `lastMicros`，跨维度合并 | 分类属于 runtime 内部；现有窗口仍保留队列、预算、工作量和积压延迟汇总 |

JMX 线程只读取管理指标；世界和 scheduler 数据由服务端线程采集。主线程长操作期间资源曲线继续推进，采样实际间隔保存在原始数据里。首个区间与极短尾区间没有有效 CPU 差值时留空。`sampling_total_ms`、`sampling_max_ms` 和 `tick_recording_total_ms` 展示记录成本；JSON 序列化与报告写盘放在采样窗口外。

### 报告与判定

- `performance.html`：独立离线页面，负载汇总对照、阶段表、共享时间轴曲线、鼠标采样值、环境与负载详情、同负载增幅表；无外部脚本或服务。
- `performance-resources.csv` / `performance-ticks.csv` / `performance-phases.csv`：资源样本、逐 tick 分类与阶段原始数据，均带进程阶段和步骤标识。
- `performance-summary.json`：测量窗口统计与比较结果。CPU 均值按实际间隔加权，p95 使用 nearest-rank；有 MEASURE 的场景只汇总该窗口，世界链路使用整步。
- `prepare-report.json` / `resume-report.json` 的 `performance`：原始记录及采样成本；`environment` 包含 Java、OS、处理器数、heap 容量、tick rate、seed、JVM 参数与正式配置；场景 `workload` 包含数量、时长、规则和动作。

通过要求：业务断言全部通过，资源与阶段非空；超过一秒的步骤有 tick 与有效 CPU 样本，tick 运行时计时完整；性能场景有全部生命周期阶段，采样线程未报错。脚本对功能失败、采样缺失或异常均返回非零，并保留已有结果。性能数值展示为观测值，统一功能入口使用机器无关的业务断言。

历史比较按进程阶段和步骤配对，核对版本环境、处理器数、heap 容量、tick rate、seed、配置、工作负载和业务 PASS。匹配后展示 runtime p95、总 tick p95、进程 CPU 均值和 heap 峰值的增幅；配置不同明确列出差异。优化前后沿用同一 JVM 参数与负载，分别重复运行，再比较同一指标。曲线采样密度为 200 ms，短时尖峰结合逐 tick 和阶段表定位。

## 本集合排除的范围

| 范围 | 原因与验证归属 |
|---|---|
| GUI 控件、布局、输入焦点、提示文案、草稿交互、S2C 显示与客户端网络分片 | 用户指定手动测试；后端集合集中验证状态与实物 |
| Fabric 原生事件与加载行为 | 本集合统一 NeoForge；Fabric 参与正常构建和发布隔离检查 |
| 每个物品、每种条件叶、效果类型和规则字段的所有组合 | 核心场景用代表输入验证选择、接管、结算和交付；修改特定类型时追加一个命中其风险的定向场景 |
| 随机概率分布、长时间压力、所有预算值与硬件矩阵 | 功能集合采用确定输入；性能集合采用有限代表负载与空白对照，数值随环境与负载解释 |
| 任意第三方 mod、魔改包和权限插件组合 | 当前独立存档使用项目声明依赖；兼容性问题用对应整合环境重现 |
| 强制杀进程、磁盘掉电、磁盘满等故障矩阵 | 当前持久化验证使用正常停服后的真实重启；保存事务重点检查部分提交回滚 |

## 借鉴来源

使用本机 `gh` 阅读 [minecraft-mod-dev 固定源码](https://github.com/yan-mc/minecraft-mod-dev/tree/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71)。本项目借鉴其真实入口、结构化状态、业务断言和运行证据组织方式；服务端入口参考 [ProbeServer](https://github.com/yan-mc/minecraft-mod-dev/blob/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71/lab/mcdev-probe/src/main/java/com/mcdev/probe/ProbeServer.java)，证据组织参考 [RunRecorder](https://github.com/yan-mc/minecraft-mod-dev/blob/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71/lab/mcdev-probe/src/main/java/com/mcdev/probe/RunRecorder.java)。本项目采用本地脚本和独立源集，直接复用已有后端观测接缝与场景。

## 相关文档

- [编辑与命令](../modules/authoring.md)
- [转化运行时](../modules/conversion-runtime.md)
- [平台接入](../modules/platform.md)
- [依赖规则](../architecture/dependency-rules.md)
