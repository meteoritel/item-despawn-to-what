# 新 GUI 字段、数值与控件规格

> 状态：已确认、已冻结、待实施；用户于 2026-10-03 随主计划确认字段与控件规格，不代表代码已实现。
> 实施状态（2026-10-03，P5 文档同步）：§2/§3/§4/§5 的域、可选性与单位已落盘到 `client/edit` 描述符——priority / trigger_after_seconds 等 21 处「后端有默认值」字段改为可省略、5 处后端 `fieldOf` / 校验 `notEmpty` 字段对齐为必填（task-10）；delay_ticks / spawn_entity.age 使用后端完整域；药水 duration_ticks / amplifier 可省略；概率原始精度与 amplifier 255↔等级 256 的显示往返由 `client/ui/screen/form` 承接（P3-B）。游戏内域边界、精度与省略状态往返**待用户执行**（见 [manual-acceptance.md](../guide/manual-acceptance.md) §10.4.2/§10.5）。本文件冻结正文不变。
> 对应主计划：[plan-gui-rule-update.md](plan-gui-rule-update.md)。
> 基线：`12a4a9a929ca564211522ee1f602ca286f300364`，2026-10-03。
> `INT_MIN=-2147483648`，`INT_MAX=2147483647`；C 表示任意条件叶的 condition 对象，E 表示 outcomes[i].effects[j]，兼容结构中为顶层 effects[j]。

## 1. 通用规则

- JSON 是草稿唯一事实来源。只修改玩家明确编辑的字段；回填、布局、显示格式和标题生成均不修改 JSON。
- 后端合法域、滑块常用窗口、交互步长与显示精度分别记录。常用窗口不能充当提交硬限额，也不能把窗口外已有合法值裁掉。
- 小范围优先滑块，所有滑块默认方向键与 Shift 细调；大范围优先步进、预设和行内精确输入。
- 整数参数的最小步长为 1；小数示例档为普通 1 / Shift 0.1，窄气候范围为普通 0.1 / Shift 0.01。只按用户交互进行吸附，回填保留原始精度。
- 数值 0 与“未设置”分离。可选字段使用端点开关或继承/自定义状态；关闭应按该字段语义省略，而不是写 0 或 JSON null。
- 后端有默认值的字段可以显示有效默认，未修改时保留原始省略形态。新建模板可显式填入其约定初值。
- 所有数值输入拒绝 NaN、Infinity 和越界值；整数解析防溢出，不通过静默钳制掩盖输入错误。
- 精确输入点击当前值进入行内编辑，Enter 提交、Esc 取消；非法文本显示错误并保留输入缓冲，不写坏数字。自由切页时有效输入提交，非法缓冲与路径错误保留到返回该字段；修正前阻止应用。缓冲不充当可恢复的持久化草稿数据。
- 预览期间仅刷新控件与相关短摘要；一次拖动结束后调用一次编辑门面。端点编辑形成一条撤销记录；仅切换显示单位不修改 JSON、不增加撤销记录。

## 2. 规则与固定成本

