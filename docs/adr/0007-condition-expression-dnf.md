# 条件表达式改为 DNF + 可扩展条件类型注册表 + 谓词/消耗拆分 + 显式优先级

> 历史记录：本文绑定的旧实现已退役；当前后端契约以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/backend/README.md) 为准，前端契约见 [ADR-0018～0022](0018-condition-tree-contract.md) 与 [plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md)。

## 背景
当前触发条件为扁平纯 AND（`ConditionContext` 5 字段，`CombinedConditionChecker` 短路合取），不支持 OR / NOT / 嵌套；且 `selectBestMatchingRule` 每周期只选中首条匹配规则，导致"写两条规则模拟 OR"也会被首条遮蔽（除非复杂度更高，那就成了特化回退而非 OR）。复杂度 = 非空条件计数，等复杂度按文件插入序决定，无显式优先级。催化剂 / 浸润流体既当触发谓词又当消耗对象，职责耦合。

## 决策
条件表达式采用析取范式（DNF）：若干**条件组**的析取（组间 OR），每个条件组为其**条件叶**的合取（组内 AND），叶可取反。

- **条件叶类型**做成可扩展注册表（`ConditionType`，镜像 `ConversionType`：id + 参数 DTO + 谓词检查器 + 客户端表单 schema），内置 dimension / outdoor / surrounding_blocks / catalyst_present / fluid_present（+ biome / weather）。第三方与未来新条件走同一注册路径。
- **谓词 / 消耗拆分**：催化剂 / 流体的"在场判定"成为纯谓词条件叶；"消耗什么"独立为**消耗指令**字段，与条件表达式解耦。检查器保持纯布尔，无需返回匹配组。
- **显式优先级**：规则新增可选 `priority` 字段（默认 0）为主排序；同优先级按条件叶总数（特异性近似）降序；再按插入序。

## 考虑过的替代方案
- **完整布尔表达式树（任意嵌套）**：表达力与 DNF 等价，但需树编辑器控件、SPI 形状更重、校验/迁移更复杂。DNF 已覆盖全部布尔表达力且形状有界。否决。
- **催化剂叶携带消耗参数（检查器返回匹配组驱动消耗）**：UX 不变，但检查器需返回匹配组、副作用混入谓词层，破坏"条件=纯谓词"。否决。
- **封闭条件集合（仅内置 5 类，不可扩展）**：与转化类型的可扩展性不对称，未来加生物群系/天气须改 core。否决。

## 后果
- `ConditionContext` / `CombinedConditionChecker` 重构为 DNF 求值；条件类型注册表为新增并行注册结构，第三方一旦依赖即锁定 SPI 形状。
- 客户端 `FormRenderer` 需新增"可重复条件组 + 条件叶编辑器"复合控件（框架级，因条件表达式为所有转化类型共享）。
- 旧 v1 配置经迁移（见 ADR-0008）转为 v2 DNF 单组 + 消耗指令。
