---
title: GUI 开发探针
layer: 5
audience: 需要验证客户端界面行为、又不愿手点一遍编辑器的开发者与 Agent
authority: tools/gui-probes（lib/run.py、lib/prelude.js、lib/sheet.py、lib/report.py）、common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/UiLayoutSource.java
---

# GUI 开发探针

## 使用

GUI 探针在 NeoForge 开发客户端通过 AutoJS 驱动真实输入、读取 `UiLayoutSource` 快照并截图。用例在 `tools/gui-probes/cases` 声明起点、动作与断言，整体判定为 `pass` 时退出零。

```powershell
powershell -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -World test
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Reuse -Only workbench-gallery
python tools/gui-probes/lib/run.py --run-dir .scratch/gui-check --validate
```

| 参数 | 含义 |
|---|---|
| `-RunDir` | 只给名字时位于 `neoforge/build/gui-runs/`，绝对路径直接使用，默认时间戳 |
| `-Cases` / `-Only` | 用例目录 / 按 JSON 的 `name` 选择，未知名称在启动前报错 |
| `-World` | 编辑器和世界预览需要的存档名称，默认列表首个存档 |
| `-WaitClient` | 等待 AutoJS 就绪的秒数 |
| `-Reuse` / `-NoLaunch` | 连接已运行客户端，保留 options 与进程 |
| `-KeepClient` | 自己启动的客户端在执行结束后继续保留 |

启动脚本先执行离线校验，再检查开发依赖并启动客户端。自己启动时备份 `options.txt`，固定英文、缩放、窗口模式，最后恢复原文件；复用时沿用当前语言和设置。Gradle 构建与客户端运行通过 `dsh-build.ps1` 串行。需要真实 GL 桌面会话。

## 调试工作台

开发客户端在标题画面、游戏内或普通 Screen 按 **Ctrl+F8** 进入调试工作台。规则编辑器在保存和模态结束后允许进入。目录中的工具根据上下文启用：

| 工具 | 状态与职责 |
|---|---|
| 组件展台 | 本地按钮、文本与数字输入、轮询、范围条、标量滑条、列表，同页查看与重置 |
| 组合交互 | 复用真实组件装配，支持 Ctrl+M 打开模态，检查焦点、拖动取消、滚动与缩放 |
| 图标预览 | 需要已加载世界；资源、多个尺寸、转台与预算，Space 切换、R 释放重读 |
| 测试规则草稿 | 需要可编辑工作区；填充或删除测试草稿，由管理页应用后落盘 |

场景独立持有本地模型和输入作用域，退出释放捕获与模态。工作台返回编辑器时按现存工作区新建屏幕；失效工作区通过正式编辑命令重新获取。

## 用例契约

每例独立声明 `start`：`editor` 表示规则列表，`new_rule: true` 在列表通过真实命名弹窗创建新草稿；`workbench` 表示目录；`scene` 配合 `gallery`、`interactions` 或 `previews`。导航每次推进一次公开输入并等待渲染。

```json
{
  "name": "open-gallery",
  "start": {"kind": "scene", "scene": "gallery"},
  "steps": [
    {"action": {"kind": "click", "selector": {"kind": "CONTROL", "lang": "gui.itemdespawntowhat.debug.workbench.increment"}}},
    {"assert": {"name": "禁用按钮", "kind": "node_enabled", "selector": {"kind": "CONTROL", "lang": "gui.itemdespawntowhat.debug.workbench.disabled"}, "expected": false}},
    {"shot": "gallery.png"},
    {"dump": "layout.json", "query": {"source": "layout"}}
  ]
}
```

`action` 支持 click、type、clear、key、resize、scroll、drag、press、release。点击、按下、拖动和滚动先用选择器定位唯一目标；目标为零或多项时失败并记录候选和屏幕。选择器支持 kind、lang/text、lang_any/text_any、enabled、within 祖先作用域。活动顶层模态遮住下层目标，点击矩形与祖先裁剪区相交；几何断言读取原始矩形。

`query.source` 支持 screen_title、window、layout、workspace、nodes、count、text、enabled、rect。`capture` 保存 JSON 值，`value_changed` 引用同例已采样的名称；`expected_capture` 用于相等对照。缺失采样在离线校验与执行时明确失败，每例 finally 清空状态并释放未完成的拖动。

| 命名断言 | 判定 |
|---|---|
| `text_present` / `text_absent` / `text_count` | 当前可达节点数量至少一项 / 零 / 精确数量 |
| `node_enabled` | 实际 boolean 与期望精确相等 |
| `geometry_inside` | 原始节点矩形完整位于窗口逻辑坐标内 |
| `value_equals` | JSON 原生类型和值精确相等；支持 expected、expected_lang、expected_capture |
| `value_changed` | 当前值与本例 capture 不同 |

`expected_lang` 在 main 与开发语言资源合并后校验双语 key，执行期按客户端当前语言读取。控件定位沿用同一语言来源。业务用例只使用结构化步骤；JavaScript 取值与输入细节集中在 prelude。

## 执行与证据

prelude 在会话内按版本和运行目录安装。每次求值的布局快照只构建一次，动作只执行一次。动作后按屏幕、几何、文案、启用态、缩放和帧缓冲指纹 settle；超时写入 metrics。异步业务结果通过 `wait_for` 轮询断言。截图前等待渲染帧，每次生成全尺寸与逻辑尺寸 PNG。

`RunRecord` 统一持有用例判定、原始断言、证据 id、用例和步骤关联、指标及输出文件角色。`sheet.build(record)` 生成拼图与清单，`report.render_report(record, shots)` 只渲染记录。截图采集失败记用例错误；派生拼图或报告失败记 `artifact_errors`，保留原始截图数量和已完成用例。

产物位于运行目录：`gui-run.json` 是完整运行记录，`gui-summary.json` 是汇总，`run-metadata.json` 是环境与指标，`gui-report.html` 内嵌 contact sheet，`gui-contact-sheet.png` 与 `visual-summary.md` 供读图，单张 PNG 和布局 JSON 为原始证据。GUI 外观与手感由截图复核和用户实机确认。

## 验证选择

| 影响范围 | 用例 |
|---|---|
| 列表、命名输入与页签 | editor-list、editor-new-rule |
| 条件树、类型选择与缩放 | editor-condition-editor、condition-scales |
| 箭雨参数和目录卡片 | editor-results-arrow-rain |
| 工作台入口、多组件与重置 | workbench-gallery |
| 模态、拖动取消、缩放与重置 | workbench-interactions |
| 图标工具导航与转台 | workbench-previews |

结果满足本次行为与证据需要后停止；代码、配置、负载与目标未变时复用报告。新增界面的实际可见几何、字段和条目作用域通过 `UiLayoutSource` 导出，避免在用例中猜测坐标。

## 相关文档

- [工具入口与模块](../../tools/gui-probes/README.md)
- [编辑器与工作台](../modules/client-ui.md)
- [UI kit](../modules/ui-kit.md)
- [后端探针](backend-probes.md)
