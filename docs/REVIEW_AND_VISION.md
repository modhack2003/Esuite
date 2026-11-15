# ESuite — Technical Review & Extraordinary Vision

Reviewer pass: 15 November 2026. Scope: product docs plus actual Kotlin source under `app/src/main`. This is an engineering review and a forward-looking ideas document, not a claim of implemented functionality. It does not change behavior or branding.

---

## Part 1 — What ESuite is today

An on-device, unrooted, phone-only web/API security workbench. The honest current reality:

- A scoped OkHttp request engine (`transport/ApiEngine.kt`) with per-project/per-identity in-memory cookie jars, no redirects/retries, bounded request/response bytes, and A/B identity comparison.
- A native WebView inspector (DOM/CSS edit, console, storage, native cookies, element selection) that records **request metadata only** — no response bodies.
- A Keystore-backed AES-GCM encrypted local workspace (`storage/`), bounded history, sanitized exports.
- Bounded parameter runner, saved-request sequences with first-response token extraction, and cURL/OpenAPI/HAR/AWS-JSON imports.

What is explicitly **not** built yet: the defining capability set — proxy interception, selected-app VPN capture, and local-CA TLS interception (handoff tasks CAP-01..04, all P0, all open). Also absent: active scanner, site map, full Comparer/Decoder tools, WebSockets/SSE/gRPC, and richer session handling.

The documentation is unusually honest about these boundaries, which is a strength worth preserving.

---

## Part 2 — Issues & risks found

### 2.1 Headline capability gap (known, but it is the product)

The feature that separates ESuite from "an API client with a WebView" — intercepting and capturing traffic from other apps on an unrooted phone (VpnService + userspace forwarding + local CA) — is entirely unimplemented. Everything shipped so far is the supporting scaffolding around that missing core. This is tracked (CAP-01..04) but it is the single largest gap between the README narrative and runtime reality. Browser capture is **metadata-only**, which also limits the "inspect the request/response" promise.

### 2.2 Code-level issues & risks (grounded in source)

1. **Process-death data-loss window.** `AppViewModel` persists via `state...debounce(500)`. Any edit (new finding, saved request, identity config) is only written 500 ms after the last change. If the process is killed inside that window there is no autosave on lifecycle stop, so the most recent evidence is lost. For a "preserve evidence" product this is a real gap, and DATA-01 (process-death/interrupted-save coverage) is open. Fix direction: flush on `onStop`/`onCleared`, and/or write-through for durable (non-history) mutations.

2. **No workspace schema migration.** `WorkspaceVault.load()` hard-requires `version == 1`; `Workspace(version = 1)` is fixed and there is no upgrade branch. The first time the persisted model changes, every existing encrypted vault becomes unreadable ("Unsupported workspace version") and users lose their evidence on update. Now that 1.0.x has shipped, this is a forward-compatibility landmine. Fix direction: versioned decode with explicit migrations (DATA-02).

3. **Budget/import work on the UI thread.** `importOpenApi`/`importHar`/`mergeSaved` parse and run `WorkspaceBudget.requireDurableCapacity` (a full `Json.encodeToString`) synchronously inside `mutable.update { }` on the caller (main) thread. Large HAR/OpenAPI inputs risk jank/ANR. Documented as DATA-02; still open.

4. **Save-time history pruning diverges from memory.** `WorkspaceVault.save()` halves `exchanges` to fit 32 MiB but persists a local `bounded` copy; the in-memory `exchanges` list still holds the full set. Disk and memory silently disagree until the next load. Low severity but surprising.

5. **Only two identities, no anonymous.** `AppViewModel.identity()` enforces `value in listOf("A","B")`, and comparison is hardcoded A vs B. The product plan promises "anonymous, account A, account B and any provided role identities" for the authorization matrix — the core IDOR/BOLA workflow — but the implementation caps at two authenticated identities with no unauthenticated baseline. This directly limits the most valuable manual-testing play.

6. **Comparer is thin.** `ResponseComparer.compare` reports normalized-JSON equal/different plus status/timing. There is no structural per-field diff, no byte diff, no dynamic-field masking surfaced to the user — well short of the "structured response differences / identity matrices" plan.

7. **Fixed task pacing.** Parameter runs and sequences hardcode `delay(1000)` and stop-on-429/503 with no user-configurable rate, concurrency, pause, or checkpoint (RUN-01 open). No resume after Android background kill.

8. **UTF-8 boundary truncation.** In `ApiEngine`, a multi-byte UTF-8 character straddling the 256 KiB cap can be corrupted by `String(bytes, charset)`. Bounded and cosmetic, but worth a note when bodies are evidence.

9. **Findings lack structure.** The `Finding` model has free-text title/detail and a `state` string, but no severity, confidence, CVSS, or endpoint linkage fields (REPORT-01 open), limiting report quality.

### 2.3 Testing & verification gaps

