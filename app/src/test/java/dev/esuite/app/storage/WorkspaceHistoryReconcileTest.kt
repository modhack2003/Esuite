package dev.esuite.app.storage

import dev.esuite.app.core.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceHistoryReconcileTest {
    @Test fun realWriterPreparationPrunesAndReconciliationPreservesConcurrentChanges() {
        val project = Project(name = "Fixture", origin = "https://example.test")
        val body = "x".repeat(Limits.RESPONSE_BYTES)
        val snapshot = Workspace(projects = listOf(project), exchanges = List(80) {
            Exchange(projectId = project.id, identity = "A", request = RequestDraft(url = project.origin, body = body), response = StoredBody(body)) })
        val (persisted, bytes) = WorkspacePersistence.prepare(snapshot)
        assertTrue(bytes.size <= Limits.VAULT_BYTES)
        assertTrue(persisted.exchanges.size < snapshot.exchanges.size)
        assertEquals(persisted, Json.decodeFromString<Workspace>(bytes.toString(Charsets.UTF_8)))
        assertEquals(persisted, WorkspacePersistence.reconcile(snapshot, snapshot, persisted))
        val capture = Exchange(projectId = project.id, identity = "B", request = RequestDraft(url = project.origin))
        val finding = Finding(projectId = project.id, title = "New edit", detail = "Arrived during save")
        val current = snapshot.copy(exchanges = listOf(capture) + snapshot.exchanges, findings = listOf(finding))
        val reconciled = WorkspacePersistence.reconcile(current, snapshot, persisted)
        assertEquals(listOf(capture) + persisted.exchanges, reconciled.exchanges)
        assertEquals(listOf(finding), reconciled.findings)
    }
}