| 字段 | 后端范围 / 默认 | 控件与写回 |
|---|---|---|
| id | 合法规则 ResourceLocation；稳定身份 | 新建自动生成、复制分配新 id；高级区域查看技术标识。别名修改不改 id |
| display_name | 可省略；trim 后为空表示无别名；最多128码点 | 可选别名文本框；空时自动标题；“恢复自动命名”清除该字段 |
| notes | 可省略；旧 UI 上限1024码点 | 折叠备注编辑区；沿用1024码点的界面限制，不用于标题 |
| enabled | 默认 true | 开关 |
| priority | 完整 int；默认0 | 整数步进与精确输入，提供0预设；旧±10000不是后端上限 |
| source.items / exclude | items 非空；合计最多256项 | 物品/标签选择、图标槽、移除；重复与匹配/排除冲突本地提示；目录请求走既有服务 |
| triggers | natural/fire/lava/cactus；未声明有效默认为natural | 四项可多选控件，防止玩家无意保存空选择后回退natural；打开省略字段时显示有效默认 |
| trigger_after_seconds | 0..INT_MAX；默认300；可省略 | 单位“秒”，步进/精确输入与0、5、30、300秒预设。只在natural选中时显示，隐藏不清值 |
| source_cost | 显式1..INT_MAX；可省略，省略由后端消耗规则推导 | 新建默认1；常用1..64辅助调整，精确输入覆盖完整域。无0成本选项 |
| catalyst_cost | 可省略的对象，不接受整数形式 | “使用催化剂成本”开关；启用后显示items/count/radius，不附加概率、条件或延迟 |
| catalyst_cost.items | 非空物品/标签列表 | 目录选择与图标槽，必须至少一项 |
| catalyst_cost.count | 1..64；默认1 | 整数滑块，普通/Shift步长均≥1 |
| catalyst_cost.radius | 1..8；默认1 | 整数滑块与半径说明 |
| combination | round_robin默认 / priority | “轮询 / 按顺序优先”分段选择；一候选时显示模式但不制造选择歧义 |
| outcomes | 候选非空，每候选效果非空；候选数≤32；全规则效果总数≤32 | 候选列表与详情；新增、复制、删除、拖动和上下移；id唯一稳定，不来自标题 |
| outcomes[i].safe_spawn | 默认false | 候选策略开关，名称明确“生物安全生成”；不适用时说明，保留已有值 |
| outcomes[i].fill_origin | 默认true | 候选策略开关，名称明确“方块起点填充”；不适用时说明，保留已有值 |
| schema_version | 当前只支持1；默认1 | 新建按当前契约写入；高级只读，不做可调滑块 |

当前合法 consume_source/consume_catalyst 结构的成本区显示来源于哪个效果。编辑成本时写回原效果路径；不同时生成对应固定成本字段。新建规则固定成本入口不生成这两类效果。已有冲突按字段定位报告，不靠迁移或自动删除效果修复。

## 3. 条件控件

| 条件类型 | 字段与后端范围 | 控件设计 |
|---|---|---|
| dimension | dimensions为非空维度ID列表，不支持标签 | 目录列表选择；排除用条件树inverted节点，不生成include/exclude字段 |
| biome（exact模式） | mode必填为exact；biomes为非空群系ID/标签列表 | 目录选择；与climate模式切换只写mode，不擅自删除原参数；不生效分支折叠并说明 |
| biome（climate模式） | temperature/humidity/continentalness/erosion/depth/weirdness 的min/max，各-1..1，可省略端点；min≤max | 六条双端区间；普通0.1、Shift0.01；每端可不约束；六维全部无约束时禁止提交 |
| weather | weather必填，clear/rain/thunder | 分段选项；与weather效果的mode字段区别映射 |
| outdoor | 无专属参数 | 直接添加条件叶；判断非户外用inverted节点，不生成布尔参数 |
| surrounding_blocks | up/down/north/south/east/west为可选方块/标签，至少填写一方向 | 六个方向槽位与目录选择；规则方向含义留在条件编辑器，通用槽位布局可进入kit |
| catalyst_present | items及count；count1..64，默认1 | 图标选择 + 整数滑块；说明“检查存在”，与消耗成本区分 |
| fluid_present | fluid可省略表示任意流体；require_source默认true | 任意/指定流体选择与“仅源流体”开关；说明是存在条件，不计流体数量 |
| time_of_day | from/to均必填0..23999，无后端默认；from>to合法跨零点 | 昼夜周期双端条，普通100tick、Shift1tick；可精确输入刻。客户端新建可用0/23999全天预设 |
| y_level | min/max各-2048..2048，可省略；min≤max | 双端区间 + 各端启用开关；普通/Shift为整数调整，保留完整域 |
| light_level | min/max各0..15，可省略；min≤max | 双端区间 + 各端启用开关；整数步长1 |