- All proxy/VPN/TLS capability is unverified on physical devices (emulator-only matrix so far).
- Tests are example-based JUnit. There are **no property-based tests**, even though several invariants here are perfect for it (identity isolation, scope enforcement, encrypt/decrypt round-trip, redaction completeness). This is the cheapest path to much stronger correctness evidence.
- Renderer-crash, in-flight identity switching, OEM/TalkBack/font-scaling, and export-provider flows remain open (per VERIFICATION.md).

---

## Part 3 — Making it extraordinary

ESuite's real superpower is its constraint: **everything stays on the device, with no backend, no telemetry, no hosted AI.** The most extraordinary features are the ones that turn that constraint into capability — things the desktop/cloud competition structurally cannot or will not do.

### Flagship ideas (novel for this category, constraint-honoring)

**F1. Fully-offline AI evidence copilot.**
An on-device small language model (e.g. Gemini Nano via AICore / MediaPipe LLM Inference where available) that summarizes a captured exchange, ranks which passive observations matter, drafts a reproducible finding write-up from the request/response, and suggests next tests — with **zero network egress**. Competitors either have no AI or cloud AI that uploads your traffic. An offline copilot is a genuine first for this category and honors the "no hosted AI" contract exactly. Opt-in, bounded, model runs in-process.

**F2. N-identity authorization matrix ("access truth table").**
Promote identities beyond A/B to: anonymous + A + B + arbitrary role identities. One tap replays a selected request as *every* identity and renders an endpoint x identity grid, auto-flagging suspicious cells (e.g. anonymous receives 200 where it should be 401). This is the IDOR/BOLA/role-escalation workflow that requires the Autorize extension in Burp — delivered natively, phone-first, with evidence capture attached. Fixing issue 2.2(5) is the on-ramp to this.

**F3. Session time-travel: record -> replay -> structured diff.**
Capture a whole testing session as an ordered, scrubbable timeline. Re-run the entire sequence as a different identity, or after the target changes, and get a per-response structured diff of the full run — "git for a pentest session," entirely on-device. Turns the thin Comparer (issue 2.2(6)) into a headline feature.

**F4. Signed, offline-verifiable evidence bundles.**
Export a finding as a tamper-evident bundle signed with the per-install key, so a teammate or client can verify it was not altered — with no server involved. Reproducible, redacted-by-default evidence packaging for bug-bounty and client reports, fully offline. Builds directly on the existing Keystore and `Redactor`.

**F5. Cross-identity leak detector as a provable invariant.**
Continuously assert that identity A's cookies/tokens/storage never appear in identity B's jar or requests, surfacing any violation as an automatic finding. The engine already isolates jars; this turns isolation into an actively checked, property-tested guarantee — and doubles as the first real property-based test suite (addresses 2.3).

### Supporting / research-tier ideas

**S1. Battery- and thermal-aware adaptive scheduler.** Pace long parameter runs/crawls against live thermal, battery, and network state with automatic checkpoint/resume. No desktop tool needs this; a phone one does, and it makes safe long-running tasks possible (fixes 2.2(7)).

**S2. Schema-aware "what-if" mutation sandbox.** From a captured JSON/GraphQL request, auto-propose typed mutations (mass-assignment fields, enum flips, id increment/decrement, negative quantities) and run them bounded, producing evidence — a guided business-logic test generator, fully local.

**S3. Native data-flow canaries (DOM Invader-style).** Inject tracked canary tokens into inputs and trace where they surface across DOM/storage/subsequent requests inside the native WebView — a real source->sink tracer. Research-tier in the current plan; making it work is a differentiator.

**S4. Local reachable-lab OAST with QR pairing.** A one-tap on-device listener QR-paired to a lab network, correlating out-of-band callbacks to the originating request — the only genuinely local OAST, honest about NAT/carrier limits.

**S5. Signed, sandboxed community rule packs.** A declarative, resource-bounded BCheck-like format importable by file/QR, executed on-device with hard limits — an extension ecosystem that never phones home.

**S6. EILIOT "field mode" HUD overlay.** A floating heads-up overlay (the JARVIS-style orb/HUD design persona already in `design/`) showing live scope status, last response code, and a quick "replay as B" action while you use the target app.

### Why these are novel here

Desktop tools (Burp/Caido) assume a workstation, extensions, and often cloud OAST. Mobile MitM apps (HttpCanary/Reqable/PCAPdroid) capture packets but offer little analysis, no identity modeling, no evidence discipline, and no privacy guarantee. ESuite can own the intersection nobody serves: **analysis-grade, evidence-first security testing that is phone-native and provably local.** F1 (offline AI) and F2 (native authz matrix) are the two that would make it genuinely stand out.

---

## Part 4 — Recommended next step

Two tracks are worth turning into specs:

- **Stability track:** schema migrations + lifecycle autosave (issues 2.2(1) and 2.2(2)) — small, high-value, protects shipped users now.
- **Extraordinary track:** start with **F2 (N-identity authorization matrix)**, because it unlocks F3/F5, has the clearest security value, and is feasible on the existing engine. **F1 (offline AI copilot)** is the bigger "wow" and pairs well once F2 exists.

Each of these can be developed as a proper spec (requirements -> design -> tasks) with correctness properties and property-based tests, which also closes the testing gap in 2.3.
