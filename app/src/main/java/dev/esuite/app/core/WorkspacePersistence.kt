package dev.esuite.app.core

/** Pure preparation/reconciliation shared by the real encrypted writer and its tests. */
object WorkspacePersistence {
    fun prepare(workspace: Workspace): Pair<Workspace, ByteArray> {
        var bounded = workspace
        while (true) {
            val size = WorkspaceEncoding.sizeUpTo(bounded, Limits.VAULT_BYTES)
            if (size != null) return bounded to WorkspaceEncoding.encodeExact(bounded, size)
            require(bounded.exchanges.isNotEmpty()) { "Workspace exceeds the encrypted vault budget" }
            bounded = bounded.copy(exchanges = bounded.exchanges.take(bounded.exchanges.size / 2))
        }
    }

    fun reconcile(current: Workspace, snapshot: Workspace, persisted: Workspace): Workspace {
        val removed = snapshot.exchanges.map { it.id }.toSet() - persisted.exchanges.map { it.id }.toSet()
        return if (removed.isEmpty()) current else current.copy(exchanges = current.exchanges.filterNot { it.id in removed })
    }

    fun sameDurable(a: Workspace, b: Workspace): Boolean = a.version == b.version &&
        a.projects == b.projects && a.findings == b.findings && a.savedRequests == b.savedRequests && a.identityConfigs == b.identityConfigs
}
