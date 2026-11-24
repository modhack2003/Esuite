package dev.esuite.app.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class Header(val name: String, val value: String)
@Serializable data class RequestDraft(
    val method: String = "GET", val url: String = "", val headers: List<Header> = emptyList(), val body: String = "",
)
@Serializable data class Project(val id: String = UUID.randomUUID().toString(), val name: String, val origin: String)
@Serializable data class StoredBody(val value: String = "", val encoding: String = "utf8", val truncated: Boolean = false)
@Serializable data class Exchange(
    val id: String = UUID.randomUUID().toString(), val projectId: String, val identity: String,
    val source: String = "API Lab", val request: RequestDraft, val status: Int? = null,
    val responseHeaders: List<Header> = emptyList(), val response: StoredBody = StoredBody(),
    val durationMs: Long = 0, val timestamp: Long = System.currentTimeMillis(), val error: String? = null,
    val completeness: String = "HTTP client normalized; not raw wire bytes",
)
/**
 * [evidenceIds] stays the authoritative reference list; [evidence] holds bounded immutable copies of
 * the referenced exchanges, so `evidenceIds - evidence.map { it.id }` is exactly the missing set.
 * Snapshots live in the durable region and are never touched by a history disposal path.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class Finding(
    val id: String = UUID.randomUUID().toString(), val projectId: String, val title: String,
    val detail: String, val evidenceIds: List<String> = emptyList(), val evidence: List<Exchange> = emptyList(),
    val state: String = "Observation", val timestamp: Long = System.currentTimeMillis(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val evidenceRequestBodies: Map<String, EvidenceRequestBody> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val triage: FindingTriage = FindingTriage(),
)

@Serializable data class FindingTriage(
    val severity: FindingSeverity = FindingSeverity.Unrated,
    val confidence: FindingConfidence = FindingConfidence.Unrated,
    val status: FindingStatus = FindingStatus.Open,
)

@Serializable enum class FindingSeverity(val label: String) {
    Unrated("Unrated"), Informational("Informational"), Low("Low"), Medium("Medium"), High("High"), Critical("Critical"),
}

@Serializable enum class FindingConfidence(val label: String) {
    Unrated("Unrated"), Low("Low"), Medium("Medium"), High("High"),
}

@Serializable enum class FindingStatus(val label: String) {
    Open("Open"), Investigating("Investigating"), Retest("Ready for retest"), Resolved("Resolved"),
}

@Serializable
enum class EvidenceRequestBody { Complete, Clipped, Unknown, Unavailable }
@Serializable data class SavedRequest(val id: String = UUID.randomUUID().toString(), val projectId: String, val name: String, val draft: RequestDraft)
@Serializable data class IdentityConfig(val projectId: String, val identity: String, val headers: List<Header> = emptyList())
@Serializable data class Workspace(
    val version: Int = WorkspaceSchema.CURRENT_VERSION, val projects: List<Project> = emptyList(), val exchanges: List<Exchange> = emptyList(),
    val findings: List<Finding> = emptyList(), val savedRequests: List<SavedRequest> = emptyList(),
    val identityConfigs: List<IdentityConfig> = emptyList(),
)

/** Runtime limits apply at the engine boundary as well as the UI. */
object Limits {
    const val REQUEST_BYTES = 256 * 1024
    const val RESPONSE_BYTES = 256 * 1024
    const val HISTORY = 100
    const val FINDINGS = 200
    const val FINDING_TITLE_CHARS = 200
    const val FINDING_NOTE_CHARS = 16_000
    const val EVIDENCE_BYTES = 16 * 1024
    const val VAULT_BYTES = 32 * 1024 * 1024
}
