---
title: GUI 开发探针
layer: 5
audience: 需要验证客户端界面行为与视觉基线、又不愿手点一遍编辑器的开发者与 Agent
authority: tools/gui-probes、tools/gui-probes/lib/prelude.js、common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiLayoutSource.java
---

# GUI 开发探针

## TL;DR

- 运行 `powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1`：拉起真实 dev 客户端，在真实世界里打开规则编辑器，按 `tools/gui-probes/cases` 里的用例逐步真实输入并截图，产出 `gui-report.html` 与 `gui-summary.json`，判定失败退出非零。
- 驱动层是 [AutoJS](https://github.com/MerrySnow404/AutoJS) 的 MCP 端点（`http://127.0.0.1:25585/mcp`）：它以 dev-only 依赖的形式装在 `neoforge/run/mods`，不入库、不进发布包。
- 只在本机跑。需要真实 GL 桌面会话，默认不接 CI，也不替代用户手测。
- 证据来自真实渲染：输入走原版 `Screen` 输入路由，截图走 `Screenshot.takeScreenshot`；几何来自 kit 的只读排版快照 `UiLayoutSource`。
- 首次使用先跑 `tools/gui-probes/setup.ps1` 把两个 dev 依赖落到 `neoforge/run/mods`（该目录已被 `.gitignore` 忽略）。

## 适用对象

需要在不手点编辑器的情况下确认界面行为或落地 [ADR-0022](../adr/0022-client-ui-kit-adoption.md) 视觉基线的开发者与 Agent。运行环境是项目现有的 JDK 21、Gradle 与 NeoForge 1.21.1 开发客户端；不覆盖 Fabric 端。

## 按改动选择验证

先确定改动影响哪一种用户能感受到的结果，再选能观察该结果的最小用例集合。

| 改动或目标 | 选择与执行时机 |
|---|---|
| 改编辑器页面装配、控件几何、列表或树的行高与文本带宽 | `editor-layout-nodes`；断言落在排版快照的结构与真实文案上 |
| 改缩放相关尺寸、留白、文字宽度 | `editor-adr0022-scales`；覆盖 GUI 缩放 1x/2x/3x 的逻辑坐标与不变量 |
| 改模态、对话框、二次确认 | `editor-new-rule-modal`；断言模态层进出与确认/取消文案 |
| 改 kit 公开原语或绘制路径 | 跑全部 GUI 用例，并配套 `tools/check-ui-kit-boundaries.ps1` |
| 纯后端、文案或文档改动 | 不跑 GUI 用例；按 [后端探针](backend-probes.md) 或 `python tools/check-i18n.py`、`python tools/check-docs.py` 验证 |

所选用例全部通过、并且证据图能支撑结论后结束验证；出现新失败、影响范围扩大或用户明确要求时再扩测。

## 运行工作流

在仓库根目录运行：

```powershell
# 首次：下载并校验两个 dev 依赖到 neoforge/run/mods，这一步当前已经完成
powershell -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1

# 全量：拉起客户端 + 跑全部用例 + 收证据 + 还原现场
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1

# 只跑一个用例；客户端已经开着时直接连，省一次启动
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Only editor-layout-nodes -NoLaunch
```

| 参数 | 作用 |
|---|---|
| `-RunDir` | 证据目录，默认 `neoforge/build/gui-runs/<时间戳>` |
| `-Cases` | 用例目录，默认 `tools/gui-probes/cases` |
| `-Only` | 只跑指定用例名，可重复 |
| `-World` | 要进入的存档名；省略则取存档列表第一个 |
| `-WaitClient` | 等待 MCP 就绪的秒数，默认 240 |
| `-NoLaunch` | 不自己拉客户端，连已经在跑的那个 |
| `-KeepClient` | 跑完不停客户端，便于连续迭代 |
| `-SkipSetup` | 跳过「进世界 + 开编辑器」前置阶段 |

编排在开跑前备份 `neoforge/run/options.txt`，把 `guiScale:2`、`lang:en_us`、`forceUnicodeFont:false`、`fullscreen:false`、`onboardAccessibility:false` 固定下来（无引导页、缩放与语言可预测），结束时（含失败）按原字节还原并停掉自己拉起的客户端进程树。客户端启动占住 `tools/dsh-build.ps1` 的全局互斥，期间不要并行跑其他 Gradle 任务。

## 断言分层与期望值来源

| 层 | 守什么 | 期望值从哪来 |
|---|---|---|
| L0 存活 | 界面不崩、布局快照可读、截图落盘 | 无期望值，失败即异常 |
| L1 语义 | 界面出现该出现的真实文案、会话处于真实状态 | 语言文件 `zh_cn.json` / `en_us.json` 的原值；工作区状态与快照来自真实服务端 |
| L2 布局不变量 | 控件不越界、文案不超出控件、同级按钮不重叠、逻辑坐标符合档位 | [ADR-0022](../adr/0022-client-ui-kit-adoption.md) 的视觉基线；`font` 实测宽度 |
| 证据 | 截图与元素矩形叠层供人工复核 | 不参与判定 |

期望值一律不写死在脚本里：文案从语言文件取，逻辑坐标从 `UiRect` 与窗口度量取，会话状态从真实工作区取。像素比对当前只作为证据，不作为门禁——动画与光标闪烁会让同一界面的两次截图存在差异。

## 用例格式

用例是 `tools/gui-probes/cases/*.json`，按文件名排序执行：

```json
{
  "name": "editor-layout-nodes",
  "description": "这条用例想证明的用户可见结果",
  "window": { "width": 640, "height": 480, "scale": 2 },
  "settle_ms": 700,
  "steps": [
    { "do": "__gp.resize(320, 240, 1)" },
    { "wait_ms": 700 },
    { "check": { "name": "1x 缩放下逻辑宽为 320", "expr": "__gp.window().logicalWidth", "expected": 320 } },
    { "check": { "name": "文案来自语言文件", "expr": "__gp.textNodes().join(' ')",
                 "expected_lang": "gui.itemdespawntowhat.edit.button.new", "lang": "en_us", "contains": true } },
    { "shot": "10-editor-layout.png", "rects": "__gp.rects(['CONTROL','TREE'])" },
    { "dump": "10-editor-layout.json", "expr": "__gp.layout()" }
  ]
}
```

| 步骤 | 作用 |
|---|---|
| `do` | 求值一条表达式，通常是一次真实输入或窗口调整 |
| `wait_ms` | 驱动侧等待，让输入在真实帧里落地 |
| `check` | 求值 `expr` 并与 `expected` 比较；`expected_lang` 从两份语言文件取真值（缺任一边即失败）；`contains` 用于「文案出现在文本快照里」；`soft` 只记录不判失败 |
| `shot` | 截图落到证据目录，`rects` 给元素矩形叠层 |
| `dump` | 把结构化数据写成 JSON 证据 |

用例只描述用户能看到的结果，不写实现细节：不断言类名、私有字段、内部调用次数。

两条书写约定：用例顶部显式给出自己的 `window`，因为上一条用例可能把窗口改到别的档位；`check` 的 `expr` 是 JSON 字符串，换行要写成 `\n` 转义，或干脆用 `join(' ')` 把文本快照拼成一行。

## 客户端原语

`tools/gui-probes/lib/prelude.js` 在每次求值前注入，提供 `__gp`：

| 原语 | 作用 |
|---|---|
| `state()` / `workspace()` | 真实会话状态：所在维度、连接、工作区状态与快照条目数 |
| `screenInfo()` / `screenName()` | 当前屏幕类型与标题 |
| `widgets()` / `clickWidget(label)` / `clickWidgetAny(labels)` | 按文案真实点击：先查排版快照（自绘界面只有这里能找到按钮），再查原版控件树（菜单这类原版界面） |
| `click` / `drag` / `wheel` / `key` / `typeText` | 走 `Screen` 输入路由的真实输入 |
| `window()` / `resize(w, h, scale)` | 窗口尺寸与 GUI 缩放，`resize` 后逻辑坐标按档位生效 |
| `layout()` / `rects(kinds)` / `textNodes()` | kit 只读排版快照：矩形、文案、文本带宽、层级 |
| `shot(name)` / `dumpJson(name, value)` | 截图与结构化证据落盘 |
| `menuStep(world)` / `editorStep()` | 单步推进前置状态，驱动侧循环调用 |

保存链路、删除确认这类会改存档的行为需要专属世界与独立存档；当前用例集合全部是只读操作。

## 证据与报告

```
neoforge/build/gui-runs/<runDir>/
  client.log                     # 客户端日志（自己拉起时）
  run-metadata.json              # 运行元数据与环境
  gui-summary.json               # 判定汇总，供编排层换算退出码
  gui-report.html                # 自包含单文件报告：截图 base64 内嵌、元素矩形叠层
  <用例截图>.png
  <用例导出>.json
```

`gui-summary.json` 的 `verdict` 只有 `pass` / `fail` / `skip` 三种，明细含每条用例的检查项、期望、实际与耗时。报告在 `file://` 下直接打开，不依赖网络与外部脚本。

## 边界与限制

- 只在有真实 GL 桌面会话的本机跑；无头环境与 Windows CI runner 不满足条件。
- 只覆盖 NeoForge 开发客户端；AutoJS 没有 1.21.1 的 Fabric 目标，Fabric 端继续由用户手测。
- AutoJS 以 `unrestricted=true` 在游戏进程内执行任意 Java，等价于本地任意代码执行：只装 dev 运行目录、只监听回环地址、绝不进发布包。
- 编辑器是 kit 自绘界面，没有原版控件树；几何与文案断言依赖 kit 只读快照，新增自绘界面时需要同时实现 `UiLayoutSource`，否则该界面只能用像素证据。
- 快照覆盖当前页的控件、规则树的可见行与模态层；页签、复合控件内部行（条件树、目录选择、列表项）、保存遮罩与类型选择器尚未接入，涉及这些区域的结论要靠截图复核。
- `takeScreenshot` 取的是上一帧已完成的帧缓冲：输入与截图之间必须隔开至少一帧，用例里用 `wait_ms` 表达。
- 探针固定 `lang:en_us`，`expected_lang` 因此默认取英文真值；语言文件仍要求中英同步，缺任一边判失败。
- 像素比对模块（`lib/pixels.py`）只提供证据与人工复核入口，不设跨机门禁：同一界面的两次真机截图会因动画、光标与抗锯齿产生差异。

## 相关文档

- 后端链路验证：[后端探针](backend-probes.md)
- kit 公开面与依赖边界：[UI kit](../modules/ui-kit.md)
- 编辑器界面与视觉基线：[编辑器界面](../modules/client-ui.md)、[ADR-0022](../adr/0022-client-ui-kit-adoption.md)
- 文案键与中英同步：[错误与状态码](../api/errors.md)、`python tools/check-i18n.py`
