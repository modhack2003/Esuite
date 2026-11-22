# ESuite interface specification

## Direction

ESuite should feel like a precise security instrument that belongs on Android. Midnight surfaces keep code readable; cyan marks the selected action; amber marks attention. Use real labels and compact tools instead of decorative hacker imagery. Brand line: **Inspect. Intercept. Investigate.**

The responsive web prototype is a design review surface. The shipping product is native Android. Desktop preview controls are not part of the Android navigation.

## Design tokens

| Token | Value | Use |
| --- | --- | --- |
| Midnight | `#071019` | Main canvas |
| Raised | `#101C29` | Cards and editor panels |
| Divider | `#263545` | Borders and separation |
| Signal | `#5BE0D6` | Primary actions, selection and successful states |
| Attention | `#FFB86B` | Attention, exceptional status |
| Ink | `#E7EEF6` | Primary text |
| Muted | `#92A5B8` | Supporting text |

Native type: Android system sans; monospace for messages and identifiers. Web concept: system sans/monospace without remote fonts. Native controls should follow at least 48 dp touch targets. Never rely on color alone: show method/status/state text and icons. Test TalkBack, font scaling, keyboard access, portrait and landscape before a UI release. A browser screenshot is not proof of native accessibility.

## Navigation

**Target product:** Home, Browser, Traffic, API Lab, Findings. Decoder, Comparer, Sequencer, Intruder and future Cloud tools open from the workspace toolkit. Keep a global project selector, identity indicator and capture state visible when relevant.

**Native navigation:** Home, Browser, Traffic, API Lab and Findings are primary destinations. Tools opens from Home. Narrow and large-font windows retain accessible icon labels. Short windows use a compact bar, and editor focus gives the keyboard room.

## Screen behavior

| Screen | Primary task | Important states |
| --- | --- | --- |
| Home | Select scope/project and begin a test | Empty project, demo project, existing local project, storage locked |
| Browser | Navigate and inspect page context | Loading, TLS failure, Elements/Cookies/Storage/Console, unsupported WebView feature |
| Traffic | Inspect actual captured exchanges | Stopped, permission needed, starting, capturing, paused, encrypted-only, dropped/truncated, error |
| Request detail | Read and transfer a message | Original/edited, headers/body, encoding, capture source, identity, completeness |
| API Lab | Edit and execute a request | Draft, sending, cancelled, response, transport failure, expired identity, comparison |
| Findings | Preserve observations and reproduce results | Empty, candidate, verified, dismissed, retest pending, export preview |
| Tools | Transform or compare values | Valid result, invalid input, empty input, unsupported format |

## Critical flows

1. Browser → select element → inspect associated request → send to API Lab → choose identity → replay → compare → attach to finding.
2. Select apps → Android VPN permission → explain any VPN conflict → start capture → show active foreground status → inspect connection → explain encrypted-content limitation if applicable → stop immediately.
3. Account A request → repeat under Account B → compare normalized response → inspect actual authorization effect → save evidence with confidence and expected behavior.
4. Finding → select attachments → preview redactions → choose local export destination. Do not silently share tokens or upload a report.

## Shared components

Message editor with Raw/Pretty/Hex views; structured parameter inspector; method badge; status badge; identity switcher; scope indicator; capture state chip; interception queue; task resource budget; empty/error state; redaction preview; send-to action sheet; evidence attachment.

A future live capture must never use a decorative active dot when capture has failed or been stopped. Fixture mode must always carry a persistent **Synthetic demo** label. Active audit, passive analysis and manual replay must be visually distinct.

## Present implementation

The web concept includes six interactive views, traffic filtering, fixture details, transfer to an editable request, fixture identity switching, simulated response comparison, session notes and real local Base64 transforms. It performs no HTTP testing. Notes are only in component memory. A reload resets them.

The native app implements compatible isolated WebView profiles, local page inspection, request metadata observation, scoped manual HTTPS requests, independent API sessions, encrypted evidence, collections, sanitized exports and offline configuration review. The 1.0.4 workspace prioritizes Home shortcuts, separate API Lab views, searchable Findings and grouped offline tools. The browser has scrollable selected tabs, a compact address control and a workspace status dialog. Traffic opens details immediately and closing them returns to the list. Findings can preview a metadata-only report for a single observation. A fresh launch opens Home with the last available project and identity A. Pending editor text stays in memory. The web concept uses synthetic data. See [the release record](RELEASE_1.0.4.md) for verification gates.

## Visual source workflow

- React/TypeScript source: `design/prototype/src/components/generated/`.
- Self-contained review artifact: `docs/ui/index.html`; generated with `npm run build` in `design/prototype`.
- Repository screenshots: `docs/assets/`; generated from the actual local web concept.
- Brand mark and banner: original SVG source in `docs/assets/`.
- Canva: editable concept board. Link and status live in `design/EXTERNAL_DESIGNS.md`.
- MagicPath: interactive canvas component using the same React component and CSS.
- Superdesign: connection not available in this session. A ready-to-use brief is included in `design/SUPERDESIGN_BRIEF.md`; it is not a generated Superdesign result.

## Validation gates

- No horizontal overflow at a 390 px viewport.
- All navigation, traffic filters, request transfer, identity toggles, decoder round trips and note addition work with synthetic data.
- Real tools have invalid-input handling; simulated actions say they are simulated.
- No network requests from the self-contained concept after loading. No bundled tokens or real target data.
- Keyboard focus, dialog dismissal and reduced-motion behavior are reviewed.
- Native APK builds and local codec unit tests pass; device-specific UI, VPN and TLS checks remain separate future milestones.

## 1.0.5 Browser refresh

Launch opens Browser with a combined search and HTTPS address field, navigation controls, page loading and errors, and a Developer tools panel for the current page. Settings provides appearance, animation preferences, local browsing controls, version and credits. Activity and Findings browse saved records by site. The app prepares an API origin through an explicit HTTPS address or a Browser handoff. See the [release notes](RELEASE_1.0.5.md).
