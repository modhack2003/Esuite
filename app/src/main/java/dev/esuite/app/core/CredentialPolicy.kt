package dev.esuite.app.core

/** Identity switches/replays do not silently reuse observed authentication headers. */
object CredentialPolicy {
    private val names = Regex("(?i)(authorization|cookie|token|secret|api[-_]?key|session|credential)")
    fun strip(draft: RequestDraft, configured: List<IdentityConfig> = emptyList()): RequestDraft {
        val configuredNames = configured.flatMap { it.headers }.map { it.name.lowercase() }.toSet()
        return draft.copy(headers = draft.headers.filterNot { names.containsMatchIn(it.name) || it.name.lowercase() in configuredNames })
    }

    /**
     * The same filter applied to a retained header EDITOR, line by line. Unlike [DraftParser.headers]
     * this never throws: a line that is not yet a well-formed `Name: value` pair is kept untouched, so
     * an identity switch cannot discard half-typed text or fail on it.
     */
    fun stripHeaderLines(text: String, configuredNames: Set<String>): String {
        val configured = configuredNames.map { it.lowercase() }.toSet()
        return text.lineSequence().filterNot { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@filterNot false
            val name = line.substring(0, separator).trim()
            names.containsMatchIn(name) || name.lowercase() in configured
        }.joinToString("\n")
    }
}
