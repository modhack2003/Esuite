package dev.esuite.app.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Single source of truth for the persisted workspace schema version plus the ordered,
 * forward-only migration registry.
 *
 * `CURRENT_VERSION` is the version this build writes (see [Workspace.version]'s default) and
 * migrates toward. [migrate] decodes a decrypted UTF-8 workspace document, applies the ordered
 * forward-only migrations `v1 -> ... -> CURRENT_VERSION`, and returns the migrated [Workspace].
 *
 * Each migration is a pure `JsonObject -> JsonObject` transform keyed by its SOURCE version:
 * `migrations[n]` upgrades a version-`n` document to version `n + 1`. New schema changes add one
 * entry here and bump [CURRENT_VERSION]; the transform must stamp the new `version` and supply a
 * sensible default for any field it introduces or renames so the result deserializes cleanly under
 * [Json] with `encodeDefaults = true` and still satisfies the existing record limits.
 *
 * This is the versioned-decode seam `WorkspaceVault.load()` routes its decrypted bytes through
 * (the encrypted vault uses this seam). Fail-closed is preserved: a `version` greater than [CURRENT_VERSION], a
 * missing/non-integer `version`, or a missing migration step throws "Unsupported workspace version"
 * without performing any write.
 */
object WorkspaceSchema {
    /** The schema version this build writes and migrates toward. */
    const val CURRENT_VERSION: Int = 4

    private val json = Json { encodeDefaults = true }

    /**
     * Ordered, forward-only migrations keyed by SOURCE version. `migrations[n]` upgrades a
     * version-`n` document to version `n + 1`. The `v1 -> v2` step is a no-op shape upgrade that
     * only stamps `version = 2`, establishing the template for future schema changes. The
     * `v2 -> v3` step backfills [Finding.evidence] (see [backfillEvidence]).
     */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = linkedMapOf(
        1 to { document -> stampVersion(document, 2) },
        2 to { document -> backfillEvidence(document) },
        // Version 4 records completeness on new evidence. Existing snapshots keep their bytes
        // and can resolve legacy request bodies against their original capture when available.
        3 to { document -> stampVersion(document, 4) },
    )

    /**
     * `v2 -> v3`: [Finding] gains `evidence`, a bounded immutable snapshot of each referenced
     * exchange. A deployed v2 vault only stored `evidenceIds`, so this step REPAIRS it — every id
     * that still resolves to a live exchange is snapshotted rather than merely defaulted to empty.
     * Ids whose exchange is already gone keep reporting the explicit missing state.
     *
     * The repair is BOUNDED by the same durable budget `addFinding` admits a finding under, because
     * nothing checks capacity on a load: an unbounded backfill could upgrade a deployed vault into a
     * state where every encrypted save throws `CapacityExceeded` and the durable region is the one
     * region `WorkspacePersistence.prepare` cannot prune. References the remaining headroom cannot
     * afford keep reporting the explicit missing state, so the repair stays opportunistic.
     *
     * Optional finding fields introduced after v3 must have defaults and must be omitted here so
     * the generated findings still use the v3 JSON shape. Any incompatible shape change must use
     * a frozen DTO for this step instead of the live serializer.
     */
    private fun backfillEvidence(document: JsonObject): JsonObject {
        val exchanges = document["exchanges"]?.let { json.decodeFromJsonElement(ListSerializer(Exchange.serializer()), it) }.orEmpty()
        val findings = document["findings"]?.let { json.decodeFromJsonElement(ListSerializer(Finding.serializer()), it) }.orEmpty()
        val headroom = WorkspaceBudget.durableHeadroom(json.decodeFromJsonElement(Workspace.serializer(), document))
        val backfilled = EvidenceRetention.backfill(findings, exchanges, headroom, ::durableCost)
        return JsonObject(document.toMutableMap().apply {
            put("exchanges", json.encodeToJsonElement(ListSerializer(Exchange.serializer()), exchanges))
            put("findings", json.encodeToJsonElement(ListSerializer(Finding.serializer()), backfilled))
            put("version", JsonPrimitive(3))
        })
    }

    /** Durable bytes one retained snapshot adds: its own encoding plus the array separator. */
    private fun durableCost(snapshot: Exchange): Int =
        json.encodeToString(Exchange.serializer(), snapshot).toByteArray(Charsets.UTF_8).size + 1

    private fun stampVersion(document: JsonObject, version: Int): JsonObject =
        JsonObject(document.toMutableMap().apply { put("version", JsonPrimitive(version)) })

    /**
     * Decode the decrypted UTF-8 workspace JSON, apply the ordered forward-only migrations
     * `v1..CURRENT_VERSION`, and return the migrated [Workspace].
     *
     * Throws [IllegalArgumentException] with "Unsupported workspace version" when the `version` is
     * absent/non-integer, is newer than [CURRENT_VERSION], or a required migration step is missing.
     */
    fun migrate(document: String): Workspace {
        var obj = json.parseToJsonElement(document).jsonObject
        val primitive = obj["version"] as? JsonPrimitive
        var version = primitive?.takeUnless { it.isString }?.intOrNull
            ?: throw IllegalArgumentException("Unsupported workspace version")
        require(version in 1..CURRENT_VERSION) { "Unsupported workspace version" }
        while (version < CURRENT_VERSION) {
            val step = migrations[version] ?: throw IllegalArgumentException("Unsupported workspace version")
            obj = step(obj)
            version += 1
        }
        return json.decodeFromJsonElement(Workspace.serializer(), obj)
    }
}
