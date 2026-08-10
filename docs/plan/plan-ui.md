# 客户端编辑 UI 重构计划

> 本计划基于 `grill-with-docs` 会话达成的共识（见 `docs/adr/0011`、`CONTEXT.md` 词汇表）。
> 目标读者：项目维护者。每阶段均以 `./gradlew build` 通过为门槛。
> 前置条件：配置系统重构（`docs/plan/plan.md`）已完成，服务端 DNF 条件模型 + 谓词/消耗解耦 + 新字段已就位。

---

## 一、背景

配置重构把触发条件升级为 DNF 条件表达式 + 谓词/消耗解耦，服务端数据模型完备。但客户端编辑 UI 仍停留在旧扁平模型，存在以下问题簇：

1. **条件编辑能力缺失**：`BuiltinFormDefinitions.addCommonFields` 绑定 7 个扁平条件字段（`dimension`/`need_outdoor`/`surrounding_blocks`/`catalyst_items`/`inner_fluid`/`biome`/`weather`），无法表达 OR / NOT / 嵌套组，也无条件类型选择。
2. **扁平垫片 no-op 隐患**：`BaseConversionConfig` 保留的旧扁平 getter/setter 仅读写"第一组、非取反、内置类型"条件叶；当表达式为 rich DNF（多组/取反/第三方类型）时 `supportsLegacyEditor()`=false，setter 静默 no-op--UI 字段空白且编辑丢失，存在数据完整性隐患。
3. **客户端无条件类型注册表**：条件叶编辑器无从获取可选条件类型与各类型参数表单。
4. **展示层语义过时**：列表 tooltip 仍说扁平条件语言（`tooltip.dimension`/`tooltip.catalyst_header` 等），无法呈现 DNF / 消耗 / 新字段。
5. **旧复合控件与解耦模型不符**：`CatalystItemsWidget`/`InnerFluidWidget` 把"在场判定"与"消耗"耦合在一个控件 + 开关里，与新谓词/消耗解耦模型冲突。

## 二、目标与非目标

### 目标（本次纳入）
- 条件编辑：独立 `ConditionEditorScreen` 子屏，平铺叶列表 + OR 分隔条，支持增删组/叶、条件类型选择、取反、参数编辑、未知类型只读回退（ADR-0011）。
- 客户端条件类型注册表：镜像转化类型，`ClientConditionTypeDefinition` + `ClientConditionTypeRegistry` + `BuiltinClientConditionTypes`（ADR-0011）。
- 消耗编辑器：内联于主表单，承载催化剂/流体消耗（消耗隐含在场）。
- 展示层：列表 tooltip/摘要改说 DNF + 消耗 + 新字段（priority/enabled/notes/search_radius），disabled 规则置灰。
- 退役扁平垫片：`BaseConversionConfig` 旧扁平 getter/setter 与 `supportsLegacyEditor` 退役，`validate` 直接读 DNF/消耗。
- 退役旧复合控件，全新编写条件参数编辑器与消耗编辑器。
- i18n 中英文同步。

### 非目标（暂缓）
- 编辑会话锁/网络分块协议的重构（现状可用，仅在子屏接入时做必要适配）。
- 列表搜索/过滤/分页等无关重构。
- 配置文件格式/迁移（配置重构已完成 v2，本次不动 schema）。

## 三、架构变更总览

| 维度 | 现状 | 目标 |
|---|---|---|
| 条件编辑 | 7 扁平字段经旧垫片读写单组非取反叶 | `ConditionEditorScreen` 子屏编辑完整 DNF；主表单摘要 + 按钮 |
| 条件类型供给 | 无客户端注册表 | `ClientConditionTypeRegistry` 镜像服务端，参数表单复用 `FormField` |
| 消耗编辑 | 与在场耦合在 `CatalystItemsWidget`/`InnerFluidWidget` + 开关 | 内联 `ConsumptionDirectiveField`（消耗隐含在场）；在场判定归条件叶 |
| 展示层 | 扁平条件 tooltip | DNF + 消耗 + 新字段摘要；disabled 置灰 |
| 旧复合控件 | `CatalystItemsWidget`/`InnerFluidWidget`/`SurroundingBlocksWidget` | 退役；全新参数/消耗编辑器 |
| 扁平垫片 | `BaseConversionConfig` 保留旧 getter/setter + `supportsLegacyEditor` | 退役；`validate` 直接读 DNF/消耗 |

