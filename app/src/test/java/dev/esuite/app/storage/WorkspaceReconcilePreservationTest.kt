package dev.esuite.app.storage

import dev.esuite.app.core.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceReconcilePreservationTest {
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
