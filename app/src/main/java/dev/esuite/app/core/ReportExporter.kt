package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ReportExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    /** Opt-out-free sanitized report: raw drafts, auth configs, free text and payloads never leave by default. */
    fun sanitized(workspace: Workspace, projectId: String): String {
        val project = workspace.projects.first { it.id == projectId }
        val report = workspace.copy(projects = listOf(project.copy(name = "Assessment")),
            exchanges = workspace.exchanges.filter { it.projectId == projectId }.map {
                Redactor.exchange(it).copy(request = Redactor.exchange(it).request.copy(url = project.origin + "/[PATH OMITTED]", headers = emptyList()), responseHeaders = emptyList())
            },
            findings = workspace.findings.filter { it.projectId == projectId }.map {
                it.copy(title = "Observation", detail = "[FREE TEXT OMITTED — review evidence locally]")
            }, savedRequests = emptyList(), identityConfigs = emptyList())
        return json.encodeToString(report)
    }
}
