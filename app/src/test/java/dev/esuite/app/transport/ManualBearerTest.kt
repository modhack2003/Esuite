package dev.esuite.app.transport

import dev.esuite.app.core.Header
import dev.esuite.app.core.Project
import dev.esuite.app.core.RequestDraft
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class ManualBearerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun signedRequestUsesOnlySelectedIdentityAndOmitsReflectedSecrets() = runBlocking {
        val path = File(temporary.root, "local.p12")
        val password = "fixture-only"
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
        val process = ProcessBuilder(keytool.path, "-genkeypair", "-alias", "fixture", "-keyalg", "RSA",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype",
            "PKCS12", "-keystore", path.path, "-storepass", password, "-keypass", password).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        val keys = KeyStore.getInstance("PKCS12").apply { path.inputStream().use { load(it, password.toCharArray()) } }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password.toCharArray()) }
        val tls = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        val server = MockWebServer()
        server.useHttps(tls.socketFactory, false)
        server.start()
        val previous = System.setProperty("javax.net.ssl.trustStore", path.path)
        val previousPassword = System.setProperty("javax.net.ssl.trustStorePassword", password)
        try {
            val engine = ApiEngine()
            try {
                val site = Project(name = "Fixture", origin = server.url("/").toString())
                val draft = RequestDraft(url = server.url("/protected").toString())
                val token = "local.fixture-token"
                val session = engine.sessions.jar(site.id, "A")
                session.setBearer(token)
                server.enqueue(MockResponse().setBody(token).setHeader("X-Echo", token).setHeader("X-$token", "masked"))
                val result = engine.execute(site, "A", draft, useManualBearer = true)
                assertEquals(listOf("Bearer $token"), server.takeRequest().headers.values("Authorization"))
                assertFalse(result.toString().contains(token))
                assertFalse(result.request.headers.any { it.name.equals("Authorization", true) })
                assertEquals("[redacted]", result.response.value)
                server.enqueue(MockResponse().setBody("public"))
                engine.execute(site, "B", draft, useManualBearer = true)
                assertNull(server.takeRequest().getHeader("Authorization"))
                assertTrue(runCatching { engine.execute(site, "A", draft.copy(headers = listOf(Header("Authorization", "Basic fixture"))), useManualBearer = true) }.isFailure)
                assertEquals(2, server.requestCount)
                engine.sessions.clearProject(site.id)
                assertFalse(session.hasBearer.value)
                assertThrows(IllegalArgumentException::class.java) { session.setBearer("bad\r\nheader") }
            } finally { engine.close() }
        } finally {
            if (previous == null) System.clearProperty("javax.net.ssl.trustStore") else System.setProperty("javax.net.ssl.trustStore", previous)
            if (previousPassword == null) System.clearProperty("javax.net.ssl.trustStorePassword") else System.setProperty("javax.net.ssl.trustStorePassword", previousPassword)
            server.shutdown()
        }
        Unit
    }
}
