#!/usr/bin/env python3
"""Integration-check publication guards using disposable keys, never a production signer."""
import importlib.util
import os
from pathlib import Path
import re
import secrets
import json
import shutil
import subprocess
import tempfile

spec = importlib.util.spec_from_file_location('release_guard', Path(__file__).with_name('verify_release_apk.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
sdk = Path(os.environ['ANDROID_HOME'])
tools = sdk / 'build-tools' / '36.0.0'
unsigned = Path('app/build/outputs/apk/release/app-release-unsigned.apk').resolve()
properties = dict(line.split('=', 1) for line in Path('release/version.properties').read_text().splitlines() if '=' in line)
version, code = properties['versionName'], properties['versionCode']


def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def rejected(action, description):
    try:
        action()
    except (ValueError, subprocess.CalledProcessError):
        print('Rejected:', description)
    else:
        raise AssertionError('Publication guard accepted ' + description)


with tempfile.TemporaryDirectory(prefix='esuite-signature-test-') as directory:
    private = Path(directory)
    os.chmod(private, 0o700)
    os.environ['ESUITE_FIXTURE_PASSWORD'] = secrets.token_urlsafe(32)
    keystore, signed = private / 'fixture.jks', private / 'fixture.apk'
    command('keytool', '-genkeypair', '-keystore', str(keystore), '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '30', '-dname', 'CN=ESuite CI verification only', '-storepass:env', 'ESUITE_FIXTURE_PASSWORD', '-keypass:env', 'ESUITE_FIXTURE_PASSWORD', '-noprompt')
    command(str(tools / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', 'fixture', '--ks-pass', 'env:ESUITE_FIXTURE_PASSWORD', '--key-pass', 'env:ESUITE_FIXTURE_PASSWORD', '--out', str(signed), str(unsigned))
    signature = command(str(tools / 'apksigner'), 'verify', '--print-certs', str(signed))
    pin = re.search(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)[1]
    metadata = guard.inspect(signed, sdk, pin, version, code)
    assert metadata['debuggable'] is False
    ci = Path('app/build/outputs/apk/ci'); ci.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(signed, ci / 'app-release-ci.apk')
    metadata.update(ciOnly=True, description='Disposable fixture signer; never distribute as production')
    (ci / 'build-info-ci.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print('Accepted correctly signed, pinned, optimized APK')
    rejected(lambda: guard.inspect(signed, sdk, '0' * 64, version, code), 'wrong certificate pin')
    rejected(lambda: guard.inspect(signed, sdk, pin, '99.99.99', code), 'wrong package version')
    rejected(lambda: guard.inspect(unsigned, sdk, pin, version, code), 'unsigned APK')
    debug = Path('app/build/outputs/apk/debug/app-debug.apk')
    rejected(lambda: guard.inspect(debug, sdk, pin, version, code), 'debug APK')
    data = bytearray(signed.read_bytes()); data[100] ^= 1
    corrupted = private / 'corrupted.apk'; corrupted.write_bytes(data)
    rejected(lambda: guard.inspect(corrupted, sdk, pin, version, code), 'tampered APK')
    del os.environ['ESUITE_FIXTURE_PASSWORD']
print('All release guard integration checks passed')
