package dev.esuite.app.storage

import dev.esuite.app.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class WorkspaceMigrationTest {
    private val json = Json { encodeDefaults = true }
    @Test fun deployedVersionOneCrossesTheActualMigrationAndRetainsAllFields() {
        val p = Project(name = "Old fixture", origin = "https://example.test")
        val old = Workspace(version = 1, projects = listOf(p),
            findings = listOf(Finding(projectId = p.id, title = "Note", detail = "Keep me")),
            exchanges = listOf(Exchange(projectId = p.id, identity = "A", request = RequestDraft(url = p.origin))),
            savedRequests = listOf(SavedRequest(projectId = p.id, name = "Saved", draft = RequestDraft(url = p.origin))),
            identityConfigs = listOf(IdentityConfig(p.id, "B", listOf(Header("X-Auth", "synthetic")))))
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val document = VaultCipher.open(VaultCipher.seal(json.encodeToString(old).toByteArray(), key), key).toString(Charsets.UTF_8)
        assertEquals(old.copy(version = WorkspaceSchema.CURRENT_VERSION), WorkspaceSchema.migrate(document))
    }
    @Test fun currentVersionIsPreservedAndUnsupportedDocumentsFailClosed() {
        val current = Workspace()
        assertEquals(current, WorkspaceSchema.migrate(json.encodeToString(current)))
        for (document in listOf("{}", "{\"version\":999}", "{\"version\":0}", "{\"version\":\"bad\"}", "[]", "bad")) {
            assertThrows(Exception::class.java) { WorkspaceSchema.migrate(document) }
        }
    }
}
