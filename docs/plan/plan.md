# 配置系统重构计划

> 本计划基于 `grill-with-docs` 会话达成的共识（见 `CONTEXT.md` 词汇表与 `docs/adr/0007`、`docs/adr/0008`）。
> 目标读者：项目维护者。每阶段均以 `./gradlew build` 通过为门槛。

---

## 一、背景

掉落物在自然消失前按规则转化为其他内容。当前配置系统存在 7 簇局限性（详见 grilling 会话）：

1. **触发条件纯 AND**，无 OR/NOT/嵌套；且每周期只选中一条规则，"写两条规则模拟 OR"也被首条遮蔽。
2. **优先级 = 非空条件计数**，等复杂度按文件插入序，无显式 `priority`。
3. **结果确定性**：`result` 不支持 `#tag`，无概率/战利品表。
4. **世界效果封闭 enum**（`WorldEffectType`），与可扩展主题相悖；`ItemToWorldEffectConfig` 是胖 tag-union。
5. **result_limit 语义漂移**（item/mob/block/world_effect 四种含义）；搜索半径 `6` 硬编码。
6. **序列化无版本/迁移**；`deserialize` 静默吞异常（配置无声丢失）。
7. **一致性杂项**：`result` 语义重载；缺 biome/weather/计数等常见触发维度。

## 二、目标与非目标

### 目标（本次纳入）
- 条件表达式改为 DNF + 可扩展条件类型注册表 + 谓词/消耗拆分 + 显式优先级（ADR-0007）。
- 配置引入 `schema_version` 与自动迁移（ADR-0008）。
- 世界效果拆为独立转化类型。
- 结果支持 `#tag` 随机 + 新增 `item_to_loot` 类型。
- `result_limit` 语义统一 + 可配置 `search_radius`。
- 新增内置条件 `biome`/`weather`；新增字段 `enabled`/`notes`/`luck`。

### 非目标（暂缓）
- 计时鲁棒性（条件失败宽限/部分进度）。
- `cooldown`（per-rule 转化冷却）。
- 其余环境条件（时间/亮度/y 高度/月相/邻近实体计数/玩家邻近）--可经条件类型注册表按需扩展，本次不内置。

## 三、架构变更总览

| 维度 | 现状 | 目标 |
|---|---|---|
| 条件结构 | 扁平 5 字段纯 AND（`ConditionContext`） | DNF 表达式（组间 OR / 组内 AND / 叶可取反） |
| 条件类型 | `ConditionCheckerRegistry`（仅注册检查器） | `ConditionType` 注册表（id + 参数 DTO + 检查器 + 客户端 schema），镜像 `ConversionType` |
| 消耗 | 与条件耦合（`catalyst_items`/`inner_fluid` 既是谓词又消耗） | 谓词归条件叶；消耗独立为 `ConsumptionDirective` |
| 优先级 | 复杂度计数 + 插入序 | 显式 `priority` + 叶数兜底 + 插入序 |
| 世界效果 | `WorldEffectType` enum + 单一 `ItemToWorldEffectConfig` | 拆为 `item_to_lightning`/`item_to_explosion`/`item_to_arrow_rain`/`item_to_weather` 独立类型 |
| 结果 | 单一注册项，确定性 | 支持 `#tag` 随机（item/block/mob）+ `item_to_loot`（战利品表） |
| 结果上限 | 四种漂移语义 + 硬编码半径 6 | 统一最大产物上限（超限拒载）+ 可行类型启用邻近累积检测 + 可配置 `search_radius` |
| 序列化 | 纯 Gson，无版本，吞异常 | `schema_version` + 自动迁移 + 显式错误 |

## 四、阶段依赖与顺序

```
阶段0 基线 ──> 阶段1 序列化基础 ──> 阶段2 条件模型核心(服务端) ──> 阶段3 客户端条件组编辑器
                                          │
                                          └──> 阶段4 世界效果拆分 + loot + tag ──> 阶段5 result_limit + 新条件 + 新字段 ──> 阶段6 清理验收
```

