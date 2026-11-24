package dev.esuite.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandUsageTest {
    private val allowed = (1..8).map { "command$it" }.toSet()

    @Test fun pinsRestoreInOrderAndRemainUnique() {
        val usage = CommandUsage.restore("command2,command1,command2", "", allowed)
        assertEquals(listOf("command2", "command1"), usage.pinned)
        assertEquals(listOf("command1"), usage.togglePin("command2", allowed).pinned)
    }

    @Test fun recentCommandsAreUniqueOrderedAndBounded() {
        val used = allowed.fold(CommandUsage.restore("", "", allowed)) { history, command -> history.record(command, allowed) }
        assertEquals(listOf("command8", "command7", "command6", "command5", "command4"), used.recent)
        assertEquals(listOf("command5", "command8", "command7", "command6", "command4"), used.record("command5", allowed).recent)
    }

    @Test fun suggestionsExcludeTypedInputAndCommandsUnavailableInTheCurrentContext() {
        val usage = CommandUsage.restore("command2,private search", "command3,private URL,command2", allowed)
        assertEquals(listOf("command2"), usage.pinned)
        assertEquals(listOf("command3", "command2"), usage.record("private query", allowed).recent)
        assertEquals(listOf("command3", "command1"), usage.order(listOf("command1", "command3")) { it })
    }
}
