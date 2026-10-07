# 第三方扩展指南

面向需要为本模组添加自定义条件类型、效果类型，或为其提供客户端编辑界面的第三方开发者。

## 本目录

| 文档 | 内容 |
| --- | --- |
| [custom-types.md](custom-types.md) | 注册自定义条件类型与效果类型（common 侧 SPI） |
| [client-editor-spi.md](client-editor-spi.md) | 为自定义类型提供客户端表单描述，以及未注册类型的只读回退 |
| [message-codes.md](message-codes.md) | 消息码（messageCode）命名、本地化前缀与新增流程 |
| [update-notes.md](update-notes.md) | 破坏性更新说明：旧 v1.2.1 config 不再加载、第二轮规则契约变更（触发方式/固定成本/候选结果/条件树）、重建入口与「无法保留」清单 |
| [entity-products.md](entity-products.md) | 统一实体产物 JSON、子类编辑与所有规则共用的生成阈值 |
| [manual-acceptance.md](manual-acceptance.md) | 双平台手动验收清单（含已知缺口表），供回归与最终审阅 |
| [ui-defect-fixes-validation-2026-10-07.md](ui-defect-fixes-validation-2026-10-07.md) | 标签轮播、模型适框、组件层级与悬停穿透的游戏内验收 |

后端模块的源码地图见 [../dev/backend/README.md](../dev/backend/README.md)。

## 权威来源

本目录是导读。2026-10-07 的统一实体生成与 UI 决策以 [ADR-0026](../adr/0026-unified-entity-spawn-effect.md)、[本轮实施记录](../plan/plan-ui-alignment-implementation-2026-10-07.md) 和当前源码为准，它们替代旧契约中对应的三个生成类型及平铺页面；其余未被替代约束按以下来源核对：

1. [../plan/plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md)：冻结契约（Java 类型名、JSON 形状、求值规则、协议）。
2. [../plan/plan-frontend-rewrite-forms.md](../plan/plan-frontend-rewrite-forms.md)：表单取值域、i18n 前缀与产品裁决。
3. 源码本身（common/src/main/java/com/meteorite/itemdespawntowhat/）。

## 扩展点总览

| 扩展点 | 接口 | 注册方式 | 生效侧 |
| --- | --- | --- | --- |
| 条件类型 | `ConditionType` / `SimpleConditionType` | `RuleTypeProvider#registerConditions` | common（服务端求值，客户端显示） |
| 效果类型 | `EffectType` / `SimpleEffectType` | `RuleTypeProvider#registerEffects` | common（服务端执行，客户端显示） |
| 客户端表单 | `TypeEditorDescriptor` | `ConditionEditorRegistry.register` / `EffectEditorRegistry.register` | 客户端初始化 |
| 类型标签 | 语言文件键 | 见 [client-editor-spi.md](client-editor-spi.md) | 客户端 |

服务端先行：只注册服务端类型也能正常加载、校验与执行规则；没有客户端编辑器描述的类型会在界面里退化为**只读 JSON 摘要**，未识别字段原样保留，不会被破坏。

## 硬性约定

- **服务端与客户端分离**：`common` 不得 import `fabric` / `neoforge`；`core/**` 不得引用 `client/**`。自定义条件/效果类型属于 common 侧，不要在类型里直接调客户端代码。
- **类型 id 稳定**：`ResourceLocation` 的 namespace 用你自己的 modid，path 一经发布不得修改。JSON 的 `type` 字段、覆盖文件名、语言键都由它派生。
- **codec 层不得产出 null**：DFU 的 `DataResult` 内部使用 `Optional.of`，null 会在解码期抛 NPE。可选字段一律用 `Optional` 承载，在 `apply` 里用 `orElse(null)` 落回可空字段。
- **条件树只接受规范 JSON**：组合节点用 `op` / `terms` / `term`，叶用 `op: "leaf"` + `condition`；叶内出现 `negated`、`conditions` 是数组、或出现 `groups` 一律是旧格式，解码期直接报错（零兼容）。
- **只有 MATCH 通过**：条件树求值有 MATCH / NO_MATCH / UNAVAILABLE / ERROR 四态，后两者一律不触发效果，也不被 `inverted` 吞掉。

## 最小上手路径

1. 写一个 `record` 参数对象，实现 `Condition` 或 `Effect`，并提供 `MapCodec`（不含 `type` 字段）。
2. 用 `SimpleConditionType` / `SimpleEffectType` 组装类型定义（含参数校验器与求值器/执行器）。
3. 写一个 `RuleTypeProvider` 实现类完成注册。
4. 在自己的 mod jar 里放 `META-INF/services/com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider`，内容为实现类全限定名。
5. 可选：注册客户端编辑器描述与语言键。
