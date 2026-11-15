# Kiro review and separate Codex contribution

Reviewed 15 November 2026 against a changing working directory. The two linked Goldfish reports record file hashes and distinguish existing implementation defects from proposed-plan risks. This is a focused EGM fresh-context review, not the full five-stage design review. Kiro's source, specifications and task checkboxes were left under Kiro's ownership.

## Highest priority findings for Kiro

| Finding | Why it matters | Concrete next action |
| --- | --- | --- |
| Persistence WPH-001: save acknowledgement precedes commit | Launching a coroutine removes the debounce delay but still returns before disk commit. A generic old save message can acknowledge a newer, unsaved finding. Launching into viewModelScope during onCleared cannot drain that canceled scope | Track committed revisions and expose an awaitable save outcome. Acknowledge the specific mutation only after commit; test a mutation arriving during a save, write failure, and actual process termination after acknowledgement |
| Persistence WPH-002/003: migration and lifecycle tests miss their claimed boundary | Version 2 fixtures equal CURRENT_VERSION and bypass v1-to-v2 migration. Unmanaged test ViewModels can keep writing after vault cleanup | Feed deployed v1 JSON through production migration/load. Own and tear down test models and their writer work before erasing the fixture |
| Browser F2: a blank white WebView passes the proposed paint test | Adding a white background already makes the view differ from Midnight. A separately hardcoded old hierarchy also does not acquire a production layout fix | Exercise actual app hosting with a recognizable page pattern, stable bounds and blank/wrong-crop negative controls. Reproduce the symptom before declaring a root cause |
| Runtime F5/F6: off-thread import still re-encodes on main and lacks ownership rules | Rechecking the full durable budget in the main-thread update preserves the original expensive work. Deletion/erase/project changes can invalidate asynchronous results | Validate a durable revision off main, apply only a cheap guarded commit on main, and revalidate off main after conflicts. Reject stale workspace/project results and preserve cancellation |

Reports: [persistence review](kiro-persistence-goldfish.md), [browser/runtime review](kiro-browser-runtime-goldfish.md). Additional findings cover charset-tail handling, save-snapshot reconciliation, reachable controls with keyboard/font scaling, and overstated verification claims. Review these before executing the plans verbatim; several items are still planned rather than shipped defects.

## Separate completed Codex work

Codex added `tools/forwarder_notices.py` and focused tests, outside the Android files Kiro is editing. The generated `native/notices` artifacts retain notices and hashes for 450 conservative Android source/header/build files; 12 files without leading comments remain explicitly listed under component attribution. Exact Git blobs, dependency pins and license hashes are checked; modified/untracked/ignored checkout content and changed supplemental notices are rejected.

The audit found seven forked YAML files identical to upstream libyaml 0.2.5 source. The fork's root notice alone did not retain that upstream authorship, so the original libyaml license is included alongside the fork notice, with pinned provenance. Full Apache-2.0 terms are retained for build-file notices too. This is an attribution inventory, not legal clearance, a linker map, or runtime forwarding verification. Final component/notice review remains required before packaging a native stack.

Verification: the notice-tool unit tests passed; the full pinned source inventory was generated successfully. The previous green Android CI run 43682611496 predates Kiro's uncommitted changes and does not verify them. No new Android/device or process-death result is claimed by this contribution.
