package dev.esuite.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkFiltersTest {
    private val callback = BrowserRequestEvent("callback:1", RequestEventKind.Request, "/assets/fixture.js", 40.0, method = "GET")
    private val error = BrowserRequestEvent("callback:2", RequestEventKind.HttpError, "/assets/missing.css", 30.0,
        method = "GET", code = 404)
    private val timing = BrowserRequestEvent("timing:1", RequestEventKind.Timing, "/assets/fixture.js", 20.0,
        timing = ResourceTiming(1, "/assets/fixture.js", "script", 200, 20.0, 10.0, 0L, "h2",
            null, null, null, null, null, 50L, 100L))
    private val rows = listOf(callback, error, timing)

    @Test fun fieldSearchCombinesWithPlainTermsAndExcludesUnwantedObservations() {
        assertEquals(listOf(error), NetworkFilters(query = "assets METHOD:get status-code:4XX").select(rows))
        assertEquals(listOf(timing), NetworkFilters(query = "resource-type:script source:timing -path:missing").select(rows))
        assertEquals(listOf(error, callback), NetworkFilters(query = "path:/assets -source:timing").select(rows))
        assertEquals(listOf(timing, callback), NetworkFilters(query = "assets fixture").select(rows))
    }

    @Test fun missingMethodsAndStatusesAreNotInferredFromOtherObservations() {
        assertEquals(emptyList<BrowserRequestEvent>(), NetworkFilters(query = "source:timing method:GET").select(rows))
        assertEquals(listOf(timing), NetworkFilters(query = "status-code:200").select(rows))
        assertEquals(listOf(error, callback), NetworkFilters(query = "-status-code:200", source = NetworkSource.Callback).select(rows))
        assertEquals(listOf(callback), NetworkFilters(status = StatusFilter.Unknown).select(rows))
        assertEquals(listOf(timing, callback, error), NetworkFilters(order = NetworkOrder.Largest).select(rows))
    }

    @Test fun resetRestoresFiltersWithoutChangingTheDisplayControls() {
        val filters = NetworkFilters(query = "status-code:404", order = NetworkOrder.Latest, waterfall = false, expanded = true)
        assertEquals(NetworkFilters(waterfall = false, expanded = true), filters.reset())
        assertFalse(filters.reset().active)
    }
}
