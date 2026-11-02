"""GUI 客户端协议：安装与握手、单一请求编码和结果边界。"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import autojs
from case_contract import check_handlers, check_local_handlers

_tools = next(parent for parent in Path(__file__).resolve().parents
              if (parent / "toolslib/__init__.py").is_file())
sys.path.insert(0, str(_tools))
from toolslib.repo import repo_root  # noqa: E402

REPO_ROOT = repo_root()
TOOLS_DIR = REPO_ROOT / "tools"
PROTOCOL_MAJOR = 1
REQUIRED_CAPABILITIES = frozenset(("environment", "sample", "read", "action", "reach", "language", "screenshot"))


class ProbeError(RuntimeError):
    """用例、客户端协议或输入执行失败。"""


class Client:
    """只发送协议请求；调用节奏、RTT 与 deadline 由驱动管理。"""

    def __init__(self, run_dir, evaluate):
        self.run_dir = Path(run_dir).resolve().as_posix()
        self._evaluate = evaluate
        self._prelude = Path(__file__).with_name("prelude.js").read_text(encoding="utf-8")
        check_local_handlers(Path(__file__).with_name("prelude.js"))
        self.source_hash = hashlib.sha256(self._prelude.encode("utf-8")).hexdigest()

    def _eval(self, code, timeout_ms=None):
        try:
            wire_value = self._evaluate(code, timeout_ms=timeout_ms)
        except autojs.AutoJsError as failure:
            raise ProbeError(f"客户端传输失败：{failure}") from failure
        # eval_js 已拆开固定 value 信封；本层只解码显式 JSON.stringify 一次。
        if not isinstance(wire_value, str):
            raise ProbeError(f"客户端未返回序列化协议文本：{wire_value!r}")
        try:
            return json.loads(wire_value)
        except json.JSONDecodeError as failure:
            raise ProbeError(f"客户端协议 JSON 无效：{wire_value[:200]!r}") from failure

    def _check_hello(self, hello):
        if not isinstance(hello, dict):
            raise ProbeError(f"prelude 握手缺失：{hello!r}")
        major = hello.get("protocol_major")
        if isinstance(major, bool) or not isinstance(major, (int, float)) or major != PROTOCOL_MAJOR:
            raise ProbeError(f"GUI 协议主版本不兼容：驱动 {PROTOCOL_MAJOR}，客户端 {major!r}")
        capabilities = hello.get("capabilities")
        if not isinstance(capabilities, list) or not all(isinstance(value, str) for value in capabilities):
            raise ProbeError("客户端能力表无效")
        missing = REQUIRED_CAPABILITIES - set(capabilities)
        if missing:
            raise ProbeError("客户端缺少能力：" + ", ".join(sorted(missing)))
        if hello.get("source_hash") != self.source_hash or hello.get("run_dir") != self.run_dir:
            raise ProbeError("prelude 安装后内容摘要或证据目录不一致")
        check_handlers(hello.get("contract"))
        return hello

    def install(self, timeout_ms=90000):
        """复用同内容和目录的会话；缺失或旧内容重装，再独立校验兼容性。"""
        hello = self._eval("JSON.stringify(typeof __gp === 'object' && __gp && typeof __gp.describe === 'function' ? __gp.describe() : null)")
        reused = isinstance(hello, dict) and hello.get("source_hash") == self.source_hash and hello.get("run_dir") == self.run_dir
        if not reused:
            code = ("var __gpRunDir = " + json.dumps(self.run_dir) + ";\n"
                    + "var __gpSourceHash = " + json.dumps(self.source_hash) + ";\n"
                    + self._prelude + "\nJSON.stringify(__gp.describe());")
            hello = self._eval(code, timeout_ms=max(timeout_ms, 30000))
        return self._check_hello(hello), not reused

    def invoke(self, verb, payload=None):
        """请求经一处序列化，业务字符串、null、boolean 与 number 原样保留。"""
        request = {"protocol_major": PROTOCOL_MAJOR, "verb": verb, "payload": payload}
        result = self._eval("JSON.stringify(__gp.invoke(" + json.dumps(request, ensure_ascii=True) + "))")
        if not isinstance(result, dict) or isinstance(result.get("protocol_major"), bool) or not isinstance(result.get("protocol_major"), (int, float)) or result["protocol_major"] != PROTOCOL_MAJOR:
            raise ProbeError(f"客户端响应协议不兼容：{result!r}")
        if type(result.get("ok")) is not bool:
            raise ProbeError(f"客户端响应缺少 boolean ok：{result!r}")
        if not result["ok"]:
            raise ProbeError(f"客户端 {verb} 失败：{result.get('error')}")
        if "value" not in result:
            raise ProbeError(f"客户端 {verb} 响应缺少 value")
        return result["value"]
