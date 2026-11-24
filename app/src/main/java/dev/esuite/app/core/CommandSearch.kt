package dev.esuite.app.core

private val commandWhitespace = Regex("\\s+")

internal fun <T> matchingCommands(items: List<T>, query: String, search: (T) -> String): List<T> {
    val terms = query.trim().split(commandWhitespace)
    val shortcut = terms.singleOrNull()?.takeIf { it.startsWith('/') }
    if (shortcut != null) {
        val exact = items.filter { command ->
            search(command).split(commandWhitespace).any { it.equals(shortcut, ignoreCase = true) }
        }
        if (exact.isNotEmpty()) return exact
    }
    return items.filter { command -> terms.all { search(command).contains(it, ignoreCase = true) } }
}
