# 触发页、条件树画布页与参数调参页：文案打磨

## 1. 目标

对玩家在「触发页 + 条件树画布页 + 参数调参页面」看到的全部文案做一轮打磨，标准是**简洁、清晰、分点明确**，一切以玩家体验为准：

- 帮助面板从「7 行流水句 + 中文长句被裁剪」改为**按动作分点、每行不超过面板内宽**；
- 修复帮助面板把 translation key 当正文显示的缺陷；
- 画布卡片标题/摘要不再重复啰嗦，截断不再是常态；
- 逻辑运算符改为**纯本地化**：中文「且 / 或 / 非」，英文 and / or / not；
- **为全部内建条件类型单独设计参数摘要**，把 JSON 与全限定 ID 换成玩家能读的话，通用格式化（范围、枚举、ID、显示名、列表）抽成可复用方法；
- **参数调参页面**（条件叶的参数表单）不再出现内部字段名、英文枚举与原始资源 ID，长标签不再被挤成「…」。

## 2. 范围（已与用户确认）

| 在范围内 | 不在范围内 |
|---|---|
| 触发页（RuleEditorScreen 的「触发」页：条件树卡片、页头、页脚提示、按钮） | 基本 / 输入 / 结果页正文 |
| 条件树画布页（ConditionTreeOverlay：帮助面板、功能栏与工具列提示、画布卡片、状态栏、自绘 tooltip） | 其他 GUI 页面与命令提示（约 1000 个语言键） |
| 参数调参页面：画布右侧参数面板（ConditionGraphSidePanel / ConditionGraphInlinePanel）、触发页展开弹窗（UiModal 承载的 LeafPanel）、内联表单行与说明行 | 效果（effect）类型摘要 |
| 全部 10 个内建条件类型的参数摘要 | |

双语同步：`en_us.json` 与 `zh_cn.json` 的键集合必须完全一致；中文为主，英文同步改写。

## 3. 文案规则（本轮统一标准）

1. **一行一条信息**：帮助面板每条以 2 字动作标签开头（新增 / 编辑 / 选择 / 移动 / 视图 / 平移 / 关闭），不再用 `·` 把多条动作串成一行。
2. **不允许靠裁剪兜底**：面板任何一行都必须完整显示；当前 `HELP_WIDTH = 236` 会让多行出现「…」，需按内容自适应并重排文案。
3. **界面文案不出现内部语法**：正文里不得出现 translation key、JSON（`{"`）、全限定 ID（`minecraft:`）、英文枚举（`exact`）、内部字段名（`block`）。
4. **同一概念全站一个词**：页签名、页内提示、按钮必须同名（当前页签是「输入」，提示却写「输入与成本」）。
5. **不嵌套冒号**：一行里最多一个冒号分隔符。
6. **运算符走本地化键**：`tree.operator.all/any/not` 中文为「且 / 或 / 非」，英文为 and / or / not；不再混排双语。
7. **标签列只有 69 像素**：参数表单的标签与控件同排时标签列宽为面板内宽的三分之一（212 / 3 = 69），因此标签文案必须短、能完整显示，禁止让玩家靠悬停猜。
8. **目录条目的可见宽度有限**：注册表条目的芯片最多 124 像素，带图标时文字区仅 82 像素，因此条目显示名优先用短名（本地化名），不得让原始 ID 成为常态。

### 帮助面板目标形态（草案，供评审）

```
条件树操作
新增   点「＋ 添加条件」选类型，再点卡片填参数
编辑   双击卡片打开参数
选择   单击选中 · Ctrl 多选 · Shift 范围 · Ctrl+A 全选
移动   拖动卡片换位置；拖到卡片上/下四分之一处插到前/后
视图   滚轮缩放 · Ctrl+0 适配 · Ctrl+Home 回根
平移   拖空白处或中键拖动
关闭   Esc 或再点「?」
```

## 4. 现状问题清单（均已定位到源码）

