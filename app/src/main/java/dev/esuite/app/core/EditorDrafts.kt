package dev.esuite.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every editor that holds pending user input across a module switch.
 *
 * [valueChars] is that editor's retained-text cap. It defaults to [EditorDrafts.VALUE_CHARS], and is
 * raised to [Limits.REQUEST_BYTES] for the two editors whose text is parser INPUT rather than a
 * field value: [ToolsInput] feeds `importOpenApi`/`importHar`/`importCurl` and [ApiRaw] feeds
 * `DraftParser.raw`, both of which accept up to that limit. Capping those at the smaller default cut
 * a legitimate paste mid-document and surfaced as a parser syntax error instead of a truncation.
 *
 * [identityScoped] declares that this editor's text belongs to ONE identity, so its [DraftKey]
 * carries the identity and an A->B switch can never carry the text across. It is the single source
 * of truth for that rule — build keys with [DraftKey.of] rather than deciding per call site:
 * - [ApiIdentityHeaders] is that identity's own credential configuration, and `api_save_identity`
 *   writes the field straight into its `IdentityConfig`.
 * - [BrowserEdit] backs `browser_cookie_input`, and `browser_set_cookie` writes the field into the
 *   SELECTED identity's isolated cookie jar, so retaining it across a switch would store identity
 *   A's cookie under identity B.
 * - [BrowserScript] is evaluated in the selected identity's own profile-bound WebView, which is the
 *   lifetime it had before drafts existed.
 * [BrowserAddress] is deliberately project-wide: a URL is not credential-bearing and page loads stay
 * explicit through Go.
 */
enum class Editor(val valueChars: Int = EditorDrafts.VALUE_CHARS, val identityScoped: Boolean = false) {
    ApiHeaders, ApiRaw(Limits.REQUEST_BYTES), ApiSavedName, ApiIdentityHeaders(identityScoped = true),
    FindingTitle, FindingDetail,
    ToolsInput(Limits.REQUEST_BYTES),
    TaskPayload1, TaskPayload2, TaskPayload3, TaskPayload4, SequenceNames, TokenPath,
    BrowserAddress, BrowserEdit(identityScoped = true), BrowserScript(identityScoped = true),
}

/**
 * Drafts are scoped to a project, so switching projects never shows another project's pending text.
 *
 * [identity] is set only for an [Editor.identityScoped] editor. Without that dimension an A->B switch
 * left identity A's pending text in the field: the identity-headers editor hid identity B's saved
 * headers and let a save write A's text into B's `IdentityConfig`, and the browser cookie editor let
 * `browser_set_cookie` store A's cookie in B's isolated jar. Every other editor leaves it null.
 */
data class DraftKey(val projectId: String?, val editor: Editor, val identity: String? = null) {
    companion object {
        /** The key factory screens use: [Editor.identityScoped] decides whether [identity] applies. */
        fun of(projectId: String?, editor: Editor, identity: String): DraftKey =
            DraftKey(projectId, editor, identity.takeIf { editor.identityScoped })
    }
}

/**
 * In-memory pending editor text, owned by the ViewModel so it survives module switches, rotation and
 * Activity recreation. It is deliberately NEVER persisted: `rememberSaveable`/`SavedStateHandle`
 * would hand this text to the platform's saved instance state, which is written to disk outside the
 * encrypted vault and would leave cookies, tokens and auth headers in plaintext. Drafts are
 * therefore lost on process death, and the UI says so ([dev.esuite.app.ui.DraftNotice]).
 *
 * Nothing here touches [Workspace]: the persistence collector observes `state.map { it.workspace }`,
 * so typing can never trigger an encrypted save.
 */
class EditorDrafts {
    private val entries = LinkedHashMap<DraftKey, String>()
    private val mutable = MutableStateFlow<Map<DraftKey, String>>(emptyMap())

    /** Snapshot for Compose; a missing key means "unset", so the caller's fallback applies. */
    val values: StateFlow<Map<DraftKey, String>> = mutable.asStateFlow()

    fun text(key: DraftKey, fallback: String = ""): String = mutable.value[key] ?: fallback

    fun set(key: DraftKey, value: String) = publish {
        it.remove(key) // Re-insert so iteration order stays least-recently-written first.
        it[key] = value.take(key.editor.valueChars)
        while (it.size > MAX_ENTRIES) it.remove(it.keys.first())
    }

    fun clear(key: DraftKey) = publish { it.remove(key) }

    fun clearProject(projectId: String?) = publish { map -> map.keys.filter { it.projectId == projectId }.forEach(map::remove) }

    /** Apply [block] to the named identity-free editors of one project, leaving unset editors unset. */
    fun transform(projectId: String?, editors: Collection<Editor>, block: (String) -> String) = publish { map ->
        editors.forEach { editor ->
            val key = DraftKey(projectId, editor)
            map[key]?.let { map[key] = block(it).take(editor.valueChars) }
        }
    }

    private inline fun publish(block: (LinkedHashMap<DraftKey, String>) -> Unit) {
        synchronized(entries) {
            block(entries)
            mutable.value = LinkedHashMap(entries)
        }
    }

    companion object {
        /**
         * Bounded memory: the default cap on one editor's pending text (see [Editor.valueChars] for
         * the two import editors that carry the larger parser bound), and how many editor drafts are
         * kept at once. `selectProject` clears the outgoing project's drafts, so in practice only one
         * project's editors are retained.
         */
        const val VALUE_CHARS = 64 * 1024
        val MAX_ENTRIES = Editor.entries.size * 20
    }
}
