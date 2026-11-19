package dev.esuite.app.browser

import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import dev.esuite.app.core.ProfileNames

object BrowserProfiles {
    /**
     * What a cleanup attempt actually achieved, so a caller can report it instead of aborting.
     *
     * [pending] names profiles the provider refused to remove because they are still loaded in
     * memory: destroying every WebView is not enough, since
     * [ProfileStore.deleteProfile] also rejects a profile loaded through the profile access APIs,
     * and there is no API to unload one. Those names are retried on the next launch.
     *
     * [deleted] means the provider ACCEPTED the deletion. It may schedule the on-disk cleanup
     * asynchronously, so this is not evidence of forensic erasure and must never be reported as one.
     */
    data class CleanupResult(
        val deleted: List<String> = emptyList(),
        val pending: List<String> = emptyList(),
        val unsupported: Boolean = false,
    ) {
        /** Honest status text shared by the ViewModel and the unreadable-vault recovery screen. */
        fun describe(): String = when {
            unsupported -> "This WebView provider does not support separate browser profiles, so ESuite created none to remove."
            pending.isNotEmpty() -> "Android WebView still has ${pending.joinToString(", ")} loaded in memory and would not delete ${if (pending.size == 1) "it" else "them"} yet. " +
                "${if (pending.size == 1) "It is" else "They are"} removed automatically when ESuite restarts; you can also retry now." +
                if (deleted.isEmpty()) "" else " Removed ${deleted.size} other browser profile${plural(deleted.size)}."
            deleted.isNotEmpty() -> "Removed ${deleted.size} ESuite browser profile${plural(deleted.size)}. Android WebView may finish deleting the files in the background."
            else -> "No ESuite browser profiles needed removing."
        }

        private fun plural(count: Int) = if (count == 1) "" else "s"
    }

    private val unsupported = CleanupResult(unsupported = true)

    private fun supported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Every ESuite-owned profile the provider currently reports; empty when MULTI_PROFILE is absent. */
    fun esuiteProfileNames(): List<String> =
        if (!supported()) emptyList() else ProfileStore.getInstance().allProfileNames.filter { ProfileNames.parse(it) != null }

    /** Must run on the UI thread after all WebViews using the profile have been destroyed. */
    fun clearProject(projectId: String): CleanupResult =
        delete(listOf("A", "B").map { ProfileNames.name(projectId, it) })

    /**
     * Must run on the UI thread. Deletes only ESuite profiles whose project id is no longer live, so
     * the pending set is DERIVED from the provider rather than from a persisted record.
     */
    fun sweepOrphans(liveProjectIds: Set<String>): CleanupResult {
        if (!supported()) return unsupported
        return delete(ProfileNames.orphans(ProfileStore.getInstance().allProfileNames, liveProjectIds))
    }

    /**
     * Must run on the UI thread. Vault-independent recovery: the provider's own profile list is the
     * only input, so this still works when the encrypted workspace cannot be read.
     */
    fun clearAllEsuiteOwned(): CleanupResult {
        if (!supported()) return unsupported
        return delete(esuiteProfileNames())
    }

    private fun delete(names: List<String>): CleanupResult {
        if (!supported()) return unsupported
        val store = ProfileStore.getInstance()
        val existing = store.allProfileNames
        val deleted = mutableListOf<String>()
        val pending = mutableListOf<String>()
        names.distinct()
            // Prefix-scoped twice over: the default profile and anything ESuite did not create are
            // not candidates, and a name the provider does not report is not deletable at all.
            .filter { ProfileNames.parse(it) != null && it != Profile.DEFAULT_PROFILE_NAME && it in existing }
            .forEach { name ->
                // A loaded profile is reported as false by some providers and as a thrown
                // IllegalStateException by others; both mean "not now", never "gone".
                val removed = try {
                    store.deleteProfile(name)
                } catch (e: IllegalStateException) {
                    false
                }
                if (removed) deleted += name else pending += name
            }
        return CleanupResult(deleted, pending)
    }
}
