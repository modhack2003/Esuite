#!/usr/bin/env python3
"""Platform fixtures, then an independent UI harness against the optimized APK.

Only fresh, disposable CI emulators are supported: debug fixture data is erased
between phases. No production key is generated or used by this test runner.
"""
from pathlib import Path
import atexit
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

out = Path('native-test-results'); out.mkdir(exist_ok=True)
root = ET.Element('testsuite', name='ESuiteAndroidDevice')
started = time.monotonic()


def diagnostics():
    for filename, args in [('crash.txt', ['adb', 'logcat', '-d', '-b', 'crash']),
                           ('package.txt', ['adb', 'shell', 'dumpsys', 'package', 'dev.esuite.app'])]:
        try:
            result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
            (out / filename).write_text(result.stdout)
            if filename == 'crash.txt' and result.stdout.strip():
                print('Device crash diagnostics:\n' + result.stdout[-20000:])
        except Exception as error:
            print('Diagnostic capture failed:', error)
    try:
        result = subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20)
        if result.returncode == 0:
            (out / 'device.png').write_bytes(result.stdout)
    except Exception:
        pass


atexit.register(diagnostics)


def command(*args):
    try:
        return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT, timeout=240)
    except subprocess.CalledProcessError as error:
        print(error.output)
        raise


def unique(pattern):
    matches = list(Path('ci-apks').glob(pattern))
    if len(matches) != 1:
        raise RuntimeError(f'Expected exactly one APK for {pattern}, got {len(matches)}')
    return str(matches[0])


def suite(component, name, minimum, test_class=None):
    args = ['adb', 'shell', 'am', 'instrument', '-w', '-r']
    if test_class:
        args += ['-e', 'class', test_class]
    args.append(component)
    result = subprocess.run(args,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
    print(result.stdout)
    (out / (name + '.txt')).write_text(result.stdout)
    status, last_key, count = {}, None, 0
    for line in result.stdout.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, _, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            status[key] = value; last_key = key
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.rsplit(' ', 1)[1])
            if code in (0, -1, -2, -3, -4) and status.get('class') and status.get('test'):
                case = ET.SubElement(root, 'testcase', classname=status['class'], name=status['test'])
                count += 1
                if code in (-3, -4):
                    ET.SubElement(case, 'skipped', message='Unsupported WebView capability or assumption').text = status.get('stack', '')
                elif code in (-1, -2):
                    ET.SubElement(case, 'failure', message=status.get('stack', 'Android test failed')).text = status.get('stack', '')
            status, last_key = {}, None
        elif last_key and not line.startswith('INSTRUMENTATION_'):
            status[last_key] += '\n' + line
    if result.returncode != 0 or 'INSTRUMENTATION_CODE: -1' not in result.stdout or count < minimum:
        case = ET.SubElement(root, 'testcase', classname='Instrumentation', name=name + 'Completed')
        ET.SubElement(case, 'failure', message='Runner crashed or test inventory incomplete').text = result.stdout


try:
    if os.environ.get('GITHUB_ACTIONS') != 'true' or command('adb', 'shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
        raise RuntimeError('Refusing destructive fixture setup outside a disposable Actions emulator')
    command('adb', 'logcat', '-c')
    command('adb', 'install', '-r', '-t', unique('**/app/build/outputs/apk/debug/app-debug.apk'))
    command('adb', 'install', '-r', '-t', unique('**/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    suite('dev.esuite.app.test/androidx.test.runner.AndroidJUnitRunner', 'PlatformBoundaries', 2)
    # Delete disposable fixture data, then test the actual optimized release code.
    command('adb', 'uninstall', 'dev.esuite.app')
    command('adb', 'install', unique('**/app/build/outputs/apk/ci/app-release-ci.apk'))
    command('adb', 'install', '-r', '-t', unique('**/ui-tests/build/outputs/apk/debug/ui-tests-debug.apk'))
    suite('dev.esuite.uitests/androidx.test.runner.AndroidJUnitRunner', 'OptimizedUi', 4, 'dev.esuite.uitests.ProductionUiTest')
    subprocess.run(['adb', 'pull', '/sdcard/Android/data/dev.esuite.uitests/files', str(out / 'ui-evidence')], check=False, timeout=30)
except Exception as error:
    case = ET.SubElement(root, 'testcase', classname='Instrumentation', name='setupAndExecution')
    ET.SubElement(case, 'failure', message=str(error)).text = repr(error)
    print('Device execution failure:', error)

cases = root.findall('testcase')
failures = len(root.findall('./testcase/failure'))
root.set('tests', str(len(cases))); root.set('failures', str(failures)); root.set('errors', '0')
root.set('skipped', str(len(root.findall('./testcase/skipped'))))
root.set('time', f'{time.monotonic()-started:.3f}')
ET.ElementTree(root).write(out / 'TEST-device.xml', encoding='utf-8', xml_declaration=True)
print('Native test evidence:', root.attrib)
sys.exit(1 if failures else 0)
