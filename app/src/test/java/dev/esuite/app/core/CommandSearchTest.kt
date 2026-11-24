package dev.esuite.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSearchTest {
    private val find = "Find in page /find page text search matches"
    private val findings = "Open Findings /findings observations evidence"
    private val settings = "Open Settings /settings appearance version help"
    private val commands = listOf(find, findings, settings)

    @Test fun exactShortcutsTakePrecedenceOverOtherCommandPrefixes() {
        assertEquals(listOf(find), matchingCommands(commands, " /FIND ") { it })
        assertEquals(listOf(findings), matchingCommands(commands, "/findings") { it })
    }

    @Test fun partialShortcutsAndMultiwordSearchStayDiscoverable() {
        assertEquals(listOf(find, findings), matchingCommands(commands, "/fin") { it })
        assertEquals(listOf(settings), matchingCommands(commands, "OPEN\tversion") { it })
        assertEquals(commands, matchingCommands(commands, "  ") { it })
        assertEquals(emptyList<String>(), matchingCommands(commands, "unrelated") { it })
    }
}
