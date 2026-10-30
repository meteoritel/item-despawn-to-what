# tools/gui-probes

NeoForge 开发客户端的真实输入与证据工具。使用、用例契约和验证选择见 [GUI 探针指南](../../docs/guides/gui-probes.md)。依赖 Python 标准库、PowerShell、真实 GL 桌面及开发目录里的 AutoJS / Rhino。

```powershell
powershell -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -World test
powershell -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Reuse -Only workbench-gallery
python tools/gui-probes/lib/run.py --run-dir .scratch/gui-check --validate
```

| 文件 | 职责 |
|---|---|
| `setup.ps1` | 安装并校验 dev-only AutoJS 与 Rhino |
| `run-gui-probes.ps1` | 离线校验、启动或复用进程、options 恢复和退出码透传 |
| `lib/autojs.py` | 回环 MCP 客户端，返回协议错误与原始 JSON |
| `lib/prelude.js` | 作用域选择器、真实输入、目标导航、布局快照和截图 |
| `lib/run.py` | 声明式动作与断言、用例状态、settle、采集和运行记录装配 |
| `lib/record.py` | 用例及整体判定、证据身份、输出角色、记录持久化 |
| `lib/sheet.py` | 从记录逐张读取 PNG，派生 contact sheet 与截图清单 |
| `lib/report.py` | 消费记录与派生图，渲染自包含 HTML |
| `lib/pixels.py` / `lib/bitmap_font.py` | 标准库 PNG 与拼图文字基元 |
| `cases/` | 每例声明独立起点、动作、命名断言和证据 |

`lib` 按脚本目录导入。`sheet.build(record)` 返回派生图信息，`report.render_report(record, shots)` 返回 HTML。判定只由记录提供。截图原始字节在渲染时按需读取，完整运行记录以 `idtw-gui-run-v1` 写入 `gui-run.json`。

启动参数、断言字段和输出角色以指南及对应实现为准。AutoJS 只装开发运行目录、只监听 `127.0.0.1:25585`。客户端运行和 Gradle 构建共用互斥锁；`-Reuse` 保留已有进程与 options。