## 四、阶段依赖与顺序

```
P1 客户端条件类型注册表+参数表单基础设施 ──> P2 ConditionEditorScreen + 主表单摘要按钮
                                                    │
                                                    └──> P3 内联消耗编辑器 ──> P4 展示层/i18n ──> P5 退役垫片+旧控件+清理
```

- **顺序与配置重构相反**：必须先建新 UI 并切换，最后才能删扁平垫片（垫片仍被当前 UI 调用）。
- P1 是纯基础设施（无 UI 集成），独立可编译。
- P2/P3 逐步替换 `BuiltinFormDefinitions` 的扁平字段：P2 替换纯条件字段，P3 替换催化剂/流体字段。
- P4 改展示层（此时 UI 已不再依赖扁平 getter，但展示层可能仍用）。
- P5 在全部调用方迁移后退役垫片与旧控件。

---

## 五、分阶段计划

### P1：客户端条件类型注册表 + 参数表单基础设施

**目标**：建立客户端条件类型 SPI 与 7 个内置条件的参数表单，暂不接入任何 UI。

**依赖**：无（服务端 `ConditionTypeRegistry` 已就位）。

**涉及文件**
- 新增 `common/.../client/ui/condition/ClientConditionTypeDefinition.java`（id + 显示名 key + `FormDefinitionFactory<ParamDTO>` 或参数字段列表工厂）
- 新增 `common/.../client/ui/condition/ClientConditionTypeRegistry.java`（`register`/`all`/`byId`/`require`，镜像 `ClientConversionTypeRegistry`）
- 新增 `common/.../client/ui/condition/BuiltinClientConditionTypes.java`
- 新增各内置条件参数编辑器（实现 `FormFieldInput`，全新编写）：
  - `DimensionParamInput`（文本框 + 维度建议）
  - `BiomeParamInput`（文本框 + biome 注册表/tag 建议）
  - `WeatherParamInput`（CycleButton: clear/raining/thundering）
  - `OutdoorParamInput`（无参数占位）
  - `SurroundingBlocksParamInput`（6 向方块编辑）
  - `CatalystPresentParamInput`（催化剂物品列表 + count，无消耗开关）
  - `FluidPresentParamInput`（fluid + require_source，无消耗开关）

**步骤**
1. 仿 `ClientConversionTypeDefinition`/`ClientConversionTypeRegistry` 建条件类型客户端注册表。`ClientConditionTypeDefinition<P>` 持有：`ResourceLocation id`、显示名 key、参数表单工厂（产出 `FormDefinition<P>` 或 `List<FormField<P>>`）。client 不引用服务端 `ConditionType`，以同一 id 关联。
2. `ClientConditionTypeRegistry` 用 `LinkedHashMap`，static 块预注册 7 内置条件（命名空间 `itemdespawntowhat`），与 `BuiltinConditionTypes` 的 id 一一对应。
3. 每个内置条件参数编辑器基于 `FormField`/`FormFieldInput` 实现，复用 `SuggestionProvider`（维度/biome/fluid/block/item 注册表建议）。消耗开关不出现（消耗隐含在场；在场叶不消耗）。
4. 为未知类型预留只读回退契约：`ClientConditionTypeRegistry` 未命中时，编辑器使用原始 JSON 文本框 + 警告（P2 接入）。

**验证**：`./gradlew build`；注册表独立可用，7 内置条件参数表单可构造。

**风险**：参数 DTO（`BuiltinConditionParameters` records）在 `common`，客户端可引用；第三方条件类型的参数 DTO 在其自身 common 包，客户端需其 `ClientConditionTypeDefinition` 提供表单工厂--契约须文档化。

