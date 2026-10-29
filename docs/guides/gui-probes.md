---
title: GUI 开发探针
layer: 5
audience: 需要验证客户端界面行为、又不愿手点一遍编辑器的开发者与 Agent
authority: tools/gui-probes（lib/run.py、lib/prelude.js、lib/sheet.py、lib/report.py）、common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiLayoutSource.java
---

# GUI 开发探针

## TL;DR

- 运行 `powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1`，在真实 dev 客户端里按 `tools/gui-probes/cases` 的用例逐项真实点击、按语言文件真值断言，产出判定报告与截图证据；整体判定不是 `pass` 时退出非零。
- 截图是证据：一轮把全部截图拼成 `gui-contact-sheet.png`，另写 `visual-summary.md` 清单，由人或 Agent 直接读图确认外观；用例判定只看 `check` 结果。
- 客户端已开着时加 `-Reuse` 复用现场，一轮几秒出结果；本机实测一轮 4 个用例、29 条明细全部通过，总耗时 8.1 秒。

## 适用对象

需要在不手点编辑器的情况下确认界面行为的开发者与 Agent：列表页文案、命名弹窗与输入校验、编辑页页签、条件编辑画布的进出路径与 GUI 缩放档位。运行环境是项目现有的 JDK 21、Gradle 与 NeoForge 1.21.1 开发客户端，需要真实 GL 桌面会话。首次使用先跑 `tools/gui-probes/setup.ps1`，把 dev 依赖（AutoJS、Rhino）下到 `neoforge/run/mods`。

## 按改动选择验证

先确定改动影响哪一种用户能感受到的结果，再选能观察该结果的最小用例集合。

| 改动或目标 | 选择与执行时机 |
|---|---|
| 编辑器列表页的标题、新建按钮、搜索占位 | `editor-list` |
| 命名弹窗、名字校验联动、点 Confirm 进入编辑页、编辑页页签 | `editor-new-rule` |
| 条件编辑画布：Trigger → Edit condition tree、空态占位、类型选择器、Outdoor 条件叶、模态 Back 关闭 | `editor-condition-editor` |
| GUI 缩放 1x/2x/3x 下条件画布的标题与窗口边界 | `condition-scales` |
| 改完界面、确认新外观符合预期 | 跑全部用例，读本轮 `gui-contact-sheet.png` 与单张 PNG |
| 改 kit 公开原语或绘制路径 | 跑全部用例，并配套 `tools/check-ui-kit-boundaries.ps1` |
| 纯后端、文案或文档改动 | 用[后端探针](backend-probes.md)、`python tools/check-i18n.py`、`python tools/check-docs.py` 验证 |

所选用例全部通过、并且证据图支撑结论后结束验证；出现新失败、影响范围扩大或用户明确要求时再扩测。

## 运行工作流

在仓库根目录运行：

```powershell
# 首次与换了机器：下载并校验 dev 依赖到 neoforge/run/mods
powershell -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1

# 冷启动全量：拉起客户端 + 跑全部用例 + 收证据 + 停客户端 + 还原 options.txt
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1

# 复用现场：客户端已经开着，不拉新进程、不动 options.txt，几秒出结果
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Reuse -RunDir quick-1

# 只跑一个用例（名字取自用例 JSON 的 name 字段）
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Reuse -Only editor-condition-editor

# 本轮保留客户端，供下一轮直接 -Reuse
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -RunDir session-1 -KeepClient
```

| 参数 | 作用 |
|---|---|
| `-RunDir` | 证据目录。只给名字时落在 `neoforge/build/gui-runs/<名字>`，给绝对路径时按原样用，省略则用时间戳 |
| `-Cases` | 用例目录，默认 `tools/gui-probes/cases` |
| `-Only` | 只跑指定用例名（用例 JSON 的 `name`），可重复 |
| `-World` | 要进入的存档名，省略则取存档列表第一个 |
| `-WaitClient` | 等待 MCP 就绪的秒数，默认 240 |
| `-NoLaunch` | 连已经在跑的客户端，不拉起、不改写也不还原 `options.txt`、不停进程 |
| `-Reuse` | 复用现场，行为与 `-NoLaunch` 一致，是本场景的显式写法 |
| `-KeepClient` | 自己拉起客户端时跑完不停，配合下一轮 `-Reuse` |
| `-SkipSetup` | 跳过「进世界 + 打开编辑器」前置阶段 |

