# tools/gui-probes

在真实 Minecraft 开发客户端里按用例真实点击、按语言文件真值断言、留全尺寸截图证据的探针工具；
一轮跑完产出判定报告、contact sheet 与截图清单。纯 Python 3.14 标准库 + PowerShell，不引第三方包。

用例怎么写、怎么选、怎么判读证据，见 [GUI 探针指南](../../docs/guides/gui-probes.md)。

## 管线形态

1. 开发客户端加载 AutoJS mod（dev-only），在 `127.0.0.1:25585` 暴露 MCP 工具；
2. `lib/autojs.py` 调 `eval_js`，在游戏进程里执行真实输入、读真实状态、抓真实截图；
3. `lib/prelude.js` 的客户端原语在会话里安装一次（版本号 7），之后每条表达式只发自身；
   连续的 `check` / `shot` / `dump` 合成一次批量求值；
4. 动作之后不做固定 sleep，驱动轮询 `probeKey()` 布局指纹判断界面是否稳定；截图前等两帧
   取新帧缓冲；
5. `lib/sheet.py` 把一轮截图拼成 `gui-contact-sheet.png` 并写 `visual-summary.md` 清单；
6. `lib/report.py` 把用例明细、截图、环境与 metrics 渲染成自包含 HTML，并汇总判定；
7. 退出码由 `lib/run.py` 换算：整体 verdict 为 `pass` → 0，其余 → 1。

## 最短使用路径

```powershell
# 首次：把 dev 依赖下到 neoforge/run/mods 并按 sha256 校验
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1

# 冷启动全量：拉起客户端、跑全部用例、收证据、停客户端并还原现场
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1

# 快速轮：客户端已经开着时复用现场，几秒出结果
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/run-gui-probes.ps1 -Reuse -RunDir quick-1
```

`-Reuse` 与 `-NoLaunch` 都直接连已在跑的客户端：不拉新进程、不改写也不还原
`neoforge/run/options.txt`、不停客户端；`-Reuse` 额外表明「这轮就是在复用现场」。
只有真正拉起客户端时，编排层才备份并改写 `options.txt`（固定 `guiScale:2`、`lang:en_us`、
关闭发光字体与引导页），结束时按原字节还原并停掉自己拉起的进程树。

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `setup.ps1` | 把 AutoJS 与 Rhino 的 jar 下到 `neoforge/run/mods/`，按文件大小 + sha256 校验；已存在且校验通过则跳过 |
| `lib/autojs.py` | AutoJS MCP 客户端（JSON-RPC 2.0 over streamable HTTP） |
| `lib/prelude.js` | 客户端原语：真实输入、截图（全尺寸 + 逻辑分辨率缩略图）、状态与布局快照、`probeKey` 指纹、`evalAll` 批量求值 |
| `lib/run.py` | 用例驱动：安装/复用 prelude、把客户端推到编辑器、批量执行 check/shot/dump、自适应 settle、汇总 metrics、拼图与渲染报告 |
| `lib/pixels.py` | 纯标准库 PNG 编解码（8 bit 非隔行灰度/真彩及 alpha 变体）、等比缩放与画布绘制基元 |
| `lib/sheet.py` | contact sheet 拼图与 `visual-summary.md` 清单 |
| `lib/report.py` | 把运行元数据、用例、截图、环境与 metrics 渲染成自包含 HTML，并汇总判定 |
| `lib/bitmap_font.py` | 5×7 位图字体与文本裁剪，供 contact sheet 标签使用 |
| `run-gui-probes.ps1` | 顶层入口：准备运行目录、拉起或复用客户端、透传参数、还原现场并透传退出码 |
| `cases/` | 用例定义，按文件名顺序执行；格式与书写约定见 [GUI 探针指南](../../docs/guides/gui-probes.md) |

`lib/` 不是 Python 包（无 `__init__.py`），调用方把该目录加进 `sys.path` 后直接
`import autojs` / `import pixels` / `import sheet` / `import report`。

## 接口

### run-gui-probes.ps1

| 参数 | 作用 |
| --- | --- |
| `-RunDir` | 证据目录；只给名字时落 `neoforge/build/gui-runs/<名字>`，给绝对路径按原样用，省略用时间戳目录 |
| `-Cases` | 用例目录，默认 `tools/gui-probes/cases` |
| `-Only` | 只跑指定用例名（用例 JSON 的 `name`），可重复 |
| `-World` | 要进入的存档名，省略取存档列表第一个 |
| `-WaitClient` | 等 MCP 就绪的秒数，默认 240 |
| `-NoLaunch` | 连已在跑的客户端，不拉起、不动 `options.txt`、不停进程 |
| `-Reuse` | 复用现场，行为与 `-NoLaunch` 一致 |
| `-KeepClient` | 自己拉起客户端时跑完不停，供下一轮 `-Reuse` |
| `-SkipSetup` | 跳过「进世界 + 打开编辑器」前置阶段 |

`neoforge/run/mods` 缺少 AutoJS 或 Rhino 时脚本以退出码 2 结束并提示先跑 `setup.ps1`。
客户端启动日志写到证据目录的 `client.log` / `client.err.log`。

### lib/run.py

```powershell
python tools/gui-probes/lib/run.py --run-dir <dir> [--cases DIR] [--only NAME]... [--world NAME]
    [--wait-client 240] [--skip-setup] [--timeout-ms 90000] [--settle-timeout-ms 1500]
```

