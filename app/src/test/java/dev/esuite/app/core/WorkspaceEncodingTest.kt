package dev.esuite.app.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceEncodingTest {
    @Test fun streamingCountAndBytesMatchLegacyUtf8IncludingEscapesAndDefaults() {
        val project = Project(name = "Unicode 😀 あ", origin = "https://example.test")
        val workspace = Workspace(projects = listOf(project), findings = listOf(Finding(projectId = project.id,
            title = "Quotes \" and slash /", detail = "Line one\nLine two\t😀")))
        val expected = Json { encodeDefaults = true }.encodeToString(workspace).toByteArray(Charsets.UTF_8)
        assertEquals(expected.size, WorkspaceEncoding.sizeUpTo(workspace, expected.size))
        assertNull(WorkspaceEncoding.sizeUpTo(workspace, expected.size - 1))
        assertArrayEquals(expected, WorkspaceEncoding.encodeExact(workspace, expected.size))
    }
}