- 阶段 1 是所有破坏性变更的安全地基，**必须最先**。
- 阶段 2 是最大阶段，内部细分为 2a–2d，每个子步保持可编译。
- 阶段 3 依赖阶段 2 的服务端 DTO/注册表（客户端以 id 关联）。
- 阶段 4/5 依赖阶段 2 的条件模型（新类型复用 DNF + priority）。
- 阶段 6 在全部功能阶段后清理退役类。

---

## 五、分阶段计划

### 当前执行状态（2026-08-10）

- 阶段 0：基线 `./gradlew build` 已通过；按项目约定未新增 test 文件，迁移样本夹具留待人工联调。
- 阶段 1：已完成。加载时缺失版本按 v1 处理，迁移成功并完成反序列化校验后写回 v2，同时保留 `.v1.bak`。
- 阶段 2a-2c：已完成。DNF 条件注册表、表达式求值、谓词/消耗拆分、显式优先级和 v1 -> v2 迁移均已接入主运行时。
- 阶段 2d：已复用现有位置缓存与短路求值；高密度掉落物 TPS 验证留待游戏内测试。
- 阶段 3：仅完成过渡兼容。现有 GUI 可编辑单个 AND 组和 `priority`；多组、NOT、第三方条件会原样保留，但尚无专用编辑控件。
- 阶段 4：已完成。世界效果已拆为四个独立转化类型；旧联合文件可安全分流迁移；`item_to_loot`、item/block/mob 的 `#tag` 随机结果和最小 GUI 适配已完成；阶段 4 全量 `./gradlew build` 已通过。
- 阶段 5：已完成。统一结果上限与 `search_radius`、`enabled`/`notes` 字段、biome/weather 条件及中英文 GUI 文本已接入；完整 DNF 条件组编辑器仍属于阶段 3 的后续工作。
- 阶段 6：已完成。退役世界效果类、旧扁平条件注册链和死代码已清理；平台依赖边界、客户端/服务端引用、i18n JSON 与退役引用已复核；最终 `./gradlew build` 已通过 common、Fabric、NeoForge。迁移行为等价与高密度 TPS 仍留待游戏内联调。

阶段 5 兼容说明：XP 转化在阶段 5 前曾继承 `result_limit` 字段。现版本不再使用该字段，但通过 nullable deprecated 字段兼容读取旧 v2 文件，避免严格字段校验阻断整个配置加载；新建 XP 配置不会默认写出该字段。

### 阶段 0：基线与迁移测试夹具

**目标**：锁定可编译基线，准备 v1 配置样本用于迁移验证。

**步骤**
1. `./gradlew build` 确认当前 `1.21.1` 分支全量编译通过。
2. 在 `common/src/test/resources/migration/`（若无测试目录则新建）保存若干代表性 v1 配置样本：item_to_item（含 catalyst）、item_to_mob（含 dimension+surrounding）、item_to_block（含 inner_fluid）、item_to_world_effect（explosion）。这些样本将贯穿后续阶段验证 v1->v2 迁移等价性。

**验证**：基线 build 通过；样本就位。

---

### 阶段 1：序列化基础（schema_version + 迁移管线 + 修复吞异常）

**目标**：为后续破坏性字段变更铺设安全迁移地基，先于任何字段重构。本阶段 v2 结构尚不存在，迁移逻辑为 no-op 框架。

**依赖**：阶段 0。

**涉及文件**
- `common/.../config/conversion/BaseConversionConfig.java`
- `common/.../config/io/ConfigJsonCodec.java`
- `common/.../config/io/JsonConfigRepository.java`
- 新增 `common/.../config/io/ConfigMigrator.java`

**步骤**
1. `BaseConversionConfig` 新增序列化字段：
   ```java
   @SerializedName("schema_version")
   protected int schemaVersion = 1;
   ```
   旧文件缺失该字段时 Gson 取默认 1，兼容。
2. 新增 `ConfigMigrator`：定义迁移管线接口（输入 `JsonElement` + 当前 `schemaVersion`，输出迁移后 `JsonElement` + 新版本号）。本阶段只实现"version=1 直通"的占位实现，v1->v2 实际转换留到阶段 2c。
3. `ConfigJsonCodec`：
   - 修复 `deserialize` 静默吞异常--不再 `catch(Exception ignored) return empty`，改为记录 `WARN` 并返回空列表（保留容错但可观测）；`deserializeStrict` 保持抛异常。
   - 反序列化后、返回前调用 `ConfigMigrator`；若版本发生变化，由调用方决定写回。