现场处理规则：

- 只有真正拉起客户端时才备份 `neoforge/run/options.txt` 并改写：固定 `guiScale:2`、`lang:en_us`、`forceUnicodeFont:false`、`fullscreen:false`、`onboardAccessibility:false`（无引导页、缩放与语言可预测），结束时（含失败）按原字节还原。`-Reuse` / `-NoLaunch` 全程不碰该文件：实测一轮 `-Reuse` 前后 `options.txt` 的 sha256 不变。
- 自己拉起的客户端用 `tools/dsh-build.ps1 -Tasks :neoforge:runClient` 启动，启动日志写进证据目录的 `client.log` / `client.err.log`；结束时按进程树停掉，`-KeepClient` 则保留。
- 客户端启动占住 `tools/dsh-build.ps1` 的全局互斥锁，探针与其它 Gradle 任务串行跑。
- `neoforge/run/mods` 少了 dev 依赖时直接以退出码 2 结束并提示先跑 `setup.ps1`。

## 运行机制

### 客户端原语常驻

`lib/prelude.js` 整份在会话里安装一次（`__gp.__version = 8`，与 `lib/run.py` 的 `PRELUDE_VERSION` 对应）。开跑前先做一次状态求值，比对「已安装 / 证据目录 / 版本号」三项，任一项不符才重装整份原语。证据目录以 `var __gpRunDir` 注入并每次调用现读，因此同一个客户端会话可以连续跑不同 `-RunDir`。

求值结果统一经 `JSON.stringify` 收口再逐层解包，布尔与数字能还原成真实类型。

### 批量求值

用例里连续的 `check` / `shot` / `dump` 合成一次求值（`__gp.evalAll`，逐项捕获异常），用例里的顺序不变；遇到 `do` / `wait_ms` 步骤或用例结束时才提交这一批。一次求值内的布局快照只构建一次，同一批里的截图与文案读取共用它。批量整体失败时退化为逐条重跑，一条坏表达式不连坐整批检查。

### 自适应 settle

`do` 步骤之后不做固定 sleep，驱动轮询布局指纹 `__gp.probeKey()` 判断界面是否稳定：

- 指纹由屏幕名、逻辑尺寸、可见节点（种类、矩形、文案、可用态）的稳定哈希与可见节点数组成；比较时再带上缩放档位与帧缓冲尺寸。同一逻辑尺寸可能来自不同窗口与缩放组合，缩放与帧缓冲一起参与比较才能区分档位切换。
- 轮询间隔取半帧，限制在 15–60ms。动作立即改变界面时，再取到一次相同采样即视为稳定；动作没有立即改变界面时，先给两帧异步窗口，窗口内仍无变化就认为该动作没有可见效果。
- 超过上限记一次 settle 超时，计入 metrics，不影响用例判定。上限默认 1500ms，用例级 `settle.timeout_ms` 或步骤级 `settle.timeout_ms` 可覆盖；当前用例按 2000–2500ms 设定。
- 该步不需要观察界面变化时用 `"settle": false`；只有动画这类必须固定等待的场景，用例才保留 `wait_ms`。

### 截图证据

`shot` 在一次求值里落两张 PNG：全尺寸截图与逻辑分辨率缩略图 `<名字>-thumb.png`（宽高为全尺寸除以 GUI 缩放），返回图像尺寸、缩放档位与截图耗时。`takeScreenshot` 取的是上一帧已完成的帧缓冲，驱动在提交含截图的批次前等两帧。

用例全部跑完后 `lib/sheet.py` 把截图拼成证据图并写清单：

