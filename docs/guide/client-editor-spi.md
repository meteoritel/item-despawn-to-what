# 客户端类型编辑器 SPI

> 权威来源：[契约 §5](../plan/plan-frontend-rewrite-contract.md)（客户端 UI 契约）、[前端字段与取值域](../plan/plan-frontend-rewrite-forms.md)（逐字段的 JSON 名与取值域）、源码 `common/src/main/java/com/meteorite/itemdespawntowhat/client/edit/`。
> 读者：为**第三方**条件/效果类型（或自定义规则字段）提供编辑界面的模组作者；以及维护内置 22 个描述符的实现者。

## 1. 一次编辑的数据流

```text
RuleEditClientWorkspace   client/net   协议唯一入口（收/发载荷、会话状态机）
  → EditSession           client/edit  唯一编辑入口（撤销/重做、脏标记）
      → RuleDraft         client/edit  工作副本，JsonObject 是唯一事实来源
          → TypeEditorDescriptor + EditorField   client/edit  描述"字段长什么样"
              → 表单引擎 / UiConditionTreeEditor / UiListEditor   client/ui  渲染与交互
```

三条硬约束：

1. **所有草稿修改都必须走** `EditSession.apply(opKey, Runnable)`；直接改 `RuleDraft` 工作副本会绕过撤销栈（契约 P6 的前置要求）。
2. `client/edit` 允许依赖 core 的公开类型（`core.api` / `core.model`）与原版客户端类；**禁止 core 反向依赖 client**，禁止引入两端 loader 专有 API（见 `client/edit/package-info.java`）。
3. 界面层**不自行发协议包**：上行一律经 `RuleEditClientWorkspace`（`client/net`），界面实现由 `EditorScreenHooks.setOpener(...)` 注册、未注册时只记日志、绝不抛异常（契约 §5.3）。

## 2. 注册一个类型编辑器

最小示例（第三方条件 `mymod:milk_level`，字段名与后端 record 组件名一致）：

```java
public final class MyTypeEditors {

    // 客户端初始化阶段调用一次；与 BuiltinEditorDescriptors.bootstrap() 同阶段
    public static void bootstrap() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mymod", "milk_level");
        ConditionEditorRegistry.register(TypeEditorDescriptor.of(id,
                Component.translatable("gui.mymod.edit.condition.milk_level"),
                EditorField.optionalInteger("min", "gui.mymod.edit.field.milk_level.min", 0, 100),
                EditorField.optionalInteger("max", "gui.mymod.edit.field.milk_level.max", 0, 100)));
    }
}
```

- 条件类型 → `ConditionEditorRegistry.register(descriptor)`；效果类型 → `EffectEditorRegistry.register(descriptor)`。
- **重复注册同一 id 抛** `IllegalStateException("类型编辑器重复注册：" + id)`，不静默覆盖。
- **未注册不会崩**：`descriptorFor(id)` 返回只读回退 `TypeEditorDescriptor.readOnly(id, label)`（即只有一个 `EditorField.rawJson()` 字段），界面只展示 JSON 摘要，草稿**原样保留未识别字段**。这就是第三方类型没有 UI 也能安全打开、保存的原因。
- 类型显示名推荐用 `TypeLabels.conditionLabel(id)` / `TypeLabels.effectLabel(id)`：优先 `gui.itemdespawntowhat.edit.condition.<type>`（本模组 `<type>` 即路径；第三方为 `<namespace>.<path>`），缺失时回退旧键 `<kind>.<namespace>.type.<path>`，再回退原始 id 字符串。
- 内置描述符的注册入口是 `BuiltinEditorDescriptors.bootstrap()`（幂等，`volatile bootstrapped`），字段名与 [前端字段与取值域](../plan/plan-frontend-rewrite-forms.md) 的 JSON 字段名逐字一致。

## 3. EditorFieldType（20 种）