4. `JsonConfigRepository.readEntries`：反序列化 + 迁移后，若 `schemaVersion` 变化则原子写回新文件（复用 `writeBytesAtomically`）。

**验证**：`./gradlew build`；旧 v1 配置加载行为不变（version 默认 1，迁移 no-op）；故意写入格式错误配置，日志可见 WARN 而非无声清空。

**风险**：迁移写回发生在加载期，若迁移有 bug 会改写用户文件。缓解：写回前校验迁移结果可反序列化；保留原文件备份一拍（写入 `.bak` 或先验证）。

---

### 阶段 2：条件模型核心（服务端）

> 最大阶段。按 2a->2d 推进，每个子步保持可编译。完成前客户端仍用旧字段（阶段 3 才适配），故 2a–2c 期间需在 2c 末统一切换服务端 DTO，客户端随即在阶段 3 适配。

**目标**：落地 ADR-0007--DNF 表达式 + 可扩展条件类型注册表 + 谓词/消耗拆分 + 显式优先级 + v1->v2 迁移。

#### 2a：ConditionType 注册表与内置条件（独立可编译）

**涉及文件**
- 新增 `common/.../config/condition/type/ConditionType.java`（id + `ConditionTypeDefinition`）
- 新增 `common/.../config/condition/type/ConditionTypeDefinition.java`（参数 DTO 类型 + 谓词检查器工厂）
- 新增 `common/.../config/condition/type/ConditionTypeRegistry.java`（镜像 `ConversionTypeRegistry`：`register`/`all`/`byId`/`require`）
- 新增 `common/.../config/condition/type/BuiltinConditionTypes.java`
- 新增内置条件 DTO + 检查器（每个条件一个 DTO + 一个 checker）：
  - `DimensionCondition`（参数：dimension id）
  - `OutdoorCondition`（无参数）
  - `SurroundingBlocksCondition`（参数：6 方向 block/tag）
  - `CatalystPresentCondition`（参数：催化剂 item/tag + 阈值 count，纯谓词）
  - `FluidPresentCondition`（参数：fluid id + requireSource，纯谓词）

**步骤**
1. 仿 `ConversionType`/`ConversionTypeDefinition`/`ConversionTypeRegistry` 建 `ConditionType` 体系。`ConditionTypeDefinition<P>` 持有：参数 DTO 的 `Type`、`PredicateChecker<P>` 工厂、客户端表单 schema 引用（client 类，common 不引用，留接口）。
2. `ConditionTypeRegistry` 用 `LinkedHashMap<ResourceLocation, ConditionType>`，static 块预注册 5 内置条件（命名空间 `itemdespawntowhat`）。
3. 每个内置条件实现：参数 DTO（record 或不可变类）+ `PredicateChecker`（输入 `ItemEntity`+`ServerLevel`+参数，返回 boolean）。复用现有 `DimensionConditionChecker`/`OutdoorConditionChecker`/`SurroundingBlocksConditionChecker`/`CatalystConditionChecker`/`InnerFluidConditionChecker` 的判定逻辑，剥离其消耗部分。

**验证**：`./gradlew build`；新注册表独立可用，尚未接入 config。

#### 2b：条件表达式 DTO + DNF 求值器（独立可编译）

**涉及文件**
- 新增 `common/.../config/condition/ConditionExpression.java`（DNF：`List<ConditionGroup>`）
- 新增 `common/.../config/condition/ConditionGroup.java`（`List<ConditionLeaf>`）
- 新增 `common/.../config/condition/ConditionLeaf.java`（`conditionTypeId` + 参数多态 DTO + `negated`）
- 新增 `common/.../config/condition/ConditionExpressionEvaluator.java`（DNF 求值：任一组全真则真）
- 新增 `common/.../config/condition/ConditionLeafDeserializer.java`（Gson 自定义反序列化：按 `conditionTypeId` 分派到对应参数 DTO）

