package dev.esuite.app.storage

import dev.esuite.app.core.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class VaultCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    @Test fun roundTripHasFreshNoncesAndDoesNotExposePlaintext() {
        val plain = "secret-session-credentials".toByteArray()
        val a = VaultCipher.seal(plain, key); val b = VaultCipher.seal(plain, key)
        assertArrayEquals(plain, VaultCipher.open(a, key)); assertFalse(a.contentEquals(b))
        assertFalse(a.toString(Charsets.ISO_8859_1).contains("secret-session-credentials"))
    }
    @Test fun tamperingWrongKeyAndUnknownEnvelopeFailClosed() {
        val encrypted = VaultCipher.seal("evidence".toByteArray(), key)
        val bad = encrypted.clone(); bad[20] = (bad[20].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { VaultCipher.open(bad, key) }
        assertThrows(Exception::class.java) { VaultCipher.open(encrypted, SecretKeySpec(ByteArray(32) { 99 }, "AES")) }
        val unknown = encrypted.clone(); unknown[3] = 99
        assertThrows(IllegalArgumentException::class.java) { VaultCipher.open(unknown, key) }
    }
    @Test fun sanitizedExportOmitsSecretsIncludingFreeTextAndPaths() {
        val p = Project(id = "p", name = "sensitive-name", origin = "https://example.test")
        val workspace = Workspace(projects = listOf(p), exchanges = listOf(Exchange(projectId = "p", identity = "A", request = RequestDraft(url = "https://example.test/reset/secret-path?key=secret-query", body = "secret-body", headers = listOf(Header("X-Unusual", "secret-header"))), response = StoredBody("secret-response"))),
            findings = listOf(Finding(projectId = "p", title = "secret-title", detail = "secret-detail")),
            identityConfigs = listOf(IdentityConfig("p", "A", listOf(Header("Authorization", "secret-auth")))))
        val report = ReportExporter.sanitized(workspace, "p")
        listOf("sensitive-name", "secret-path", "secret-query", "secret-body", "secret-header", "secret-response", "secret-title", "secret-detail", "secret-auth").forEach { assertFalse(it, report.contains(it)) }
        assertTrue(report.contains("PATH OMITTED"))
    }
    @Test fun durableBudgetRejectsOversizedCollectionsInsteadOfDroppingThem() {
        val draft = RequestDraft("POST", "https://example.test", body = "x".repeat(Limits.REQUEST_BYTES))
        val workspace = Workspace(savedRequests = List(70) { SavedRequest(projectId = "p", name = "r$it", draft = draft) })
        assertThrows(IllegalArgumentException::class.java) { WorkspaceBudget.requireDurableCapacity(workspace) }
        WorkspaceBudget.requireDurableCapacity(Workspace(savedRequests = workspace.savedRequests.take(1)))
    }
}
