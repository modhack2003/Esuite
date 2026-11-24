package dev.esuite.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SelectedObservationReportTest {
    @Test fun exportsOnlyTheSelectedObservationAndAllowlistedEvidenceFields() {
        val project = Project(name = "private project name", origin = "https://example.test")
        val otherProject = Project(name = "Other project", origin = "https://elsewhere.test")
        val exchange = Exchange(projectId = project.id, identity = "A", source = "unrecognized private source",
            request = RequestDraft("private-method", "https://example.test/private-path?token=private-query",
                listOf(Header("Authorization", "private-header")), "private-request"), status = 200,
            responseHeaders = listOf(Header("Set-Cookie", "private-cookie")),
            response = StoredBody("private-response", truncated = true), completeness = "private-description")
        val foreignExchange = exchange.copy(id = "foreign-evidence", projectId = otherProject.id)
        val finding = Finding(projectId = project.id, title = "private title", detail = "private note",
            evidenceIds = listOf(exchange.id, "missing", foreignExchange.id), evidence = listOf(exchange, foreignExchange))
        val otherFinding = finding.copy(id = "other-note", projectId = otherProject.id)
        val workspace = Workspace(projects = listOf(project, otherProject), findings = listOf(finding, otherFinding))

        val report = requireNotNull(ReportExporter.selectedObservation(workspace, project.id, finding.id))
        assertFalse(report.contains("private"))
        assertFalse(report.contains(otherProject.origin))
        assertFalse(report.contains(otherFinding.id))
        val fields = Json.parseToJsonElement(report).jsonObject
        assertEquals(finding.id, fields.getValue("observationId").jsonPrimitive.content)
        val evidence = fields.getValue("evidence").jsonArray.map { it.jsonObject }
        assertEquals("OTHER", evidence[0].getValue("method").jsonPrimitive.content)
        assertEquals("Other", evidence[0].getValue("source").jsonPrimitive.content)
        assertTrue(evidence[0].getValue("retained").jsonPrimitive.boolean)
        assertTrue(evidence[0].getValue("responseBodyTruncated").jsonPrimitive.boolean)
        assertEquals(listOf(false, false), evidence.drop(1).map { it.getValue("retained").jsonPrimitive.boolean })
        assertNull(ReportExporter.selectedObservation(workspace, otherProject.id, finding.id))

        val markdown = requireNotNull(ReportExporter.selectedObservationMarkdown(workspace, project.id, finding.id))
        assertTrue(markdown.startsWith("# ESuite observation"))
        assertTrue(markdown.contains("Clipped"))
        assertTrue(markdown.contains("| missing | Missing |"))
        assertFalse(markdown.contains("private"))
        assertFalse(markdown.contains(otherProject.origin))
        assertFalse(markdown.contains(otherFinding.id))
        assertNull(ReportExporter.selectedObservationMarkdown(workspace, otherProject.id, finding.id))
    }

    @Test fun markdownEscapesEvidenceIdentifiersAndOmitsNotes() {
        val project = Project(id = "p", name = "Fixture", origin = "https://example.test")
        val finding = Finding(id = "<id>|[link]", projectId = project.id, title = "Hidden title", detail = "Hidden note",
            evidenceIds = listOf("row|alias\n# injected"))
        val report = requireNotNull(ReportExporter.selectedObservationMarkdown(
            Workspace(projects = listOf(project), findings = listOf(finding)), project.id, finding.id))
        assertTrue(report.contains("&lt;id&gt;\\|\\[link\\]"))
        assertTrue(report.contains("| row\\|alias \\# injected | Missing |"))
        assertFalse(report.contains("Hidden"))
    }
}