**步骤**
1. `ConditionExpression` 持有有序 `ConditionGroup` 列表（空列表 = 恒真）。
2. `ConditionLeaf` 持有 `ResourceLocation conditionTypeId`、参数对象、`boolean negated`。参数多态用自定义 `JsonDeserializer`：读 `type` 字段 -> 查 `ConditionTypeRegistry` -> 用该类型的参数 DTO 类反序列化 `params`。
3. `ConditionExpressionEvaluator`：遍历组，首个全真组即匹配（短路）；返回 boolean（纯谓词，无副作用，符合 ADR-0007 拆分决策）。
4. 复杂度 = `ConditionExpression` 的条件叶总数（用于优先级兜底）。

**验证**：`./gradlew build`；单元测试 DNF 求值（含取反、空组、多组短路）。

#### 2c：接入 BaseConversionConfig + 谓词/消耗拆分 + v1->v2 迁移

**涉及文件**
- `common/.../config/conversion/BaseConversionConfig.java`
- 新增 `common/.../config/consumption/ConsumptionDirective.java`（催化剂消耗列表 + consume 标志、流体消耗 + consume 标志）
- `common/.../config/catalogue/CatalystItems.java`、`InnerFluid.java`（剥离 consume 到 ConsumptionDirective，保留谓词参数）
- `common/.../config/condition/ConditionCheckerUtil.java`、`ConditionContext.java`、`checker/*`（退役或重构）
- `common/.../config/runtime/ConversionRuleCompiler.java`、`CompiledConversionRule.java`、`RuntimeConfigSnapshotBuilder.java`
- `common/.../server/conversion/ItemConversionProcessor.java`
- `common/.../config/io/ConfigMigrator.java`（填充 v1->v2）

**步骤**
1. `BaseConversionConfig` 字段重构：
   - 移除扁平条件字段 `dimension`/`needOutdoor`/`surroundingBlocks`/`catalystItems`/`innerFluid`。
   - 新增 `@SerializedName("conditions") ConditionExpression conditionExpression`（默认空 = 恒真）。
   - 新增 `@SerializedName("consumption") @Nullable ConsumptionDirective consumptionDirective`。
   - 新增 `@SerializedName("priority") int priority = 0`。
   - `computeComplexity()` 改为 `conditionExpression.leafCount()`。
2. `ConsumptionDirective`：承载原 `catalyst_items` 的 consume 部分（催化剂列表 + 每轮 count + consume 标志）与 `inner_fluid` 的 consume 部分（fluid + consume 标志）。`CatalystItems`/`InnerFluid` 拆分：谓词参数留在条件叶 DTO，消耗数据移入 `ConsumptionDirective`。
3. 条件检查器层重构：
   - `ConditionContext` 退役（叶自带参数）。
   - `ConditionCheckerUtil.buildCombinedChecker` 改为 `buildEvaluator(ConditionExpression)`，返回 `ConditionExpressionEvaluator`。
   - `ConditionCheckerRegistry` 退役，由 `ConditionTypeRegistry` 取代。
   - 旧 `checker/*` 的判定逻辑已迁入 2a 的内置条件，此处删除旧类。
4. `ConversionRuleCompiler`：从 `config.getConditionExpression()` 构建 evaluator；复杂度 = 叶数。
5. `CompiledConversionRule`：暴露 `priority()`；`matches()` 委托 evaluator。
6. `RuntimeConfigSnapshotBuilder`：规则排序改为 `priority` desc -> 叶数 desc -> 插入序。
7. `ItemConversionProcessor.selectBestMatchingRule`：按 `priority` desc -> 叶数 desc 选择；`getMaxComplexityForItem` 改为 `getMaxPriorityForItem`（或等价的"是否存在更高优先级规则"判断），相应调整"升级到更高优先级规则"的逻辑。
8. 执行器适配：`AbstractConversionExecutor.consumeAllOthers` 改为从 `config.getConsumptionDirective()` 读取消耗（催化剂 `consumeFromLevel` + 流体 `consumeFluidFromLevel`）；`computeActualRounds` 的催化剂轮数计算改读消耗指令。
9. `ConfigMigrator` 填充 v1->v2：
   - v1 扁平字段 -> 单个 `ConditionGroup`，含对应条件叶（非空字段才生成叶）。
   - `catalyst_items.consume`/`inner_fluid.consume` -> `ConsumptionDirective`。
   - `schema_version` 置 2，写回。

