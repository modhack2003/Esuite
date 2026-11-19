package dev.esuite.app.core

object WorkspaceBudget {
    /** Half the vault is reserved for capture history; only history may be pruned automatically. */
    const val DURABLE_BYTES = Limits.VAULT_BYTES / 2

    /** Reserve half the vault for capture history; only history may be pruned automatically. */
    fun requireDurableCapacity(workspace: Workspace) {
        require(WorkspaceEncoding.sizeUpTo(workspace.copy(exchanges = emptyList()), DURABLE_BYTES) != null) { "Saved collections/notes exceed the 16 MiB durable-data budget. Remove saved items before adding more." }
    }

    /**
     * Durable bytes [workspace] still has available, and 0 once it fills the budget. Used where
     * durable data is added OUTSIDE an admission check — currently the schema's evidence backfill,
     * which must not upgrade a vault into a state where every encrypted save exceeds capacity.
     */
    fun durableHeadroom(workspace: Workspace): Int =
        WorkspaceEncoding.sizeUpTo(workspace.copy(exchanges = emptyList()), DURABLE_BYTES)?.let { DURABLE_BYTES - it } ?: 0
}