管证据目录、装 prelude、跑用例、拼图、写 `gui-report.html` / `gui-summary.json` /
`run-metadata.json`，终端打印每条用例判定、`[shots]` 行、contact sheet 与清单路径、
`verdict=` 汇总行、`[metrics]` 行与报告路径。整体 verdict 为 `pass` 时退出码 0。

### setup.ps1

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1 -DryRun
```

- 默认目标 `neoforge/run/mods`，可用 `-ModsDir` 覆盖；`-DryRun` 只打印计划，不联网不写文件。
- 校验项是文件大小 + sha256。下载失败或校验不符时非零退出并打印原因。
- 这些 jar 是 dev-only 依赖，`neoforge/run` 已被 `.gitignore` 忽略，不入库。

### lib/autojs.py

```python
health(endpoint="http://127.0.0.1:25585", timeout_s=5.0) -> dict
status(endpoint=..., timeout_s=30.0) -> dict
eval_js(code, side="client", timeout_ms=90000, *, endpoint=..., timeout_s=None) -> object
```

- `health` 走 `GET /health`，`status` / `eval_js` 走 JSON-RPC `tools/call`。
- `eval_js` 返回 AutoJS 包装后的 JSON（通常是 `{"value": ...}`），不在客户端拆包。
- 端点不可达、JSON-RPC error、工具 `isError`、返回文本不是 JSON 统一抛 `AutoJsError`。
- `side` 只能是 `auto` / `server` / `client`。求值在渲染线程同步执行，等待进世界之类
  的阻塞动作要拆成多次调用；`timeout_ms` 是 AutoJS 侧超时，HTTP 超时默认比它多留 15 秒。

### lib/pixels.py

```python
png_size(blob) -> (width, height)                       # 只读 PNG 头
decode_png(blob) -> (width, height, rgba)              # 8 bit 非隔行的灰度/真彩及 alpha 变体
encode_png(width, height, rgba) -> bytes
scale_to_fit(width, height, rgba, box_w, box_h) -> (width, height, rgba)
new_canvas(width, height, color) / fill_rect(...) / blit(...)   # contact sheet 绘图基元
```

索引色、16 bit、隔行 PNG 会明确抛 `ValueError`；`decode_png` 返回可直接原地绘制的 RGBA 字节数组。

### lib/sheet.py

```python
build(run_dir, cases) -> dict
```

- 逐张读取用例产物里的 PNG，按三列网格缩放绘制，格间留边距，每格两行标签
  （`#序号 + 用例名`、截图文件名）。
- 返回 `{"contact_sheet", "png_bytes", "list_path", "count", "shots", "sheet_ms"}`；
  `contact_sheet` / `list_path` 是 `gui-contact-sheet.png` 与 `visual-summary.md` 的路径。
- `visual-summary.md` 给出运行目录、截图张数、contact sheet 名，以及每张一行（序号、用例、
  截图名、图像尺寸与缩放档位、文件路径）。
- 单张解码失败时该格画占位图，本轮其余截图照常入图；截图只作证据，由人或 Agent 读图确认外观。

### lib/report.py

```python
render_report(run_metadata: dict, cases: list[dict], env: dict,
              shots: dict | None = None, metrics: dict | None = None) -> (html, summary)
```

`render_report` 只做渲染：素材全部由调用方以 `png_bytes` 传入并 base64 内嵌，结构化数据放进
`<script type="application/json">`，因此报告在 `file://` 下可直接打开。
`shots` / `metrics` 是可选的证据块：缺省时报告只含用例明细；给定时额外渲染内嵌 contact sheet
的截图区与折叠的「性能」区（phases、eval RTT、settle 汇总、截图耗时、每用例耗时）。
`summary["verdict"]` 取 `pass` / `warn` / `fail` / `skip` 之一，另有用例与细项计数供编排层用。

读图与路径拼装由 `lib/run.py` 负责：把 contact sheet 与截图读成字节塞进 `png_bytes` 后再调
`render_report`。

## 产物

```text
neoforge/build/gui-runs/<runDir>/
  client.log / client.err.log      # 自己拉起客户端时的启动日志
  run-metadata.json                # 运行元数据、环境快照与 metrics
  gui-summary.json                 # 判定汇总 + metrics + 截图清单
  gui-report.html                  # 自包含单文件报告
  gui-contact-sheet.png            # 截图网格证据图
  visual-summary.md                # 截图清单
  <截图>.png / <截图>-thumb.png     # 全尺寸截图与逻辑分辨率缩略图
  <导出>.json                      # 用例里 dump 的布局快照
```

`gui-summary.json` 的 `metrics` 含 `phases`（install / wait_client / setup / cases / sheet /
report）、`evals`（次数与 RTT 平均/最大/合计）、`settle`（次数、累计等待、轮询、超时）、
`shots`（张数与耗时）与每用例明细。

## 使用约束

- AutoJS 等于游戏进程内任意 Java 执行（mod 的 `unrestricted` 语义），只做本地开发工具，
  只监听回环地址，不进发布包。
- 截图取的是上一帧已完成的帧缓冲：驱动在提交含截图的批次前等两帧，用例里不需要再为截图加固定等待。
- 同一台机器上跑 dev 客户端会占住 Gradle 的构建锁，GUI 探针的启动与构建要串行。
- 只在本机、需要真实 GL 桌面会话时跑，默认不接 CI；`neoforge/build` 是构建产物，不进仓库。
