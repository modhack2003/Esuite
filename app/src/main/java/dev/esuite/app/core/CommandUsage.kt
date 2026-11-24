package dev.esuite.app.core

internal class CommandUsage private constructor(val pinned: List<String>, val recent: List<String>) {
    fun record(id: String, allowed: Set<String>): CommandUsage =
        if (id !in allowed) this else CommandUsage(pinned, (listOf(id) + recent).distinct().take(RECENT_LIMIT))

    fun togglePin(id: String, allowed: Set<String>): CommandUsage {
        if (id !in allowed) return this
        val next = if (id in pinned) pinned - id else pinned + id
        return CommandUsage(next, recent)
    }

    fun <T> order(available: List<T>, id: (T) -> String): List<T> {
        val byId = available.associateBy(id)
        return (pinned + recent + byId.keys).distinct().mapNotNull(byId::get)
    }

    companion object {
        const val RECENT_LIMIT = 5

        fun restore(pinned: String, recent: String, allowed: Set<String>): CommandUsage {
            fun knownIds(value: String) = value.splitToSequence(',').filter(allowed::contains).distinct().take(allowed.size).toList()
            return CommandUsage(knownIds(pinned), knownIds(recent).take(RECENT_LIMIT))
        }
    }
}