---

### P2：ConditionEditorScreen + 主表单摘要按钮

**目标**：落地 DNF 条件编辑子屏，替换主表单中 5 个纯条件扁平字段（`dimension`/`need_outdoor`/`surrounding_blocks`/`biome`/`weather`）。催化剂/流体扁平字段暂留（P3 处理）。

**依赖**：P1。

**涉及文件**
- 新增 `common/.../client/ui/screen/ConditionEditorScreen.java`（DNF 编辑器主屏）
- 新增 `common/.../client/ui/form/ConditionSummaryField.java`（实现 `FormFieldInput<ConditionExpression>`：摘要文本 + "编辑条件"按钮，打开子屏）
- 新增 `common/.../client/ui/condition/ConditionLeafRow.java`（叶行：类型 CycleButton + 取反 + 参数子表单 + 删除）
- 新增 `common/.../client/ui/condition/UnknownConditionFallback.java`（未知类型只读回退：类型 id + 原始 JSON + 警告）
- 修改 `common/.../client/ui/form/BuiltinFormDefinitions.java`（`addCommonFields` 移除 5 纯条件扁平字段，加入 `ConditionSummaryField`）
- 修改 `common/.../client/ui/screen/BaseConfigEditScreen.java`（接入子屏打开/关闭、返回时回写 `ConditionExpression` 并刷新摘要）
- i18n：新增条件编辑器 key（组/叶/取反/添加条件/添加组(OR)/类型选择/未知类型警告 + 7 条件类型显示名）

**步骤**
1. `ConditionEditorScreen`：以平铺叶列表 + OR 分隔条渲染 DNF。维护 `ConditionExpression` 工作副本；组间插"OR"分隔条 + "添加组(OR)"按钮；每组末尾"添加条件"按钮；每叶一行 `ConditionLeafRow` + 删除。完成/取消按钮回写或丢弃。
2. `ConditionLeafRow`：类型 `CycleButton`（从 `ClientConditionTypeRegistry.all()`）-> 选中后用该类型参数表单工厂渲染参数子表单（顶层字段，复用 `FormField`）-> 取反开关 -> 删除按钮。切换类型时清空旧参数。
3. 未知类型只读回退：`ClientConditionTypeRegistry.byId` 未命中 -> `UnknownConditionFallback`（类型 id 文本 + 原始 `params` JSON 文本框 + 警告）；可删除该叶或编辑原始 JSON，保存原样保留。
4. `ConditionSummaryField`：显示一行 DNF 实时摘要（如 `dimension=the_nether AND outdoor` 或 `2 组 · 5 叶`，由 `ConditionExpressionPresenter` 产出，P4 完善；此处先用简易摘要）+"编辑条件"按钮；点击打开 `ConditionEditorScreen`，返回时 `setValue` 更新 `ConditionExpression`。
5. `BuiltinFormDefinitions.addCommonFields`：移除 `dimension`/`need_outdoor`/`surrounding_blocks`/`biome`/`weather` 5 个扁平字段及其 `addSurroundingBlocks` 调用；加入 `ConditionSummaryField`。保留 `catalyst_items`/`inner_fluid` 扁平字段至 P3。保留 `priority`/`enabled`/`notes`/`search_radius`/`luck` 等已接入字段。
6. `BaseConfigEditScreen`：子屏作为同会话内的嵌套屏，打开时暂停主表单交互，返回时回写并触发可见性/校验刷新。编辑会话锁不需变更（同一玩家同一会话）。

**验证**：`./gradlew build`；客户端能增删条件组/叶、切换条件类型、取反、编辑参数；rich DNF（多组/取反/第三方）配置可正确加载并在子屏编辑；保存写出合法 v2 JSON；未知类型只读回退生效。

