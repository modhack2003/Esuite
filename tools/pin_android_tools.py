#!/usr/bin/env python3
"""Keep emulator-runner's hard-coded cmdline-tools/latest on our pinned SDK tools.

Only used on disposable GitHub-hosted runners. The legacy directory is preserved
in RUNNER_TEMP; no tools or user files are deleted.
"""
import os
from pathlib import Path
import re
import shutil


def pin(sdk, selected, temporary):
    sdk, selected, temporary = Path(sdk).resolve(), Path(selected).resolve(), Path(temporary).resolve()
    tool_root = sdk / 'cmdline-tools'
    if selected.parent != tool_root or not (selected / 'bin/avdmanager').is_file():
        raise RuntimeError('Selected command-line tools must be a direct SDK cmdline-tools child')
    properties = (selected / 'source.properties').read_text()
    match = re.search(r'^\s*Pkg.Revision\s*=\s*([^\r\n]+)', properties, re.MULTILINE)
    if not match or match[1].strip() not in ('22.0', '22.0.0'):
        raise RuntimeError('Expected official Command-line Tools 22.0, refusing to change SDK alias')
    latest = tool_root / 'latest'
    if latest.resolve() == selected:
        print('SDK latest already resolves to pinned Command-line Tools 22.0')
        return
    backup = temporary / 'esuite-original-cmdline-tools'
    if latest.exists() or latest.is_symlink():
        if backup.exists() or backup.is_symlink():
            raise RuntimeError('Previous SDK alias backup exists; refusing to overwrite it')
        latest.rename(backup)
    latest.symlink_to(selected, target_is_directory=True)
    print('SDK latest now resolves to pinned Command-line Tools 22.0:', selected)


if __name__ == '__main__':
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('This SDK alias adjustment is restricted to disposable GitHub Actions runners')
    executable = shutil.which('avdmanager')
    if not executable:
        raise SystemExit('avdmanager was not found after SDK setup')
    pin(os.environ['ANDROID_HOME'], Path(executable).resolve().parents[1], os.environ['RUNNER_TEMP'])
