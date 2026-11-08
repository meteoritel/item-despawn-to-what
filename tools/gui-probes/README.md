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
| `lib/autojs.py` | 回环 MCP 客户端，逐层解码固定信封，返回 eval 的原生 value |
| `gen-case-reference.py` | 更新指南契约受管块；--check 只读核对用例、handler 与文档；--root 指定输入仓库 |
| `lib/case_files.py` | 离线读取用例与语言，按输入仓库校验本地 handler 和用例契约 |
| `lib/case_contract.py` | 字段声明、递归校验、断言取数与判定、客户端能力投影 |
| `lib/client.py` | 仓库定位、prelude 安装、协议/能力握手、单一 invoke 与内容摘要 |
| `lib/prelude.js` | 作用域选择器、真实输入、目标导航、布局快照和截图 |
| `lib/run.py` | 声明式动作与断言、用例状态、settle、采集和运行记录装配 |
| `lib/record.py` | 用例及整体判定、证据身份、输出角色、记录持久化 |
| `lib/sheet.py` | 从记录逐张读取 PNG，派生原始像素分页 contact sheet 与截图清单 |
| `lib/report.py` | 消费记录与派生图，渲染自包含 HTML |
| `lib/pixels.py` / `lib/bitmap_font.py` | 标准库 PNG 与拼图文字基元 |
| `cases/` | 每例声明独立起点、动作、命名断言和证据 |

`lib` 按脚本目录导入。`sheet.build(snapshot)` 返回派生图信息，`report.render_report(snapshot, shots)` 返回 HTML。RunRecord 通过 add_case、set_environment、complete_run 接收采集数据，derive_outputs 先保存原始证据，再生成派生产物。snapshot 是不可变 JSON 快照，消费者取得独立数据副本；判定只由记录提供。截图原始字节在渲染时按需读取，完整运行记录以 `idtw-gui-run-v1` 写入 `gui-run.json`。

启动参数、断言字段和输出角色以指南及对应实现为准。AutoJS 只装开发运行目录、只监听 `127.0.0.1:25585`。客户端运行和 Gradle 构建共用互斥锁；`-Reuse` 保留已有进程与 options。