**风险**
- 子屏与主屏的状态同步（`ConditionExpression` 工作副本 vs 主表单 config）：建议子屏持有副本，返回时整体回写，避免部分编辑中间态。
- `BaseConfigEditScreen` 当前是否支持嵌套屏打开/关闭需核实（见代码审查），若不支持需补一个 `Screen` 转交机制（Minecraft 原生 `Minecraft.setScreen`）。

---

### P3：内联消耗编辑器 + 退役催化剂/流体扁平字段

**目标**：新增内联 `ConsumptionDirectiveField`，替换主表单中 `catalyst_items`/`inner_fluid` 扁平字段。催化剂/流体"在场判定"自此归条件子屏的 `catalyst_present`/`fluid_present` 叶。

**依赖**：P2。

**涉及文件**
- 新增 `common/.../client/ui/widget/ConsumptionDirectiveField.java`（实现 `FormFieldInput<ConsumptionDirective>`：催化剂消耗物品列表 + 流体消耗）
- 新增催化剂/流体消耗行编辑器（全新编写，无消耗开关）
- 修改 `common/.../client/ui/form/BuiltinFormDefinitions.java`（移除 `addCatalystItems`/`addInnerFluid` 扁平字段，加入 `ConsumptionDirectiveField`）
- i18n：新增消耗编辑器 key（消耗催化剂/消耗流体标签，复用既有 catalyst/inner_fluid 子标签）

**步骤**
1. `ConsumptionDirectiveField`：编辑 `ConsumptionDirective`--催化剂消耗物品列表（item/tag + count，消耗隐含在场，无消耗开关）+ 流体消耗（fluid + require_source，无消耗开关）。空 directive 时表单为空，保存写出 null。
2. `BuiltinFormDefinitions.addCommonFields`：移除 `addCatalystItems`/`addInnerFluid` 扁平字段；加入 `ConsumptionDirectiveField`。
3. 文案明确"消耗隐含在场"：消耗编辑器旁加 tooltip 提示"消耗的催化剂/流体需在场才会触发转化"；"要求在场但不消耗"请用条件子屏的 `catalyst_present`/`fluid_present` 叶。
4. 校验：消耗物品 id 合法、催化剂不与源物品冲突、fluid id 合法（复用 `IdValidator`）。

**验证**：`./gradlew build`；催化剂/流体消耗可编辑；常见"在场则消耗"只需消耗指令一处；配置写出合法 v2 JSON，`ConsumptionDirective` 空时序列化为 null。

**风险**：旧扁平 `getCatalystItems`/`getInnerFluid` getter 仍被 `validate` 与展示层调用，P3 暂保留垫片（P5 退役）；P3 后 UI 不再调用它们。

---

### P4：展示层 / tooltip / i18n 改说 DNF

**目标**：列表 tooltip 与摘要呈现 DNF + 消耗 + 新字段；disabled 规则置灰；i18n 中英文同步。

**依赖**：P2、P3（展示层引用的新字段均已就位）。

**涉及文件**
- 新增 `common/.../client/ui/presentation/ConditionExpressionPresenter.java`（DNF -> `Component` 行：组间 OR、组内 AND、取反、类型显示名 + 参数摘要）
- 新增 `common/.../client/ui/presentation/ConsumptionPresenter.java`（消耗 -> `Component` 行）
- 修改 `common/.../client/ui/presentation/BuiltinConfigPresentations.java`、`ConfigTooltipProvider.java`、`ConfigTooltipBuilder.java`（移除扁平条件 tooltip，接入 DNF + 消耗 + priority/enabled/notes 摘要）
- 修改 `common/.../client/ui/panel/configlist/*`（disabled 规则置灰 + `[Disabled]` 前缀）
- i18n：`en_us.json`/`zh_cn.json` 移除过时扁平条件 tooltip key，新增 DNF/消耗/条件类型显示名 key

