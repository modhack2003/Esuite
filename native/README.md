# Forwarding candidate audit (CAP-01)

Selected candidate for isolated build evaluation: `heiher/hev-socks5-tunnel`, at the exact revision in [forwarder-lock.json](forwarder-lock.json). The Android wrapper `heiher/sockstun` at `df7b2a0342079b2abf3a7030f41e9b77a23def14` supplied the reference dependency tree. No upstream application, keystore, signing settings or native binary is integrated into ESuite.

The audit inspected the actual recursive checkout and Android.mk/build.mk files, rather than relying on GitHub's repository license badge. The build pulls the tunnel/core, task scheduler, lwIP and YAML sources. Each exact submodule revision and root license hash is locked. No moving branch is used for the tested source.

| Component | Observed license/notice requirements |
| --- | --- |
| Tunnel and SOCKS5 core | MIT; preserve copyright and permission notices |
| hev-task-system | MIT; preserve notices |
| heiher/yaml fork and original libyaml | Retain both MIT notices: the fork notice plus the original Ingy döt Net / Kirill Simonov permission text. Seven files match the pinned original source; see notices/libyaml-provenance.json |
| heiher/lwip fork | BSD-3-Clause root; individual files also carry BSD and permissive CMU, ANU, Sun and other notices. Preserve the actual source notices and applicable acknowledgements in any binary distribution |
| Android makefiles | Apache-2.0 headers from the Android Open Source Project; retain notices/license on reuse |
| Windows-only Wintun | Header explicitly offers GPL-2.0 OR MIT; Windows files/DLLs are outside the Android build and are not copied or packaged |

The recursive C/header/assembly review found no GPL-only source notice. That observation is not a substitute for retaining all file-specific notices. A conservative 450-file source/header/build inventory and retained notice bundle are now generated under `native/notices`. This includes preprocessor-disabled files and is not an exact linker map. Review the component attribution and 12 files without leading comments before packaging; do not treat extraction as legal clearance. ESuite's own MIT license stays unchanged.

`python3 tools/verify_forwarder.py` uses Linux with `ANDROID_HOME` and NDK `29.0.14206865`. It checks exact dependency/license pins, builds only the shared library for API 28 on armeabi-v7a, arm64-v8a, x86 and x86_64, then repeats a clean build and compares stripped output hashes. Every ELF LOAD segment must have at least 16 KiB alignment. Run this check with the pinned NDK; actual results are recorded in VERIFICATION.md. Adding a dedicated CI workflow requires a GitHub credential with workflow scope. No generated native files are committed.

This is a build feasibility gate. It does not establish packet forwarding, UDP/DNS correctness, socket protection, connectivity, cancellation, TLS inspection or Android/OEM compatibility. The existing app still has no VPN capture service. Next: finish the distribution notice inventory, implement CAP-02's scoped loopback transport, then integrate the candidate with selected-app VpnService and controlled unrooted device fixtures under CAP-03. Do not introduce an empty TUN service.

## Direct Linux build evidence — 15 November 2026

The isolated build passed on the connected Linux device using the official NDK r29 archive. Its SHA-1 matched Google's SDK repository manifest (`87e2bb7e9be5d6a1c6cdf5ec40dd4e0c6d07c30b`). Two clean stripped builds matched for all four ABIs, targeting API 28; every ELF LOAD alignment was at least 16 KiB. This proves build feasibility only. A subsequent repeat's result could not be retrieved after the device connection timed out.

| ABI | Shared-library SHA-256 |
| --- | --- |
| armeabi-v7a | `4ff5175660535c237dce5b898ac89b2182ea6f7720a1f8728eb01ca66fce924f` |
| arm64-v8a | `88265a62d80e2aeb380ab40c53a74daa1a2d8eabf63d0c025b6d0498dba77461` |
| x86 | `ef7249d3fd05f44f72e6775e1b3712d6012878cc71bf9a1ccf52e5299696cc28` |
| x86_64 | `22971a01062911941825ebc724e6f919c4c330b963b4ba9ead26c8a0039066fc` |

## Reproducible attribution inventory

Run `python tools/forwarder_notices.py --source <clean-pinned-checkout> --output native/notices` after recursively checking out the exact lock revisions. The tool reads canonical Git blobs so Windows line endings cannot alter hashes. It rejects changed component revisions, tracked edits, untracked/ignored checkout content and changed notice supplements. `native/notice-supplements.json` pins additional original notices; no binary is copied.

`native/notices/libyaml-provenance.json` records seven files whose exact Git bytes match upstream libyaml revision `2c891fc7a770e8ba2fec34fc6b545c672beb37e6` (0.2.5). Their original copyright/permission notice is retained in addition to the fork's root notice. The supplemental Apache-2.0 text preserves the complete terms referenced by Android build-file headers. The manifest explicitly lists sources without leading comments, rather than treating absence of a header as public domain.

Focused checks: `python -m unittest discover -s tools -p test_forwarder_notices.py -v`. These exercise complete successive comment retention, deterministic inventory, source/supplement pin rejection, unsafe supplement paths and rejection of untracked source that a wildcard build could otherwise consume. This work does not integrate the library, establish packet capture, or replace the remaining distribution review.