| # | 位置 | 现状 | 问题 |
|---|---|---|---|
| P1 | `ConditionTreeOverlay.java:665` | 帮助正文第 6 行是 `Component.translatable(UI + "hint")` | 键 `gui.itemdespawntowhat.edit.tree.hint` 在 `en_us.json` / `zh_cn.json` 中都不存在（全仓 grep 零命中），玩家看到的是裸键 |
| P2 | `ConditionTreeOverlay.java:81-83`、`:651` | `HELP_WIDTH = 236`，正文用 `TextScroll.trimToWidth` 裁剪 | 至少 4 行被截成「…」，玩家读不到完整说明 |
| P3 | `ConditionTreeOverlay.java:658-667` | 7 行按「手势类型」随意排列，无分组 | 与目标「分点明确」直接冲突 |
| P4 | `ConditionTreeOverlay.java:159-165` → `NaturalSummary.java` | 叶卡片摘要 = `NaturalSummary.conditionLeaf(condition)`；已有 `summary.*` 18 个键与 `field`/`leaf`/`separator` 通用模板 | 参数原文直接进卡片，如「生物群系（模式：exact、生物群系：minecraft:badlands、温度：{"min":-0.9737,"max":0.8431}、湿度：{"min":-1.0,"max":1.0}）」 |
| P5 | 画布卡片 | 卡片宽上限 168，摘要超宽即裁剪 | 截图出现「维度（维度：minecraft:overworl…」「气：clear)」；标题与摘要重复「维度」「天气」 |
| P6 | `zh_cn.json:426-428` | `AND · 且` / `OR · 或` / `NOT · 非` | 双语混排，冗长 |
| P7 | `zh_cn.json:794` vs `:824` | 页签名「输入」vs 提示「消耗类动作在「输入与成本」页编辑」 | 同一概念两个名字 |
| P8 | `zh_cn.json:955` + `:756` | 「当前编辑：%s」+「%1$s，另有 %2$s 项源」 | 组合出「当前编辑：金合欢木船，另有 1 项源：方案 1：实体产出」，双冒号嵌套 |
| P9 | `zh_cn.json:957` | 状态按钮「未应用」 | 语义不明（是状态还是动作） |
| P10 | `UiConditionTreeSummary` | 树摘要用 `−` 作节点标记、用 `- ` 作层级前缀 | 两种横线混用，缩进层级不直观 |
| P11 | `RuleDisplayLabels.java:50-54` | DIMENSION / BIOME 条目按 `dimension.<ns>.<path>` / `biome.<ns>.<path>` 查语言键，查不到返回 null，调用方 `CatalogSelectionControl.java:154` 回退 `Component.literal(id)` | 实测 vanilla `en_us.json`（6881 键）含 64 个 `biome.minecraft.*`（如 `biome.minecraft.badlands` = Badlands）但 `dimension.*` **为 0**；本模组双语文件也无这两类键 → 维度条件的条目显示原始 ID `minecraft:overworld` |
| P12 | `CatalogSelectionControl.java:38-41`、`:78-79`、`:114-117` | 芯片宽 `CHIP_MAX_WIDTH = 124`，带图标时文字区 = 124 − 24 − 18 = **82 像素**（无图标 102） | `minecraft:overworld`（约 114 像素）与标签 `#minecraft:is_forest` 必然被截成「…」；现有缓解只有悬停 tooltip（`:298 tooltipAt`） |
| P13 | `FormRowLayout.java:73-121` | 标签列宽 = `availableWidth / 3`（面板内宽 212 → 69），控件列 135 | 实测必然截断的标签：条件 `time_of_day`「起始（0-23999）」「结束（0-23999）」(78)、效果 `place_block`「使用源物品的方块」(72)、效果 `spawn_xp`「按源物品数量倍增」(72)；效果 `spawn_entity`「年龄（刻；-24000 幼年，0 成年）」(149 > 135) 最严重 |
| P14 | `zh_cn.json` | `field.place_block.note` = 「block 与「使用源物品的方块」至少要填一个」；`field.weather.note` = 「模式为 clear 时该项不生效」 | 正文泄露内部字段名与英文枚举 |
| P15 | `FormRowRenderer.java:82-84` | `tooltipAt` 显式跳过 NOTE 行 | 说明行一旦截断没有任何恢复通道（当前说明行宽 208 尚能容纳，属结构性隐患） |

内建条件类型共 10 个（来自 `gui.itemdespawntowhat.edit.condition.*` 键）：
`biome`、`catalyst_present`、`dimension`、`fluid_present`、`light_level`、`outdoor`、`surrounding_blocks`、`time_of_day`、`weather`、`y_level`。

调参页面的字段来自 `BuiltinEditorDescriptors.java`：`dimension` 只有维度列表；`biome` = 模式 + 群系列表 + 6 个气候区间（仅 climate 模式显示，且 `RuleEditorPanelFactory.java:552-554` 隐藏行标签）+ 说明；`time_of_day` = 起止刻（各配 `UiRangeBar`/`UiCyclicTimeBar`）+ 说明；`y_level` / `light_level` = 上下限 + 说明；`surrounding_blocks` = 六个方向标签 + 说明；`catalyst_present` = 物品列表 + 数量；`fluid_present` = 流体列表 + 是否要求源方块 + 说明；`weather` = 天气枚举 + 说明；`outdoor` = 仅说明。

