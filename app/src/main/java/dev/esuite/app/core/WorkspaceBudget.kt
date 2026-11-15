package dev.esuite.app.core

object WorkspaceBudget {
    /** Reserve half the vault for capture history; only history may be pruned automatically. */
    fun requireDurableCapacity(workspace: Workspace) {
        require(WorkspaceEncoding.sizeUpTo(workspace.copy(exchanges = emptyList()), Limits.VAULT_BYTES / 2) != null) { "Saved collections/notes exceed the 16 MiB durable-data budget. Remove saved items before adding more." }
    }
}