**验证**：`./gradlew build`；阶段 0 的 v1 样本经迁移后等价加载（条件、消耗、计时行为不变）；DNF 单组场景与旧 AND 行为一致。

**风险**
- 多态叶 Gson 反序列化：用自定义 `JsonDeserializer`（按 `type` 分派），不依赖 guava `RuntimeTypeAdapterFactory`。
- 2c 服务端 DTO 切换会断客户端编译：建议 2c 完成后立即进入阶段 3，避免 `@Deprecated` 双写垫片债务。若必须保持中间可编译，临时垫片需在阶段 3 入口删除。

#### 2d：内置条件检查器落地与性能

**步骤**
1. 5 个内置条件的 `PredicateChecker` 落地，复用 `PositionCachedConditionChecker` 模式（位置相关条件按 blockPos 缓存，天气/维度全局缓存）。
2. 确认 DNF 求值在 `CHECK_INTERVAL_TICKS=20` 周期下对被追踪物品集（O(T)）的性能可接受；叶数通常 ≤5，短路求值。

**验证**：`./gradlew build`；高密度掉落物场景下 TPS 无明显回落。

---

### 阶段 3：客户端条件组编辑器

**目标**：在 ADR-0006 字段中心架构上，为条件表达式（所有类型共享）新增框架级复合控件。

**依赖**：阶段 2（服务端 DTO + `ConditionTypeRegistry`）。

**涉及文件**
- 新增 `common/.../client/ui/form/ConditionExpressionField.java`（实现 `FormFieldInput`）
- 新增 `common/.../client/ui/form/ConditionLeafEditor.java`（条件类型选择 + 参数表单 + 取反开关）
- 新增 `common/.../client/ui/form/ConsumptionDirectiveField.java`（消耗指令编辑）
- 新增客户端条件类型注册表：`common/.../client/ui/condition/ClientConditionTypeDefinition.java`、`ClientConditionTypeRegistry.java`、`BuiltinClientConditionTypes.java`
- `common/.../client/ui/form/BuiltinFormDefinitions.java`（各类型 FormDefinition 移除扁平条件字段，改用新复合控件）
- `common/.../client/ui/form/FormRenderer.java`（支持复合控件的焦点遍历/布局，若尚未支持）

**步骤**
1. `ClientConditionTypeDefinition`：持有条件类型 id + 参数表单 schema（`ConfigFieldSchema` 或 `FormDefinition` 工厂）+ 显示名 key。镜像服务端 `ConditionType`，以同一 `ResourceLocation` 关联，client 不引用 server 类。
2. `ClientConditionTypeRegistry`：预注册 5 内置条件（+ 阶段 5 的 biome/weather），第三方走同路径。
3. `ConditionExpressionField`：渲染可重复"条件组"列表，每组内渲染可重复"条件叶"行 + 组间 OR 提示。实现 `FormFieldInput`（管理内部焦点、增删组/叶）。
4. `ConditionLeafEditor`：条件类型 CycleButton（从 `ClientConditionTypeRegistry`）-> 选中后渲染该类型的参数表单 -> 取反开关。
5. `ConsumptionDirectiveField`：催化剂列表编辑（item/tag + count + consume）+ 流体消耗开关。
6. `BuiltinFormDefinitions`：5 个内置类型的 `FormDefinition` 移除原 9 个通用条件字段，改为 `ConditionExpressionField` + `ConsumptionDirectiveField` + `priority` 字段。
7. `FormRenderer`：确认对复合控件内部焦点遍历、逐字段校验、建议浮层的支持（ADR-0006 已奠定，按需补齐）。

**验证**：`./gradlew build`；客户端编辑 UI 能增删条件组/叶、切换条件类型、取反、编辑消耗；保存写出合法 v2 JSON。

**风险**：条件组编辑器是迄今最复杂的复合控件；建议先做最小可用（单组 + 叶增删），再迭代多组/联动。

