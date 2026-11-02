#!/usr/bin/env python3
"""核对各平台发布 jar（含 sources/javadoc）未携带开发探针。"""
from pathlib import Path
import json
import sys
from zipfile import BadZipFile, ZipFile

from toolslib.checks import CheckResult, Finding, run


def check(root: Path) -> CheckResult:
    errors: list[str] = []
    checked = 0
    properties = dict(line.strip().split('=', 1) for line in (root / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    required = {'version', 'mod_id', 'minecraft_version'}
    if missing := required - properties.keys():
        raise ValueError('gradle.properties 缺少属性：' + ', '.join(sorted(missing)))
    version = properties['version']
    missing_jars = []
    for module in ('common', 'fabric', 'neoforge'):
        prefix = f"{properties['mod_id']}-{module}-{properties['minecraft_version']}-{version}"
        jars = [root / module / 'build/libs' / (prefix + suffix + '.jar') for suffix in ('', '-sources', '-javadoc')]
        for path in jars:
            if not path.is_file():
                missing_jars.append(str(path))
                continue
            checked += 1
            try:
                jar = ZipFile(path)
            except BadZipFile as failure:
                raise ValueError(f'损坏的 jar：{path}') from failure
            with jar:
                for name in jar.namelist():
                    if any(token in name for token in ('/core/debug/', '/client/debug/', 'idtw-debug/', '/probe/', 'idtw_probe', 'ProbeLedgerAccess', 'BackendWorldProbe')):
                        errors.append(f'{path.name}: {name}')
                    if name.endswith('/lang/en_us.json') or name.endswith('/lang/zh_cn.json'):
                        if any(key.startswith(('itemdespawntowhat.command.debug.', 'gui.itemdespawntowhat.debug.workbench.', 'gui.itemdespawntowhat.edit.preview.examples.')) for key in json.loads(jar.read(name))):
                            errors.append(f'{path.name}: development translations in {name}')
    if missing_jars:
        raise ValueError('缺少发布 jar，请先运行 build：' + '；'.join(missing_jars))
    return CheckResult(
        [Finding('发布 jar', '开发内容泄漏', error) for error in errors],
        [f'检查发布 jar：{checked} 个'],
        '发布 jar 开发内容隔离检查通过：0 处违规。',
        '将开发类型、资源和语言 key 留在开发源集。',
    )


def main() -> int:
    return run(check, __doc__)


if __name__ == '__main__':
    sys.exit(main())
