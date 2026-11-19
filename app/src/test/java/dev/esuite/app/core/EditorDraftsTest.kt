package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * The module-switch invariant of QA-02: pending editor text written under one key must survive any
 * amount of unrelated editing, because every screen is destroyed and recomposed on a module switch.
 *
 * Property-style cases follow the repository pattern of a seeded [java.util.Random] loop that prints
 * the counterexample in the assertion message (see
 * [dev.esuite.app.storage.WorkspacePreservationTest]); no property-testing dependency is available.
 */
class EditorDraftsTest {
    private val projectA = "project-a"
    private val projectB = "project-b"

    @Test fun oneEditorsTextSurvivesArbitraryWritesToOtherEditors() {
        val drafts = EditorDrafts()
        val random = Random(0x2DA)
        val retained = DraftKey(projectA, Editor.ApiHeaders)
        drafts.set(retained, "X-ESuite-QA: retained-header")
        repeat(500) { round ->
            val other = Editor.entries[random.nextInt(Editor.entries.size)]
            if (other != Editor.ApiHeaders) drafts.set(DraftKey(projectA, other), "value-$round")
            assertEquals("round $round overwrote the retained header draft",
                "X-ESuite-QA: retained-header", drafts.text(retained))
        }
    }

    @Test fun draftsAreIsolatedPerProject() {
        val drafts = EditorDrafts()
        drafts.set(DraftKey(projectA, Editor.FindingDetail), "evidence for A")
        drafts.set(DraftKey(projectB, Editor.FindingDetail), "evidence for B")
        assertEquals("evidence for A", drafts.text(DraftKey(projectA, Editor.FindingDetail)))
        assertEquals("evidence for B", drafts.text(DraftKey(projectB, Editor.FindingDetail)))
        // An unset editor reports the caller's fallback, never another project's text.
        assertEquals("fallback", drafts.text(DraftKey(null, Editor.FindingDetail), "fallback"))
    }

    @Test fun clearProjectRemovesOnlyThatProjectsEntries() {
        val drafts = EditorDrafts()
        Editor.entries.forEach { editor ->
            drafts.set(DraftKey(projectA, editor), "a-${editor.name}")
            drafts.set(DraftKey(projectB, editor), "b-${editor.name}")
        }
        drafts.clearProject(projectA)
        Editor.entries.forEach { editor ->
            assertEquals("", drafts.text(DraftKey(projectA, editor)))
            assertEquals("b-${editor.name}", drafts.text(DraftKey(projectB, editor)))
        }
        assertEquals(Editor.entries.size, drafts.values.value.size)
    }

    @Test fun eachValueAndTheWholeMapStayBounded() {
        val drafts = EditorDrafts()
        val key = DraftKey(projectA, Editor.ApiHeaders)
        drafts.set(key, "z".repeat(EditorDrafts.VALUE_CHARS * 2))
        assertEquals(EditorDrafts.VALUE_CHARS, drafts.text(key).length)
        // Beyond the map cap the least recently written entry is dropped, newest ones are kept.
        repeat(EditorDrafts.MAX_ENTRIES + 50) { index ->
            drafts.set(DraftKey("project-$index", Editor.ToolsInput), "value-$index")
            assertTrue("map grew past the cap at write $index", drafts.values.value.size <= EditorDrafts.MAX_ENTRIES)
        }
        assertEquals(EditorDrafts.MAX_ENTRIES, drafts.values.value.size)
        assertEquals("", drafts.text(DraftKey("project-0", Editor.ToolsInput)))
        val newest = EditorDrafts.MAX_ENTRIES + 49
        assertEquals("value-$newest", drafts.text(DraftKey("project-$newest", Editor.ToolsInput)))
    }