---

### 阶段 4：世界效果拆分 + item_to_loot + #tag 随机

**目标**：退役 `WorldEffectType` enum，世界效果成为独立转化类型；新增战利品表结果；结果支持 `#tag` 随机。

**依赖**：阶段 2（新类型复用 DNF + priority + 迁移）。

#### 4a：世界效果拆分为独立转化类型

**涉及文件**
- 新增 `common/.../config/conversion/ItemToLightningConfig.java` + `ItemToLightningExecutor.java`
- 新增 `common/.../config/conversion/ItemToExplosionConfig.java` + `ItemToExplosionExecutor.java`
- 新增 `common/.../config/conversion/ItemToArrowRainConfig.java` + `ItemToArrowRainExecutor.java`
- 新增 `common/.../config/conversion/ItemToWeatherConfig.java`（mode: rain/clear + duration + thundering）+ `ItemToWeatherExecutor.java`
- `common/.../config/WorldEffectType.java`、`ItemToWorldEffectConfig.java`、`ItemToWorldEffectExecutor.java`（退役）
- `common/.../config/type/ConversionTypeRegistry.java`、`BuiltinConversionTypes.java`（注册新类型，移除 `item_to_world_effect`）
- 客户端 `BuiltinFormDefinitions.java`（新类型 FormDefinition）
- `common/.../config/io/ConfigMigrator.java`（v1 `item_to_world_effect` 配置按 `side_effect` 分流到对应新类型文件）

**步骤**
1. 为 lightning/explosion/arrow_rain/weather 各建 `ConversionType`：独立 Config DTO（仅本效果参数）+ Executor（复用 `LevelTaskManager` + 对应 Task）+ 客户端 FormDefinition。
2. `WorldEffectType.canExecute(level)` 逻辑迁入各 Executor。
3. `Constants.lightningIntervalTicks`/`explosionIntervalTicks`/`arrowIntervalTicks` 由各 Executor 直接引用。
4. 注册 4 新类型到 `ConversionTypeRegistry`，从 `BuiltinConversionTypes` 移除 `ITEM_TO_WORLD_EFFECT`，新增 4 常量。
5. `ConfigMigrator`：v1 `item_to_world_effect` 文件按每条 `side_effect` 分流到 `item_to_lightning`/`item_to_explosion`/`item_to_arrow_rain`/`item_to_weather` 文件。
6. 退役 `WorldEffectType`/`ItemToWorldEffectConfig`/`ItemToWorldEffectExecutor`。

**验证**：`./gradlew build`；v1 世界效果配置迁移后各效果行为等价。

#### 4b：新增 item_to_loot 类型

**涉及文件**
- 新增 `common/.../config/conversion/ItemToLootConfig.java`（`result` = 战利品表 id + `luck`）+ `ItemToLootExecutor.java`
- `common/.../config/type/ConversionTypeRegistry.java`、`BuiltinConversionTypes.java`
- 客户端 `BuiltinFormDefinitions.java`

**步骤**
1. `ItemToLootConfig extends BaseItemToEntityConfig`（复用 `result_limit`）：`result` = 战利品表 `ResourceLocation`，新增 `@SerializedName("luck") float luck = 0`。
2. `ItemToLootExecutor`：`level.getServer().getLootData().getLootTable(id)` -> `lootTable.getRandomItems(builder.withLuck(luck))` -> 生成物品实体栈。消耗/轮数复用 `AbstractConversionExecutor`。
3. 注册 `item_to_loot` 到注册表 + `BuiltinConversionTypes`。
4. 客户端 FormDefinition：result 输入（战利品表 id 建议）+ luck 字段。

**验证**：`./gradlew build`；配置一条 item_to_loot 规则，转化时按战利品表掷出物品。

#### 4c：结果支持 #tag 随机

**涉及文件**
- `common/.../config/conversion/ItemToItemConfig.java`、`ItemToBlockConfig.java`、`ItemToMobConfig.java`
- 各对应 Executor

