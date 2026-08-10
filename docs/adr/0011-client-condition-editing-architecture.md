# 客户端条件编辑采用独立子屏 + 客户端条件类型注册表，退役扁平条件垫片

## 背景

配置重构（ADR-0007）已将触发条件升级为 DNF 条件表达式 + 谓词/消耗解耦，服务端数据模型完备。但客户端编辑 UI 仍停留在旧扁平模型：`BuiltinFormDefinitions.addCommonFields` 为每个转化类型绑定 7 个扁平条件字段（`dimension`/`need_outdoor`/`surrounding_blocks`/`catalyst_items`/`inner_fluid`/`biome`/`weather`），经由 `BaseConversionConfig` 上保留的旧扁平 getter/setter 垫片读写"第一组、非取反、内置类型"的条件叶。当表达式出现多组 / 取反 / 第三方类型时 `supportsLegacyEditor()` 返回 false，这些 setter 全部静默 no-op--UI 既读不出（字段空白）也写不进（编辑丢失），存在数据完整性隐患，且无法表达 OR/NOT/嵌套组。

## 决策

1. **建完整 DNF 编辑器作为唯一编辑面，退役扁平垫片**：UI 移除 7 个扁平条件字段；`BaseConversionConfig` 上的旧扁平 getter/setter 与 `supportsLegacyEditor` 在新 UI 切换完成后退役，`validate` 改为直接读 `ConditionExpression`/`ConsumptionDirective`。单一真相，消除 no-op 隐患。
2. **客户端条件类型注册表镜像转化类型注册表**：新增 `ClientConditionTypeDefinition`（id + 显示名 key + 参数表单工厂）+ `ClientConditionTypeRegistry` + `BuiltinClientConditionTypes`，以同一 `ResourceLocation` 关联服务端 `ConditionType`，客户端不引用服务端类。第三方条件类型同时注册服务端 `ConditionType` 与客户端 `ClientConditionTypeDefinition`。参数表单复用 `FormField`/`FormFieldInput`，能为每个参数字段挂校验/建议/可见性。
3. **条件编辑器承载于独立子屏**：新增 `ConditionEditorScreen`，主表单以一行 DNF 实时摘要 + "编辑条件"按钮打开。子屏自有滚动/布局/焦点，每个条件叶的参数子表单作为顶层字段渲染，可复用复合控件而不产生"复合套复合"。

## 考虑过的替代方案

- **内联复合控件（`ConditionExpressionField` 作为主表单内的一个 `FormFieldInput` 复合控件）**：无切屏、与其他字段一致，但 DNF 树（组 > 叶 > 参数子表单）是嵌套结构，`surrounding_blocks`/`catalyst_present` 参数本身就是复合控件，会产生"复合套复合"；而 `FormRenderer` 的焦点遍历与校验扁平化（`getInternalEditBoxes`）不递归嵌套复合控件，需先扩展 `FormRenderer` 支持递归或将参数控件重实现为裸 `EditBox`，风险与工作量都高。否决。
- **双模式（完整 DNF 编辑器 + 保留扁平字段作简单模式）**：常见简单规则用扁平模式更省事，但维护两套编辑路径、两套真相，且模式切换边界（何时自动升级到 DNF）易出 bug。否决。
- **反射自动派生参数表单**：从 `ConditionTypeDefinition` 已暴露的 `parameterType` 反射生成表单，样板最少，但无法表达类型特异的建议/校验/可见性（fluid 需 fluid-id 建议、dimension 需维度建议），除非引入注解 schema 系统--等价于重造 ADR-0006 已退役的声明式 schema。否决。
- **仅修复 no-op 隐患（rich 表达式时禁用扁平字段 + 只读 DNF 摘要）**：改动最小，但 rich 表达式仍无法在 UI 创建/编辑，与"彻底重构"目标相悖。否决。

## 后果

- 第三方条件类型客户端扩展契约锁定：实现新条件类型 = 服务端 `ConditionType` + 客户端 `ClientConditionTypeDefinition`（参数表单工厂 + 显示名）。一旦第三方依赖此形状即难反转。
- 客户端条件类型注册表与服务端注册表以 id 关联但不互相引用，严格遵守客户端/服务端分离。
- `BaseConfigEditScreen` 需支持打开/关闭子屏（暂停主表单交互、子屏返回时回写 `ConditionExpression` 并刷新摘要）。
- 旧复合控件 `CatalystItemsWidget`/`InnerFluidWidget`/`SurroundingBlocksWidget` 退役，条件参数编辑器与消耗编辑器全新编写（基于同一套 `FormField`/`FormFieldInput` 原语）。
- 顺序约束：必须先建新 UI 并切换过去，最后才能删扁平垫片（与配置重构"服务端先行"的顺序相反）。`validate`、展示层、inspect 命令等所有旧扁平 getter 调用方须同步迁移。
- 消耗编辑器内联于主表单；"消耗隐含在场"已是运行时事实（`ConsumptionDirective.getMaxConvertibleRounds` 不足返 0 轮），故常见"催化剂在场则消耗"只需消耗指令一处录入，`catalyst_present`/`fluid_present` 条件叶仅用于"要求在场但不消耗"的进阶场景。
