package dev.esuite.app.core

/** Identity switches/replays do not silently reuse observed authentication headers. */
object CredentialPolicy {
    private val names = Regex("(?i)(authorization|cookie|token|secret|api[-_]?key|session|credential)")
    fun strip(draft: RequestDraft, configured: List<IdentityConfig> = emptyList()): RequestDraft {
        val configuredNames = configured.flatMap { it.headers }.map { it.name.lowercase() }.toSet()
        return draft.copy(headers = draft.headers.filterNot { names.containsMatchIn(it.name) || it.name.lowercase() in configuredNames })
    }
}
