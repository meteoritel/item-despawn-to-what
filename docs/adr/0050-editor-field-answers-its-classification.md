# ADR-0050：编辑器字段自答分类，调用方不再 switch 字段类型

- 状态：已实施。
- 依据：界面层原先在 18 个文件 22 处对 `EditorFieldType` 做 switch 或枚举比较；`RuleEditorPanelFactory.isListField` 在枚举未命中时用 `name().endsWith("s")` 猜列表形态，把字段名当契约。
- 相关决策：[ADR-0049](0049-single-parameter-declaration.md)（参数只声明一次；本 ADR 处理声明投影出的 `EditorField` 如何回答编辑器内部的分类问题）。

## 背景

`EditorFieldType` 是 21 值枚举，界面层反复用它推导同一批分类问题：是否是说明行、是否是列表、是否走滑杆、是否可带标签、是否由目录多选、是否自带行标签。判定散落在表单控件、行布局、结果页与规则面板里，新增字段类型要同步改多处，漏一处就出现控件与数据形态不一致。

`RuleEditorPanelFactory.isListField` 还会在枚举未命中时按字段名后缀回退，使扩展类型里恰好以 `s` 结尾的普通字段被当成列表，向目录弹窗请求多选。

## 决策

1. **分类问题由字段自己回答**：`EditorField` 提供一组公开布尔谓词（`isNote`、`isRawJson`、`isDisplayOnly`、`isConditionTree`、`isText`、`isLongText`、`isNumeric`、`isIntegerLike`、`isTicks`、`isPercent`、`isAmplifier`、`isBoolean`、`isEnum`、`isReference`、`isCatalogReference`、`allowsTags`、`isTagList`、`isStringList`、`isListShaped`、`isMultiCatalog`、`isItemCounts`、`isClimateRange`、`isSublist`、`isObject`、`hidesRowLabel`），调用方只问字段，不再对 `EditorFieldType` 做 switch 或枚举比较。
2. **谓词落在 `client/edit` 的 `EditorField`**：分类属于字段契约而非某个控件；`client/edit` 与界面层都能读，谓词实现内部集中映射枚举值，不引入新的依赖方向。
3. **列表形态只由声明类型判定**：`isListShaped` 取 `TAG_LIST`、`RL_LIST`、`STRING_LIST`，删除按字段名后缀猜测的启发式。
4. **控件注册表保留类型键**：`FormControlRegistry` 仍以 `EditorFieldType` 选择控件工厂，因为实例化必须覆盖全部取值；这是注册表职责，不是调用方分类。

## 考虑过的替代方案

- **谓词放 `EditorFieldType`**：调用点仍写成 `field.type().isListShaped()`，继续泄露类型，而且 `nullable`、`registry` 等字段级事实不在枚举上。否决。
- **保留散落的 switch 与枚举比较**：新增字段类型要改 22 处，名称启发式会把扩展类型误判为列表。否决。
- **把谓词并入 `core` 的参数声明**：控件选择与展示差异正交于「参数只有一份声明」，且第三方类型没有参数声明也需要分类。否决（[ADR-0049](0049-single-parameter-declaration.md) 已记录同一取舍）。

## 后果

- `EditorField` 公开面增加一组布尔谓词；新增 `EditorFieldType` 取值时必须补谓词实现，否则新类型对分类问题默认回答「不是」。
- 界面层不再读字段名推断形态；扩展类型沿用既有 `EditorField` 工厂即可得到正确分类。
- 字段顺序、标签、控件选择、多选能力与 JSON 契约不变。