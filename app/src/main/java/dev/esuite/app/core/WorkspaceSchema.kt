package dev.esuite.app.core

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
    const val CURRENT_VERSION: Int = 2

    private val json = Json { encodeDefaults = true }

    /**
     * Ordered, forward-only migrations keyed by SOURCE version. `migrations[n]` upgrades a
     * version-`n` document to version `n + 1`. The `v1 -> v2` step is a no-op shape upgrade that
     * only stamps `version = 2`, establishing the template for future schema changes.
     */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = linkedMapOf(
        1 to { document -> stampVersion(document, 2) },
    )

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
