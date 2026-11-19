package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ProfileNamesTest {
    /**
     * Names that must never be recognised as ESuite-owned: an empty project id, an identity outside
     * A/B, a mis-cased prefix, the default WebView profile and a foreign app's profile.
     */
    private val foreign = listOf("esuite__A", "esuite_x_C", "Esuite_x_A", "default", "Default", "other_app_A", "esuite_", "esuite_A", "_A", "")

    @Test fun nameAndParseRoundTripForBothIdentities() {
        val random = Random(0x9F0)
        repeat(200) {
            val projectId = randomProjectId(random)
            listOf("A", "B").forEach { identity ->
                val name = ProfileNames.name(projectId, identity)
                val counterexample = "projectId=$projectId identity=$identity name=$name"
                assertEquals(counterexample, "esuite_${projectId}_$identity", name)
                assertEquals(counterexample, projectId to identity, ProfileNames.parse(name))
            }
        }
    }

    @Test fun parseRejectsEveryNameEsuiteDoesNotOwn() {
        foreign.forEach { assertNull("'$it' must not parse as an ESuite profile", ProfileNames.parse(it)) }
    }

    @Test fun orphansAreExactlyTheWellFormedEsuiteNamesWithDeadProjectIds() {
        val random = Random(0x9F0)
        repeat(200) {
            val live = List(1 + random.nextInt(4)) { randomProjectId(random) }
            val dead = List(1 + random.nextInt(4)) { randomProjectId(random) }.filterNot { it in live }
            val liveNames = live.flatMap { listOf(ProfileNames.name(it, "A"), ProfileNames.name(it, "B")) }
            val deadNames = dead.flatMap { listOf(ProfileNames.name(it, "A"), ProfileNames.name(it, "B")) }
            val all = (liveNames + deadNames + foreign).shuffled(random)
            val orphans = ProfileNames.orphans(all, live.toSet())
            val counterexample = "live=$live dead=$dead orphans=$orphans"
            assertEquals(counterexample, deadNames.toSet(), orphans.toSet())
            assertTrue(counterexample, orphans.none { it in liveNames })
            assertTrue(counterexample, orphans.none { it in foreign })
            assertTrue(counterexample, orphans.all { it.startsWith(ProfileNames.PREFIX) })
        }
    }

    @Test fun orphansSparesTheDefaultAndForeignProfilesEvenWithNoLiveProjects() {
        val all = foreign + listOf("chrome_profile", ProfileNames.name("dead", "A"))
        assertEquals(listOf(ProfileNames.name("dead", "A")), ProfileNames.orphans(all, emptySet()))
        assertEquals(emptyList<String>(), ProfileNames.orphans(foreign, emptySet()))
    }

    /** UUID-shaped ids, plus ids carrying their own underscores so the identity split stays exact. */
    private fun randomProjectId(r: Random): String {
        val alphabet = "abcdef0123456789-_"
        return buildString { repeat(1 + r.nextInt(36)) { append(alphabet[r.nextInt(alphabet.length)]) } }
    }
}
