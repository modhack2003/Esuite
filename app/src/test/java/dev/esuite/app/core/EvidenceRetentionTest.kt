package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * QA-01 · retained evidence is independent of disposable history.
 *
 * Seeded-property style (see `WorkspacePreservationTest`): each case loops over generated
 * workspaces from a fixed `Random(0x51E)` and prints the counterexample in every assertion message,
 * so a failure identifies the exact iteration that broke the invariant.
 */
class EvidenceRetentionTest {
    private val random = Random(0x51E)

    /**
     * The invariant: a snapshot stored on a [Finding] is unchanged by every path that disposes of
     * [Workspace.exchanges] — `clearHistory()`'s project filter, `record()`'s rollover past
     * [Limits.HISTORY], and [WorkspacePersistence.prepare]'s exchange halving.
     */
    @Test fun retainedEvidenceSurvivesEveryHistoryDisposalPath() {
        repeat(24) { iteration ->
            val project = Project(id = "p-$iteration", name = "Retention fixture", origin = "https://example.test")
            val captured = List(1 + random.nextInt(6)) { exchange(project.id, "cap-$iteration-$it") }
            val findings = captured.map { e ->
                Finding(id = "f-${e.id}", projectId = project.id, title = "note ${e.id}", detail = randomText(),
                    evidenceIds = listOf(e.id), evidence = listOf(EvidenceRetention.snapshot(e)), timestamp = 1_000L)
            }
            val workspace = Workspace(projects = listOf(project), exchanges = captured, findings = findings)
            val counterexample = "seed 0x51E iteration $iteration · ${captured.size} captures"

            // clearHistory(): every exchange belonging to the active project leaves memory.
            val cleared = workspace.copy(exchanges = workspace.exchanges.filterNot { it.projectId == project.id })
            assertTrue("history clear must leave no exchange · $counterexample", cleared.exchanges.isEmpty())
            assertEquals("retained evidence must survive a history clear · $counterexample", findings, cleared.findings)

            // record(): rollover past Limits.HISTORY evicts every original capture.
            val capturedIds = captured.map { it.id }.toSet()
            var rolled = workspace
            repeat(Limits.HISTORY + 1) { i ->
                rolled = rolled.copy(exchanges = (listOf(exchange(project.id, "roll-$iteration-$i")) + rolled.exchanges).take(Limits.HISTORY))
            }
            assertEquals("rollover must keep history bounded · $counterexample", Limits.HISTORY, rolled.exchanges.size)
            assertTrue("rollover must evict the original captures · $counterexample", rolled.exchanges.none { it.id in capturedIds })
            assertEquals("retained evidence must survive rollover · $counterexample", findings, rolled.findings)

            // prepare(): at a durable-sized budget the save-time prune halves history away entirely.
            val durable = WorkspaceEncoding.sizeUpTo(workspace.copy(exchanges = emptyList()), Limits.VAULT_BYTES)
            assertNotNull("the durable fixture must be measurable · $counterexample", durable)
            val (pruned, bytes) = WorkspacePersistence.prepare(workspace, durable!!)
            assertTrue("prune must drop all history at a durable-sized budget · $counterexample", pruned.exchanges.isEmpty())
            assertTrue("pruned bytes must fit the budget · $counterexample", bytes.size <= durable)
            assertEquals("retained evidence must survive the prune · $counterexample", findings, pruned.findings)
        }
    }

