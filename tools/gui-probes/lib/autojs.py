"""AutoJS MCP 端点的最小客户端。

用途：GUI 探针通过它在真实 Minecraft 客户端进程里求值 Rhino 脚本（真实点击、读真实状态、
抓真实截图），拿到可断言的 JSON 证据。端点由 AutoJS mod 以 streamable HTTP 暴露在本机
127.0.0.1:25585。

边界：
- 只面向本地开发客户端。AutoJS 未启动、HTTP 失败、JSON-RPC error、工具 isError、内容不是
  JSON 时统一抛 AutoJsError，由调用方决定重试还是判失败。
- 不维护会话状态，每次调用独立建连；调用节奏由编排层控制。
- eval_js 返回 AutoJS 包装后的 JSON（通常是 {"value": ...}），本模块不拆包。
"""

import json
import urllib.error
import urllib.request

DEFAULT_ENDPOINT = "http://127.0.0.1:25585"
VALID_SIDES = ("auto", "server", "client")

_JSON = "application/json"
_SSE = "text/event-stream"


class AutoJsError(RuntimeError):
    """AutoJS 端点不可达、协议异常或工具调用失败。"""


def _decode_sse(body: str) -> str:
    """从 text/event-stream 响应里取最后一条 data 载荷。"""
    payload = ""
    for line in body.splitlines():
        if line.startswith("data:"):
            payload = line[len("data:"):].strip()
    if not payload:
        raise AutoJsError("SSE 响应里没有 data 载荷")
    return payload


def _post(endpoint: str, envelope: dict, timeout_s: float) -> str:
    """POST 到 /mcp，按响应 Content-Type 返回 JSON 或 SSE 的载荷文本。"""
    request = urllib.request.Request(
        endpoint + "/mcp",
        data=json.dumps(envelope).encode("utf-8"),
        headers={"Content-Type": _JSON, "Accept": _JSON + ", " + _SSE},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            body = response.read().decode("utf-8", "replace")
            content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as exc:
        raise AutoJsError(f"HTTP {exc.code} {exc.reason}") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise AutoJsError(f"连不上 AutoJS 端点 {endpoint}: {exc}") from exc
    return _decode_sse(body) if _SSE in content_type else body


def _rpc(method: str, params: dict, endpoint: str, timeout_s: float) -> object:
    """发一条 JSON-RPC 2.0 请求，返回 result 字段。"""
    raw = _post(endpoint, {"jsonrpc": "2.0", "id": 1, "method": method, "params": params}, timeout_s)
    try:
        envelope = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise AutoJsError(f"{method} 的响应不是 JSON: {raw[:200]!r}") from exc
    if not isinstance(envelope, dict):
        raise AutoJsError(f"{method} 的响应不是 JSON 对象: {raw[:200]!r}")
    if "error" in envelope:
        raise AutoJsError(f"{method} 失败: {envelope['error']}")
    if "result" not in envelope:
        raise AutoJsError(f"{method} 的响应缺少 result: {raw[:200]!r}")
    return envelope["result"]


def _tool_text(name: str, result: object) -> str:
    """取出 tools/call 结果里的第一段文本，isError 视为失败。"""
    if not isinstance(result, dict):
        raise AutoJsError(f"工具 {name} 的 result 不是 JSON 对象")
    if result.get("isError"):
        details = [str(item["text"]) for item in result.get("content") or []
                   if isinstance(item, dict) and item.get("text")]
        raise AutoJsError("工具 " + name + " 返回错误: " + (" ".join(details) or "无错误详情"))
    content = result.get("content") or []
    first = content[0] if content else None
    if not isinstance(first, dict) or "text" not in first:
        raise AutoJsError(f"工具 {name} 没有返回文本内容")
    return str(first["text"])


def _call_tool(name: str, arguments: dict, endpoint: str, timeout_s: float) -> object:
    """执行一次 tools/call，把返回文本解析成 JSON 值。"""
    text = _tool_text(name, _rpc("tools/call", {"name": name, "arguments": arguments}, endpoint, timeout_s))
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise AutoJsError(f"工具 {name} 返回的不是 JSON: {text[:200]!r}") from exc


def health(endpoint: str = DEFAULT_ENDPOINT, timeout_s: float = 5.0) -> dict:
    """GET /health，确认 AutoJS 已加载且端点可达；返回端点报告的健康信息。"""
    request = urllib.request.Request(endpoint + "/health", headers={"Accept": _JSON})
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            raw = response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        raise AutoJsError(f"health 返回 HTTP {exc.code} {exc.reason}") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise AutoJsError(f"连不上 AutoJS 端点 {endpoint}: {exc}") from exc
    # 端点是否健康只看「能连上并给出响应」：真实实现回纯文本 ok，不强制 JSON。
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {"status": raw.strip() or "ok"}
    if not isinstance(data, dict):
        return {"status": raw.strip()}
    return data


def status(endpoint: str = DEFAULT_ENDPOINT, timeout_s: float = 30.0) -> dict:
    """调用 status 工具，返回 AutoJS 运行时信息（内核、求值限制、脚本目录等）。"""
    data = _call_tool("status", {}, endpoint, timeout_s)
    if not isinstance(data, dict):
        raise AutoJsError("status 工具返回的不是 JSON 对象")
    return data


def eval_js(code: str, side: str = "client", timeout_ms: int = 90000, *,
            endpoint: str = DEFAULT_ENDPOINT, timeout_s: float | None = None) -> object:
    """在游戏进程里求值一段 Rhino 代码，返回 AutoJS 包装后的 JSON。

    side 只能是 auto / server / client。求值在渲染线程同步执行，阻塞动作（等待进入世界等）
    必须拆成多次调用，不要在一次 eval 里长等；timeout_ms 是 AutoJS 侧超时，HTTP 超时默认
    比它多留 15 秒。
    """
    if side not in VALID_SIDES:
        raise ValueError(f"side 必须是 {VALID_SIDES} 之一，收到 {side!r}")
    if timeout_ms <= 0:
        raise ValueError("timeout_ms 必须为正数")
    http_timeout = timeout_ms / 1000.0 + 15.0 if timeout_s is None else timeout_s
    return _call_tool("eval_js", {"code": code, "side": side, "timeout_ms": timeout_ms}, endpoint, http_timeout)
