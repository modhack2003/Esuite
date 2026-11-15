package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.Permission
import java.util.concurrent.atomic.AtomicReference

/**
 * runtime-robustness-fixes · Task 3 · Property 3 (Bug Condition)
 * Imports Parse Off-Thread with a Version-Safe Merge.
 *
 * JVM-SUPPORTING layer for Defect 3 (large OpenAPI/HAR imports block the UI thread). The actual bug
 * — `importOpenApi`/`importHar` running `ApiImports.openApi`/`har` parsing and
 * `WorkspaceBudget.requireDurableCapacity` (a full `Json.encodeToString`) synchronously on the main
 * thread — can only be triggered with the real `AndroidViewModel` + main looper, so the exploration
 * test that MUST FAIL on unfixed code is the instrumented case in
 * [dev.esuite.app.DeviceBoundaryTest] (CI emulator matrix, `:app:connectedDebugAndroidTest`).
 *
 * This JVM class is NOT the bug trigger and is EXPECTED TO PASS on unfixed code: Task 7 only
 * relocates the parse/budget work off the main thread, it does not change what that work does. The
 * guarantee that must still hold after the relocation is that the relocated work is pure CPU-only —
 * it performs no network request (the "no requests executed during import" contract). These
 * assertions pin that guarantee down so the off-thread move in Task 7 cannot smuggle in I/O.
 *
 * What is asserted (all JVM, no `Context`, no server, no dispatcher):
 *  1. `ApiImports.openApi` / `ApiImports.har` are pure: the same near-2 MiB input yields the same
 *     structural output across repeated calls (the random `SavedRequest.id`s aside), so the result
 *     depends only on the input — a hallmark of a side-effect-free function.
 *  2. `WorkspaceBudget.requireDurableCapacity` is a pure size-gate: its verdict depends only on the
 *     encoded durable bytes and IGNORES `exchanges` (adding capture history never changes it), and
 *     over the 16 MiB durable budget it throws with the exact unchanged message.
 *  3. A best-effort connect tripwire (a reversible `SecurityManager` that only denies socket
 *     connects) confirms running the parse + budget opens no network connection. It is installed
 *     defensively — if the platform forbids installing a `SecurityManager`, the tripwire is skipped
 *     rather than failing the build, while the purity/boundary assertions above still run.
 *
 * **Validates: Requirements 1.4, 1.5, 2.5, 2.6, 3.5, 3.6**
 */
class ImportThreadingTest {
    private val project = Project(name = "Imports", origin = "https://example.test")
    private val durableBudget = Limits.VAULT_BYTES / 2 // 16 MiB

