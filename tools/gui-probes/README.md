# tools/gui-probes

GUI 自动化测试的**离线证据管线**：驱动真实 Minecraft 客户端跑用例，把真实截图与断言
整理成一份可以离线打开的自包含 HTML 报告，并给出像素级差异证据。纯 Python 3.14 标准库 +
PowerShell，不依赖第三方包、不依赖网络服务。

## 管线形态

1. 开发客户端加载 AutoJS mod（dev-only），它在 `127.0.0.1:25585/mcp` 暴露 MCP 工具；
2. 编排层用 `lib/autojs.py` 调用 `eval_js`，在游戏进程里执行真实输入、读真实状态、抓真实截图；
3. 截图交给 `lib/pixels.py` 与 golden 图比对，得到差异像素数与 diff 图；
4. 行为断言、像素结论、截图和环境信息交给 `lib/report.py`，渲染成单文件 HTML；
5. 退出码由编排层按判定换算（`lib/run.py`：PASS / WARN → 0，FAIL / ERROR → 1）。

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `setup.ps1` | 把 AutoJS 与 Rhino 两个 jar 下到 `neoforge/run/mods/` 并按 sha256 校验；已存在且校验通过则跳过 |
| `lib/autojs.py` | AutoJS MCP 客户端（JSON-RPC 2.0 over streamable HTTP） |
| `lib/pixels.py` | 纯标准库 PNG 编解码、pixelmatch 5.3.0 语义比对、整数倍降采样 |
| `lib/report.py` | 把一次运行的元数据、用例、截图、环境渲染成自包含 HTML，并汇总判定 |
| `run-gui-probes.ps1` | 顶层入口：准备运行目录、拉起真实客户端、驱动用例、还原现场并透传退出码 |
| `cases/` | 用例定义，按文件名顺序执行；格式与书写约定见 [GUI 探针指南](../../docs/guides/gui-probes.md) |
| `lib/run.py` | 用例驱动：连接客户端、把客户端推到编辑器、逐步执行用例、落证据并调用 `report.render_report` 出报告 |
| `lib/prelude.js` | 每次 eval 前置注入的客户端原语（真实输入、截图、状态与布局快照导出） |

`lib/` 不是 Python 包（无 `__init__.py`），调用方把该目录加进 `sys.path` 后直接
`import pixels` / `import report` / `import autojs`。

## 接口

### setup.ps1

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1 -DryRun
```

- 默认目标 `neoforge/run/mods`，可用 `-ModsDir` 覆盖；`-DryRun` 只打印计划，不联网不写文件。
- 校验项是文件大小 + sha256。下载失败或校验不符时非零退出并打印原因。
- 两个 jar 是 dev-only 依赖，`neoforge/run` 已被 `.gitignore` 忽略，不入库。

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
decode_png(blob: bytes) -> tuple[int, int, bytearray]          # 8 bit 非隔行的灰度/真彩及 alpha 变体
encode_png(width: int, height: int, rgba) -> bytes            # 输出 8 bit 真彩+alpha
compare(img1, img2, width, height, threshold=0.1, include_aa=False) -> CompareResult
downscale(width, height, rgba, factor) -> tuple[int, int, bytearray]
```

`compare` 返回 `{"diff_pixels", "total_pixels", "ratio", "diff_png"}`：`diff_png` 是
可直接写入文件或 base64 内嵌的 PNG 字节；`ratio = diff_pixels / total_pixels`。
索引色、16 bit、隔行 PNG 会明确抛 `ValueError`。逐像素 Python 循环，1080p 量级约数秒。

### lib/report.py

```python
render_report(run_metadata: dict, cases: list[dict], env: dict) -> tuple[str, dict]  # 纯渲染，无 IO
```

`render_report` 只做渲染：不读文件、不写文件、不取时间和环境，图片以 `png_bytes` 传入、
base64 内嵌，结构化数据放进 <script type="application/json">，所以 `file://` 下也能直接用。
`summary["verdict"]` 是 `pass` / `warn` / `fail` / `skip` 之一，另有用例与细项计数供编排层用。

读图与路径拼装由编排层负责：`lib/run.py` 把截图读成字节塞进 `artifacts[].png_bytes` 后再调
`render_report`。`cases` 每项的结构见 `lib/report.py` 模块 docstring（`name` / `verdict` /
`detail` / `checks` / `artifacts`）；截图的 `artifacts[].rects` 可给元素矩形，报告会叠在图上。

## 使用约束

- AutoJS 等于游戏进程内任意 Java 执行（mod 的 `unrestricted` 语义），只做本地开发工具，
  不暴露到 `127.0.0.1` 之外，不进发布包。
- 截图取的是上一帧已完成的帧缓冲：输入之后至少再等一帧才截。搜索框光标会闪烁，
  golden 比对时需要 mask 该区域或接受固定噪声。
- 同一台机器上跑 dev 客户端会占住 Gradle 的构建锁，GUI 探针的启动与构建要串行。
- 只在本机、需要真实 GL 桌面会话时跑，默认不接 CI；编排脚本会备份并还原 `neoforge/run/options.txt`，
  并停掉自己拉起的客户端进程树。
