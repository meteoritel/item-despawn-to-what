# ADR-0034：调度契约与实现分层

- 状态：已实施；契约与实现分工见 [scheduling-budget.md](../dev/backend/systems/scheduling-budget.md)。
- 相关决策：[ADR-0024](0024-shared-server-tick-budget-scheduler.md)（共享 tick 预算模型本身不变，本 ADR 只调整其类型的归属与依赖方向）。

## 背景

契约层是全项目的依赖底座，效果与条件的执行器、网络层与平台宿主都只应依赖契约。但调度相关的类型此前与调度实现同处一个包：任务（`ServerTask`）、任务种类（`ServerTaskKind`）、步进结果（`StepResult` / `StepOutcome`）、预算句柄（`ServerTickBudget`）与队列抽象（`TaskQueue`）都定义在调度实现所在包内。

后果有两条：一是「任务能跑多久、按什么种类排队」这类跨层契约不得不由运行时层的包承载，依赖方向变成「契约层之上再定义契约」；二是任何需要排期的一方（例如编辑层把保存后的续接工作排入公共预算）只能直接持有调度器本身，于是窄接口上出现了直接暴露调度器的入口。

## 决策

1. **契约归契约层**：`ServerTask`、`ServerTaskKind`、`CancelReason`、`StepResult`、`StepOutcome`、`ServerTickBudget`、`ServerTickBudgetSnapshot`、`RunnableTask`、`TaskQueue`、`ScheduledTaskHandle`、`StepWorkCounter` 定义在契约层的调度子包。
2. **实现留运行时层**：`ServerScheduler`、`RealmQueues`、`TaskLane`、`ScheduledTask`、`SchedulerConfig`、`SchedulerStats`、`QueueStats`、`KindStats` 留在运行时调度包，只实现契约。
3. **依赖方向固定为实现依赖契约**：效果与条件的执行器（`core/type/effect/exec`、`core/type/condition`）只依赖契约类型，不引用运行时调度包。
4. **排期以最小抽象暴露**：跨层排期只给「realm 标识 + 任务 + 到期刻」这一能力（队列抽象与窄接口上的排期方法），不把调度器实例或配置面交出去。

## 考虑过的替代方案

- **契约与实现继续同包**：实现包被迫充当契约来源，效果执行器与编辑层都要引用运行时实现类，与本项目的分层前提冲突。否决。
- **只搬类型、仍对外暴露调度器**：类型搬家解决编译期包依赖，但调用方仍会持有调度器并按实现细节编排，分层只停在表面。否决。
- **另立独立的调度契约包**：与既有契约层职责重叠，多出一层抽象与一次性迁移成本，而调度契约并无独立于契约层的发布节奏。否决。

## 后果

- 契约层文档中新增「调度契约」一族；新增任务种类改契约侧枚举，新增调度策略改运行时实现。
- 效果与条件执行器、平台宿主与网络层不再依赖运行时调度包，可在测试与替换实现时只替换实现侧。
- 契约层因此承载了「队列」这一抽象，需要持续约束它只表达排期能力，不泄漏实现细节。
