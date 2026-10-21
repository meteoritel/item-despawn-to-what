---
title: 类型系统
layer: 3
audience: 新增条件或效果类型的模组作者、维护类型注册与内置实现的开发者
authority: common/core/registry、common/core/extension、common/core/type，装配点 common/core/service/BuiltinTypeRegistries.java
---

# 类型系统

## TL;DR

- 类型系统提供条件与效果类型的注册设施：注册期可变，`freeze()` 之后只读，冻结快照供运行时按 `type` 字段查表。
- 内置类型与第三方 SPI 在同一次装配中完成注册并统一冻结，SPI 在冻结前分两阶段调用。
- 新增一个类型是「参数记录 + 编解码器 + 校验与执行实现 + 一条注册」，落点见第 5 节。

## 适用对象

准备新增内置条件或效果类型的维护者，以及需要在其他模组中注册自定义类型、接入动态引用校验的扩展开发者。阅读前先了解 [核心契约](core-contract.md) 与 [规则模型](rule-model.md) 中的类型定义形态。

## 1. 模块职责

类型系统负责三件事：类型注册表的语义（注册期可变、冻结后只读）、第三方类型扩展入口（SPI 与类型自带扩展点）、内置条件与效果实现的位置与装载。

类型本身的形态——`TypeDefinition`、`ConditionType`、`EffectType`、`Condition`、`Effect` 及其 Codec 组装——归 `core/api` 与 `core/model`；本模块只消费这些契约并决定「有哪些类型可用」。

## 2. 模块边界

| 方向 | 内容 |
|---|---|
| 对外入口 | `SimpleTypeRegistry`（注册表实现）、`RuleTypeProvider`（第三方 SPI）、`DynamicReferenceCheck` / `DynamicReferenceContext`（类型自带扩展点）、`BuiltinConditionTypes` / `BuiltinEffectTypes`（内置类型装载）、`BuiltinTypeRegistries`（规范装配点与冻结边界） |
| 依赖 | `core/api` 的类型与注册契约、`core/model` 的类型定义接口与参数对象接口、DFU `MapCodec` |
| 被依赖 | 宿主启动（装配类型注册表并交给加载与运行时）、规则加载（解码与校验时按 `type` 查表）、编辑与快照装配（类型元数据参与候选与分类） |

注册表在服务端启动装配时建立并冻结；数据包重载复用同一份已冻结注册表，类型集合不随规则文件变化。

## 3. 关键类地图

仅列对外入口、扩展点、装配聚合点与并发敏感类，非穷举。

| 类/包 | 角色 | 职责 | 协作对象 |
|---|---|---|---|
| `core/registry/SimpleTypeRegistry` | 注册表实现 | 注册期用保序映射登记；`freeze()` 构造不可变快照并以 `volatile` 发布；重复 id 与冻结后注册各自抛出语义异常 | 全部查询方、`BuiltinTypeRegistries` |
| `core/api/TypeRegistry`、`core/api/TypeDefinition` | 契约 | 注册与查询接口；类型定义由 id、参数 `MapCodec` 与参数校验组成 | 契约细节见 [核心契约](core-contract.md) |
| `core/extension/RuleTypeProvider` | 第三方 SPI | 两阶段默认方法：先注册条件，再携带条件表达式 Codec 注册效果 | `BuiltinTypeRegistries.create()` |
| `core/extension/DynamicReferenceCheck`、`DynamicReferenceContext` | 扩展点 | 参数对象申报「须依赖服务端已加载内容才能判定的引用」，由核心校验器遍历分派 | `RuleReferenceValidator`（[规则加载](rule-loading.md)） |
| `core/type/BuiltinConditionTypes`、`core/type/BuiltinEffectTypes` | 内置类型装载 | `createMutable(...)` 逐条登记内置类型并交给装配点统一冻结；`create(...)` 用于直接构建并冻结的场景 | `BuiltinTypeRegistries` |
| `core/service/BuiltinTypeRegistries` | 装配聚合点 | 固定「条件 → 条件表达式 Codec → 效果」顺序，SPI 在两处冻结前插入 | 宿主启动、规则加载 |
| `core/type/EnumCodecs` | 公共工具 | 枚举取值统一小写下划线形式、解析大小写不敏感、未知取值报错时附可选值 | 各内置类型的参数 Codec |

