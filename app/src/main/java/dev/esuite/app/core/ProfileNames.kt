package dev.esuite.app.core

/**
 * The naming seam for the isolated per-project/per-identity WebView profiles created in
 * `browser/BrowserScreen.kt`. Pure and Android-free, so the ownership rule that decides what may be
 * deleted is decidable on the JVM instead of only on a provider.
 *
 * Pending cleanup is DERIVED from this rule — the provider's own profile list measured against the
 * live project ids — so there is no persisted pending-cleanup field, no new schema migration, and
 * nothing to lose. It is also readable when the vault is NOT, which is exactly what unreadable-vault
 * recovery needs.
 */
object ProfileNames {
    /** Everything ESuite owns carries this prefix; nothing else may ever be deleted. */
    const val PREFIX = "esuite_"
    private val IDENTITIES = listOf("A", "B")

    /** Today's exact profile name, unchanged from the format `BrowserScreen` already installs. */
    fun name(projectId: String, identity: String): String = "$PREFIX${projectId}_$identity"

    /**
     * Split an ESuite-owned profile name into its project id and identity, or null when the name is
     * not one of ours: a missing/mis-cased prefix, an empty project id or an identity outside A/B.
     */
    fun parse(name: String): Pair<String, String>? {
        if (!name.startsWith(PREFIX)) return null
        val identity = name.substringAfterLast('_', "")
        if (identity !in IDENTITIES) return null
        val end = name.length - identity.length - 1
        if (end <= PREFIX.length) return null
        return name.substring(PREFIX.length, end) to identity
    }

    /**
     * The ESuite profiles whose project no longer exists. Only well-formed ESuite names survive, so
     * a live project's profile, the default profile, a foreign profile and a malformed name are
     * never returned.
     */
    fun orphans(all: List<String>, liveProjectIds: Set<String>): List<String> =
        all.filter { candidate -> parse(candidate)?.let { (projectId, _) -> projectId !in liveProjectIds } == true }
}
