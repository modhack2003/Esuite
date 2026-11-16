package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    /** Retained finding snapshots get exactly the treatment capture history gets; the new durable field cannot leak bodies, paths or header values. */
    private fun omitted(exchange: Exchange, origin: String): Exchange = Redactor.exchange(exchange).let {
        it.copy(request = it.request.copy(url = origin + "/[PATH OMITTED]", headers = emptyList()), responseHeaders = emptyList())
    }
}
