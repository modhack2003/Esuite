# Release 1.0.2 persistence correctness audit

Reviewed the working tree on 15 November 2026. Focus: actual save acknowledgement, workspace generation, import application, migration, and test boundaries. No source edits or test executions were performed by this reviewer. Latest prior remote Build and verify run [43700508851](https://github.com/modhack2003/Esuite/actions/runs/43700508851) succeeded for `0daad498757c3a6c8f55137f05fb31a6a6f826c1`; that result does not validate the current working changes.

## Blocker: AtomicFile can silently fail its commit while the UI acknowledges success

Affected: `app/src/main/java/dev/esuite/app/storage/WorkspaceVault.kt:43–49`; `app/src/main/java/dev/esuite/app/AppViewModel.kt:69–74`; `app/src/main/java/dev/esuite/app/MainActivity.kt:103`.

`save()` assumes `AtomicFile.finishWrite(stream)` throws on sync/close/replacement errors. The Android framework implementation instead logs failed `FileUtils.sync`, catches close errors, and logs a failed rename. Consequently, a failed replacement can leave the old base bytes and still return the new bounded workspace. The ViewModel then stores that new value as `persistedWorkspace`, so the UI displays “Workspace saved with AES-GCM” for data that cannot be read after restart. This is a false acknowledgement after a real failed write, distinct from killing the process before any save completed.

Primary implementation reference: [AOSP AtomicFile.java](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/util/AtomicFile.java), specifically `finishWrite` and its private `rename` helper, independently fetched during this review.

Required correction: explicitly propagate file synchronization failure before committing, and verify the committed base envelope equals the encrypted bytes intended for this transaction before returning success. Failure must leave the UI pending/error rather than update the committed snapshot. Add a storage-boundary failure test that causes the replacement/readback to fail and proves save does not acknowledge it; retain the prior readable vault where the failure occurs before replacement. Do not claim all post-replacement errors imply the previous bytes necessarily remain.

## Blocker: obsolete loads are allowed to reactivate an erased/reloaded workspace

Affected: `app/src/main/java/dev/esuite/app/AppViewModel.kt:41–58,84–91`; `app/src/main/java/dev/esuite/app/MainActivity.kt:61–68`.

`loadWorkspace()` increments `workspaceGeneration`, but its coroutine never captures/checks that generation and there is no load-job cancellation. “Retry open” remains active during an outstanding load. A load finishing after a newer retry or erase still sets `ready=true`, replaces workspace/project/draft, marks the old workspace persisted, and starts another collector. The writer and importer completion paths check generations; this load completion path bypasses the same protection.

Concrete failure sequence: an old load reads existing evidence; erase increments the generation and waits on the storage lane; the old load continuation runs and reopens the old workspace while erase is pending/completing. The exposed workspace permits a durable edit/save; that queued save can recreate old evidence after erase. Repeated retries can also expose an earlier result before a later load finishes, then replace intervening in-memory edits with the later disk snapshot.

Required correction: capture the load generation and suppress obsolete success/failure/collector installation; cancel or replace an outstanding load job. Erase must invalidate all existing load completions. Add deterministic delayed-load tests covering retry versus retry and load versus erase, proving obsolete completions cannot set ready or restore evidence.

## Major: tests do not currently establish the ViewModel's generation and acknowledgement guarantees

Affected: `app/src/test/java/dev/esuite/app/core/CollectionImporterTest.kt`; `app/src/androidTest/java/dev/esuite/app/DeviceBoundaryTest.kt`; `ui-tests/src/main/java/dev/esuite/uitests/ProductionUiTest.java`.

The added helper tests exercise the production import preparation/reconciliation functions, including preservation of concurrent exchanges and rejection of changed durable state. The Android tests cross the actual encrypted vault migration/pruning boundaries. The independent production UI test waits on the saved-state control and then force-stops the real app process, which correctly avoids proving durability through a surviving writer ViewModel. No writer-ViewModel leak is present in these inspected tests because they do not instantiate writer ViewModels.

However, none of those tests execute the ViewModel's generation checks, retry loop, overlapping explicit/debounced writes, failure acknowledgement, or load/erase race. The importer worker test asserts the parse thread; it does not independently instrument/assert the budget operation's thread. Add controlled-dispatcher/store integration tests around the real coordinator. If tests instantiate ViewModels, own each through a ViewModelStore and clear it before opening a reader, so an old persistence collector cannot make the read-back assertion pass.

## Assessment of remaining inspected paths

- The current marker compares the whole workspace with the committed bounded snapshot. This avoids acknowledging a newer ordinary edit merely because an earlier save produced a generic message. `WorkspacePersistence.reconcile` removes only IDs actually pruned from the saved snapshot and preserves newer exchanges and durable edits.
- Explicit and debounced save requests share one `limitedParallelism(1)` storage lane and the vault's synchronized methods; no concrete ordinary overlapping-save overwrite was established by source inspection. Their ordering and cancellation behavior still need the deterministic integration coverage above. In particular, exercise changing back to a previously persisted value while a different snapshot is queued; equality alone should not advertise a settled save if another known transaction can overwrite that value.
- `CollectionImporter.prepare` rechecks exact project ID/origin, per-request project/scope, collection count and durable budget on the worker. `Prepared.apply` preserves current exchanges while rejecting changed durable fields. The ViewModel checks generation/project/readiness before and after preparation and performs a compare-and-set with bounded retries. No concrete import scope escape or concurrent-capture overwrite was found in this implementation.
- The migration path is real: v1 is stamped to v2, decoded through the current serializer, then subjected to vault record/origin checks. Missing/noninteger/string/future versions fail closed. The Android migration test actually encrypts v1 and reloads through WorkspaceVault; the unknown-version test checks encrypted base bytes are unchanged. No migration shape-loss defect was found.
- `flush()` is documented as best effort. Process death before a completed acknowledgement can lose queued edits; that limitation alone is not a release blocker or evidence that save completion is false.

Acceptance before release: correct the two blockers, execute the focused concurrency/failure tests and required Android gates, and retain distinct evidence for device/process-restart checks versus assembly. This report records the source state at review time; later fixes require explicit recheck.

## Correction recheck — 15 November 2026

The two original source blockers are resolved by the final inspected implementation. No additional concrete release-blocking persistence defect was established in this recheck. This is a source assessment; the reviewer did not run compilation, native tests, or the production APK.

- `WorkspaceVault.save` now delegates to `VerifiedAtomicWrite`. That helper explicitly synchronizes the descriptor, commits, then reads through `AtomicFile.openRead` and compares both length and exact encrypted envelope bytes before returning. Only pre-commit write/sync exceptions invoke `failWrite`; post-finish or readback failures never attempt a potentially destructive rollback. The ViewModel error text correctly says the save could not be verified rather than universally promising previous bytes remain.
- During recheck, an intermediate direct-base readback was rejected for Android 9: that platform writes the new base while retaining `.bak`, and its reader can subsequently restore the backup. Its `failWrite` also deletes the base before restoring backup, making rollback after a completed commit unsafe. The final helper uses the platform recovery reader outside the rollback region and resolves both issues. Primary reference: [Android 9 AtomicFile implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r61/core/java/android/util/AtomicFile.java).
- The new native `silentAtomicFinishFailureCannotAcknowledgeUnreplacedBytes` test calls the production writer helper with a finish callback that closes without committing. It asserts an exception and retained previous bytes. This exercises actual platform file behavior and should distinguish the Android 9 backup implementation from newer staging-file implementations. It is added test coverage, not an executed pass in this review.
- `loadWorkspace` now cancels the previous `loadJob`, captures the generation, checks it before publishing a result or installing an observer, and suppresses obsolete failure messages. Erase cancels that load job and increments the generation. The originally identified obsolete-load path is closed by source inspection.
- Save execution increments `pendingWrites` before dispatch and decrements it on completion within the same generation. The UI requires zero pending writes as well as workspace equality before displaying saved status. This closes the identified return-to-an-old-value acknowledgement concern while an already captured write is queued. `record` additionally rejects not-ready and deleted-project captures.

The original Major coverage limitation remains explicit: there are still no deterministic tests executing the ViewModel's generation/erase/retry, pending-write counter, overlapping explicit/debounced writes, or import generation retry paths. The native fault test covers the real writer helper rather than the ViewModel error presentation. These missing tests are a confidence/coverage limitation; source review did not establish another corresponding defect after the corrections. Run the new native case on API 28 and the full supported matrix, plus the real optimized-APK restart/upgrade gates, before claiming release verification. No writer ViewModel was introduced by the added native helper test.

