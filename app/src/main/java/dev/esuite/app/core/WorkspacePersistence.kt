package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Pure preparation/reconciliation shared by the real encrypted writer and its tests. */
object WorkspacePersistence {
    private val json = Json { encodeDefaults = true }
    fun prepare(workspace: Workspace): Pair<Workspace, ByteArray> {
        var bounded = workspace
        var bytes = json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        while (bytes.size > Limits.VAULT_BYTES && bounded.exchanges.isNotEmpty()) {
            bounded = bounded.copy(exchanges = bounded.exchanges.take(bounded.exchanges.size / 2))
            bytes = json.encodeToString(bounded).toByteArray(Charsets.UTF_8)
        }
        require(bytes.size <= Limits.VAULT_BYTES) { "Workspace exceeds the encrypted vault budget" }
        return bounded to bytes
    }

    fun reconcile(current: Workspace, snapshot: Workspace, persisted: Workspace): Workspace {
        val removed = snapshot.exchanges.map { it.id }.toSet() - persisted.exchanges.map { it.id }.toSet()
        return if (removed.isEmpty()) current else current.copy(exchanges = current.exchanges.filterNot { it.id in removed })
    }

    fun sameDurable(a: Workspace, b: Workspace): Boolean = a.version == b.version &&
        a.projects == b.projects && a.findings == b.findings && a.savedRequests == b.savedRequests && a.identityConfigs == b.identityConfigs
}