| 取值 | 语义 / 渲染 |
|---|---|
| `TEXT` | 单行文本 |
| `LONG_TEXT` | 多行文本（`notes` 等） |
| `INTEGER` | 整数，取值域形如 `0..64` |
| `DECIMAL` | 小数，取值域形如 `0.0..1.0` |
| `BOOLEAN` | 布尔开关 |
| `ENUM` | 枚举，取值域为逗号分隔取值列表（JSON 取值小写） |
| `RESOURCE_LOCATION` | `namespace:path`，不允许标签 |
| `REGISTRY_ID` | 注册表 id（`registry` 给注册表名） |
| `TAG` | 可带 `#` 的 id |
| `TAG_LIST` | 可带标签的 id 列表 |
| `RL_LIST` | 纯资源位置列表（不给标签模式） |
| `STRING_LIST` | 字符串列表 |
| `CLIMATE_RANGE` | 气候区间 `[-1,1]`，两端可空 |
| `PERCENT` | JSON 存 `0..1`，界面显示 0..100 百分比 |
| `AMPLIFIER` | JSON 存 amplifier（0 起算），界面输入 1..255 并以罗马数字显示 |
| `TICKS` | 刻数，值 0 显示为「立即」 |
| `SUBLIST` | 对象数组子列表（如 `arrow_rain.potion_effects`） |
| `CONDITION_TREE` | 条件树，条件编辑器与效果编辑器共用同一控件 |
| `NOTE` | 只读说明行，**不写入 JSON** |
| `RAW_JSON` | 原始 JSON 只读展示（未注册类型的回退） |

## 4. EditorField 工厂与派生

| 工厂 | 说明 |
|---|---|
| `text(name, labelKey)` | 必填单行文本 |
| `optionalText(name, labelKey)` | 可选单行文本 |
| `longText(name, labelKey)` | 可选多行文本 |
| `integer(name, labelKey, min, max)` | 必填整数 |
| `optionalInteger(name, labelKey, min, max)` | 可空整数（留空 = 不约束） |
| `ticks(name, labelKey, min, max)` | 刻数（0 = 立即） |
| `decimal(name, labelKey, min, max)` | 必填小数 |
| `percent(name, labelKey)` | 概率（JSON 0..1） |
| `amplifier(name, labelKey)` | 药水等级（JSON amplifier） |
| `bool(name, labelKey)` | 布尔 |
| `enumOf(name, labelKey, values...)` | 枚举（无 i18n 组名） |
| `enumIn(name, labelKey, enumGroup, values...)` | 枚举（键 `gui.itemdespawntowhat.edit.enum.<组>.<值>`） |
| `registryId(name, labelKey, registry)` / `optionalRegistryId(...)` | 注册表 id |
| `tag(name, labelKey, registry)` / `optionalTag(...)` | 可带标签的 id |
| `tagList(name, labelKey, registry)` | 可带标签的 id 列表 |
| `rlList(name, labelKey, registry)` | 纯资源位置列表 |
| `stringList(name, labelKey)` | 字符串列表 |
| `resourceLocation(name, labelKey)` | 资源位置 |
| `climateRange(name, labelKey)` | 气候区间 |
| `subList(name, labelKey, subFields...)` | 对象数组子列表 |
| `conditionTree(name, labelKey)` | 条件树 |
| `note(labelKey)` | 说明行（字段名固定 `__note`） |
| `rawJson()` | 原始 JSON 摘要（字段名固定 `__raw_json`） |

派生方法：`optional()`、`asRequired()`、`withHint(key)`、`withPresets(EditorPreset...)`；预设值是 `EditorPreset(String value, String labelKey)`（只影响界面输入，写回仍按字段类型转换）。

约定：

- 字段顺序 = `List` 顺序 = 表单顺序；
- `domain`：数值类型为 `min..max`，`ENUM` 为逗号分隔取值列表，其余 `null`；
- `registry`：`REGISTRY_ID` / `TAG` / `TAG_LIST` 的注册表名；
- `enumGroup`：枚举键组名；
- `name` **必须与后端 JSON 字段名逐字一致**，否则编辑会写坏 JSON；
- `NOTE` / `RAW_JSON` 的 name 是内部占位，不写入 JSON。