无规则级条件时省略conditions，不输出null或空组。每条规则/效果条件表达式各自受叶≤128、节点≤256、深度≤16约束。普通区间的两端不能交叉；昼夜区间必须允许跨周期，不复用min≤max错误校验。

昼夜标记与日时显示由宿主注入周期控件；周期为24000tick，0刻对应Minecraft06:00。全天用0..23999表示；from=to表示该刻，不误显示为全天。预设至少提供全天、白天和夜间，具体刻边界用显式from/to而不是“日出日落实时查询”。

## 4. 效果公共字段

| 字段 | 后端范围 / 默认 | 控件与注意事项 |
|---|---|---|
| E.delay_ticks | 0..INT_MAX，默认0，可省略 | 时间步进、立即/1秒/5秒预设、精确输入；旧72000是UI人为上限，不作为新硬限额 |
| E.chance | 有限double0..1，默认1，可省略 | 显示0..100%；普通1个百分点、Shift0.1个百分点；显示不得反写舍入后的值 |
| E.conditions | 可省略条件树 | 沿用递归条件编辑器；候选内路径定位需包含候选和效果 |

tick字段默认以秒显示，并保留刻数旁注或单位切换。秒模式以0.05秒为最小可表示单位，转换需验证秒×20为整数；用十进制计算避免浮点误差。保存仍是完整整数tick，不改JSON单位。trigger_after_seconds本身存秒，不经过×20写回。

源/催化剂固定成本不显示这些公共概率、延迟与条件控件；catalyst_cost对象中的这些字段由后端严格拒绝。当前consume_source/consume_catalyst效果模型仍携带公共字段，RuleValidation未禁止其合法非默认值，不能把固定成本对象的禁用规则错误套用到它们。成本区编辑其count/items/radius时保留原公共字段，详情只读说明这些旧字段与固定成本结算的区别；如实施时要改变其合法性，另立后端任务，不能在GUI里静默删值。

## 5. 十二种内置效果

| 类型 | 专属数值字段 / 后端范围 / 默认 | 控件 |
|---|---|---|
| spawn_item | count1..64默认1；limit1..4096可省略；radius1..32可省略 | item图标选择；count整数滑块；limit开关+步进/精确输入；radius可选整数滑块 |
| spawn_entity | count1..64默认1；age完整int默认0；limit1..4096可省略；radius1..32可省略 | entity目录；count滑块；成年0/幼年-24000预设、age步进/精确输入；可选limit/radius |
| place_block | count1..64默认1；radius1..32默认6；limit1..4096可省略 | block目录或use_source_block；square/circle/cross；count/radius滑块；limit开关+步进 |
| spawn_xp | amount1..65536默认1 | 步进、预设与精确输入；不使用只有绝对定位的大范围滑块 |
| loot_table | luck为float-100..100默认0 | table目录；带零点luck滑块，普通1、Shift0.1 |
| lightning | count1..16默认1 | 整数滑块；一次性语义说明 |
| arrow_rain | count1..256默认16 | 整数滑块+精确输入；药水效果对象子列表 |
| arrow_rain药水子项 | duration_ticks1..1000000默认100；amplifier0..255默认0，均可省略 | 时间步进/精确输入；等级显示1..256、写回减1，罗马数字与阿拉伯数字辅助；effect目录必选 |
| weather | duration_ticks1..24000默认6000 | clear/rain选项、thundering开关；时间步进/滑块、预设与精确输入 |
| consume_source（当前合法结构） | count1..64默认1 | 成本区兼容路径编辑；新建效果选择器不提供该类型 |
| consume_catalyst（当前合法结构） | count1..64默认1；radius1..8默认1 | 成本区编辑items/count/radius，保留原效果结构；新建选择器不提供该类型 |
| consume_fluid | 无专属数值；fluid可省略；require_source默认true | 保留效果入口、任意/指定流体与“仅移除源流体”开关；不是物品成本，也不是检查源掉落物是否存在 |
| explosion | power为float0..16默认3 | 小数滑块，普通1、Shift0.1，精确输入保留更细合法值；保留其他类型字段 |