- `gui-contact-sheet.png`：3 列网格，每格等比缩放后的截图 + 两行标签（`#序号 + 用例名`、截图文件名）。本轮 9 张截图拼成 1464×1008。
- `visual-summary.md`：运行目录、截图张数、contact sheet 文件名，以及每张一行（序号、用例、截图、图像尺寸@缩放、文件路径）。
- 拼图失败只打印一行 `[shots] 拼图失败`，报告照常生成。

截图只作证据：外观是否符合预期由人或 Agent 读 contact sheet 与单张 PNG 判断，用例判定只看 `check` 结果。

## metrics

每轮汇总并落盘到 `gui-summary.json` 的 `metrics` 字段，终端最后打印一行。

- `phases`：install / wait_client / setup / cases / sheet / report 各阶段耗时。
- `evals`：求值次数、RTT 平均 / 最大 / 合计。
- `settle`：settle 次数、累计等待、轮询次数、超时次数。
- `shots`：截图张数与截图耗时。
- `cases`：每条用例的耗时、eval 次数、明细数、截图数、settle 与截图耗时。

终端输出形如：

```text
[pass] editor-list 561ms evals=8
[pass] editor-new-rule 1243ms evals=18
[pass] editor-condition-editor 1606ms evals=22
[pass] condition-scales 2801ms evals=40
[shots] 9 张 1729ms
verdict=pass 用例 4/4 细项 29/29
[metrics] total=8.1s evals=91 rtt_avg=39.9ms settle=3210ms shots=9
```

本轮（`-Reuse -RunDir docs-final`）实测：总耗时 8.1s；phases 依次为 install 55ms、wait_client 44ms、setup 33ms、cases 6212ms、sheet 1729ms、report 不足 1ms（整毫秒取整记为 0）；evals 91 次、RTT 平均 39.9ms、最大 173.4ms；settle 28 次共等待 3210ms、44 轮询、0 超时；截图 9 张共 804ms。同配置的另一轮 `lead-accept` 为 8.2s / 91 evals / settle 3462ms。报告里的「性能」折叠区给出同一份数据。

## 断言口径与期望值来源

| 层 | 守什么 | 期望值从哪来 |
|---|---|---|
| 存活 | 界面不崩、布局快照可读、截图落盘 | 无期望值，取不到即异常 |
| 交互真值 | 真实点击后界面按语言文件文案出现或消失 | 语言文件原文；真实点击结果 |
| 几何边界 | 条件画布模态完整落在窗口逻辑坐标内（1x/2x/3x 各一次） | `__gp.window()` 的窗口度量与实际模态矩形 |
| 证据 | contact sheet、单张截图、布局 dump 供人/Agent 复核 | 不参与判定 |

- 文案真值只从 `common/src/main/resources/assets/itemdespawntowhat/lang` 的 `en_us.json` / `zh_cn.json` 取；`expected_lang` 的 key 必须在两份文件里都存在，缺任一边这条直接判失败。
- 用例定位点击目标与读取文案一律经 `__gp.lang(key)`，展示文本只来自语言文件。
- 交互真值在当前用例里的落点：命名弹窗里按 `END` + 40 次 `BACKSPACE` 清空名字后 Confirm 变灰、输入合法名字后恢复（说明键盘输入落到了真实控件）；点 Confirm 后 Basic / Trigger / Results 三个页签出现；点空态占位后类型选择器标题与 Outdoor 选项出现；点 Outdoor 后画布出现条件叶；点模态 Back 后画布标题消失。
- 几何断言只在条件画布上做一次：`MODAL` 节点的矩形落在 `__gp.window()` 的逻辑宽高内。

`check` 的其他字段：`contains` 对文本快照做包含判断；`soft` 只记录不判失败；`expected` 用于非文案类真值（字符串、布尔、数字）。

## 用例格式

用例是 `tools/gui-probes/cases/*.json`，按文件名顺序执行：

