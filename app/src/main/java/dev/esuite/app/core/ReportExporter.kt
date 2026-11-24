package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Instant

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
    fun selectedObservation(workspace: Workspace, projectId: String, findingId: String): String? =
        observationDocument(workspace, projectId, findingId)?.let { json.encodeToString(it) }

    /** Renders the same allowlisted evidence as Markdown for a local review or export. */
    fun selectedObservationMarkdown(workspace: Workspace, projectId: String, findingId: String): String? {
        val report = observationDocument(workspace, projectId, findingId) ?: return null
        val evidence = report.getValue("evidence").jsonArray
        return buildString {
            appendLine("# ESuite observation")
            appendLine()
            appendLine("- Origin · ${markdownText(report.getValue("origin").jsonPrimitive.content)}")
            appendLine("- Observation ID · ${markdownText(report.getValue("observationId").jsonPrimitive.content)}")
            appendLine("- Recorded at · ${Instant.ofEpochMilli(report.getValue("recordedAtEpochMs").jsonPrimitive.long)}")
            appendLine()
            appendLine("## Evidence")
            appendLine()
            if (evidence.isEmpty()) {
                appendLine("No attached exchanges.")
            } else {
                appendLine("| Exchange ID | State | Observed at (UTC) | Source | Identity | Method | HTTP status | Time | Response body |")
                appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
                for (item in evidence) {
                    val entry = item.jsonObject
                    val id = markdownText(entry.getValue("exchangeId").jsonPrimitive.content)
                    if (!entry.getValue("retained").jsonPrimitive.boolean) {
                        appendLine("| $id | Missing | — | — | — | — | — | — | — |")
                        continue
                    }
                    fun field(key: String) = entry[key]?.jsonPrimitive?.contentOrNull?.let(::markdownText) ?: "—"
                    val observed = Instant.ofEpochMilli(entry.getValue("observedAtEpochMs").jsonPrimitive.long)
                    val body = when {
                        entry.getValue("responseBodyTruncated").jsonPrimitive.boolean -> "Clipped"
                        entry.getValue("outcome").jsonPrimitive.content == "response" -> "Complete capture"
                        else -> "Unavailable"
                    }
                    appendLine("| $id | ${field("outcome")} | $observed | ${field("source")} | ${field("identity")} | " +
                        "${field("method")} | ${field("status")} | ${field("durationMs")} ms | $body |")
                }
            }
        }
    }

    private fun observationDocument(workspace: Workspace, projectId: String, findingId: String): JsonObject? {
        val project = workspace.projects.firstOrNull { it.id == projectId } ?: return null
        val finding = workspace.findings.firstOrNull { it.id == findingId && it.projectId == projectId } ?: return null
        val snapshots = finding.evidence.filter { it.projectId == projectId }.associateBy { it.id }
        return buildJsonObject {
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
    }

    private fun markdownText(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\\', '|', '[', ']', '#', '*', '_', '^', '~', '`' -> { append('\\'); append(char) }
                else -> append(if (char.isISOControl()) ' ' else char)
            }
        }
    }

    private val identities = setOf("A", "B")
    private val sources = setOf("API Lab", "Browser metadata", "Identity comparison", "Parameter runner", "Request sequence")
    private val methods = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "CONNECT", "TRACE")

    /** Retained finding snapshots get exactly the treatment capture history gets; the new durable field cannot leak bodies, paths or header values. */
    private fun omitted(exchange: Exchange, origin: String): Exchange = Redactor.exchange(exchange).let {
        it.copy(request = it.request.copy(url = origin + "/[PATH OMITTED]", headers = emptyList()), responseHeaders = emptyList())
    }
}
