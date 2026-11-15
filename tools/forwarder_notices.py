#!/usr/bin/env python3
"""Retain upstream notices for the pinned Android candidate, without packaging binaries.

The inventory conservatively covers tracked C/assembly/headers and Android makefiles
in the Android build roots, including files disabled by preprocessor configuration.
It is an attribution artifact, not an automatic legal clearance or link map.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BUILD_FILES = {'Android.mk', 'build.mk', 'configs.mk'}


def git(source, *args):
    return subprocess.check_output(['git', '-c', 'core.fsmonitor=false', '-C', str(source), *args])


def digest(data):
    return hashlib.sha256(data).hexdigest()


def leading_notice(text, makefile=False):
    """Keep complete leading comment blocks, including successive license blocks."""
    pattern = r'\s*(/\*.*?\*/|//[^\n]*(?:\n|$)' + (r'|\#[^\n]*(?:\n|$)' if makefile else '') + ')'
    offset, comments = 0, []
    while match := re.match(pattern, text[offset:], re.S):
        comments.append(match[1].strip())
        offset += match.end()
    return '\n'.join(comments)


def inventory(source, lock, supplements=()):
    """Validate the checkout and return deterministic notices plus file hashes."""
    source = source.resolve()
    components = {'': lock['revision'], **lock['submodules']}
    licenses = {}
    for relative, expected in lock['licenses'].items():
        path = source / relative
        if path.is_symlink() or not path.resolve().is_relative_to(source):
            raise ValueError('Unsafe license path: ' + relative)
        owner = max((part for part in components if not part or relative.startswith(part + '/')), key=len)
        entry = relative[len(owner) + 1:] if owner else relative
        # Hash repository blobs, so Windows checkout line endings cannot change attribution.
        data = git(source / owner, 'show', 'HEAD:' + entry)
        if digest(data) != expected:
            raise ValueError('License hash changed: ' + relative)
        licenses[relative] = data.decode('utf-8').replace('\r\n', '\n').strip()
    actual_submodules = {}
    for line in git(source, 'submodule', 'status', '--recursive').decode().splitlines():
        if not line.startswith(' '):
            raise ValueError('Unresolved or modified submodule')
        revision, path, *_ = line.split()
        actual_submodules[path] = revision
    if actual_submodules != lock['submodules']:
        raise ValueError('Dependency revision inventory changed')
    records, notices = [], {}
    for component, revision in sorted(components.items()):
        checkout = source / component
        if git(checkout, 'rev-parse', 'HEAD').decode().strip() != revision:
            raise ValueError('Wrong component revision: ' + component)
        if git(checkout, 'status', '--porcelain', '--untracked-files=all', '--ignored').strip():
            raise ValueError('Modified component: ' + component)
        for entry in sorted(git(checkout, 'ls-files', '-z').decode().split('\0')):
            if not entry:
                continue
            relative = Path(component, entry).as_posix()
            # Root src/core is itself a locked component; ls-files does not recurse into it.
            android_root = entry.startswith(('src/', 'include/'))
            if not ((android_root and Path(entry).suffix in {'.c', '.h', '.S'}) or entry in BUILD_FILES):
                continue
            path = source / relative
            if path.is_symlink() or not path.resolve().is_relative_to(source):
                raise ValueError('Unsafe source path: ' + relative)
            data = git(checkout, 'show', 'HEAD:' + entry)
            text = data.decode('utf-8').replace('\r\n', '\n')
            notice = leading_notice(text, Path(entry).name in BUILD_FILES)
            records.append({'path': relative, 'sha256': digest(data),
                            'notice_sha256': digest(notice.encode()) if notice else None,
                            'component': component or '.', 'revision': revision})
            if notice:
                notices.setdefault(notice, []).append(relative)
    if not records:
        raise ValueError('Empty Android source inventory')
    manifest = {'revision': lock['revision'], 'scope': 'conservative Android source/header/build-file inventory; not a linker map',
                'licenses': lock['licenses'], 'files': sorted(records, key=lambda r: r['path']),
                'supplements': list(supplements),
                'without_leading_comment': sorted(r['path'] for r in records if not r['notice_sha256'])}
    parts = ['ESuite forwarding candidate — retained third-party notices',
             'Pinned revision: ' + lock['revision'],
             'No native library is distributed by this audit. Retain all applicable notices when integrating.',
             'Files without a leading comment are listed in the manifest; component licenses still apply.']
    for name, content in sorted(licenses.items()):
        parts += ['\n=== Component license: ' + name + ' ===', content]
    for supplement in supplements:
        path = (ROOT / supplement['path']).resolve()
        if not path.is_relative_to(ROOT / 'native/licenses'):
            raise ValueError('Unsafe supplement path')
        data = path.read_bytes()
        if digest(data) != supplement['sha256']:
            raise ValueError('Supplement hash changed: ' + supplement['path'])
        parts += ['\n=== Retained upstream license: ' + supplement['path'] + ' ===',
                  supplement['url'], supplement['reason'], data.decode('utf-8').replace('\r\n', '\n').strip()]
    for notice, paths in sorted(notices.items(), key=lambda item: sorted(item[1])[0]):
        parts += ['\n=== Source notices ===', '\n'.join(sorted(paths)), notice]
    return manifest, '\n\n'.join(parts) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True, help='Clean recursively checked-out pinned tunnel repository')
    parser.add_argument('--output', type=Path, required=True, help='Directory for notice text and JSON inventory')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'native/forwarder-lock.json').read_text(encoding='utf-8'))
    supplements = json.loads((ROOT / 'native/notice-supplements.json').read_text(encoding='utf-8'))
    manifest, notices = inventory(args.source, lock, supplements)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'THIRD_PARTY_NOTICES.txt').write_text(notices, encoding='utf-8', newline='\n')
    (args.output / 'forwarder-source-inventory.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    print(f"Retained notices for {len(manifest['files'])} Android source/header/build files; "
          f"{len(manifest['without_leading_comment'])} rely on component attribution. No binaries packaged.")


if __name__ == '__main__':
    main()
