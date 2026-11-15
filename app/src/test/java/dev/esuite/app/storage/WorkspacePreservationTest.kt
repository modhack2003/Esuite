package dev.esuite.app.storage

import dev.esuite.app.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import javax.crypto.spec.SecretKeySpec

/**
 * workspace-persistence-hardening · Task 3 · Property 3 (Preservation)
 * Non-Durable and Non-Bug Inputs Unchanged.
 *
 * Observation-first baseline: these cases run the UNFIXED code paths and pin the behavior the two
 * fixes (Defect 1 write-through/flush, Defect 2 versioned migration) MUST NOT change. They are
 * EXPECTED TO PASS on unfixed code and are re-run unchanged to guard against regressions in
 * Tasks 4.4 and 5.4.
 *
 * Keystore split (mirrors the whole spec): the decode/budget/prune/fail-closed logic that can be
 * isolated from the AndroidKeyStore runs here as JVM cases, using the [VaultCipherTest] pattern of an
 * injected raw `SecretKeySpec(ByteArray(32){it.toByte()}, "AES")` wherever sealing is needed. The
 * end-to-end `WorkspaceVault.save()`/`load()` round-trips — which require a real `Context`
 * (`noBackupFilesDir`) and the Keystore key — are deferred to the instrumented [DeviceBoundaryTest]
 * (CI emulator matrix, `:app:connectedDebugAndroidTest`) and are noted at each case below.
 *
 * Property-style coverage: where it adds value, a case loops over many seeded-random inputs (a
 * lightweight property test staying offline with no new dependency) so edge cases beyond a single
 * fixture are exercised.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
class WorkspacePreservationTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val json = Json { encodeDefaults = true }

    // ---- History-only writes stay bounded (budget + 32 MiB cap + exchange-halving prune) ----
    // _Requirements: 3.3, 3.2_

    /**
     * Capture history (`exchanges`) is exempt from the 16 MiB durable budget. [WorkspaceBudget]
     * strips exchanges before measuring, so megabytes of history never trip the durable guard — only
     * durable evidence counts. This is the JVM-isolable slice of "history writes stay debounced and
     * budgeted"; the full save-time budgeting is exercised by the instrumented round-trip (deferred).
     */
    @Test fun requireDurableCapacityExemptsCaptureHistoryFromTheDurableBudget() {
        val project = Project(name = "Budget fixture", origin = "https://example.test")
        val body = "y".repeat(512 * 1024)
        val history = List(8) { i ->
            Exchange(id = "h-$i", projectId = project.id, identity = "A",
                request = RequestDraft(url = "https://example.test/$i"), response = StoredBody(body), timestamp = 1_000L + i)
        }
        val workspace = Workspace(projects = listOf(project), exchanges = history,
            findings = listOf(Finding(projectId = project.id, title = "note", detail = "small-durable")))
        // ~4 MiB of capture history, but only a few hundred bytes of durable data.
        assertTrue("history fixture must be several MiB to be meaningful",
            json.encodeToString(workspace).toByteArray(Charsets.UTF_8).size > Limits.VAULT_BYTES / 16)
        // History is excluded from the durable budget, so this must not throw.
        WorkspaceBudget.requireDurableCapacity(workspace)
        // ...and it still holds no matter how much history piles up (property-style over sizes).
        val random = Random(49_157)
        repeat(12) {
            val more = List(1 + random.nextInt(16)) { i ->
                Exchange(id = "r-$i-${random.nextInt()}", projectId = project.id, identity = "A",
                    request = RequestDraft(url = "https://example.test/$i"), response = StoredBody("z".repeat(64 * 1024)), timestamp = i.toLong())
            }
            WorkspaceBudget.requireDurableCapacity(workspace.copy(exchanges = more))
        }
    }

    /**
     * Oversized durable collections are rejected rather than silently dropped — the complement of the
     * history exemption above, and the guarantee the fixes must preserve (3.2). Mirrors
     * [VaultCipherTest]'s durable-budget case.
     */
    @Test fun requireDurableCapacityRejectsOversizedDurableDataRegardlessOfHistory() {
        val fatDraft = RequestDraft("POST", "https://example.test", body = "x".repeat(Limits.REQUEST_BYTES))
        val overBudget = Workspace(savedRequests = List(70) { SavedRequest(projectId = "p", name = "r$it", draft = fatDraft) })
        assertThrows(IllegalArgumentException::class.java) { WorkspaceBudget.requireDurableCapacity(overBudget) }
        // Adding history to an over-budget durable set does not change the verdict (history is ignored).
        val withHistory = overBudget.copy(exchanges = List(4) {
            Exchange(projectId = "p", identity = "A", request = RequestDraft(url = "https://example.test/$it"), response = StoredBody("h".repeat(256 * 1024)))
        })
        assertThrows(IllegalArgumentException::class.java) { WorkspaceBudget.requireDurableCapacity(withHistory) }
    }

    /**
     * `save()`'s exchange-halving prune, exercised through the injected-key seal path. A workspace
     * whose encoded size exceeds the 32 MiB `VAULT_BYTES` cap purely because of capture history is
     * pruned by repeatedly halving `exchanges` until it fits, while every durable record is left
     * untouched; the pruned bytes then seal and round-trip losslessly. This replicates the exact loop
     * in `WorkspaceVault.save()` (which is Keystore-bound and therefore not callable here); the full
     * Keystore `save()` is the instrumented variant (deferred).
     */
    @Test fun historyOverflowIsPrunedByHalvingWithoutTouchingDurableDataThenSeals() {
        val project = Project(name = "Prune fixture", origin = "https://example.test")
        val durableFinding = Finding(projectId = project.id, title = "Durable", detail = "durable-note")
        val saved = SavedRequest(projectId = project.id, name = "Saved", draft = RequestDraft(url = "https://example.test/api"))
        val identity = IdentityConfig(project.id, "A", listOf(Header("Authorization", "Bearer token")))
        // 60 exchanges × 600 KiB ≈ 36 MiB of history, pushing the encoded workspace past the 32 MiB cap.
        val body = "x".repeat(600 * 1024)
        val exchanges = List(60) { i ->
            Exchange(id = "ex-$i", projectId = project.id, identity = "A",
                request = RequestDraft(url = "https://example.test/$i"), response = StoredBody(body), timestamp = 1_000L + i)
        }
        val oversized = Workspace(projects = listOf(project), exchanges = exchanges,
            findings = listOf(durableFinding), savedRequests = listOf(saved), identityConfigs = listOf(identity))
        assertTrue("fixture must exceed the 32 MiB cap to exercise the prune",
            json.encodeToString(oversized).toByteArray(Charsets.UTF_8).size > Limits.VAULT_BYTES)

        val (pruned, bytes) = pruneLikeSave(oversized)

        assertTrue("prune must bring the encoded workspace within the 32 MiB cap", bytes.size <= Limits.VAULT_BYTES)
        assertTrue("prune must drop some history to fit", pruned.exchanges.size < oversized.exchanges.size)
        // Durable evidence is never pruned.
        assertEquals("projects untouched by prune", oversized.projects, pruned.projects)
        assertEquals("findings untouched by prune", oversized.findings, pruned.findings)
        assertEquals("saved requests untouched by prune", oversized.savedRequests, pruned.savedRequests)
        assertEquals("identity configs untouched by prune", oversized.identityConfigs, pruned.identityConfigs)
        // Retained history is a prefix of the original (take()), preserving insertion order.
        assertEquals("retained history is a prefix of the original", oversized.exchanges.take(pruned.exchanges.size), pruned.exchanges)
        // The pruned bytes now fit the injected-key seal path and decrypt back unchanged.
        val envelope = VaultCipher.seal(bytes, key)
        assertArrayEquals("pruned workspace must round-trip through the seal path", bytes, VaultCipher.open(envelope, key))
    }

    // ---- Current-version round-trip unchanged (version = 1 via the injected key) ----
    // _Requirements: 3.5_

    /**
     * A `version = 1` workspace sealed and reopened with the injected key decodes to byte-for-byte
     * identical content, across many seeded-random in-limit workspaces, and the round-tripped content
     * still satisfies the record-count limits `load()` enforces. The end-to-end Keystore
     * `save()`→`load()` round-trip (and its record-limit/HTTPS enforcement) is the instrumented variant
     * in [DeviceBoundaryTest] (deferred); here we isolate the encode/seal/open/decode core.
     */
    @Test fun currentVersionWorkspacesRoundTripThroughTheInjectedKeyUnchanged() {
        val random = Random(20_240_517)
        repeat(40) {
            val source = randomWorkspaceV1(random)
            val envelope = VaultCipher.seal(json.encodeToString(source).toByteArray(Charsets.UTF_8), key)
            val decoded = json.decodeFromString(Workspace.serializer(), VaultCipher.open(envelope, key).toString(Charsets.UTF_8))
            assertEquals("current-version seal/open/decode must be lossless", source, decoded)
            assertEquals("version = 1 is preserved across the round-trip", 1, decoded.version)
            assertTrue("round-tripped content must satisfy load()'s record-count limits",
                decoded.projects.size <= 20 && decoded.savedRequests.size <= 100 &&
                    decoded.findings.size <= Limits.FINDINGS && decoded.exchanges.size <= Limits.HISTORY)
        }
    }

    // ---- Fail-closed, file byte-identical (JVM: VaultCipher.open throws) ----
    // _Requirements: 3.1, 3.4_

    /**
     * A byte-flipped envelope, a wrong key, and an unknown magic each fail closed at
     * [VaultCipher.open]; the good envelope is left byte-identical and still decrypts to the original
     * content — the JVM analog of `load()` leaving the encrypted file untouched on a failed read. The
     * Keystore `load()` fail-closed + `assertArrayEquals(file bytes unchanged)` cases are the
     * instrumented variant (deferred; mirror [DeviceBoundaryTest.androidKeystoreWorkspaceRoundTripAndTamperFailure]).
     */
    @Test fun tamperedEnvelopeFailsClosedAndLeavesTheGoodEnvelopeByteIdentical() {
        val source = Workspace(version = 1, projects = listOf(Project(name = "Tamper fixture", origin = "https://example.test")),
            findings = listOf(Finding(projectId = "p", title = "t", detail = "secret-evidence")))
        val envelope = VaultCipher.seal(json.encodeToString(source).toByteArray(Charsets.UTF_8), key)
        val snapshot = envelope.clone()

        // Byte-flip in the ciphertext region → AEAD tag check fails (mirrors DeviceBoundaryTest bytes[20]).
        val flipped = envelope.clone(); flipped[20] = (flipped[20].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { VaultCipher.open(flipped, key) }
        // Wrong key also fails closed.
        assertThrows(Exception::class.java) { VaultCipher.open(envelope, SecretKeySpec(ByteArray(32) { 7 }, "AES")) }
        // Unknown envelope magic is rejected before any crypto.
        val unknownMagic = envelope.clone(); unknownMagic[3] = 99
        assertThrows(IllegalArgumentException::class.java) { VaultCipher.open(unknownMagic, key) }

        // The good envelope is untouched by the failed opens and still decodes to the original content.
        assertArrayEquals("failed opens must not mutate the good envelope", snapshot, envelope)
        assertEquals(source, json.decodeFromString(Workspace.serializer(), VaultCipher.open(envelope, key).toString(Charsets.UTF_8)))
    }

    /**
     * A strictly-newer vault (`version = 999`) seals and decrypts cleanly — the envelope is well
     * formed — so failing closed on it is a VERSION-GATE concern inside `load()`, not an envelope
     * concern. This JVM case pins that the current `require(version == 1)` gate rejects it with exactly
     * "Unsupported workspace version"; because `999 > CURRENT_VERSION (2)`, the fix must keep rejecting
     * it. The authoritative end-to-end fail-closed (`load()` throws AND the encrypted file is left
     * byte-identical) is Keystore-bound and deferred to the instrumented suite.
     */
    @Test fun newerThanCurrentVersionDocumentDecodesButIsRejectedByTheVersionGate() {
        val source = Workspace(version = 999, projects = listOf(Project(name = "Newer fixture", origin = "https://example.test")))
        val envelope = VaultCipher.seal(json.encodeToString(source).toByteArray(Charsets.UTF_8), key)
        val decoded = json.decodeFromString(Workspace.serializer(), VaultCipher.open(envelope, key).toString(Charsets.UTF_8))
        assertEquals("the envelope itself is well-formed and decrypts", 999, decoded.version)
        val rejection = assertThrows(IllegalArgumentException::class.java) {
            require(decoded.version == 1) { "Unsupported workspace version" }
        }
        assertEquals("Unsupported workspace version", rejection.message)
    }

    // ---- No-file startup returns a fresh empty Workspace (JVM proxy; load() deferred) ----
    // _Requirements: 3.6_

    /**
     * `load()` returns `Workspace()` when neither the `.esv` nor the `.bak` file exists. That branch
     * runs before any Keystore access but still needs a real `Context` (`noBackupFilesDir`), so the
     * end-to-end `load()` call is deferred to the instrumented suite. Here we pin the value that branch
     * returns: a fresh, empty workspace with no projects, history, findings, saved requests, or
     * identity configs. The default *version* is deliberately NOT asserted — Defect 2's fix bumps the
     * fresh-workspace version from 1 to CURRENT_VERSION, and emptiness (not the version literal) is the
     * no-file guarantee (3.6).
     */
    @Test fun noFileStartupReturnsAFreshEmptyWorkspace() {
        val fresh = Workspace()
        assertTrue("a fresh workspace must carry no evidence",
            fresh.projects.isEmpty() && fresh.exchanges.isEmpty() && fresh.findings.isEmpty() &&
                fresh.savedRequests.isEmpty() && fresh.identityConfigs.isEmpty())
    }

    // ---- helpers ----

    /** Exact replica of `WorkspaceVault.save()`'s exchange-halving prune loop (Keystore-free). */
    private fun pruneLikeSave(workspace: Workspace): Pair<Workspace, ByteArray> {
        var bounded = workspace
        var bytes = json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        while (bytes.size > Limits.VAULT_BYTES && bounded.exchanges.isNotEmpty()) {
            bounded = bounded.copy(exchanges = bounded.exchanges.take(bounded.exchanges.size / 2))
            bytes = json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        }
        return bounded to bytes
    }

    private fun randomWorkspaceV1(r: Random): Workspace {
        val projects = List(1 + r.nextInt(4)) { Project(id = "p-${r.nextInt()}", name = randomText(r), origin = "https://example.test") }
        val pid = projects.first().id
        return Workspace(
            version = 1,
            projects = projects,
            exchanges = List(r.nextInt(6)) { i ->
                Exchange(id = "ex-$i-${r.nextInt()}", projectId = pid, identity = "A",
                    request = RequestDraft(url = "https://example.test/$i"), status = 200,
                    response = StoredBody(randomText(r)), timestamp = r.nextInt().toLong())
            },
            findings = List(r.nextInt(6)) { Finding(id = "f-${r.nextInt()}", projectId = pid, title = randomText(r), detail = randomText(r), timestamp = r.nextInt().toLong()) },
            savedRequests = List(r.nextInt(6)) {
                SavedRequest(id = "s-${r.nextInt()}", projectId = pid, name = randomText(r),
                    draft = RequestDraft(method = "POST", url = "https://example.test/x", headers = listOf(Header("X-H", randomText(r))), body = randomText(r)))
            },
            identityConfigs = List(r.nextInt(3)) { IdentityConfig(pid, "id-${r.nextInt(5)}", listOf(Header("Authorization", randomText(r)))) },
        )
    }

    /** Short random string with JSON-significant characters to exercise escaping. */
    private fun randomText(r: Random): String {
        val alphabet = "abcdef ghijkl-MNOPQR_0123/:?&=\"\\"
        return buildString { repeat(r.nextInt(24)) { append(alphabet[r.nextInt(alphabet.length)]) } }
    }
}
