package dev.esuite.app.data

data class TrafficFixture(val method: String, val path: String, val status: Int, val durationMs: Int)

/** Explicit synthetic fixtures, never presented as an observed network capture. */
val demoTraffic = listOf(
    TrafficFixture("GET", "/api/profile", 200, 84),
    TrafficFixture("POST", "/api/session", 201, 126),
    TrafficFixture("GET", "/api/projects", 200, 63),
    TrafficFixture("GET", "/api/admin", 403, 42),
    TrafficFixture("GET", "/assets/app.js", 200, 18),
)
