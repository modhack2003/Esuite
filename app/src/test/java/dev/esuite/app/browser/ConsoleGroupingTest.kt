package dev.esuite.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleGroupingTest {
    @Test fun groupsConsecutiveMessagesByLevelTextAndSourceWhilePreservingEveryOccurrence() {
        val rows = listOf(
            ConsoleEntry(1, ConsoleLevel.Warning, "sample", "fixture:1", 10),
            ConsoleEntry(2, ConsoleLevel.Warning, "sample", "fixture:1", 20),
            ConsoleEntry(3, ConsoleLevel.Warning, "sample", "fixture:2", 30),
            ConsoleEntry(4, ConsoleLevel.Error, "sample", "fixture:2", 40),
            ConsoleEntry(5, ConsoleLevel.Warning, "sample", "fixture:1", 50),
            ConsoleEntry(6, ConsoleLevel.Command, "1+1", time = 60),
            ConsoleEntry(7, ConsoleLevel.Command, "1+1", time = 70),
            ConsoleEntry(8, ConsoleLevel.Result, "2", time = 80),
            ConsoleEntry(9, ConsoleLevel.Result, "2", time = 90),
        )
        val groups = groupConsoleEntries(rows)
        assertEquals(listOf(2, 1, 1, 1, 1, 1, 1, 1), groups.map { it.entries.size })
        assertEquals(listOf(10L, 20L), groups.first().entries.map { it.time })
        assertEquals(2L, groups.first().newestId)
        assertEquals(rows, groups.flatMap { it.entries })
    }
}
