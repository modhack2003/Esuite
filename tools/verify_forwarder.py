#!/usr/bin/env python3
"""Build an isolated, pinned candidate; never package it or start a VPN."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'native/forwarder-lock.json').read_text())


def run(*args, cwd=None):
    try:
        return subprocess.check_output(args, cwd=cwd, text=True, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as error:
        print(error.output)
        raise


def verify():
    sdk = Path(os.environ['ANDROID_HOME'])
    ndk = sdk / 'ndk' / LOCK['ndk']
    if not (ndk / 'ndk-build').is_file():
        raise RuntimeError('Install the pinned Android NDK before running this Linux build check')
    if 'Pkg.Revision = ' + LOCK['ndk'] not in (ndk / 'source.properties').read_text():
        raise RuntimeError('Android NDK revision does not match lock')
    with tempfile.TemporaryDirectory(prefix='esuite-forwarder-') as temporary:
        work = Path(temporary)
        source = work / 'source'
        run('git', 'clone', '--no-checkout', LOCK['url'], str(source))
        run('git', 'fetch', 'origin', LOCK['revision'], cwd=source)
        run('git', 'checkout', '--detach', LOCK['revision'], cwd=source)
        run('git', 'submodule', 'update', '--init', '--recursive', cwd=source)
        actual = {}
        for line in run('git', 'submodule', 'status', '--recursive', cwd=source).splitlines():
            if not line.startswith(' '):
                raise RuntimeError('Unresolved or modified submodule')
            revision, path, *_ = line.split()
            actual[path] = revision
        if actual != LOCK['submodules']:
            raise RuntimeError('Dependency revision inventory changed')
        for path, expected in LOCK['licenses'].items():
            if hashlib.sha256((source / path).read_bytes()).hexdigest() != expected:
                raise RuntimeError('License changed: ' + path)
        # Build only the Android shared library and its static dependencies.
        # No upstream app, signing configuration, Windows DLL or VPN service is copied.
        app = work / 'Application.mk'
        app.write_text('APP_PLATFORM := android-28\nAPP_OPTIM := release\n'
                       'APP_ABI := armeabi-v7a arm64-v8a x86 x86_64\n'
                       'APP_MODULES := hev-socks5-tunnel\n'
                       'APP_CFLAGS := -O2 -ffile-prefix-map=' + str(work) + '=.\n'
                       'APP_LDFLAGS := -Wl,--build-id=none\n')
        outputs = []
        for index in range(2):
            output = work / f'build-{index}'
            try:
                log = run(str(ndk / 'ndk-build'), '-j2', 'NDK_PROJECT_PATH=null',
                          'APP_BUILD_SCRIPT=' + str(source / 'Android.mk'),
                          'NDK_APPLICATION_MK=' + str(app),
                          'NDK_OUT=' + str(output / 'obj'), 'NDK_LIBS_OUT=' + str(output / 'lib'),
                          'REV_ID=' + LOCK['revision'], cwd=source)
            except subprocess.CalledProcessError as error:
                print(error.output)
                raise
            (work / f'build-{index}.log').write_text(log)
            libraries = {str(p.relative_to(output / 'lib')): p for p in (output / 'lib').rglob('*.so')}
            expected = {abi + '/libhev-socks5-tunnel.so' for abi in ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')}
            if set(libraries) != expected:
                raise RuntimeError('Unexpected Android library inventory')
            outputs.append({name: hashlib.sha256(p.read_bytes()).hexdigest() for name, p in libraries.items()})
            readelf = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
            for name, library in libraries.items():
                loads = [line.split() for line in run(str(readelf), '-lW', str(library)).splitlines() if line.strip().startswith('LOAD ')]
                if not loads or any(int(fields[-1], 16) < 16384 for fields in loads):
                    raise RuntimeError('ELF load alignment below 16 KiB: ' + name)
        if outputs[0] != outputs[1]:
            raise RuntimeError('Clean native rebuilds differ')
        print(json.dumps({'revision': LOCK['revision'], 'api': 28, 'sha256': outputs[0],
                          'clean_rebuilds_equal': True, 'elf_load_alignment': '16 KiB or larger'}, indent=2))
        print('PASS: isolated Android candidate build and license pins; no runtime forwarding verified')


if __name__ == '__main__':
    verify()
