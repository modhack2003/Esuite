#!/usr/bin/env python3
"""Run the exact optimized validation artifacts once per OS, preserving JUnit evidence."""
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT, timeout=240)


def unique(pattern):
    matches = list(Path('ci-apks').glob(pattern))
    if len(matches) != 1:
        raise RuntimeError(f'Expected exactly one APK for {pattern}, got {len(matches)}')
    return str(matches[0])


command('adb', 'install', unique('**/validation/app-validation.apk'))
command('adb', 'install', unique('**/validation/app-validation-androidTest.apk'))
started = time.monotonic()
result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-r',
                         'dev.esuite.app.validation.test/androidx.test.runner.AndroidJUnitRunner'],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=240)
print(result.stdout)
root = ET.Element('testsuite', name='ESuiteOptimizedDevice', time=f'{time.monotonic()-started:.3f}')
status, last_key = {}, None
for line in result.stdout.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, _, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
        status[key] = value; last_key = key
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.rsplit(' ', 1)[1])
        if code in (0, -1, -2, -3, -4) and status.get('class') and status.get('test'):
            case = ET.SubElement(root, 'testcase', classname=status['class'], name=status['test'])
            if code in (-3, -4):
                ET.SubElement(case, 'skipped', message='Unsupported WebView capability or assumption').text = status.get('stack', '')
            elif code in (-1, -2):
                ET.SubElement(case, 'failure', message=status.get('stack', 'Android test failed')).text = status.get('stack', '')
        status, last_key = {}, None
    elif last_key and not line.startswith('INSTRUMENTATION_'):
        status[last_key] += '\n' + line
cases = list(root)
# No test runner crash, empty output, partial execution or new tests disappearing silently.
protocol_ok = result.returncode == 0 and 'INSTRUMENTATION_CODE: -1' in result.stdout and len(cases) >= 3
if not protocol_ok:
    case = ET.SubElement(root, 'testcase', classname='Instrumentation', name='runnerCompleted')
    ET.SubElement(case, 'failure', message='Runner crashed, incomplete test inventory or protocol failure').text = result.stdout
failures = len(root.findall('./testcase/failure'))
root.set('tests', str(len(root)))
root.set('failures', str(failures)); root.set('errors', '0')
root.set('skipped', str(len(root.findall('./testcase/skipped'))))
ET.SubElement(root, 'system-out').text = result.stdout
out = Path('native-test-results'); out.mkdir(exist_ok=True)
ET.ElementTree(root).write(out / 'TEST-device.xml', encoding='utf-8', xml_declaration=True)
print('Native test evidence:', root.attrib)
sys.exit(1 if failures else 0)
