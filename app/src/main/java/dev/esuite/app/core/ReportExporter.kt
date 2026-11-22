package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ReportExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    /** Opt-out-free sanitized report: raw drafts, auth configs, free text and payloads never leave by default. */
    fun sanitized(workspace: Workspace, projectId: String): String {
        val project = workspace.projects.first { it.id == projectId }
        val report = workspace.copy(projects = listOf(project.copy(name = "Assessment")),
            exchanges = workspace.exchanges.filter { it.projectId == projectId }.map { omitted(it, project.origin) },
            findings = workspace.findings.filter { it.projectId == projectId }.map {
                it.copy(title = "Observation", detail = "[FREE TEXT OMITTED — review evidence locally]",
                    evidence = it.evidence.map { snapshot -> omitted(snapshot, project.origin) })
            }, savedRequests = emptyList(), identityConfigs = emptyList())
        return json.encodeToString(report)
    }

    /** Selected evidence report with an allowlist of transport metadata and generated identifiers. */
    fun selectedObservation(workspace: Workspace, projectId: String, findingId: String): String? {
        val project = workspace.projects.firstOrNull { it.id == projectId } ?: return null
        val finding = workspace.findings.firstOrNull { it.id == findingId && it.projectId == projectId } ?: return null
        val snapshots = finding.evidence.filter { it.projectId == projectId }.associateBy { it.id }
        val report = buildJsonObject {
            put("format", "esuite-observation")
            put("version", 1)
            put("origin", project.origin)
            put("observationId", finding.id)
            put("recordedAtEpochMs", finding.timestamp)
            put("evidence", buildJsonArray {
                finding.evidenceIds.forEach { id ->
                    val snapshot = snapshots[id]
                    add(buildJsonObject {
                        put("exchangeId", id)
                        put("retained", snapshot != null)
                        if (snapshot != null) {
                            put("observedAtEpochMs", snapshot.timestamp)
                            put("identity", snapshot.identity.takeIf { it in identities })
                            put("source", snapshot.source.takeIf { it in sources } ?: "Other")
                            put("method", snapshot.request.method.takeIf { it in methods } ?: "OTHER")
                            put("status", snapshot.status)
                            put("durationMs", snapshot.durationMs)
                            put("outcome", when {
                                snapshot.error != null -> "error"
                                snapshot.status != null -> "response"
                                else -> "metadata-only"
                            })
                            put("responseBodyTruncated", snapshot.response.truncated)
                        }
                    })
                }
            })
        }
        return json.encodeToString(report)
    }

    private val identities = setOf("A", "B")
    private val sources = setOf("API Lab", "Browser metadata", "Identity comparison", "Parameter runner", "Request sequence")
    private val methods = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "CONNECT", "TRACE")

    /** Retained finding snapshots get exactly the treatment capture history gets; the new durable field cannot leak bodies, paths or header values. */
    private fun omitted(exchange: Exchange, origin: String): Exchange = Redactor.exchange(exchange).let {
        it.copy(request = it.request.copy(url = origin + "/[PATH OMITTED]", headers = emptyList()), responseHeaders = emptyList())
    }
}
