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

## 适用对象

需要验证规则加载、物品转化、结算恢复、调度或覆盖层落盘的开发者与 Agent。运行环境是项目现有的 JDK 21、Gradle 与 NeoForge 1.21.1 开发环境。

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

`-Chain` 接受 `core`、`natural`、`settlement`、`damage`、`recovery`、`loading`、`budget`、`state`、`save`。`-TimeoutSeconds` 设置每次服务端进程的等待上限，默认 900 秒。Gradle 始终经已有构建互斥脚本串行运行。

每次运行创建 `neoforge/build/probe-runs/<运行编号>/`，写入专用平坦世界配置、EULA 和本地服务端配置。服务端绑定 `127.0.0.1`，端口由系统分配；夹具只操作该次运行的存档和 config。该目录保留现场，使用脚本自动生成的目录进行验证。

Agent 的处理顺序：选择与改动相关的链路 → 查看失败断言和真实日志 → 修复 → 重跑相关链路；跨链路改动或交付前运行 `core`。新增场景先写明它能发现的具体故障，再补最少输入与后置断言。

## 核心链路、埋点与判据

按故障对物品守恒和规则生效的影响排序。每条都核对业务结果，命令成功、Gradle 成功和无异常日志分别只作为运行条件。

| 优先级 / 链路 | 探针类型与输入 | 入口与埋点位置 | 观测指标与通过条件 |
|---|---|---|---|
| P0 / `natural` | 声明场景：`priority`、`expiry` | 实体真实入世界 → `RuleRuntimeEvents` / `ConversionRuntime`；`RuntimeObserver.converted`、`outputAdded`；NeoForge `ItemExpireEvent` | 选中预期优先规则；转化和实物数量精确匹配；零提前、重复转化；寿命场景至少一次 `NATURAL_EXPIRY_DEFERRED`，首次转化年龄达到寿命边界 |
| P0 / `settlement` | 声明场景：`source_cost`、`catalyst_limited`、`delay` | 正式候选判定 → `ConversionSettlementTask` → `SettlementItemOutputs` / `EffectTask`；`SETTLEMENT_COMPLETED` 与产物加入事件 | 已消费源、完整组数、返还与剩余催化剂均匹配声明；世界实物与输出事件双重核对；`pending_delivery=0`、待完成效果为零；延迟产物距接管至少 100 tick |
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
| `tools/check-probe-packaging.py`、根 `checkProbePackaging` 任务 | 扫描 common / Fabric / NeoForge 的发布 jar、sources jar、javadoc jar；拒绝探针类、夹具资源与开发语言 key |

`main` 的编译类路径只含生产代码；两平台的 jar 任务只使用 `main`。`check` 同时编译 NeoForge 探针、组装各端发布物并检查打包边界。`check-isolation.py` 还拒绝 `src/main` 对开发探针包的 import。

## 本集合排除的范围

| 范围 | 原因与验证归属 |
|---|---|
| GUI 控件、布局、输入焦点、提示文案、草稿交互、S2C 显示与客户端网络分片 | 用户指定手动测试；后端集合集中验证状态与实物 |
| Fabric 原生事件与加载行为 | 本集合统一 NeoForge；Fabric 参与正常构建和发布隔离检查 |
| 每个物品、每种条件叶、效果类型和规则字段的所有组合 | 核心场景用代表输入验证选择、接管、结算和交付；修改特定类型时追加一个命中其风险的定向场景 |
| 随机概率分布、长时间压力、所有预算值与硬件性能分位数 | 功能集合采用确定输入；吞吐评估使用现有开发 bench / pipeline 并单独解释机器与负载 |
| 任意第三方 mod、魔改包和权限插件组合 | 当前独立存档使用项目声明依赖；兼容性问题用对应整合环境重现 |
| 强制杀进程、磁盘掉电、磁盘满等故障矩阵 | 当前持久化验证使用正常停服后的真实重启；保存事务重点检查部分提交回滚 |

## 借鉴来源

使用本机 `gh` 阅读 [minecraft-mod-dev 固定源码](https://github.com/yan-mc/minecraft-mod-dev/tree/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71)。本项目借鉴其真实入口、结构化状态、业务断言和运行证据组织方式；服务端入口参考 [ProbeServer](https://github.com/yan-mc/minecraft-mod-dev/blob/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71/lab/mcdev-probe/src/main/java/com/mcdev/probe/ProbeServer.java)，证据组织参考 [RunRecorder](https://github.com/yan-mc/minecraft-mod-dev/blob/bb5be3bc1bae88ef145259ac74bcca5dc4b64f71/lab/mcdev-probe/src/main/java/com/mcdev/probe/RunRecorder.java)。本项目采用本地脚本和独立源集，直接复用已有后端观测接缝与场景。

## 相关文档

- [编辑与命令](../modules/authoring.md)
- [转化运行时](../modules/conversion-runtime.md)
- [平台接入](../modules/platform.md)
- [依赖规则](../architecture/dependency-rules.md)