```json
{
  "name": "editor-condition-editor",
  "description": "这条用例要证明的用户可见结果",
  "window": { "width": 1280, "height": 720, "scale": 2 },
  "settle": { "timeout_ms": 2000 },
  "steps": [
    { "do": "__gp.clickWidget(__gp.lang('gui.itemdespawntowhat.edit.tab.trigger'))" },
    { "check": { "name": "条件画布标题来自语言文件",
                 "expr": "__gp.textNodes().join(' ')",
                 "expected_lang": "gui.itemdespawntowhat.edit.tree.title",
                 "lang": "en_us", "contains": true } },
    { "shot": "30-condition-canvas-empty.png" },
    { "dump": "30-editor-condition-editor.json", "expr": "__gp.layout()" }
  ]
}
```

| 字段或步骤 | 作用 |
|---|---|
| `name` | 用例名，`-Only` 按它匹配 |
| `description` | 这条用例要证明的用户可见结果 |
| `window` | 用例开始前把窗口与 GUI 缩放设到该档并 settle；用例显式声明，因为上一条用例可能改过档位 |
| `settle` | 该用例的 settle 开关（`enabled`）与超时上限（`timeout_ms`） |
| `do` | 求值一条表达式，通常是一次真实输入或窗口调整；执行后做一次 settle |
| `check` | 求值 `expr` 并与期望比较 |
| `shot` | 截图落到证据目录；可选 `rects` 表达式把元素矩形叠层一起带回落盘 |
| `dump` | 把结构化数据写成 JSON 证据 |
| `wait_ms` | 固定等待，只在自适应 settle 观察不到的动画场景保留 |

书写约定：用例顶部显式给出自己的 `window`；`check` 的 `expr` 是 JSON 字符串，换行写 `\n` 转义，或直接用 `join(' ')` 把文本快照拼成一行。用例的断言落在用户能看到的结果上：可见文案、可见节点与几何边界；期望值取自语言文件与布局快照。

## 客户端原语

`tools/gui-probes/lib/prelude.js` 在会话内安装一次，提供 `__gp`：

| 原语 | 作用 |
|---|---|
| `begin()` | 每次求值开头清空本次求值内的布局缓存 |
| `state()` / `workspace()` | 会话状态（所在位置、当前屏幕、玩家、连接、单人服务端是否运行）与客户端工作区单例的公开状态（状态、会话 id、结果序号、条目数） |
| `screenInfo()` / `screenName()` | 当前屏幕的标题与类型 |
| `widgets()` / `findClickable(label)` | 原版控件树；`findClickable` 先查排版快照再查控件树 |
| `clickWidget(label)` / `clickWidgetAny(labels)` / `clickWidgetLast(label)` | 按文案真实点击；`Last` 取同名文案里靠后的那个（模态节点排在页面节点之后） |
| `clickKindAt(kind, dx, dy)` | 按节点种类点第一处可见节点，用于没有文案的模态内容区 |
| `click` / `drag` / `wheel` / `key` / `typeText` | 走 `Screen` 输入路由的真实输入 |
| `window()` / `resize(w, h, scale)` | 窗口尺寸与 GUI 缩放，`resize` 后逻辑坐标按档位生效 |
| `layout()` / `layoutCached()` / `flat()` / `flatCached()` | kit 只读排版快照：矩形、文案、文本带宽、层级 |
| `rects(kinds)` / `textNodes()` | 从快照里取矩形叠层（可按节点种类过滤，每项含 kind、x、y、w、h、label）与文本列表 |
| `lang(key)` | 语言文件真值：客户端当前语言下该 key 的展示文案 |
| `probeKey()` / `evalAll(expressions)` | 布局指纹；一批表达式逐项求值并捕获异常 |
| `shot(name)` / `dumpJson(name, value)` | 截图（全尺寸 + 缩略图）与结构化证据落盘 |
| `menuStep(world)` / `editorStep()` / `openEditor()` | 单步推进前置状态，驱动侧循环调用 |
| `fps()` / `runtime()` | 当前帧率与运行时信息（帧缓冲尺寸、缩放、eval 内耗时） |