    @Test fun openApiHarParseAndDurableBudgetAreDeterministicCpuOnlyWithNoNetwork() {
        val spec = largeOpenApiSpec(operations = 100, padBytes = 1_200_000)
        val har = largeHar(entries = 100, padBytes = 1_200_000)

        // Representative near-limit inputs, still within ApiImports' own 2 MiB guard.
        assertTrue("OpenAPI fixture should be a large, representative spec", spec.toByteArray(Charsets.UTF_8).size in 1_000_000..(2 * 1024 * 1024))
        assertTrue("HAR fixture should be a large, representative capture", har.toByteArray(Charsets.UTF_8).size in 1_000_000..(2 * 1024 * 1024))

        // (1) Purity via determinism. ids are random UUIDs, so compare the deterministic shape
        //     (name + draft) which is a pure function of the input.
        fun shape(list: List<SavedRequest>) = list.map { it.name to it.draft }
        val openApiA = ApiImports.openApi(spec, project)
        val openApiB = ApiImports.openApi(spec, project)
        assertEquals("OpenAPI import must yield one draft per operation", 100, openApiA.size)
        assertEquals("ApiImports.openApi must be deterministic (pure function of its input)", shape(openApiA), shape(openApiB))

        val harA = ApiImports.har(har, project)
        val harB = ApiImports.har(har, project)
        assertEquals("HAR import must yield one draft per in-scope entry", 100, harA.size)
        assertEquals("ApiImports.har must be deterministic (pure function of its input)", shape(harA), shape(harB))

        // (2) requireDurableCapacity is a pure size-gate that ignores exchanges.
        val fitting = Workspace(projects = listOf(project), savedRequests = openApiA)
        WorkspaceBudget.requireDurableCapacity(fitting) // within budget → no throw

        val withHeavyHistory = fitting.copy(
            exchanges = List(50) { i ->
                Exchange(
                    id = "ex-$i", projectId = project.id, identity = "A",
                    request = RequestDraft(url = "https://example.test/$i"),
                    response = StoredBody("y".repeat(256 * 1024)), timestamp = i.toLong(),
                )
            },
        )
        // ~12.5 MiB of capture history added on top of a fitting durable set; the gate must still
        // pass because it encodes `copy(exchanges = emptyList())` — exchanges never count.
        WorkspaceBudget.requireDurableCapacity(withHeavyHistory)

        val overBudget = Workspace(
            projects = listOf(project),
            savedRequests = listOf(
                SavedRequest(
                    projectId = project.id, name = "oversized",
                    draft = RequestDraft(url = "https://example.test/", body = "z".repeat(durableBudget + 1024)),
                ),
            ),
        )
        val durableBytes = Json.encodeToString(overBudget.copy(exchanges = emptyList())).toByteArray(Charsets.UTF_8).size
        assertTrue("over-budget fixture must actually exceed the 16 MiB durable budget", durableBytes > durableBudget)
        val rejected = assertThrows(IllegalArgumentException::class.java) { WorkspaceBudget.requireDurableCapacity(overBudget) }
        assertEquals(
            "Saved collections/notes exceed the 16 MiB durable-data budget. Remove saved items before adding more.",
            rejected.message,
        )

        // (3) Best-effort genuine no-network guard. A SecurityManager that denies only socket
        //     connects (and permits everything else) proves the parse + budget open no connection.
        val tripped = AtomicReference<String?>(null)
        val previous = System.getSecurityManager()
        val installed = runCatching {
            System.setSecurityManager(object : SecurityManager() {
                override fun checkConnect(host: String, port: Int) {
                    tripped.set("$host:$port"); throw SecurityException("network blocked in purity test")
                }
                override fun checkConnect(host: String, port: Int, context: Any?) {
                    tripped.set("$host:$port"); throw SecurityException("network blocked in purity test")
                }
                override fun checkPermission(perm: Permission?) { /* allow everything except connect */ }
                override fun checkPermission(perm: Permission?, context: Any?) { /* allow everything except connect */ }
            })
        }.isSuccess
        try {
            ApiImports.openApi(spec, project)
            ApiImports.har(har, project)
            WorkspaceBudget.requireDurableCapacity(fitting)
        } finally {
            if (installed) runCatching { System.setSecurityManager(previous) }
        }
        if (installed) {
            assertNull(
                "ApiImports parsing / requireDurableCapacity opened a network connection — the import " +
                    "pipeline must stay pure CPU-only so the off-thread relocation (Task 7) preserves the " +
                    "no-network contract.",
                tripped.get(),
            )
        }
    }

    // ---- fixtures ----

    /** A large, valid OpenAPI 3 JSON spec: [operations] GET paths plus [padBytes] of ignored text. */
    private fun largeOpenApiSpec(operations: Int, padBytes: Int): String {
        val pad = "a".repeat(padBytes)
        val paths = (0 until operations).joinToString(",") { "\"/p$it\":{\"get\":{}}" }
        return "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"t\",\"description\":\"$pad\"},\"paths\":{$paths}}"
    }

    /** A large, valid HAR with [entries] in-scope GET requests plus [padBytes] of ignored text. */
    private fun largeHar(entries: Int, padBytes: Int): String {
        val pad = "a".repeat(padBytes)
        val items = (0 until entries).joinToString(",") {
            "{\"request\":{\"method\":\"GET\",\"url\":\"https://example.test/h$it\",\"headers\":[]}}"
        }
        return "{\"log\":{\"comment\":\"$pad\",\"entries\":[$items]}}"
    }
}
