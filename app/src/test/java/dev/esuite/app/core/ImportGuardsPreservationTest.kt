package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test

/**
 * runtime-robustness-fixes · Task 4 · Property 4 (Preservation)
 * Import Guards Unchanged — the parse-side guards Defect 3's off-thread move must preserve.
 *
 * Observation-first baseline for Defect 3 (large imports block the UI thread). Task 7 only
 * RELOCATES `ApiImports.openApi`/`har` off the main thread; it does not change what they reject or
 * how. These cases pin the exact over-limit / over-size guard messages (and that `importCurl` still
 * yields a single draft with scope enforcement intact) so the relocation cannot silently weaken
 * them. EXPECTED TO PASS on unfixed code; re-run unchanged to guard the fix in task 7.
 *
 * Scope split (as directed by the task): the guards that live at the `ApiImports`/`WorkspaceBudget`
 * level and need no `AndroidViewModel` are asserted here on the JVM. The ViewModel-surfaced messages
 * — the mergeSaved "Collection limit is 100 requests…" and the generic "Import failed"/"HAR import
 * failed" fallbacks — are deferred to the instrumented [dev.esuite.app.DeviceBoundaryTest] (CI
 * emulator matrix, `:app:connectedDebugAndroidTest`). The 16 MiB durable-budget message
 * ("Saved collections/notes exceed the 16 MiB durable-data budget…") and the pure CPU-only /
 * no-network guarantee are already pinned by [ImportThreadingTest] and are not duplicated here.
 *
 * `ApiImports` performs no I/O, so every guard below throws synchronously with no network request —
 * the no-network contract is preserved by construction.
 *
 * **Validates: Requirements 3.5, 3.6**
 */
class ImportGuardsPreservationTest {
    private val project = Project(name = "Imports", origin = "https://example.test")

    @Test fun openApiImportGuardsRejectOverLimitAndOverSizeWithExactMessages() {
        // >100 in-scope operations → ApiImports' own operation cap, with its exact message.
        val tooMany = buildOpenApiSpec(operations = 101)
        val overLimit = assertThrows(IllegalArgumentException::class.java) { ApiImports.openApi(tooMany, project) }
        assertEquals("Specification exceeds 100 operation import limit", overLimit.message)

        // >2 MiB input → size guard, with its exact message.
        val huge = "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"t\",\"description\":\"" +
            "a".repeat(2 * 1024 * 1024 + 16) + "\"},\"paths\":{}}"
        assertTrue("over-size fixture must exceed 2 MiB", huge.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
        val overSize = assertThrows(IllegalArgumentException::class.java) { ApiImports.openApi(huge, project) }
        assertEquals("Specification exceeds 2 MiB", overSize.message)
    }

    @Test fun harImportGuardsRejectOverEntryOverInScopeAndOverSizeWithExactMessages() {
        // >200 entries → entry-count guard (checked before mapping), with its exact message.
        val tooManyEntries = buildHar(entries = 201, origin = project.origin)
        val overEntries = assertThrows(IllegalArgumentException::class.java) { ApiImports.har(tooManyEntries, project) }
        assertEquals("HAR entry limit exceeded (200)", overEntries.message)

        // 101 in-scope entries (within the 200-entry cap) → in-scope cap, with its exact message.
        val tooManyInScope = buildHar(entries = 101, origin = project.origin)
        val overInScope = assertThrows(IllegalArgumentException::class.java) { ApiImports.har(tooManyInScope, project) }
        assertEquals("HAR contains more than 100 in-scope requests", overInScope.message)

        // >2 MiB input → size guard, with its exact message.
        val huge = "{\"log\":{\"comment\":\"" + "a".repeat(2 * 1024 * 1024 + 16) + "\",\"entries\":[]}}"
        assertTrue("over-size fixture must exceed 2 MiB", huge.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
        val overSize = assertThrows(IllegalArgumentException::class.java) { ApiImports.har(huge, project) }
        assertEquals("HAR exceeds 2 MiB", overSize.message)
    }

    @Test fun importCurlProducesExactlyOneDraftAndKeepsScopeEnforcement() {
        // importCurl is out of Defect 3's scope (single draft, no large parse or budget encode) and
        // must behave exactly as today: one draft, method/url/body preserved.
        val draft = ApiImports.curl("curl -X POST 'https://example.test/api' --data-raw '{\"a\":1}'", project.origin)
        assertEquals("POST", draft.method)
        assertEquals("https://example.test/api", draft.url)
        assertEquals("{\"a\":1}", draft.body)
        // Scope enforcement is preserved: an out-of-origin cURL target is still rejected.
        assertThrows(IllegalArgumentException::class.java) { ApiImports.curl("curl https://outside.test/api", project.origin) }
    }

    // ---- fixtures ----

    private fun buildOpenApiSpec(operations: Int): String {
        val paths = (0 until operations).joinToString(",") { "\"/p$it\":{\"get\":{}}" }
        return "{\"openapi\":\"3.0.3\",\"paths\":{$paths}}"
    }

    private fun buildHar(entries: Int, origin: String): String {
        val items = (0 until entries).joinToString(",") {
            "{\"request\":{\"method\":\"GET\",\"url\":\"$origin/h$it\",\"headers\":[]}}"
        }
        return "{\"log\":{\"entries\":[$items]}}"
    }
}