    /**
     * The two editors whose text is parser INPUT keep the parser's own bound, so a pasted
     * import-sized document is never cut on entry and then rejected as a syntax error.
     */
    @Test fun importEditorsRetainValuesUpToTheImportLimit() {
        val drafts = EditorDrafts()
        val document = "{".repeat(Limits.REQUEST_BYTES)
        listOf(Editor.ToolsInput, Editor.ApiRaw).forEach { editor ->
            val key = DraftKey(projectA, editor)
            drafts.set(key, document + "overflow")
            assertEquals("$editor must survive the holder at the import limit", document, drafts.text(key))
            assertEquals(Limits.REQUEST_BYTES, editor.valueChars)
        }
        // Every other editor keeps the smaller default cap.
        val field = DraftKey(projectA, Editor.ApiHeaders)
        drafts.set(field, "z".repeat(Limits.REQUEST_BYTES))
        assertEquals(EditorDrafts.VALUE_CHARS, drafts.text(field).length)
        assertEquals(EditorDrafts.VALUE_CHARS, Editor.ApiHeaders.valueChars)
    }

    /**
     * An identity switch must not carry one identity's pending credential text into the other's
     * editor, because that text is what `api_save_identity` writes into the identity's config.
     */
    @Test fun identityHeaderDraftsCannotCrossBetweenIdentities() {
        val drafts = EditorDrafts()
        val a = DraftKey(projectA, Editor.ApiIdentityHeaders, "A")
        val b = DraftKey(projectA, Editor.ApiIdentityHeaders, "B")
        drafts.set(a, "X-Tenant: pending-a")
        // While B is selected the key is unset, so the screen's fallback — B's SAVED config — is what
        // the field shows and therefore what a save would store.
        assertEquals("X-Tenant: saved-b", drafts.text(b, "X-Tenant: saved-b"))
        drafts.set(b, "X-Tenant: pending-b")
        assertEquals("X-Tenant: pending-a", drafts.text(a))
        // Clearing after a successful save restores B's fallback and leaves A's pending text alone.
        drafts.clear(b)
        assertEquals("X-Tenant: saved-b", drafts.text(b, "X-Tenant: saved-b"))
        assertEquals("X-Tenant: pending-a", drafts.text(a))
        // Both identities stay scoped to the project, so a project clear still removes both.
        drafts.clearProject(projectA)
        assertTrue(drafts.values.value.isEmpty())
    }

    /**
     * The same rule for the Browser editors, which the first fix missed. `browser_cookie_input` text
     * is handed to the SELECTED identity's isolated cookie jar by `browser_set_cookie`, so a value
     * typed under A must not still be in the field after a switch to B — that would store A's cookie
     * under B. [Editor.identityScoped] is the one place that decides, so pin the set too.
     */
    @Test fun browserCookieAndScriptDraftsCannotCrossBetweenIdentities() {
        val drafts = EditorDrafts()
        val cookieUnderA = DraftKey.of(projectA, Editor.BrowserEdit, "A")
        val cookieUnderB = DraftKey.of(projectA, Editor.BrowserEdit, "B")
        assertNotEquals(cookieUnderA, cookieUnderB)
        drafts.set(cookieUnderA, "session=secret_A; Path=/; Secure; HttpOnly")
        // Under B the key is unset, so the field is empty and a Set press has nothing of A's to store.
        assertEquals("", drafts.text(cookieUnderB))
        assertEquals("session=secret_A; Path=/; Secure; HttpOnly", drafts.text(cookieUnderA))
        drafts.set(cookieUnderB, "session=secret_B; Path=/; Secure")
        assertEquals("session=secret_A; Path=/; Secure; HttpOnly", drafts.text(cookieUnderA))
        // The console script shares that profile-bound lifetime; the address bar is project-wide.
        assertEquals("B", DraftKey.of(projectA, Editor.BrowserScript, "B").identity)
        assertNull(DraftKey.of(projectA, Editor.BrowserAddress, "B").identity)
        assertEquals(setOf(Editor.ApiIdentityHeaders, Editor.BrowserEdit, Editor.BrowserScript),
            Editor.entries.filter { it.identityScoped }.toSet())
        // Both identities stay scoped to the project, so a project clear still removes both.
        drafts.clearProject(projectA)
        assertTrue(drafts.values.value.isEmpty())
    }

