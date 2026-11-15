"""Exercise attribution extraction and fail-closed pin validation without an NDK."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from forwarder_notices import digest, inventory, leading_notice


class NoticeTests(unittest.TestCase):
    def test_successive_comments_preserve_full_licenses(self):
        text = '/* banner */\n/* Copyright A\nPermission B */\n// attribution C\nint x; /* not a leading notice */'
        self.assertEqual('/* banner */\n/* Copyright A\nPermission B */\n// attribution C', leading_notice(text))
        self.assertEqual('# Copyright A\n# Licensed under Apache-2.0', leading_notice('# Copyright A\n# Licensed under Apache-2.0\ninclude x', True))
        self.assertEqual('', leading_notice('#include <x.h>\n/* implementation */'))

    def test_deterministic_inventory_excludes_windows_and_rejects_changed_pins(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', '-c', 'core.fsmonitor=false', '-C', str(root), *args])
            git('init', '-q')
            for name, text in {'LICENSE': 'Permission notice', 'src/a.c': '/* Copyright A */\nint a;',
                               'src/no-comment.h': 'int b;', 'third-part/wintun/win.c': '/* Windows only */',
                               'Android.mk': '# Apache header\ninclude x'}.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding='utf-8')
            git('add', '.')
            git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')
            lock = {'revision': git('rev-parse', 'HEAD').decode().strip(), 'submodules': {},
                    'licenses': {'LICENSE': digest((root / 'LICENSE').read_bytes())}}
            first = inventory(root, lock)
            self.assertEqual(first, inventory(root, lock))
            self.assertEqual(['Android.mk', 'src/a.c', 'src/no-comment.h'], [r['path'] for r in first[0]['files']])
            self.assertEqual(['src/no-comment.h'], first[0]['without_leading_comment'])
            notice_path = 'native/licenses/libyaml-0.2.5.txt'
            supplement = {'path': notice_path, 'url': 'https://example.invalid/fixture', 'reason': 'Retain attribution',
                          'sha256': digest((Path(__file__).resolve().parents[1] / notice_path).read_bytes())}
            included = inventory(root, lock, [supplement])
            self.assertIn('Kirill Simonov', included[1])
            with self.assertRaisesRegex(ValueError, 'Supplement hash changed'):
                inventory(root, lock, [{**supplement, 'sha256': '0' * 64}])
            with self.assertRaisesRegex(ValueError, 'Unsafe supplement path'):
                inventory(root, lock, [{**supplement, 'path': '../outside.txt'}])
            untracked = root / 'src/unreviewed.c'
            untracked.write_text('int unreviewed;', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Modified component'):
                inventory(root, lock)
            untracked.unlink()
            (root / 'src/a.c').write_text('changed', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Modified component'):
                inventory(root, lock)
            lock['licenses']['LICENSE'] = '0' * 64
            with self.assertRaisesRegex(ValueError, 'License hash changed'):
                inventory(root, lock)


if __name__ == '__main__':
    unittest.main()