**步骤**
1. `ConditionExpressionPresenter`：遍历 `ConditionExpression.groups()`，每组渲染其叶（类型显示名来自 `ClientConditionTypeRegistry` + 参数摘要），组内 AND 连接，组间 OR 换行；取反叶前缀 NOT。空表达式显示"无条件（恒真）"。
2. `ConsumptionPresenter`：渲染消耗催化剂列表（`item xN`）+ 消耗流体。
3. `BuiltinConfigPresentations`/`ConfigTooltipProvider`：tooltip 结构 = 转化摘要行 + `Priority: N` + 条件 DNF 块 + 消耗块 + `Notes: ...`（若有）。disabled 规则在列表行置灰并加 `[Disabled]` 前缀。
4. i18n 清理与新增：移除 `tooltip.dimension`/`tooltip.need_outdoor`/`tooltip.surrounding_block*`/`tooltip.catalyst_*`/`tooltip.inner_fluid*` 等扁平 key（编辑器标签 key 随 P2/P3 处理）；新增 `condition.itemdespawntowhat.type.*` 显示名、DNF 连接词（AND/OR/NOT）、消耗标签、disabled 标签。

**验证**：`./gradlew build`；列表 tooltip 正确呈现 DNF/消耗/新字段；disabled 置灰；中英文 key 齐全；无遗漏的扁平条件 tooltip。

**风险**：`inspect` 命令（`command.itemdespawntowhat.inspect.condition.*`）可能复用条件展示逻辑或旧 getter--若复用，统一走 `ConditionExpressionPresenter`；若用旧 getter，P5 一并迁移。

---

### P5：退役扁平垫片 + 旧控件 + 清理

**目标**：在新 UI 与展示层全部切换完成后，退役 `BaseConversionConfig` 扁平垫片与旧复合控件，迁移所有旧 getter 调用方。

**依赖**：P2、P3、P4。

**涉及文件**
- 修改 `common/.../config/conversion/BaseConversionConfig.java`（移除旧扁平 getter/setter、`supportsLegacyEditor`、`firstGroupLeaf`/`setFirstGroupLeaf`；`validate` 改为直接读 `ConditionExpression`/`ConsumptionDirective`）
- 修改 `common/.../config/condition/ConditionExpression.java`（移除 `supportsLegacyEditor`/`firstGroupLeaf`/`setFirstGroupLeaf` 等仅为垫片服务的 API，若已无调用方）
- 退役 `common/.../client/ui/widget/CatalystItemsWidget.java`、`InnerFluidWidget.java`、`SurroundingBlocksWidget.java`
- 审计并迁移所有旧 getter 调用方（`inspect` 命令、测试、任何残余引用）
- i18n：移除完全废弃的 key（`edit.dimension`/`edit.need_outdoor`/`edit.surrounding_blocks`/`edit.catalyst_items`/`edit.inner_fluid`/`edit.biome`/`edit.weather` 等扁平字段标签，若未被新编辑器复用）

**步骤**
1. 全局审计旧扁平 getter（`getDimension`/`setDimension`/`isNeedOutdoor`/`setNeedOutdoor`/`getSurroundingBlocks`/`setSurroundingBlocks`/`getCatalystItems`/`setCatalystItems`/`getInnerFluid`/`setInnerFluid`/`getBiome`/`setBiome`/`getConditionWeatherMode`/`setConditionWeatherMode`）的调用方：UI（应已无）、展示层（P4 已迁移）、`validate`、`inspect` 命令、测试。
2. `validate` 迁移：`ConditionExpression.compile(config)` 已校验叶参数（`ConditionTypeDefinition.createChecker` 检查 allowedFields + 构建检查器）；额外校验（催化剂不与源物品冲突、fluid id 合法、surrounding block id 合法）改为遍历条件叶 + 读 `ConsumptionDirective` 直接执行。
3. `inspect` 命令迁移：若用旧 getter 读条件，改为遍历 `ConditionExpression` 并用 `ConditionExpressionPresenter` 或等价逻辑展示。
4. 移除 `BaseConversionConfig` 旧扁平 getter/setter、`supportsLegacyEditor`、`firstGroupLeaf`/`setFirstGroupLeaf`。
5. 退役 `CatalystItemsWidget`/`InnerFluidWidget`/`SurroundingBlocksWidget`（P2/P3 后应无引用）。
6. 清理废弃 i18n key，确保中英文同步。
7. 清理死代码、未用导入（与近期 `style` 提交风格一致）。