**步骤**
1. `initResultCache` 扩展：`resultId` 以 `#` 开头时走 `TagResolver.resolveTagItems`（对应注册表：ITEM/BLOCK/ENTITY_TYPE），缓存展开列表。
2. 结果获取方法：标签模式下从缓存列表随机抽一个（由执行器在转化时用 `level.random` 抽取）。
3. `additionalCheck`：标签模式下校验标签可解析、非空。
4. 各 Executor 在转化时调用随机结果获取。

**验证**：`./gradlew build`；`#tag` 结果每次转化随机抽一个，标签为空时配置被拒载。

---

### 阶段 5：result_limit 统一 + search_radius + 新条件 + 新字段 + i18n

**目标**：统一结果上限语义；新增内置条件 biome/weather；新增字段 enabled/notes/luck（luck 已在 4b）；补 i18n。

**依赖**：阶段 2、4。

**涉及文件**
- `common/.../config/ConversionLimits.java`
- `common/.../config/conversion/BaseConversionConfig.java`、`BaseItemToEntityConfig.java`
- 各 Executor（`ItemToItemExecutor`/`ItemToMobExecutor`/`ItemToBlockExecutor`/`ItemToLootExecutor`）
- 新增 `common/.../config/condition/type/BiomeCondition.java`、`WeatherCondition.java` + 客户端定义
- 客户端 `BuiltinClientConditionTypes.java`、`BuiltinFormDefinitions.java`
- `common/.../config/condition/type/ConditionTypeRegistry.java`、`BuiltinConditionTypes.java`
- i18n：`common/src/main/resources/assets/itemdespawntowhat/lang/en_us.json`、`zh_cn.json`

**步骤**
1. **result_limit 统一**：
   - `ConversionLimits` 新增 `MAX_RESULT_UNITS`（统一最大产物上限，超限 `validate` 拒载）与 `MAX_SEARCH_RADIUS`。
   - `BaseConversionConfig`/`BaseItemToEntityConfig`：`result_limit` 语义统一为"搜索半径内累积结果数上限"；新增 `@SerializedName("search_radius") int searchRadius = 6`（上限 `MAX_SEARCH_RADIUS`）。
   - 各 Executor 的硬编码 `6` 改为 `config.getSearchRadius()`。
   - 仅 item/mob/block/loot 启用邻近累积检测；lightning/explosion/arrow_rain/weather 跳过（只受 `MAX_RESULT_UNITS` 单次批量约束）。block 的 `MAX_BLOCK_PLACEMENTS`、effect 的执行次数上限归为"单次批量上限"（类型特定安全阀），与 `result_limit`（邻近累积）正交。
2. **新内置条件**：
   - `BiomeCondition`（参数：biome id 或 `#biome_tag`），`level.getBiome(pos)` 比对。
   - `WeatherCondition`（参数：raining/thundering/clear），`level.isRaining()`/`isThundering()`。
   - 注册到 `ConditionTypeRegistry` + `BuiltinConditionTypes` + 客户端 `ClientConditionTypeRegistry`。
3. **新字段**：
   - `enabled`（`BaseConversionConfig`，默认 true）：`shouldProcess` 前置检查，false 则拒载。
   - `notes`（`BaseConversionConfig`，`@Nullable String`）：纯注释，序列化但不参与运行时/校验。
   - `luck`：已在 4b 落地于 `ItemToLootConfig`。
4. **i18n**：新增字段的 GUI key（priority/search_radius/enabled/notes/luck/conditions/consumption + biome/weather 条件名 + 4 新世界效果类型名），同步 `en_us.json` 与 `zh_cn.json`。

**验证**：`./gradlew build`；超 `MAX_RESULT_UNITS` 配置拒载并日志告警；`search_radius` 生效；biome/weather 条件可配置可触发；新字段 UI 可编辑；中英文 key 齐全。

---

### 阶段 6：清理与验收

**目标**：退役遗留类，全量验证。

