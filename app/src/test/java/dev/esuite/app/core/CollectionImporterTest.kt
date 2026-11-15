package dev.esuite.app.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class CollectionImporterTest {
    @Test fun parsingAndBudgetPreparationRunOnWorkerAndMergePreservesNewCaptures() = runBlocking {
        Executors.newSingleThreadExecutor { r -> Thread(r, "import-worker") }.asCoroutineDispatcher().use { worker ->
            val importer = CollectionImporter(worker)
            val p = Project(name = "Fixture", origin = "https://example.test")
            val snapshot = Workspace(projects = listOf(p))
            val requests = importer.parse {
                assertTrue(Thread.currentThread().name.startsWith("import-worker"))
                ApiImports.openApi("""{"openapi":"3.0.3","paths":{"/test":{"get":{}}}}""", p)
            }
            val prepared = importer.prepare(snapshot, p, requests)
            val newCapture = Exchange(projectId = p.id, identity = "A", request = RequestDraft(url = p.origin))
            val merged = prepared.apply(snapshot.copy(exchanges = listOf(newCapture)))!!
            assertEquals(requests, merged.savedRequests)
            assertEquals(listOf(newCapture), merged.exchanges)
            assertNull(prepared.apply(snapshot.copy(findings = listOf(Finding(projectId = p.id, title = "Changed", detail = "During parse")))))
            assertNull(prepared.apply(snapshot.copy(projects = emptyList())))
            val other = importer.prepare(snapshot, p, requests)
            assertNull(other.apply(merged)) // A second import must revalidate against the accepted first import.
        }
    }
}
