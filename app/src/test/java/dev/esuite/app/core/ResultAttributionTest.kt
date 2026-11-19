package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ResultAttributionTest {
    private val base = RequestContext(generation = 3, projectId = "p1", identity = "A")

    @Test fun acceptsOnlyWhenGenerationProjectAndIdentityAllMatch() {
        assertTrue(ResultAttribution.accepts(base, base.copy()))
        assertFalse(ResultAttribution.accepts(base, base.copy(generation = 4)))
        assertFalse(ResultAttribution.accepts(base, base.copy(projectId = "p2")))
        assertFalse(ResultAttribution.accepts(base, base.copy(projectId = null)))
        assertFalse(ResultAttribution.accepts(base.copy(projectId = null), base))
        assertFalse(ResultAttribution.accepts(base, base.copy(identity = "B")))
    }

    @Test fun comparisonSecondLegStillAppliesWhenNothingChanged() {
        val started = RequestContext(generation = 7, projectId = "proj", identity = "A")
        // The A leg then the B leg complete without any identity()/selectProject()/load change, so
        // both still apply under the same started context.
        assertTrue(ResultAttribution.accepts(started, RequestContext(generation = 7, projectId = "proj", identity = "A")))
        assertTrue(ResultAttribution.accepts(started, RequestContext(generation = 7, projectId = "proj", identity = "A")))
        // A mid-flight switch to B bumps the generation and flips identity, so the late result is rejected.
        assertFalse(ResultAttribution.accepts(started, RequestContext(generation = 8, projectId = "proj", identity = "B")))
    }

    @Test fun labelReportsExchangeOwnIdentitySourceAndProjectNeverACurrentIdentity() {
        val random = Random(20260503L)
        val identities = listOf("A", "B")
        val sources = listOf("API Lab", "Identity comparison", "Parameter runner", "Request sequence", "Browser metadata")
        repeat(200) {
            val own = identities[random.nextInt(identities.size)]
            val current = identities[random.nextInt(identities.size)]
            val source = sources[random.nextInt(sources.size)]
            val exchange = Exchange(projectId = "proj-${random.nextInt(1000)}", identity = own, source = source,
                request = RequestDraft(url = "https://example.test/"), timestamp = 1_700_000_000_000L + random.nextInt())
            val label = ResultAttribution.label(exchange, "Example project")
            val counterexample = "own=$own current=$current source=$source label=$label"
            assertTrue(counterexample, label.contains("identity $own"))
            assertTrue(counterexample, label.contains(source))
            assertTrue(counterexample, label.contains("Example project"))
            if (current != own) assertFalse(counterexample, label.contains("identity $current"))
        }
    }

    @Test fun labelFallsBackToProjectIdWhenNameMissingOrBlank() {
        val exchange = Exchange(projectId = "proj-xyz", identity = "B", source = "API Lab", request = RequestDraft())
        assertTrue(ResultAttribution.label(exchange, null).contains("proj-xyz"))
        assertTrue(ResultAttribution.label(exchange, "   ").contains("proj-xyz"))
    }
}