**步骤**
1. 删除阶段 2/4 标记退役的旧类（`WorldEffectType`、`ItemToWorldEffectConfig`、`ItemToWorldEffectExecutor`、`ConditionCheckerRegistry`、`ConditionContext`、旧 `checker/*` 若已无引用）。
2. 清理死代码、未用导入（与近期 `style` 提交风格一致）。
3. 全量 `./gradlew build`。
4. 迁移等价性测试：阶段 0 的 v1 样本逐条验证迁移后行为等价。
5. 文档：按需补 `docs/adr/0009-world-effect-flatten.md`、`docs/adr/0010-result-limit-unification.md`（若判定为 ADR-worthy）；`CONTEXT.md` 已在 grilling 阶段更新，复核术语一致。

**验证**：`./gradlew build` 通过；无退役类残留引用；v1->v2 迁移等价。

---

## 六、迁移与向后兼容

- **版本字段**：`schema_version` 默认 1（v1=当前扁平）。加载时 `ConfigMigrator` 按版本迁移，变化则原子写回。
- **v1->v2 映射**：
  - 扁平条件（dimension/need_outdoor/surrounding_blocks/catalyst_items/inner_fluid）-> 单 `ConditionGroup`，非空字段生成对应条件叶。
  - `catalyst_items.consume`/`inner_fluid.consume` -> `ConsumptionDirective`。
  - `item_to_world_effect` 配置按 `side_effect` 分流到 4 个新类型文件。
- **路径迁移**：保留 ADR-0004 的旧路径兼容读取（`JsonConfigRepository.migrateLegacyIfNeeded`）。
- **错误暴露**：`deserialize` 不再静默吞异常；未知字段在迁移后 strict 校验（v2 起）。
- **风险缓解**：迁移写回前校验结果可反序列化；建议保留原文件一拍备份。

## 七、风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 阶段 2 触及全链（编译器/检查器/执行器/客户端），最大风险源 | 编译断裂、行为回归 | 细分 2a–2d，每子步可编译；阶段 0 样本贯穿验证等价性 |
| 多态条件叶 Gson 反序列化 | 反序列化失败 | 自定义 `JsonDeserializer`（按 `conditionTypeId` 分派），不依赖 guava |
| 客户端/服务端分离约束 | common 误引用 client 类 | 条件类型注册表双轨：common `ConditionType` + client `ClientConditionTypeDefinition`，以 id 关联，同 ConversionType 模式 |
| 迁移写回改写用户文件 | 配置损坏 | 写回前校验可反序列化；保留备份；迁移单元测试覆盖 |
| DNF 求值性能 | 高密度掉落物 TPS 下降 | 短路求值 + 位置缓存（`PositionCachedConditionChecker` 模式）；叶数通常 ≤5 |
| 客户端条件组编辑器复杂度 | UI 工期长 | 先最小可用（单组），再迭代多组/联动 |
| 2c 服务端切换致客户端编译断 | 阶段间不可编译 | 2c 后立即进阶段 3；避免 `@Deprecated` 双写垫片债务 |

## 八、验收标准

1. 全 6 阶段 `./gradlew build` 通过（neoforge + fabric）。
2. v1 配置自动迁移到 v2，行为等价（条件触发、消耗、计时、结果上限）。
3. DNF 条件表达式可表达 OR/NOT/嵌套组（组间 OR）。
4. 第三方可经 `ConditionTypeRegistry` 注册新条件类型、经 `ConversionTypeRegistry` 注册新转化类型，客户端表单自动生成或自定义逃逸。
5. 世界效果为独立转化类型；`WorldEffectType` enum 退役。
6. `item_to_loot` 可掷战利品表；`#tag` 结果随机抽选。
7. `result_limit` 跨类型语义一致；`search_radius` 可配置；超 `MAX_RESULT_UNITS` 拒载。
8. 新字段 `priority`/`enabled`/`notes`/`luck`/`search_radius`/`schema_version` 就位；i18n 中英文齐全。

## 九、文档与 ADR

- **已落档**：`CONTEXT.md`（+6 术语）、`docs/adr/0007-condition-expression-dnf.md`、`docs/adr/0008-config-schema-versioning.md`。
- **按需补**：`0009-world-effect-flatten.md`、`0010-result-limit-unification.md`（若判定为 ADR-worthy：难反转 + 非显而易见 + 真实权衡）。
- 每阶段完成后更新本计划的阶段状态（建议在阶段标题标注 ✅/进行中）。
