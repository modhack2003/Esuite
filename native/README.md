# Forwarding candidate audit (CAP-01)

Selected candidate for isolated build evaluation: `heiher/hev-socks5-tunnel`, at the exact revision in [forwarder-lock.json](forwarder-lock.json). The Android wrapper `heiher/sockstun` at `df7b2a0342079b2abf3a7030f41e9b77a23def14` supplied the reference dependency tree. No upstream application, keystore, signing settings or native binary is integrated into ESuite.

The audit inspected the actual recursive checkout and Android.mk/build.mk files, rather than relying on GitHub's repository license badge. The build pulls the tunnel/core, task scheduler, lwIP and YAML sources. Each exact submodule revision and root license hash is locked. No moving branch is used for the tested source.

| Component | Observed license/notice requirements |
| --- | --- |
| Tunnel and SOCKS5 core | MIT; preserve copyright and permission notices |
| hev-task-system | MIT; preserve notices |
| heiher/yaml fork | MIT root license; preserve notices |
| heiher/lwip fork | BSD-3-Clause root; individual files also carry BSD and permissive CMU, ANU, Sun and other notices. Preserve the actual source notices and applicable acknowledgements in any binary distribution |
| Android makefiles | Apache-2.0 headers from the Android Open Source Project; retain notices/license on reuse |
| Windows-only Wintun | Header explicitly offers GPL-2.0 OR MIT; Windows files/DLLs are outside the Android build and are not copied or packaged |

The recursive C/header/assembly review found no GPL-only source notice. That observation is not a substitute for retaining all file-specific notices. Before packaging the stack, generate a complete third-party notice bundle from the compiled source inventory and review its attribution requirements. ESuite's own MIT license stays unchanged.

`python3 tools/verify_forwarder.py` uses Linux with `ANDROID_HOME` and NDK `29.0.14206865`. It checks exact dependency/license pins, builds only the shared library for API 28 on armeabi-v7a, arm64-v8a, x86 and x86_64, then repeats a clean build and compares stripped output hashes. Every ELF LOAD segment must have at least 16 KiB alignment. Run this check with the pinned NDK; actual results are recorded in VERIFICATION.md. Adding a dedicated CI workflow requires a GitHub credential with workflow scope. No generated native files are committed.

This is a build feasibility gate. It does not establish packet forwarding, UDP/DNS correctness, socket protection, connectivity, cancellation, TLS inspection or Android/OEM compatibility. The existing app still has no VPN capture service. Next: finish the distribution notice inventory, implement CAP-02's scoped loopback transport, then integrate the candidate with selected-app VpnService and controlled unrooted device fixtures under CAP-03. Do not introduce an empty TUN service.