    /** Snapshot bodies are capped at [Limits.EVIDENCE_BYTES] and a cut response keeps the truncation label. */
    @Test fun snapshotCapsBodiesAtTheEvidenceLimitAndLabelsTruncation() {
        repeat(12) { iteration ->
            val overflow = 1 + random.nextInt(4096)
            val source = Exchange(id = "cap-$iteration", projectId = "p", identity = "A",
                request = RequestDraft("POST", "https://example.test/", body = "r".repeat(Limits.EVIDENCE_BYTES + overflow)),
                status = 200, response = StoredBody("s".repeat(Limits.EVIDENCE_BYTES + overflow)), timestamp = 1_000L)
            val snapshot = EvidenceRetention.snapshot(source)
            val counterexample = "seed 0x51E iteration $iteration · overflow $overflow"
            assertEquals("the request body must be capped · $counterexample",
                Limits.EVIDENCE_BYTES, snapshot.request.body.toByteArray(Charsets.UTF_8).size)
            assertEquals("the response body must be capped · $counterexample",
                Limits.EVIDENCE_BYTES, snapshot.response.value.toByteArray(Charsets.UTF_8).size)
            assertTrue("a cut response must be labelled truncated · $counterexample", snapshot.response.truncated)
            assertEquals("the cap must retain a prefix · $counterexample",
                source.response.value.take(Limits.EVIDENCE_BYTES), snapshot.response.value)
            // An in-limit exchange is retained verbatim and is never labelled truncated.
            val small = source.copy(request = source.request.copy(body = randomText()), response = StoredBody(randomText()))
            assertEquals("an in-limit snapshot must be verbatim · $counterexample", small, EvidenceRetention.snapshot(small))
            // A cap landing inside a multi-byte character must not retain a partial character.
            val multiByte = source.copy(response = StoredBody("ab" + "\u20AC".repeat(Limits.EVIDENCE_BYTES)))
            val capped = EvidenceRetention.snapshot(multiByte).response.value
            assertTrue("a multi-byte cap must stay within the limit · $counterexample",
                capped.toByteArray(Charsets.UTF_8).size <= Limits.EVIDENCE_BYTES)
            assertFalse("a multi-byte cap must not retain a partial character · $counterexample", capped.contains('\uFFFD'))
        }
    }

    /** `missing()` is exactly the references with no snapshot, and `backfill()` restores only resolvable ones. */
    @Test fun missingEqualsTheReferenceSetDifferenceAndBackfillRestoresResolvableIdsOnly() {
        repeat(32) { iteration ->
            val ids = List(1 + random.nextInt(6)) { "e-$iteration-$it" }
            val retained = ids.filter { random.nextBoolean() }
            val finding = Finding(id = "f-$iteration", projectId = "p", title = "t", detail = "d", evidenceIds = ids,
                evidence = retained.map { EvidenceRetention.snapshot(exchange("p", it)) }, timestamp = 1_000L)
            val counterexample = "seed 0x51E iteration $iteration · ids $ids · retained $retained"
            assertEquals("missing must equal the exact set difference · $counterexample",
                ids - retained.toSet(), EvidenceRetention.missing(finding))

            // Only the still-live half of the missing set can be backfilled.
            val resolvable = EvidenceRetention.missing(finding).filter { random.nextBoolean() }
            val live = resolvable.map { exchange("p", it) }
            val backfilled = EvidenceRetention.backfill(listOf(finding), live).single()
            assertEquals("backfill must snapshot exactly the resolvable ids · $counterexample",
                retained + resolvable, backfilled.evidence.map { it.id })
            assertEquals("backfill must leave unresolvable ids missing · $counterexample",
                EvidenceRetention.missing(finding) - resolvable.toSet(), EvidenceRetention.missing(backfilled))
            assertEquals("backfill must never touch the reference list · $counterexample", ids, backfilled.evidenceIds)
            assertEquals("backfill must store bounded snapshots · $counterexample",
                live.map(EvidenceRetention::snapshot), backfilled.evidence.drop(retained.size))
        }
    }

    private fun exchange(projectId: String, id: String) = Exchange(id = id, projectId = projectId,
        identity = if (random.nextBoolean()) "A" else "B",
        request = RequestDraft("POST", "https://example.test/$id", listOf(Header("X-Test", randomText())), randomText()),
        status = 200, responseHeaders = listOf(Header("Content-Type", "application/json")),
        response = StoredBody(randomText()), durationMs = random.nextInt(50).toLong(), timestamp = 1_000L)

    /** Short random string with JSON-significant characters to exercise escaping. */
    private fun randomText(): String {
        val alphabet = "abcdef ghijkl-MNOPQR_0123/:?&=\"\\"
        return buildString { repeat(random.nextInt(24)) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}
