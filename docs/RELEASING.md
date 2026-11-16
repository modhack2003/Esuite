# Production APK delivery

The requested distribution is an installable, optimized, privately signed APK in GitHub Releases. An unsigned APK or CI validation signer is never a production release. Version intent is recorded in `release/version.properties`. [ESuite 1.0.2/code 5 is published](https://github.com/modhack2003/Esuite/releases/tag/v1.0.2) from source `2fc166dca9d2fa1637f4e8ef15e51040d3cabe5b`; [release workflow](https://github.com/modhack2003/Esuite/actions/runs/43833769184) and [full CI](https://github.com/modhack2003/Esuite/actions/runs/43831951202) passed. The existing signer is retained, and install-over-1.0.1 tests retained encrypted evidence on API 28/35/37.0. Public assets were independently downloaded and verified against the signed artifact, checksums, certificate and metadata. Reuse the configured signer for subsequent versions.

## Build contract

- Android 9–17: minimum API 28, target API 37, compile platform 37.0; no maximum SDK.
- AGP 9.2.1, Gradle 9.4.1 with distribution checksum, JDK 17 and built-in Kotlin 2.2.10. Command-line Tools 22.0/build 15859902 is pinned, including emulator-runner's cmdline-tools/latest alias, so API 37.0 AVD metadata is not truncated to android-0. The old runner SDK alias is preserved in RUNNER_TEMP, and this adjustment never runs on a user device.
- `release`: R8 optimization, resource shrinking, non-debuggable, production signing only when all signing inputs are configured.
- Internal platform fixtures run in the debug app. A separate `ui-tests` APK drives the actual optimized release APK with a disposable CI signer, without linking app classes. The test harness, debug app and CI-signed APK are never production release assets.
- Runtime remains HTTPS-only with system certificate trust, no telemetry, no root requirement and no automatic target requests.

## One-time signing setup (account owner)

A durable signing key is required to install future versions over an existing production installation. Retain an encrypted offline backup outside GitHub and this repository. Do not create a replacement key for every release. If a production signer already exists, reuse it.

Create or use a private JKS/PKCS12 keystore with an RSA key of at least 3072 bits and long validity, using the official JDK `keytool`. Use its interactive password prompts or environment-password options; never commit passwords, key material or local signing files. Verify your backup before relying on the first release.

Configure these **repository Actions secrets** in GitHub Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `ESUITE_KEYSTORE_BASE64` | Base64 of the private keystore, one line |
| `ESUITE_STORE_PASSWORD` | Keystore password |
| `ESUITE_KEY_ALIAS` | Signing key alias |
| `ESUITE_KEY_PASSWORD` | Signing key password |

Configure repository **Actions variable** `ESUITE_CERT_SHA256` with the signing certificate SHA-256 fingerprint: 64 hexadecimal characters, no colons. Obtain it with `keytool -list -v` from the same keystore. This public pin prevents an accidental signer change. Record the public fingerprint in this document once configured; keep the private keystore/password out of chat, source, logs and artifacts.

**Signing status:** configured and verified by the repository owner through authenticated GitHub CLI. All four Actions secrets and the public certificate pin exist. A private PKCS12 keystore and verified local recovery copy are retained on the owner's device; no private signing material is committed or uploaded as an artifact. The owner must retain an offline backup with the password stored separately.

Production certificate SHA-256:

```text
55800cce75d814b64a1f9663463da2f0521cd0da55541ff56449f5a94fb88e12
```

CI uses Ubuntu 24.04 explicitly: ubuntu-latest moved to 26.04 and the pinned Playwright 1.51 verification package does not support that host. Signing credentials are exposed only to the restore/build steps after build-tool setup, and the temporary keystore is removed on exit.

## Automated path

1. A push to main runs Build and verify: JVM tests, release/debug lint, optimized assembly, publication guard integration fixtures, design checks, platform boundaries and independent optimized-APK UI checks on API 28, 29, 30, 31, 32, 33, 34, 35, 36 and 37.0. Newer System WebView capabilities are reported separately from operating-system compatibility; unsupported profile tests may skip, which must be disclosed.
2. Signed production release is triggered only after successful main-branch push CI. Manual Run workflow also requires successful CI for that exact commit. Pull-request runs cannot access signing material through this workflow.
3. The signing job restores the durable key to a restricted temporary file, builds the production APK, verifies its certificate pin, a valid v2 or v3 APK signature, package/version/SDK metadata, non-debuggable flag, zip alignment and 64-bit native ELF segment compatibility with 16 KiB pages, then removes the temporary key even on failure.
4. Separate jobs install/relaunch the exact signed APK on API 28, 35 and 37.0. They install the checksum-pinned previous production release, create and read back an encrypted observation after process restart, install the new APK with `adb install -r` without clearing data, and verify the retained project/observation. Fresh-fixture API, browser (where supported) and persistence UI checks then run against those exact signed bytes. These jobs never receive signing credentials.
5. The publication job receives only verified assets, checks SHA-256, creates a draft, uploads APK/metadata/checksums/notes, then publishes a normal stable release. Existing published versions are never replaced. A failed upload/test cannot publish the draft.

After configuring secrets, run the release workflow on main or make a coherent main-branch change. Subsequent releases increment **both** versionCode and versionName and update release/notes.md. The build-info file records exact source and certificate; CI reports record what was actually tested. Preserve signing identity across updates. Before the next release, update the previous-version URL/SHA in tools/verify_signed_ui.py and the baseline scroll behavior in UpgradeUiTest to match the latest published production APK. Check the download, checksum, certificate and an upgrade over the previous release before declaring delivery complete.

## Readiness and remaining product scope

A production build configuration does not complete all planned Burp-style capabilities. Full proxy/VPN/TLS interception, direct cloud-account audits, richer protocol tooling, physical/OEM/accessibility validation and several data-lifecycle checks remain in AGENT_HANDOFF.md. Do not erase those tasks or infer scanner parity from a release name. This release's notes explicitly describe the implemented web/API workbench subset.