**验证**：`./gradlew build`（common + Fabric + NeoForge）；无旧扁平 getter/setter 残留引用；v2 配置加载 + `validate` 行为等价；UI 全链路可用；i18n 无废弃 key。

**风险**
- `validate` 迁移须保证等价：旧 `validate` 经 getter 合成的校验对象与新直接读 DNF/消耗的校验结果一致。建议对照旧 `validateDefinition` 逐条核对。
- `inspect` 命令展示若依赖旧 getter 的"单组"视图，迁移后应展示完整 DNF（行为改进，需确认符合预期）。

---

## 六、迁移与向后兼容

- **配置文件不动**：本次为纯 UI + 垫片退役，不涉及 schema 版本或文件迁移。v2 配置加载行为不变。
- **垫片退役顺序**：P2/P3 切换 UI -> P4 迁移展示层 -> P5 退役垫片。垫片在 P1-P4 期间保留，保证中间可编译、可编辑（单组场景行为不变）。
- **第三方条件类型**：未注册客户端定义的类型在编辑器只读回退（ADR-0011 决策 7），配置不损坏。
- **i18n**：每阶段新增的 GUI key 同步 `en_us.json`/`zh_cn.json`；P4/P5 清理废弃 key。

## 七、风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 子屏与主屏状态同步 | `ConditionExpression` 部分编辑中间态 | 子屏持有工作副本，返回时整体回写 |
| `BaseConfigEditScreen` 嵌套屏支持 | 子屏打开/关闭机制可能需补 | P2 核实，必要时补 `Screen` 转交；Minecraft 原生 `setScreen` 可用 |
| `validate` 等价性 | 退役垫片后校验行为漂移 | P5 逐条对照旧 `validateDefinition`；保留 v2 配置回归验证 |
| 第三方条件类型客户端契约 | 第三方未提供客户端定义则只读 | 只读回退 + 文档化 SPI 形状（ADR-0011） |
| 旧 getter 残留调用方 | P5 退役时编译断裂 | P5 前全局审计；`inspect`/测试一并迁移 |
| 子屏是迄今最复杂 UI | 工期长 | P2 先做最小可用（单组 + 叶增删 + 类型选择），再迭代多组/未知类型回退 |

## 八、验收标准

1. 全 5 阶段 `./gradlew build` 通过（common + Fabric + NeoForge）。
2. DNF 条件表达式可在 UI 完整编辑：组间 OR、组内 AND、叶取反、条件类型选择、参数编辑。
3. rich DNF（多组/取反/第三方类型）配置可正确加载并在子屏编辑，不再 no-op 丢编辑。
4. 未知第三方条件类型在编辑器只读回退，配置不损坏。
5. 消耗编辑器内联于主表单；"消耗隐含在场"行为正确；"要求在场不消耗"经条件叶可达。
6. 列表 tooltip/摘要呈现 DNF + 消耗 + priority/enabled/notes；disabled 规则置灰。
7. `BaseConversionConfig` 扁平垫片与 `supportsLegacyEditor` 退役；`validate` 直接读 DNF/消耗，行为等价。
8. 旧复合控件退役；i18n 中英文齐全，无废弃 key。
9. 第三方可经 `ClientConditionTypeRegistry` 注册新条件类型的客户端编辑表单。

## 九、文档与 ADR

- **已落档**：`docs/adr/0011-client-condition-editing-architecture.md`（决策 1-3：退役垫片 + 客户端条件注册表 + 条件子屏）。
- **CONTEXT.md**：已有客户端 UI 术语（转化类型定义 / 声明式字段 schema / 手写逃逸 / 复合组件 / 条件联动 / 编辑会话锁）覆盖本次所需；条件编辑子屏为实现产物，不入词汇表。
- 每阶段完成后更新本计划阶段状态（标题标注 ✅/进行中）。
