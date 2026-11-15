package dev.esuite.app.storage

import dev.esuite.app.core.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceReconcilePreservationTest {
    @Test fun lowHeapBudgetPrunesOnlyHistoryAndRejectsDurableOverflow() {
        val budget = WorkspacePersistence.byteBudgetForHeap(16L * 1024 * 1024)
        assertTrue(budget in 1..(2 * 1024 * 1024))
        assertEquals(Limits.VAULT_BYTES, WorkspacePersistence.byteBudgetForHeap(1024L * 1024 * 1024))
        val p = Project(name = "Low heap", origin = "https://example.test")
        val note = Finding(projectId = p.id, title = "Durable", detail = "Keep me")
        val snapshot = Workspace(projects = listOf(p), findings = listOf(note), exchanges = List(12) {
            Exchange(projectId = p.id, identity = "A", request = RequestDraft(url = p.origin), response = StoredBody("x".repeat(Limits.RESPONSE_BYTES))) })
        val (persisted, bytes) = WorkspacePersistence.prepare(snapshot, budget)
        assertTrue(bytes.size <= budget)
        assertTrue(persisted.exchanges.size < snapshot.exchanges.size)
        assertEquals(snapshot.findings, persisted.findings)
        assertThrows(WorkspacePersistence.CapacityExceeded::class.java) {
            WorkspacePersistence.prepare(snapshot.copy(exchanges = emptyList(), findings = listOf(note.copy(detail = "x".repeat(budget + 1)))), budget)
        }
    }
    @Test fun fittingSaveReturnsTheSameWorkspaceAndDoesNotUndoConcurrentDeletion() {
        val p = Project(name = "Fixture", origin = "https://example.test")
        val capture = Exchange(projectId = p.id, identity = "A", request = RequestDraft(url = p.origin))
        val snapshot = Workspace(projects = listOf(p), exchanges = listOf(capture))
        val saved = WorkspacePersistence.prepare(snapshot).first
        assertSame(snapshot, saved)
        val current = snapshot.copy(exchanges = emptyList())
        assertEquals(current, WorkspacePersistence.reconcile(current, snapshot, saved))
    }
    @Test fun durableOverflowFailsBeforeAnEncryptedWriteCanBegin() {
        val p = Project(name = "Fixture", origin = "https://example.test")
        val oversized = Workspace(projects = listOf(p), findings = listOf(Finding(projectId = p.id,
            title = "Oversized fixture", detail = "x".repeat(Limits.VAULT_BYTES + 1))))
        assertThrows(IllegalArgumentException::class.java) { WorkspacePersistence.prepare(oversized) }
    }
}