## 5. 任务拆分（严格串行）

`en_us.json` / `zh_cn.json` 是全部写作任务的共享资源，无法并行编辑，因此写作任务**必须串行**，每次写作后立即独立验证。

| 任务 | 内容 | 负责 | 依赖 |
|---|---|---|---|
| T1 | **参数摘要人话化**：为 10 个条件类型逐个设计本地化摘要；抽取可复用格式化（数值范围、枚举、ID→显示名、列表、布尔）；消除 JSON 与全限定 ID | canvas-engineer | — |
| T2 | 独立验证 T1 | verifier | T1 |
| T3 | **画布页文案与帮助面板**：修复 P1 裸键；帮助面板分点重排 + 面板宽度自适应（P2/P3）；卡片标题/摘要去重与截断策略（P5）；运算符本地化（P6） | canvas-engineer | T2 |
| T4 | 独立验证 T3 | verifier | T3 |
| T5 | **触发页文案**：页签与提示口径统一（P7）、页头「当前编辑」行（P8）、状态按钮（P9）、树摘要标记（P10） | canvas-engineer | T4 |
| T6 | 独立验证 T5 | verifier | T5 |
| T7 | **参数调参页面文案**：维度条目显示名（P11）、长条目与长标签不再截断（P12/P13）、正文内部字段名与英文枚举（P14）、说明行的可读性兜底（P15） | canvas-engineer | T6 |
| T8 | 独立验证 T7 | verifier | T7 |
| T9 | 集成校验 + 实机验收清单 | verifier | T8 |

## 6. 验收与校验命令

每个写作任务的通用校验（全部必须通过）：

1. IDEA MCP `lint_files`：改动文件 0 error；
2. `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → 退出码 0；
3. `python tools/check-docs.py` → 退出码 0（改了文档时）；
4. 双语键集合一致（集合差为空）：
   `python -c "import json,pathlib;a=json.loads(pathlib.Path('common/src/main/resources/assets/itemdespawntowhat/lang/en_us.json').read_text('utf-8'));b=json.loads(pathlib.Path('common/src/main/resources/assets/itemdespawntowhat/lang/zh_cn.json').read_text('utf-8'));print(len(a),len(b),sorted(set(a)^set(b)))"`

专项验收：

- **T1**：10 个条件类型每个都有一条本地化摘要键；摘要输出中**不出现** `{`、`minecraft:`、`exact` 一类内部语法（可用单测式临时程序或人工列举 10 条样例）；通用格式化方法有 JavaDoc 且被至少 2 个类型复用。
- **T3**：帮助面板渲染文本**不含** `gui.itemdespawntowhat.`（裸键已消除）；面板内每一行宽度 ≤ 面板内宽（无裁剪省略号）；运算符键中文为「且 / 或 / 非」、英文为 and / or / not。
- **T5**：页签名与页内提示同名；页头不再出现双冒号。
- **T7**：维度条件面板的条目在中文环境显示为中文字名（vanilla 三维度至少覆盖主世界 / 下界 / 末地），不再显示 `minecraft:` 原始 ID；带图标芯片的条目名在常见条目下不再以「…」结尾；P13 列出的 4 条标签至少不再截断（改文案或改标签列宽，二选一并在证据中说明）；参数页面正文不出现 `block`、`exact`、`clear` 一类内部语法；说明行若有截断风险，悬停能看到完整文本。
- **T9**：合并全部阶段后构建通过；给出面向用户的实机验收清单。

## 7. 约束与风险

- **共享文件**：全部写作任务改同一对 lang 文件，必须串行；并行会撞 inScope 检查。
- **第三方类型**：`NaturalSummary` 对未注册类型按原始字段名展示（既有行为），本轮不改变该兜底。
- **模组维度**：新增 `dimension.minecraft.*` 只能覆盖 vanilla 三个维度，模组维度仍会回退原始 ID；T7 需一并给出兜底（例如在 `catalogLabel` 里把未命中的 path 转成可读形式）或明确接受现状。
- **标签列宽**：`FormRowLayout` 的标签列宽是全表单共享参数，改动会影响所有行；优先改文案，确需改宽度时必须在实机确认窄面板（画布侧栏 212 像素）下不挤压控件。
- **范围外文案**：其他页面文案本轮不动，避免回归面失控。
- **文档同步**：`docs/dev/internals/editor-card-layout.md` 与 `editor-pages-contract.md` 涉及卡片、tooltip 与参数面板描述的段落需随对应写作任务同步。
- **实机验收**：文案最终观感（帮助面板行宽、卡片是否仍截断、参数表单标签是否仍截断）由用户截图确认。
