"""GUI 用例与语言的离线读取；文档校验和运行驱动消费同一份输入。"""
import json
from pathlib import Path

from case_contract import ContractError, check_local_handlers, validate_case


def load_lang(root: Path) -> dict:
    """期望文案只从指定仓库的生产与开发语言文件读取。"""
    directory = root / "common/src/main/resources/assets/itemdespawntowhat/lang"
    development = root / "common/src/dev/resources/assets/idtw_probe/lang"
    langs = {}
    for name in ("en_us", "zh_cn"):
        langs[name] = json.loads((directory / (name + ".json")).read_text(encoding="utf-8"))
        langs[name].update(json.loads((development / (name + ".json")).read_text(encoding="utf-8")))
    return langs


def load_cases(root: Path, cases_dir: Path, only: list[str]) -> list[dict]:
    """读取和选择用例，并按同一仓库的语言与本地 handler 契约校验。"""
    cases = []
    for path in sorted(cases_dir.iterdir()):
        if path.suffix != ".json":
            continue
        case = json.loads(path.read_text(encoding="utf-8"))
        case.setdefault("name", path.stem)
        if not only or case["name"] in only:
            cases.append(case)
    missing = set(only) - {case["name"] for case in cases}
    if missing:
        raise ContractError("未知 --only: " + ", ".join(sorted(missing)))
    check_local_handlers(root / "tools/gui-probes/lib/prelude.js")
    langs = load_lang(root)
    for case in cases:
        validate_case(case, langs)
    return cases