## 5. 谁说了算：客户端与后端的边界

| 主题 | 权威方 |
|---|---|
| 字段名、取值域、默认值 | 后端 record + [前端字段与取值域](../plan/plan-frontend-rewrite-forms.md)；描述符只是 UI 形状 |
| 类型 id 与 type 分发 | 后端 `TypeDispatch.flat`（类型专属字段与 `type` 同层，见 [custom-types.md](custom-types.md)） |
| 保存是否合法 | 服务端：`RuleCodecs` 解码 + `RuleValidation` + `RuleReferenceValidator`；客户端校验只是即时提示 |
| 条件树 JSON 形状 | [契约 §2](../plan/plan-frontend-rewrite-contract.md)；空表达式**删除** `conditions` 字段，绝不写 `null` |
| 未识别字段 | 必须原样保留：用 `RuleDraft.mergeAt(path, patch)`，不要整体替换对象 |

## 6. 常用 API（client/edit）

- `RuleDraft`：`of(JsonObject)` / `id()` / `original()` / `view()` / `toJson()` / `toOverlayJson()` / `isDeleted()` / `setDeleted(boolean)` / `isDirty()` / `revert()` / `has` / `get` / `set` / `remove` / 类型化 `getString` / `getBoolean` / `getInt` / `getDouble` 与 `set*` / 路径 `getAt` / `setAt` / `removeAt` / `mergeAt` / `rawConditions()` / `conditionsOrNull(registry)` / `setConditions(expression, registry)` / `rawConditionsAt(path)` / `conditionsAt(path, registry)` / `setConditionsAt(path, expression, registry)` / 静态 `encodeConditions` / `decodeConditions` / `effects()` / `effectCount()` / `setEffects` / `addEffect` / `removeEffect(int)` / `moveEffect(int, int)`。路径形如 `a.b[2].c`；空字符串路径表示规则级 `conditions`。
- `EditSession(targetId, draft)`：`apply(opKey, change)` / `undo()` / `redo()` / `canUndo()` / `canRedo()` / `undoDepth()` / `redoDepth()` / `undoOpKey()` / `redoOpKey()` / `clearHistory()` / `snapshot()` / `isDirty()` / `load(draft)` / `addListener(Runnable)`；`HISTORY_LIMIT = 100`；`OP_*` 常量是操作标签 i18n key。
- `JsonSummary.compact(element[, maxLength])` / `describe(element, maxLength)`：只截断展示，不改数据。
- `EditorScreenHooks`：`setOpener` / `opener` / `open(EditorOpenRequest)` / `close`。

## 7. 常见错误

| 症状 | 原因 | 处理 |
|---|---|---|
| 启动即抛 `类型编辑器重复注册：<id>` | 两个 mod（或本模组内置）注册了同一 id | 只注册自己的类型；内置 id 不要复用 |
| 保存后第三方字段消失 | 用整体替换而非 `mergeAt` | 改字段用 `setAt` / `mergeAt` |
| 类型显示为原始 id | 缺 lang 键（en_us 与 zh_cn 都要有） | 补 `gui.*.edit.condition.<type>` |
| 枚举显示原始取值 | `enumGroup` 键缺失，或值不在 `domain` 列表 | 补 `gui.itemdespawntowhat.edit.enum.<组>.<值>` |
| 撤销无效 / 不记录 | 绕过 `EditSession.apply` 直接改草稿 | 所有写入包在 `apply` 里 |
| 未注册类型显示只读 JSON 摘要 | 预期行为（回退路径） | 注册描述符即可编辑；未识别字段始终保留 |

## 8. 相关

- 后端类型注册：[custom-types.md](custom-types.md)
- 消息码与问题记录：[message-codes.md](message-codes.md)
- 契约 §5 / §7：[plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md)
