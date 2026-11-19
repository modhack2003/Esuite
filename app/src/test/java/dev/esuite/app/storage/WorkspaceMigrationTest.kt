package dev.esuite.app.storage

import dev.esuite.app.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class WorkspaceMigrationTest {
    private val json = Json { encodeDefaults = true }
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    @Test fun deployedVersionOneCrossesTheActualMigrationAndRetainsAllFields() {
        val p = Project(name = "Old fixture", origin = "https://example.test")
        val old = Workspace(version = 1, projects = listOf(p),
            findings = listOf(Finding(projectId = p.id, title = "Note", detail = "Keep me")),
            exchanges = listOf(Exchange(projectId = p.id, identity = "A", request = RequestDraft(url = p.origin))),
            savedRequests = listOf(SavedRequest(projectId = p.id, name = "Saved", draft = RequestDraft(url = p.origin))),
            identityConfigs = listOf(IdentityConfig(p.id, "B", listOf(Header("X-Auth", "synthetic")))))
        val document = VaultCipher.open(VaultCipher.seal(json.encodeToString(old).toByteArray(), key), key).toString(Charsets.UTF_8)
        assertEquals(old.copy(version = WorkspaceSchema.CURRENT_VERSION), WorkspaceSchema.migrate(document))
    }

    /** v1 and v2 both reach v3, and the v2 -> v3 step REPAIRS deployed findings instead of defaulting them. */
    @Test fun deployedVersionsReachVersionThreeAndBackfillResolvableEvidenceOnly() {
        assertEquals(3, WorkspaceSchema.CURRENT_VERSION)
        val p = Project(id = "p-backfill", name = "Backfill fixture", origin = "https://example.test")
        val live = Exchange(id = "live-1", projectId = p.id, identity = "A",
            request = RequestDraft("POST", p.origin + "/api", listOf(Header("X-Test", "synthetic")), "request-body"),
            status = 200, responseHeaders = listOf(Header("Content-Type", "application/json")),
            response = StoredBody("response-body"), durationMs = 7, timestamp = 1_000L)
        val finding = Finding(id = "f-1", projectId = p.id, title = "Note", detail = "Keep me",
            evidenceIds = listOf(live.id, "gone-1"), timestamp = 2_000L)
        for (version in listOf(1, 2)) {
            val old = Workspace(version = version, projects = listOf(p), exchanges = listOf(live), findings = listOf(finding),
                savedRequests = listOf(SavedRequest(id = "s-1", projectId = p.id, name = "Saved", draft = RequestDraft(url = p.origin))),
                identityConfigs = listOf(IdentityConfig(p.id, "B", listOf(Header("X-Auth", "synthetic")))))
            val document = VaultCipher.open(VaultCipher.seal(json.encodeToString(old).toByteArray(), key), key).toString(Charsets.UTF_8)
            val migrated = WorkspaceSchema.migrate(document)
            val repaired = migrated.findings.single()
            assertEquals("v$version must migrate to the current version", 3, migrated.version)
            assertEquals("v$version: a resolvable id must be snapshotted", listOf(EvidenceRetention.snapshot(live)), repaired.evidence)
            assertEquals("v$version: an unresolvable id must stay missing", listOf("gone-1"), EvidenceRetention.missing(repaired))
            assertEquals("v$version: references must be untouched", finding.evidenceIds, repaired.evidenceIds)
            assertEquals("v$version: every other field must survive the migration",
                old.copy(version = 3, findings = listOf(finding.copy(evidence = listOf(EvidenceRetention.snapshot(live))))), migrated)
        }
    }

    /**
     * A v2 upgrade that lands at the durable budget. The backfill adds durable bytes where no
     * admission check runs, so it is bounded by the same budget `addFinding` admits a finding under:
     * the upgraded workspace must still be saveable, and a reference the remaining headroom cannot
     * afford must stay in its explicit missing state rather than being half-restored or dropped.
     */
    @Test fun versionTwoUpgradeAtTheDurableBudgetLeavesUnaffordableEvidenceMissing() {
        val p = Project(id = "p-full", name = "At-capacity fixture", origin = "https://example.test")
        val live = Exchange(id = "live-full", projectId = p.id, identity = "A",
            request = RequestDraft("POST", p.origin + "/api", listOf(Header("X-Test", "synthetic")), "request-body"),
            status = 200, response = StoredBody("response-body"), durationMs = 7, timestamp = 1_000L)
        val note = Finding(id = "f-full", projectId = p.id, title = "Note", detail = "",
            evidenceIds = listOf(live.id), timestamp = 2_000L)
        val base = Workspace(version = 2, projects = listOf(p), findings = listOf(note))
        val baseBytes = WorkspaceEncoding.sizeUpTo(base, WorkspaceBudget.DURABLE_BYTES)
        assertNotNull("the empty fixture must fit the durable budget", baseBytes)
        val snapshotCost = json.encodeToString(Exchange.serializer(), EvidenceRetention.snapshot(live)).toByteArray(Charsets.UTF_8).size
        // Sized so the document still fits but the headroom left is strictly less than one snapshot.
        val filler = "x".repeat(WorkspaceBudget.DURABLE_BYTES - baseBytes!! - snapshotCost / 2)
        val finding = note.copy(detail = filler)
        val old = base.copy(exchanges = listOf(live), findings = listOf(finding))
        assertTrue("the fixture must leave less headroom than one snapshot costs",
            WorkspaceBudget.durableHeadroom(old) in 1 until snapshotCost)

        val migrated = WorkspaceSchema.migrate(json.encodeToString(old))
        assertEquals("the upgrade must still reach the current version", 3, migrated.version)
        val repaired = migrated.findings.single()
        assertEquals("an unaffordable reference must stay missing", listOf(live.id), EvidenceRetention.missing(repaired))
        assertEquals("the finding must survive the bounded upgrade untouched", finding, repaired)
        assertEquals("every other field must survive the bounded upgrade", old.copy(version = 3), migrated)
        // The upgraded vault must remain saveable: the backfill respects the admission budget.
        WorkspaceBudget.requireDurableCapacity(migrated)
    }

    /** A v3 document already carrying retained evidence is returned unchanged. */
    @Test fun currentVersionDocumentsWithRetainedEvidenceRoundTripUnchanged() {
        val p = Project(id = "p-v3", name = "Current fixture", origin = "https://example.test")
        val snapshot = EvidenceRetention.snapshot(Exchange(id = "ex-v3", projectId = p.id, identity = "A",
            request = RequestDraft(url = p.origin + "/api", body = "kept"), status = 200, response = StoredBody("kept"), timestamp = 3_000L))
        val current = Workspace(projects = listOf(p), exchanges = listOf(snapshot),
            findings = listOf(Finding(id = "f-v3", projectId = p.id, title = "t", detail = "d",
                evidenceIds = listOf(snapshot.id), evidence = listOf(snapshot), timestamp = 4_000L)))
        val document = VaultCipher.open(VaultCipher.seal(json.encodeToString(current).toByteArray(), key), key).toString(Charsets.UTF_8)
        assertEquals(current, WorkspaceSchema.migrate(document))
    }
    @Test fun currentVersionIsPreservedAndUnsupportedDocumentsFailClosed() {
        val current = Workspace()
        assertEquals(current, WorkspaceSchema.migrate(json.encodeToString(current)))
        for (document in listOf("{}", "{\"version\":999}", "{\"version\":0}", "{\"version\":\"bad\"}", "[]", "bad")) {
            assertThrows(Exception::class.java) { WorkspaceSchema.migrate(document) }
        }
    }
}