    @Test fun transformAppliesTheCredentialStripToRetainedHeaderEditorsOnly() {
        val drafts = EditorDrafts()
        val headers = "Authorization: Bearer abc\nAccept: application/json"
        drafts.set(DraftKey(projectA, Editor.ApiHeaders), headers)
        drafts.set(DraftKey(projectA, Editor.ApiIdentityHeaders), "X-Tenant: acme\nCookie: session=1")
        drafts.set(DraftKey(projectA, Editor.FindingDetail), headers)
        drafts.set(DraftKey(projectB, Editor.ApiHeaders), headers)
        drafts.set(DraftKey(projectA, Editor.ApiIdentityHeaders, "A"), "X-Tenant: acme")
        drafts.transform(projectA, listOf(Editor.ApiHeaders, Editor.ApiIdentityHeaders)) {
            CredentialPolicy.stripHeaderLines(it, setOf("X-Tenant"))
        }
        assertEquals("Accept: application/json", drafts.text(DraftKey(projectA, Editor.ApiHeaders)))
        assertEquals("", drafts.text(DraftKey(projectA, Editor.ApiIdentityHeaders)))
        // transform addresses identity-free keys, so an identity-scoped draft is left alone; that is
        // why the ViewModel's identity switch now strips ApiHeaders only.
        assertEquals("X-Tenant: acme", drafts.text(DraftKey(projectA, Editor.ApiIdentityHeaders, "A")))
        // Only the named editors of the named project change.
        assertEquals(headers, drafts.text(DraftKey(projectA, Editor.FindingDetail)))
        assertEquals(headers, drafts.text(DraftKey(projectB, Editor.ApiHeaders)))
        // An unset editor stays unset rather than becoming an empty entry.
        drafts.transform(projectA, listOf(Editor.TokenPath)) { "written" }
        assertFalse(drafts.values.value.containsKey(DraftKey(projectA, Editor.TokenPath)))
    }

    @Test fun stripHeaderLinesDropsExactlyTheHeadersCredentialPolicyWouldDrop() {
        val random = Random(0x2DA)
        val names = listOf("Authorization", "Cookie", "X-Api-Key", "x-session-id", "Accept", "Content-Type",
            "X-Tenant", "If-None-Match", "X-Credential-Hint", "User-Agent", "X-Request-Token", "Origin")
        val values = listOf("Bearer abc", "application/json", "session=1", "cookie-looking-value", "acme", "*/*")
        repeat(300) { round ->
            val headers = List(1 + random.nextInt(8)) {
                Header(names[random.nextInt(names.size)], values[random.nextInt(values.size)])
            }
            val configured = if (random.nextBoolean()) setOf("X-Tenant") else emptySet()
            val text = headers.joinToString("\n") { "${it.name}: ${it.value}" }
            val expected = CredentialPolicy.strip(RequestDraft(headers = headers),
                listOf(IdentityConfig(projectA, "A", configured.map { Header(it, "configured") })))
                .headers.joinToString("\n") { "${it.name}: ${it.value}" }
            val actual = CredentialPolicy.stripHeaderLines(text, configured)
            assertEquals("round $round counterexample:\n$text\nconfigured=$configured", expected, actual)
        }
    }

    @Test fun stripHeaderLinesKeepsMalformedAndBlankLinesInsteadOfThrowing() {
        val text = "Authorization Bearer abc\n\n: leading colon\nAccept: application/json\nAuthorization: Bearer abc\nhalf-typed"
        val stripped = CredentialPolicy.stripHeaderLines(text, emptySet())
        // DraftParser.headers throws on these lines; the editor filter must not discard or reject them.
        assertEquals("Authorization Bearer abc\n\n: leading colon\nAccept: application/json\nhalf-typed", stripped)
    }
}