## 4. 关键流程

注册生命周期（`BuiltinTypeRegistries.create()`）是冻结语义的唯一执行点：

```mermaid
flowchart TD
    A["BuiltinTypeRegistries.create()"] --> B["ServiceLoader 读取 RuleTypeProvider<br/>按实现类名排序"]
    B --> C["BuiltinConditionTypes.createMutable()<br/>登记内置条件类型"]
    C --> D["provider.registerConditions(conditionTypes)"]
    D --> E["conditionTypes.freeze()"]
    E --> F["RuleCodecs.conditionExpressionCodec(conditionTypes)"]
    F --> G["BuiltinEffectTypes.createMutable(expressionCodec)<br/>登记内置效果类型"]
    G --> H["provider.registerEffects(effectTypes, expressionCodec)"]
    H --> I["effectTypes.freeze()"]
    I --> J["注册表与条件表达式 Codec 交给加载与运行时"]
```

- 顺序的成因：效果记录带效果级 `conditions` 字段，效果参数 Codec 需要完整的条件表达式 Codec，因此条件必须先注册并冻结，再构建表达式 Codec。
- 冻结是终态：`freeze()` 幂等，之后 `register` 抛 `RegistryFrozenException`；重复 id 在注册期就抛 `DuplicateTypeException`，注册表保持原状，不做静默覆盖。
- 异常处理：装配点自身不吞异常，注册冲突与 provider 抛出的运行时异常向上交给宿主启动边界（见 [平台模块](platform.md)）。
- 并发约定：注册期只允许单线程注册，`freeze()` 由执行注册的同一线程调用；冻结后 `find` / `require` / `all` / `ids` 可被任意线程并发读。

## 5. 扩展点与约束

### 新增内置条件类型

目录：参数记录放 `common/src/main/java/com/meteorite/itemdespawntowhat/core/type/condition/`，求值器放同层的 `eval/`。

1. 写参数 `record` 并实现 `Condition`；该类型需要动态引用校验时同时实现 `DynamicReferenceCheck`。
2. 提供 `static final ResourceLocation ID`（同时是 JSON 中 `type` 的取值）、参数 `MapCodec`、`validateParams(...)`，以及返回 `ConditionType` 的静态工厂。静态工厂命名为 `conditionType()`，因为 `Condition` 已有无参实例方法 `type()`。
3. 求值器实现 `ConditionEvaluator`：它是纯谓词，只给原始判定，不承担消耗；上下文不足时由类型经 `evaluability(...)` 申报 `UNAVAILABLE`。
4. 在 `BuiltinConditionTypes.createMutable(...)` 增加一条 `registry.register(...)`；冻结由装配点统一完成，不要在类型内部冻结。
5. 该类型的 JSON 字段语义写入 [规则 JSON](../api/rule-json.md)。

### 新增内置效果类型

目录：参数记录放 `core/type/effect/`，执行器放同层的 `exec/`。

