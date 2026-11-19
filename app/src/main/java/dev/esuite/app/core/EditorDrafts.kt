package dev.esuite.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Every editor that holds pending user input across a module switch. */
enum class Editor {
    ApiHeaders, ApiRaw, ApiSavedName, ApiIdentityHeaders, FindingTitle, FindingDetail, ToolsInput,
    TaskPayload1, TaskPayload2, TaskPayload3, TaskPayload4, SequenceNames, TokenPath,
    BrowserAddress, BrowserEdit, BrowserScript,
}

/** Drafts are scoped to a project, so switching projects never shows another project's pending text. */
data class DraftKey(val projectId: String?, val editor: Editor)

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
        it[key] = value.take(VALUE_CHARS)
        while (it.size > MAX_ENTRIES) it.remove(it.keys.first())
    }

    fun clear(key: DraftKey) = publish { it.remove(key) }

    fun clearProject(projectId: String?) = publish { map -> map.keys.filter { it.projectId == projectId }.forEach(map::remove) }

    /** Apply [block] to the named editors of one project, leaving unset editors unset. */
    fun transform(projectId: String?, editors: Collection<Editor>, block: (String) -> String) = publish { map ->
        editors.forEach { editor ->
            val key = DraftKey(projectId, editor)
            map[key]?.let { map[key] = block(it).take(VALUE_CHARS) }
        }
    }

    private inline fun publish(block: (LinkedHashMap<DraftKey, String>) -> Unit) {
        synchronized(entries) {
            block(entries)
            mutable.value = LinkedHashMap(entries)
        }
    }

    companion object {
        /** Bounded memory: one editor's pending text, and how many editor drafts are kept at once. */
        const val VALUE_CHARS = 64 * 1024
        val MAX_ENTRIES = Editor.entries.size * 20
    }
}
