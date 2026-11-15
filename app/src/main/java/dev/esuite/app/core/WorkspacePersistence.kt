package dev.esuite.app.core

/** Pure preparation/reconciliation shared by the real encrypted writer and its tests. */
object WorkspacePersistence {
    class CapacityExceeded : IllegalArgumentException("Saved data exceeds this device's encrypted-save memory budget. Remove saved items before retrying.")
    /** Leave room for model strings, cipher/provider buffers and authenticated reload parsing. */
    fun byteBudgetForHeap(maxHeap: Long): Int = minOf(Limits.VAULT_BYTES.toLong(), maxHeap / 12).coerceAtLeast(1).toInt()

    fun prepare(workspace: Workspace, byteLimit: Int = Limits.VAULT_BYTES): Pair<Workspace, ByteArray> {
        require(byteLimit in 1..Limits.VAULT_BYTES)
        var bounded = workspace
        while (true) {
            val size = WorkspaceEncoding.sizeUpTo(bounded, byteLimit)
            if (size != null) return bounded to WorkspaceEncoding.encodeExact(bounded, size)
            if (bounded.exchanges.isEmpty()) throw CapacityExceeded()
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