1. 写参数 `record` 并实现 `Effect`，同时实现 `withConditions(...)`：返回仅替换效果级条件、其余参数原样保留的新实例（运行期催化剂门槛投影依赖该重建入口）。
2. 参数 `MapCodec` 以 `codec(expressionCodec)` 形式提供，通用字段（延迟、概率、效果级 `conditions`）复用 `CommonFields` 片段；Codec 在解码期不产出 `null`。
3. `effectType(expressionCodec)` 返回 `SimpleEffectType`：id + 参数 `MapCodec` + 参数校验器 + `EffectExecutor` + 一次性标记 + 产出类别 + 容量需求解析器。
4. 执行器放 `exec/`：同步执行只需 `execute`；需要在成功转化当刻固定输入的类型覆盖 `prepare` 与按准备结果创建执行状态的 `start`，长循环在 `start` 返回的执行状态里按共享 tick 预算续接（见 [转化运行时](conversion-runtime.md)）。
5. 在 `BuiltinEffectTypes.createMutable(expressionCodec)` 增加一条登记。
6. 声明产出类别（`productKind`）后，含该效果的规则可按类别归组；`productKind` 默认 `ProductKind.NONE`，含这类效果的规则参与归类时按无产出处理（分类装配见 [编辑与落盘](authoring.md)）。
7. 产物占用邻近容量时通过 `capacityDemand` 声明每轮一组最小产出需求（具体物品/实体件数，或产物类型未知时按区域物品总量的保守下界）；不声明则视为不占容量，结算的容量承诺不会为该效果预留空间。

### 第三方类型 SPI

1. 实现 `core/extension/RuleTypeProvider`，在自身 jar 的 `META-INF/services/com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider` 中登记实现类全名。
2. 调用时机就是 `BuiltinTypeRegistries.create()`：内置条件登记之后、`conditionTypes.freeze()` 之前调 `registerConditions`；内置效果登记之后、`effectTypes.freeze()` 之前调 `registerEffects`，后者可以拿到完整的条件表达式 Codec，用来解码效果级 `conditions`。
3. 多个 provider 按实现类名排序后依次调用，跨端与跨次启动顺序稳定。
4. 注册的 id 自带命名空间；与已注册 id 冲突会抛 `DuplicateTypeException`，注册表不做覆盖。

### 动态引用校验扩展点

在参数对象上实现 `DynamicReferenceCheck.checkDynamicReferences(DynamicReferenceContext)`：上下文给出当前服务端、议题收集通道与当前效果/条件叶相对规则根的字段路径。实现把问题写入 `issues()`，字段路径以 `fieldPath()` 为前缀拼接，`origin` 传 `null`，由收集通道按当前规则来源统一补齐。

调用时机在规则加载的两档校验中：`RuleReferenceValidator` 遍历规则级条件树、顶层效果、候选结果内效果以及效果级条件树逐叶分派，自身不含任何具体类型的判定。第三方类型实现该接口即获得同等校验。

### 约束与验证

- 类型 id 同时是 JSON `type` 的取值；未注册类型的编解码属编程错误，由分发层报错（见 [规则模型](rule-model.md)）。
- `all()` / `ids()` 在注册期也返回快照，调用方拿到的集合不会随之后的注册变化；输出顺序即注册顺序。
- 类型参数的枚举取值统一走 `EnumCodecs`，新增枚举取值时无需另写解析。
- 改动后依次运行：`python tools/check-docs.py`、`tools/dsh-build.ps1 -Tasks build`，再做实机验证；类型注册冲突会在启动装配时立即暴露。

## 6. 数据与配置引用

| 事实 | 唯一落点 |
|---|---|
| 规则 JSON 中 `type` 字段、各类型的参数形状与新增类型的字段写法 | [规则 JSON](../api/rule-json.md) |
| 未注册类型、参数非法、注册冲突等错误与拒载粒度 | [错误与状态码](../api/errors.md) |
| 运行期可配置项（类型注册不受其影响） | [运行配置](../ops/configuration.md) |
| 扁平类型分发与条件树形状的决策 | [ADR-0013](../adr/0013-dfu-codec-and-flat-type-dispatch.md) |
| 动态引用校验放在参数对象上的决策 | [ADR-0036](../adr/0036-dynamic-reference-check-extension-point.md) |

## 相关文档

- [核心契约](core-contract.md)
- [规则模型](rule-model.md)
- [规则加载](rule-loading.md)
- [转化运行时](conversion-runtime.md)
- [依赖规则](../architecture/dependency-rules.md)
- [关键链路](../architecture/key-flows.md)
