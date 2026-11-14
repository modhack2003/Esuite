package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WorkspaceBudget {
    /** Reserve half the vault for capture history; only history may be pruned automatically. */
    fun requireDurableCapacity(workspace: Workspace) {
        val durable = Json.encodeToString(workspace.copy(exchanges = emptyList())).toByteArray(Charsets.UTF_8)
        require(durable.size <= Limits.VAULT_BYTES / 2) { "Saved collections/notes exceed the 16 MiB durable-data budget. Remove saved items before adding more." }
    }
}
