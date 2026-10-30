#!/usr/bin/env python3
"""核对各平台发布 jar（含 sources/javadoc）未携带开发探针。"""
from pathlib import Path
import json
import sys
from zipfile import ZipFile


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors: list[str] = []
    checked = 0
    properties = dict(line.strip().split('=', 1) for line in (root / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    version = properties['version']
    for module in ('common', 'fabric', 'neoforge'):
        prefix = f"{properties['mod_id']}-{module}-{properties['minecraft_version']}-{version}"
        jars = [root / module / 'build/libs' / (prefix + suffix + '.jar') for suffix in ('', '-sources', '-javadoc')]
        for path in jars:
            if not path.is_file():
                errors.append(f'{module}: missing {path.name} (run build first)')
                continue
            checked += 1
            with ZipFile(path) as jar:
                for name in jar.namelist():
                    if any(token in name for token in ('/core/debug/', '/client/debug/', 'idtw-debug/', '/probe/', 'idtw_probe', 'ProbeLedgerAccess', 'BackendWorldProbe')):
                        errors.append(f'{path.name}: {name}')
                    if name.endswith('/lang/en_us.json') or name.endswith('/lang/zh_cn.json'):
                        if any(key.startswith(('itemdespawntowhat.command.debug.', 'gui.itemdespawntowhat.debug.workbench.', 'gui.itemdespawntowhat.edit.preview.examples.')) for key in json.loads(jar.read(name))):
                            errors.append(f'{path.name}: development translations in {name}')
    for error in errors:
        print(f'[probe-packaging] FAIL {error}')
    print(f'[probe-packaging] {checked} jars, {len(errors)} violations')
    return int(bool(errors))


if __name__ == '__main__':
    sys.exit(main())
