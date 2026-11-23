#!/usr/bin/env python3
"""Disposable-emulator UI and same-certificate upgrade checks for exact release bytes."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import urllib.request


def run(*args, timeout=600):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    print(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + args[0])
    return result.stdout


def instrument(method, expected):
    output = run('adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 method, 'dev.esuite.uitests/androidx.test.runner.AndroidJUnitRunner')
    Path('signed-ui-results').mkdir(exist_ok=True)
    subprocess.run(['adb', 'pull', '/sdcard/Android/data/dev.esuite.uitests/files', 'signed-ui-results/ui-evidence'], check=False, timeout=30)
    Path('signed-ui-results', method.split('.')[-1].replace('#', '-') + '.txt').write_text(output)
    # Assumption skips count toward the fixture inventory. Android's am can exit 0 on JUnit failure.
    statuses = re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', output, re.MULTILINE)
    completed = sum(code in {'0', '-3', '-4'} for code in statuses)
    if completed != expected or any(code in {'-1', '-2'} for code in statuses) or 'FAILURES!!!' in output or 'INSTRUMENTATION_CODE: -1' not in output:
        raise RuntimeError('Signed APK UI suite failed or incomplete: ' + method)


if os.environ.get('GITHUB_ACTIONS') != 'true' or run('adb', 'shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('Refusing destructive fixture setup outside a disposable Actions emulator')
apks = list(Path('release-assets').glob('ESuite-*.apk'))
harnesses = list(Path('signed-ui-harness').glob('*.apk'))
assert len(apks) == len(harnesses) == 1
run('adb', 'install', '-r', '-t', str(harnesses[0]))
base = Path('previous-production.apk')
# Immutable published baseline; verify bytes before installing. Android enforces signer continuity on -r.
url = 'https://github.com/modhack2003/Esuite/releases/download/v1.0.6/ESuite-1.0.6.apk'
with urllib.request.urlopen(url, timeout=60) as response:
    data = response.read(10 * 1024 * 1024)
assert hashlib.sha256(data).hexdigest() == 'bdbc125d391b07f226f49b55c1f17071b34c715a0fe3527c86d5b4c4b0c55f92'
base.write_bytes(data)
run('adb', 'uninstall', 'dev.esuite.app') if 'dev.esuite.app' in run('adb', 'shell', 'pm', 'list', 'packages', 'dev.esuite.app') else None
run('adb', 'install', str(base))
instrument('dev.esuite.uitests.UpgradeUiTest#seedPreviousRelease', 1)
# No uninstall or pm clear is allowed between seeding and verification.
run('adb', 'install', '-r', str(apks[0]))
instrument('dev.esuite.uitests.UpgradeUiTest#verifyUpgradeRetainsEncryptedWorkspace', 1)
instrument('dev.esuite.uitests.ProductionUiTest', 4)
print('PASS: exact production-signed APK UI and v1.0.6 same-signer encrypted-data upgrade')