spawn_item/spawn_entity的radius省略与place_block默认6是不同语义：前两者在有limit时运行有效默认6，界面不得仅因展示该有效值就写入radius。age预设不是域边界，年龄只影响后端支持年龄的实体。

非数值字段仍由各类型描述符提供：物品/实体/方块/战利品/状态效果等ID由对应目录选取；枚举、布尔和条件按后端现行字段呈现，未知字段不通过已知字段DTO重建而丢失。

### 5.1 非数值字段补充

| 类型 | 字段与控件要求 |
|---|---|
| spawn_item | item必填，支持物品ID/标签；目录选择结果写回原TaggedId形态 |
| spawn_entity | entity必填，支持实体ID/标签；使用ENTITY目录与技术ID输入，不把ITEM数据集直接套用 |
| place_block | block或use_source_block提供有效来源；后者默认false；shape为square/circle/cross，默认square；切换来源保留未编辑分支，说明哪个字段生效 |
| spawn_xp | per_source_item默认false，提供开关并沿用后端参数说明，不遗漏该合法高级字段 |
| loot_table | loot_table必填，使用LOOT_TABLE目录，不用物品目录猜测注册ID |
| arrow_rain | pickup为disallowed/allowed/creative_only，默认disallowed；potion_effects对象列表可省略；每项effect必填、duration_ticks/amplifier可省略 |
| weather | mode为clear/rain，必填；thundering默认false。雷雨由rain + thundering表达，不写不存在的thunder枚举 |
| explosion | fire、visual_only为开关，均默认false；visual_only只播放粒子与音效、不破坏方块或伤害实体，依据当前ExplosionExecutor |

各类型原有合法字段必须仍可查看、编辑或明确只读保留，不能为了更短的默认表单丢失高级参数。ConsumeFluidEffect中的旧注释把require_source解释为源掉落物，但执行器实际调用fluidState.isSource()；界面文案以执行器为准，实施时同步修正该处误导注释。

## 6. 必须修正的旧UI差异

1. priority、trigger_after_seconds、source_cost、delay_ticks、spawn_entity.age支持后端完整域；旧人工上限不能阻止合法规则编辑。
2. 触发秒数不得继续套用TICKS单位转换。
3. 概率回填保留原始精度；例如0.123456不能只因打开界面就变成0.123。
4. amplifier最高255对应显示等级256；旧UI遗漏的最高等级需可编辑并可往返保存。
5. 药水持续时间/强度的后端省略默认与旧描述符“必填”标记对齐；effect ID仍按后端要求处理。
6. 可选上下界保留缺省状态，time_of_day跨零点合法，气候全部空范围非法。

## 7. 源码证据入口

以下路径均相对 `common/src/main/java/com/meteorite/itemdespawntowhat/`：

- `core/model/RuleCodecs.java`、`RuleValidation.java`、`CommonFields.java`、`CatalystCost.java`：规则、通用数值域与默认。
- `core/type/condition/{Biome,TimeOfDay,YLevel,LightLevel,CatalystPresent}Condition.java`：条件域与可选端点。
- `core/type/effect/*Effect.java`、`ArrowRainEffect.PotionEffectSpec`：效果域与默认。
- `core/type/effect/exec/{SpawnItem,SpawnEntity}Executor.java`：检测半径省略时的运行默认。
- `client/edit/BuiltinEditorDescriptors.java`、`client/ui/screen/form/FormControl.java`：旧UI域、概率显示和等级转换的差异。

本文件仅记录文档。实施阶段按主计划执行IDEA检查与串行构建；游戏内域边界、精度、单位和省略状态往返由用户验收，不新增test文件。
