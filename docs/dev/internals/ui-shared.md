# 展示工具层（`client/ui/shared`）

> 层级：展示工具层；模块路径：`client/ui/shared`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/shared`。
> 事实来源：本文件「3. 类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 展示工具层 | `client/ui/shared` | 第 2 节 |

## 2. 模块：`client/ui/shared`

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.shared`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/shared`
- 覆盖：12 个 `.java` 文件（含 `package-info.java`；`TagPreviewIcons` 内含嵌套 record `Tag` 与 `Member`，两者不单独占文件）

### 2.2 职责

- 目录条目图标分发：把目录类型与引用文本转成 `UiIcon`，无法静态预览时给出中性占位标记。
- 各目录类型的宿主预览缓存：实体、方块、流体、标签成员。
- 展示名称查询：注册表名称、语言键、标签名与缺键兜底名。
- 字段到目录类型的映射，以及字段可选标签的过滤。
- 条件摘要文本：通用格式化、内建条件类型摘要、条件树只读文本摘要。
- `package-info.java` 声明本包不依赖 `client/ui/screen`、`client/ui/screen/form`、`client/ui/widget`，也不含界面状态与交互逻辑。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `CatalogPreviewIcons` | final class | `unavailableMarker(int,int)`、`iconFor(RuleCatalogType,String)` | 目录条目图标分发入口 |
| `EntityPreviewIcons` | final class | `clear()`、`icon(String,int)`、`generation()`、`unavailable(String,int)` | 可见实体模型缓存与占位标记 |
| `BlockPreviewIcons` | final class | `clear()`、`icon(String)`、`unavailable(String)` | 方块预览缓存 |
| `FluidPreviewIcons` | final class | `isExcluded(String)`、`label(ResourceLocation)`、`icon(String)` | 流体静态材质预览 |
| `TagPreviewIcons` | final class | `resolve(RuleCatalogType,String)`、`sourceBlocks(String)` | 标签成员快照 |
| `TagPreviewIcons.Tag` | record（嵌套） | 组件 `type`、`id`、`members`、`carousel`；方法 `label()`、`tooltip()`、`icon()` | 可展开的标签快照 |
| `TagPreviewIcons.Member` | record（嵌套） | 组件 `name`、`icon` | 标签成员的实际名称与模型 |
| `RuleDisplayLabels` | final class | `tagLabel(String)`、`label(ResourceLocation)`、`catalogLabel(RuleCatalogType,ResourceLocation)`、`idFallback(ResourceLocation)` | 客户端展示名称 |
| `FieldCatalogTypes` | final class | `catalogTypeOf(EditorField)` | 字段到目录类型的映射 |
| `FieldCatalogChoices` | final class | `tags(EditorField)`、`isGenericProduct(EditorField)` | 按已同步标签过滤字段选择 |
| `SummaryFormats` | final class | 常量 `KEY_PREFIX`、`MAX_ENTRIES`；`join(List)`、`joinCapped(List,int)`、`refs(RuleValue,RuleCatalogType)`、`ref(String,RuleCatalogType)`、`range(String,String)`、`enumText(String,String)`、`flag(boolean,String)`、`numberText(RuleValue)` | 条件摘要的通用格式化 |
| `BuiltinConditionSummaries` | final class | `summary(RuleValue,ResourceLocation)` | 内建条件类型的叶卡片摘要 |
| `ConditionTreeTextSummary` | final class | `format(RuleValue,int,Function)` | 条件树只读多行文本摘要 |
| `package-info.java` | 包声明 | — | 声明职责、边界与扩展落点 |

### 2.4 依赖关系

- 依赖本模组：`client/edit`（6 个文件 import，共 13 处：`ConditionEditorRegistry`、`EditorField`、`RuleValue`、`TypeEditorDescriptor`、`TypeLabels`、`EffectEditorRegistry`、`FieldNumbers`）
- 依赖本模组：`client/ui/kit`（5 个文件 import，共 14 处：`UiBlockPreview`、`UiIcon`、`UiModelBounds`、`UiEntityPreview`、`UiRect`、`UiRenderLayers`、`UiCarousel`）
- 依赖本模组：`client/ui/theme`（1 个文件 import，共 2 处：`CatalogPreviewIcons` 取 `UiHostTheme`、`UiHostThemes`）
- 依赖本模组：`core.api`（3 个文件 import，共 4 处：`RuleFields`、`TaggedId`）
- 依赖本模组：`core.model`（1 个文件 import，共 1 处：`ConditionLimits`）
- 依赖本模组：`core.network.protocol`（6 个文件 import，共 6 处：`RuleCatalogType`）
- 依赖外部：`net.minecraft`（10 个文件 import，共 59 处，覆盖 15 个子包）
- 依赖外部：`java.util`（9 个文件 import，共 18 处：`List`、`Map`、`Set`、`Locale` 等）
- 依赖外部：`org.jetbrains.annotations`（9 个文件 import，共 9 处：`@Nullable`）
- 依赖外部：`org.joml`（1 个文件 import，共 1 处：`Quaternionf`）
- 被依赖：`client/ui/screen`（11 个文件 import，共 28 处）
- 被依赖：`client/ui/screen/form`（6 个文件 import，共 13 处）
- 被依赖：`client/ui/widget`（1 个文件 import，共 1 处：`UiCatalogGrid` 取 `RuleDisplayLabels`）

### 2.5 关键实现位置

- `CatalogPreviewIcons#iconFor`：类型或引用为空时返回 `null`；`#` 前缀且类型为 `ITEM`、`BLOCK`、`ENTITY`、`FLUID` 时返回 `TagPreviewIcons#resolve(type, id).icon()`；其余引用补 `minecraft:` 后交给对应预览类。
- `CatalogPreviewIcons#iconFor` 的非标签分支：`ENTITY` 走 `EntityPreviewIcons#icon(id, 0)`，`BLOCK` 走 `BlockPreviewIcons#icon`，`FLUID` 走 `FluidPreviewIcons#icon`，`ITEM` 与 `switch` 内的 `BLOCK` 走 `UiIcon.Item`；物品栈为空时返回 `null`。
- `CatalogPreviewIcons#unavailableMarker`：生成 `UiIcon.Rendered`，占位边长上限 `MARKER_MAX_SIZE = 16`；用 `THEME.palette().textDisabled()` 画 1 像素方框，用 `textSecondary()` 画居中问号。
- `EntityPreviewIcons#generation`：以 `Minecraft#level`、`Language#getInstance()` 与 `Blocks.STONE` 默认状态的方块模型实例三者作为失效键；任一变化时清 `CACHE` 并回写，返回模型代号。
- `EntityPreviewIcons#icon`：缓存键为 `id + "/" + age`，容器为访问序 `LinkedHashMap`，超过 `MAX_ENTRIES = 256` 时逐个移除最旧条目；返回 18×18 的 `UiIcon.Rendered`。
- `EntityPreviewIcons#create`：从 `BuiltInRegistries.ENTITY_TYPE` 建实体，`AgeableMob` 应用 `age`；渲染器名为 `NoopRenderer`、`InteractionRenderer`、`MarkerRenderer` 时判为不可用，`falling_block`、`item`、`area_effect_cloud`、`item_display`、`block_display`、`text_display` 同样判为不可用；宽度与高度下限 0.35；`ender_dragon`、`ghast`、`squid`、`glow_squid` 有单独尺寸覆盖。
- `EntityPreviewIcons#unavailable`：先调用 `generation()` 刷新失效状态，再读 `CACHE`；未绘制过的实体返回 `false`。
- `EntityPreviewIcons#clear`：清空 `CACHE` 与 `ICONS`，并重置 `world`、`language`、`models` 三个失效键。
- `BlockPreviewIcons#icon`：返回 18×18 图标；失效键为 `EntityPreviewIcons#generation()` 的返回值与 `Minecraft#level`；每个方块只测量一次模型，渲染失败后记 `failed`，缓存上限 256。
- `BlockPreviewIcons#unavailable`：只读缓存，未进缓存的方块返回 `false`。
- `FluidPreviewIcons#render`：每帧从方块模型取 `getParticleIcon()` 作为材质；三面绘制，侧面明度为 0.6、正面 0.8、顶面 1；无染色且属于 `FluidTags.WATER` 时用 `DEFAULT_WATER_TINT = 0x3F76E4`。
- `FluidPreviewIcons#isExcluded`：默认流体状态为空或不是源流体时返回 `true`。
- `FluidPreviewIcons#label`：取 `createLegacyBlock().getBlock().getName()`，未注册的流体返回 `null`。
- `TagPreviewIcons#resolve`：按目录类型从 `BuiltInRegistries` 取 `TagKey` 成员；物品过滤空气，方块用 `CatalogPreviewIcons#iconFor`，实体用 `EntityPreviewIcons#icon(id, 0)`，流体过滤空与非源；最后剔除图标为 `null` 的成员。
- `TagPreviewIcons#sourceBlocks`：只保留 `BlockItem`，映射成方块名与 `BlockPreviewIcons#icon`，快照类型固定为 `RuleCatalogType.ITEM`。
- `TagPreviewIcons#snapshot`：给引用补一个前置 `#`，并用成员图标构造 `UiCarousel`。
- `TagPreviewIcons#kind`：`BLOCK`、`ENTITY`、`FLUID`、`BIOME` 分别映射为 `blocks`、`entities`、`fluids`、`biomes`，其余为 `items`；映射结果拼进提示键 `gui.itemdespawntowhat.edit.tag.supports.<kind>`。
- `RuleDisplayLabels#tagLabel`：给引用补一个前置 `#`，再经 `gui.itemdespawntowhat.edit.tag.label` 翻译。
- `RuleDisplayLabels#label`：按物品、方块、实体类型顺序查注册表，随后查 `EffectEditorRegistry` 与 `ConditionEditorRegistry`，全部未命中返回 `null`。
- `RuleDisplayLabels#catalogLabel`：按目录类型分派；流体交给 `FluidPreviewIcons#label`；`DIMENSION` 与 `BIOME` 的语言键为 `<type>.<namespace>.<path 以点分隔>`，键不存在时返回 `null`。
- `RuleDisplayLabels#idFallback`：把资源路径按 `_` 与 `/` 切词并首字母大写，路径为空时返回原始路径。
- `FieldCatalogTypes#catalogTypeOf`：先取 `EditorField#registry()`，为空时取 `domain()`，再交给 `registryType` 判定；未命中时按 `EditorField#name()` 兜底。
- `FieldCatalogTypes#registryType`：按注册表名包含关系判定目录类型，覆盖 item、block、entity_type、fluid、loot_table、biome、dimension 与 level、mob_effect 与 potion 八组。
- `FieldCatalogChoices#tags`：按 `FieldCatalogTypes#catalogTypeOf` 的类型取注册表 `getTagNames()`，返回带 `#` 前缀的标签集合；`BIOME` 从当前世界的注册表访问器取，其余取 `BuiltInRegistries`。
- `FieldCatalogChoices#isGenericProduct`：比较 `EditorField#labelKey()` 是否等于 `gui.itemdespawntowhat.edit.field.spawn_entity.entity`。
- `FieldCatalogChoices#tags(Registry,boolean)`：`genericProduct` 为真时剔除只含 `EntityType.ITEM` 或 `EntityType.EXPERIENCE_ORB` 的标签。
- `SummaryFormats#join`：按顺序拼接文本，片段之间插入 `gui.itemdespawntowhat.edit.summary.separator`。
- `SummaryFormats#joinCapped`：超过条目上限时只保留前 `maxEntries` 项，追加 `gui.itemdespawntowhat.edit.summary.cond.more` 并带上总数。
- `SummaryFormats#ref`：解析 `TaggedId`；非标签引用先查 `RuleDisplayLabels#catalogLabel`，缺名时回落去掉默认命名空间的短名，标签保留 `#` 前缀。
- `SummaryFormats#range`：两端都有值用 `cond.range`，只有上界用 `cond.range.max`，只有下界用 `cond.range.min`。
- `SummaryFormats#enumText` 与 `#flag`：要求语言键存在才输出，缺键返回 `null`。
- `SummaryFormats#numberText`：JSON 数值经 `FieldNumbers#text` 输出，字符串按原文返回。
- `BuiltinConditionSummaries#summary`：条件或类型为空、条件不是对象、类型命名空间不等于 `TypeLabels.OWN_NAMESPACE` 时返回 `null`；否则按类型路径分派给 10 个内建条件分支。
- `BuiltinConditionSummaries#clockText`：刻值按 `TICKS_PER_DAY = 24000`、`MINUTES_PER_DAY = 1440`、`MINUTES_PER_HOUR = 60` 换算，起点偏移 `DAY_START_MINUTE = 360`（0 刻对应 06:00）。
- `BuiltinConditionSummaries#biome`：`mode` 为 `climate` 时逐轴展示区间，其余模式展示群系列表；轴顺序为 `temperature`、`humidity`、`continentalness`、`erosion`、`depth`、`weirdness`。
- `BuiltinConditionSummaries#surroundingBlocks`：方向顺序为 `up`、`down`、`north`、`south`、`east`、`west`，方向名取 `EditorField#labelKey()`。
- `BuiltinConditionSummaries#catalystPresent`：逐物品组装「物品名 × 数量」，数量读 `RuleFields.ITEM_COUNTS` 对应项，缺省回落到条件级 `count`。
- `BuiltinConditionSummaries#fluidPresent`：先取 `RuleFields.FLUIDS` 列表，为空时取单个 `RuleFields.FLUID`；源流体要求默认值为真。
- `ConditionTreeTextSummary#format`：根为空或 `isNull` 时返回 `gui.itemdespawntowhat.edit.summary.any_condition`；行数上限经 `Math.clamp(maxLines, 2, ConditionLimits.MAX_NODES)` 收敛；缩进为两个空格；递归深度上限 `ConditionLimits.MAX_DEPTH`。
- `ConditionTreeTextSummary#format` 的截断：超出上限时移除末行，追加 `gui.itemdespawntowhat.edit.recipe.condition_tree_more` 并置灰色。