保存链路、删除确认这类会改存档的行为需要专属世界与独立存档；当前用例集合只覆盖只读操作。

## 证据与报告

```text
neoforge/build/gui-runs/<runDir>/
  client.log / client.err.log      # 自己拉起客户端时的启动日志
  run-metadata.json                # 运行元数据、环境快照与 metrics
  gui-summary.json                 # 判定汇总 + metrics + 截图清单，供编排层换算退出码
  gui-report.html                  # 自包含单文件报告：内嵌 contact sheet、用例明细、环境、性能区
  gui-contact-sheet.png            # 本轮截图的网格证据图
  visual-summary.md                # 截图清单
  <截图>.png / <截图>-thumb.png     # 全尺寸截图与逻辑分辨率缩略图
  <导出>.json                      # 用例里 dump 的布局快照
```

`gui-summary.json` 的 `verdict` 取 `pass` / `warn` / `fail` / `skip`；用例级 verdict 另有 `error`（用例执行中断），整体随即判为 `fail`。明细含每条用例的检查项、期望、实际与耗时；`shots` 给出截图张数、contact sheet 与清单路径。报告在 `file://` 下直接打开，不依赖网络与外部脚本。

本轮证据：`gui-report.html` 约 96 KB，`gui-contact-sheet.png` 1464×1008；9 张截图覆盖列表页、命名弹窗、编辑页、条件画布空态、类型选择器、含条件叶的画布与 1x/2x/3x 三档画布。

## 边界与适用范围

- 只在有真实 GL 桌面会话的本机跑；AutoJS 以 dev-only 依赖装在 `neoforge/run/mods`，只在 `127.0.0.1:25585` 暴露。
- 覆盖范围是 NeoForge 1.21.1 开发客户端，即当前 AutoJS 构建的目标平台。
- AutoJS 以 unrestricted 模式在游戏进程内执行任意 Java，等价于本地任意代码执行：只装 dev 运行目录、只监听回环地址、不进发布包。
- 编辑器是 kit 自绘界面，几何与文案来自 `UiLayoutSource` 快照；屏幕、模态、页签、图标工具列、条件画布、类型选择器、文本框与表单视图都实现该接口。新增自绘界面实现 `UiLayoutSource` 即可被探针读到。
- 命名输入框不导出布局节点，用例用「Confirm 变灰 / 恢复」间接证明键盘输入生效，输入框里的文本在截图里可见。
- 类型选择器只导出可视区的行（虚拟滚动）；首屏 10 行里含 Outdoor，当前用例选的正是首屏可见项，选更靠后的类型需要先滚动到可视区。
- GUI 缩放：1x/2x 用 1280×720 窗口；3x 用 1920×1080（逻辑 640×360）。1280×720@3 只有 427×240 逻辑像素，条件画布模态的下沿与页脚重叠，点击会落到页脚上。
- 条件画布内按键由画布与工具列消费，关闭画布统一点模态 Back 按钮。
- 探针固定 `lang:en_us`（自己拉起时改写 `options.txt`）；`-Reuse` 沿用现场语言，`expected_lang` 的 `lang` 需与现场一致。
- 界面持续动画或被其它窗口遮挡时，布局指纹会一直变化，settle 走到超时；超时只记 metrics，这类场景在用例里用 `wait_ms` 或步骤级 `settle:false`。
- 客户端启动期间占住 Gradle 全局互斥锁，探针与构建串行。

## 相关文档

- 探针内部结构与入口参数：[tools/gui-probes/README.md](../../tools/gui-probes/README.md)
- 后端链路验证：[后端探针](backend-probes.md)
- kit 公开面与依赖边界：[UI kit](../modules/ui-kit.md)
- 编辑器界面与尺寸缩放约定：[编辑器界面](../modules/client-ui.md)、[ADR-0022](../adr/0022-client-ui-kit-adoption.md)
- 文案键与中英同步：[错误与状态码](../api/errors.md)、`python tools/check-i18n.py`
