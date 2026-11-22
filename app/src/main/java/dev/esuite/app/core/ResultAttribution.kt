package dev.esuite.app.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The project/identity/generation a long-running request launched under. A result may only be
 * APPLIED to the UI (selected/comparison/message) while the context it started in is
 * still current; recording into Traffic is unconditional and is never gated by this.
 */
data class RequestContext(val generation: Int, val projectId: String?, val identity: String)

object ResultAttribution {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

    /** True only while generation, project and identity are all unchanged from launch. */
    fun accepts(started: RequestContext, current: RequestContext): Boolean =
        started.generation == current.generation &&
            started.projectId == current.projectId &&
            started.identity == current.identity

    /**
     * Describes the exchange by its OWN origin — the identity, source, project and time recorded on
     * the exchange itself, never the currently selected identity.
     */
    fun label(exchange: Exchange, projectName: String?): String {
        val project = projectName?.takeIf { it.isNotBlank() } ?: exchange.projectId
        return "Project $project · identity ${exchange.identity} · ${exchange.source} · ${stamp.format(Instant.ofEpochMilli(exchange.timestamp))}"
    }
}