### 2.6 代码现状（可核对）

- 跨层依赖主题层：`CatalogPreviewIcons` import `UiHostTheme` 与 `UiHostThemes`，并在类初始化时执行 `UiHostThemes.defaultTheme()` 存入静态字段 `THEME`；占位标记因此固定取默认像素调色板，注入 `UiFlatTheme` 时不随之改变。
- `CatalogPreviewIcons#iconFor` 存在不可达分支：`BLOCK` 且非 `#` 前缀的引用在前置判断处已返回，`switch (type)` 中的 `case BLOCK` 不会被执行；`#` 前缀的 `BLOCK` 已在标签分支返回。
- 缓存策略不一致：`EntityPreviewIcons` 与 `BlockPreviewIcons` 带 256 条缓存，`FluidPreviewIcons` 与 `TagPreviewIcons` 每次调用重建图标，`CatalogPreviewIcons` 只做分发；只有实体与方块类型提供 `unavailable` 查询。
- 失效键不一致：`EntityPreviewIcons#generation` 只清 `CACHE`，不清 `ICONS`；`EntityPreviewIcons#clear` 把两者都清空。
- 字面量键比较：`FieldCatalogChoices#isGenericProduct` 用字符串字面量 `gui.itemdespawntowhat.edit.field.spawn_entity.entity` 与 `EditorField#labelKey()` 比较，未抽成常量。
- 跨层注释引用：`BuiltinConditionSummaries#clockText` 的注释声明与 `client/ui/screen` 的 `RuleEditorPanelFactory` 时间滑条同一换算规则，并在本包内就地实现；本包不 import `client/ui/screen`。
- 跨层注释引用：`BuiltinConditionSummaries` 类 JavaDoc 提到由 `NaturalSummary.conditionLeaf` 兜底，本包不 import `client/ui/screen/form`。
- 缺少 JavaDoc：`EntityPreviewIcons` 的 `clear`、`icon`、`generation`、`unavailable`；`BlockPreviewIcons` 的 `clear`、`icon`；`FluidPreviewIcons` 的 `isExcluded`、`label`、`icon`；`TagPreviewIcons` 的 `resolve`、`sourceBlocks`；`FieldCatalogTypes#catalogTypeOf`；`FieldCatalogChoices#tags`、`#isGenericProduct`；`RuleDisplayLabels#label`、`#catalogLabel` 只有行注释。带 `/** */` 的公开方法是 `CatalogPreviewIcons#unavailableMarker`、`BlockPreviewIcons#unavailable`、`RuleDisplayLabels#tagLabel`、`RuleDisplayLabels#idFallback`、`SummaryFormats` 全部方法、`BuiltinConditionSummaries#summary`、`ConditionTreeTextSummary#format`。
- 图标尺寸字面量：`EntityPreviewIcons`、`BlockPreviewIcons`、`TagPreviewIcons` 的图标尺寸 18 与占位标记 16 以字面量出现在各自类中，未共享常量。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `CatalogPreviewIcons` | 目录条目图标分发与占位标记 | `iconFor`、`unavailableMarker` |
| `EntityPreviewIcons` | 可见实体模型缓存、资源失效与占位标记 | `icon`、`generation`、`unavailable`、`clear` |
| `BlockPreviewIcons` | 方块预览缓存与不可预览判定 | `icon`、`unavailable`、`clear` |
| `FluidPreviewIcons` | 流体静态材质预览、显示名与不可预览过滤 | `icon`、`label`、`isExcluded` |
| `TagPreviewIcons` | 标签成员解析、物品标签到方块映射 | `resolve`、`sourceBlocks`；嵌套 record `Tag`、`Member` |
| `RuleDisplayLabels` | 展示名称与缺键兜底名 | `tagLabel`、`label`、`catalogLabel`、`idFallback` |
| `FieldCatalogTypes` | 字段到目录类型的映射 | `catalogTypeOf` |
| `FieldCatalogChoices` | 按真实标签归属过滤字段选择 | `tags`、`isGenericProduct` |
| `SummaryFormats` | 条件摘要的通用格式化 | `join`、`joinCapped`、`ref`、`refs`、`range`、`enumText`、`flag`、`numberText` |
| `BuiltinConditionSummaries` | 10 种内建条件类型的摘要组装 | `summary` |
| `ConditionTreeTextSummary` | 条件树只读多行文本摘要 | `format` |
| `package-info.java` | 包声明：职责、边界与扩展落点 | — |

## 4. 相关文档

- [主题层](ui-theme.md)
- [UI kit](ui-kit/README.md)
- [客户端层](../layers/client.md)
- [ADR-0021：显示名称与稳定 ID](../../adr/0021-display-name-and-stable-ids.md)
- [ADR-0029：客户端依赖边界与中立共享子包](../../adr/0029-client-dependency-boundary-and-shared-subpackage.md)
- [文档索引](README.md)
