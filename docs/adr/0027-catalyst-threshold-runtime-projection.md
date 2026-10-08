# ADR-0027：催化剂默认门槛保留声明、在运行投影中派生

状态：已接受，2026-10-07；core 侧已实施（2026-10-08），客户端 UI 契约已实施（2026-10-08，见 [编辑器四页职责与输入↔触发联动契约](../dev/internals/editor-pages-contract.md)）。依据：[正式 spec](../spec/editor-page-responsibilities-2026-10-07.md)；讨论取舍见[编辑器四页职责方案](../plan/grill-editor-page-responsibilities-2026-10-07.md) Q9–Q11。

催化剂每轮消耗量与存在门槛独立。条件门槛未填写时跟随对应消耗量，没有消耗配置时默认 1 件；明确填写的门槛保持独立。保存时必须保留“未填写”，否则下一次调整消耗量会丢失跟随语义。

采用声明与运行投影分离：编辑和序列化保留可选门槛，运行入口按所属规则解析有效门槛，覆盖规则级与动作局部条件、普通索引及 debug 入口，保留既有条件作用域。派生值不反写声明，不扩展公开 ConditionContext 来承担规则配置查询；代价是必须共用并维护运行投影的构建与失效入口，避免每次检查复制条件树。

实施补充（2026-10-08）：core 侧已按本决策落地——`catalyst_present` 的 `count` 改为**可省略**（省略即「未填写」，编码同样省略、仅在填写时校验 1–64）；新增 `core/runtime/CatalystThresholdProjection`（`project(Rule)` 零分配快速路径；`resolveThreshold(rule, items, scopeEffect)` 同作用域优先 → 全规则唯一匹配 → 否则 1），唯一接入点 `RuleIndex.build`，普通运行与 debug 场景共用，reload 随索引重建自动失效。为此 `core/model/Effect` 新增公开方法 `withConditions(@Nullable ConditionExpression)`（10 个内置效果全部实现）：投影必须重建**真实效果类型**，执行器按具体类型强转、不能用包装 record 顶替，故扩展公开接口；`ConditionContext` 未扩展。

项目尚未 release，不实现旧数据迁移。流体继续按存在判定、独立移除，不以数量预留或限制组数。
