"""从工具位置定位仓库，显式目录可用于隔离的校验输入。"""
import os
from pathlib import Path

EXCLUDED_DIRS = frozenset({
    "build", ".gradle", ".git", ".local", ".scratch", "node_modules",
    "run", "runs", "logs", ".idea", ".agents", ".agent-teams",
})


def collect_files(root: Path, suffix: str) -> list[Path]:
    """按统一源码范围收集并排序；构建产物由领域入口显式读取。"""
    paths = []
    for directory, children, files in os.walk(root):
        children[:] = [name for name in children if name not in EXCLUDED_DIRS]
        paths.extend(Path(directory) / name for name in files if name.endswith(suffix))
    return sorted(paths)


def repo_root(explicit: str | Path | None = None, *, anchor: Path | None = None) -> Path:
    """显式根优先；默认按仓库标识寻找，调用者工作目录不参与默认定位。"""
    if explicit is not None:
        root = Path(explicit).resolve()
        if not root.is_dir():
            raise ValueError(f"根目录不存在：{root}")
        return root
    start = (anchor or Path(__file__)).resolve()
    if not start.exists():
        raise ValueError(f"工具位置不存在：{start}")
    start = start if start.is_dir() else start.parent
    for candidate in (start, *start.parents):
        if ((candidate / "settings.gradle").is_file()
                and (candidate / "gradle.properties").is_file()
                and (candidate / "tools").is_dir()):
            return candidate
    raise ValueError(f"从工具位置未找到仓库：{start}")
