package dev.esuite.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.esuite.app.core.*
import dev.esuite.app.transport.ApiEngine
import dev.esuite.app.storage.WorkspaceVault
import dev.esuite.app.browser.BrowserProfiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce

data class AppState(
    val workspace: Workspace = Workspace(), val projectId: String? = null, val identity: String = "A",
    val draft: RequestDraft = RequestDraft(), val selected: Exchange? = null, val busy: Boolean = false,
    val message: String = "Opening encrypted workspace…", val comparison: String = "", val ready: Boolean = false,
)

@OptIn(FlowPreview::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    val engine = ApiEngine()
    private var requestJob: Job? = null
    private val vault = WorkspaceVault(application)
    private val storageDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var persistenceJob: Job? = null
    init { loadWorkspace() }
    fun loadWorkspace() {
        persistenceJob?.cancel()
        viewModelScope.launch {
            mutable.update { it.copy(ready = false) }
            try {
                val workspace = withContext(storageDispatcher) { vault.load() }
                val project = workspace.projects.firstOrNull()
                mutable.update { it.copy(workspace = workspace, projectId = project?.id, draft = RequestDraft(url = project?.origin?.plus("/").orEmpty()), ready = true,
                    selected = null, message = "Encrypted workspace open. Requests only run when you explicitly start them.") }
                persistenceJob?.cancel()
                persistenceJob = viewModelScope.launch {
                    state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { _ ->
                        try { withContext(storageDispatcher) { vault.save(state.value.workspace) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { message("Encrypted save failed. Previous disk state retained; current edits remain in memory. Use Save now to retry.") }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Workspace could not be opened. No file was overwritten. ${e.message ?: e.javaClass.simpleName}") }
        }
    }
    fun saveNow() { viewModelScope.launch { runCatching { withContext(storageDispatcher) { vault.save(state.value.workspace) } }.onSuccess { message("Workspace saved with AES-GCM; keys remain in Android Keystore.") }.onFailure { message("Save failed; previous file retained: ${it.message}") } } }
    fun eraseVault() {
        cancelRequest(); persistenceJob?.cancel(); engine.sessions.clearAll()
        viewModelScope.launch {
            mutable.update { it.copy(ready = false) }
            runCatching { withContext(storageDispatcher) { vault.erase() } }
                .onSuccess { mutable.value = AppState(); loadWorkspace() }
                .onFailure { message("Could not erase encrypted workspace: ${it.message}") }
        }
    }
    fun message(text: String) = mutable.update { it.copy(message = text.take(4000)) }
    fun currentProject(): Project? = state.value.workspace.projects.firstOrNull { it.id == state.value.projectId }
    fun createProject(name: String, origin: String) {
        runCatching {
            require(name.isNotBlank() && name.length <= 100) { "Enter a project name (up to 100 characters)" }
            require(state.value.workspace.projects.size < 20) { "Project limit reached (20)" }
            val scope = ScopePolicy(origin.trim())
            require(scope.target.isHttps) { "This Android build requires an HTTPS project origin" }
            val project = Project(name = name.trim(), origin = scope.canonicalOrigin)
            mutable.update { it.copy(workspace = it.workspace.copy(projects = it.workspace.projects + project), projectId = project.id,
                draft = RequestDraft(url = project.origin + "/"), selected = null, comparison = "", message = "Project created. Only this exact scheme, host and port are in scope.") }
        }.onFailure { message(it.message ?: "Invalid project") }
    }
    fun selectProject(id: String) {
        cancelRequest()
        mutable.update { current ->
            val project = current.workspace.projects.first { it.id == id }
            current.copy(projectId = id, draft = RequestDraft(url = project.origin + "/"), selected = null, comparison = "", message = "Selected ${project.name}")
        }
    }
    fun identity(value: String) { require(value in listOf("A", "B")); mutable.update { it.copy(identity = value, selected = null) } }
    fun draft(value: RequestDraft) = mutable.update { it.copy(draft = value) }
    fun select(exchange: Exchange) = mutable.update { it.copy(selected = exchange) }
    fun replay(exchange: Exchange) = mutable.update { it.copy(draft = exchange.request, selected = exchange, message = if (exchange.source == "Browser metadata") "Browser bodies are unavailable. Supply any missing body before sending." else "Draft loaded; sending is always explicit.") }
    fun record(exchange: Exchange) {
        mutable.update { it.copy(workspace = it.workspace.copy(exchanges = (listOf(exchange) + it.workspace.exchanges).take(Limits.HISTORY))) }
    }
    fun observeBrowser(project: Project, identity: String, draft: RequestDraft) {
        record(Exchange(projectId = project.id, identity = identity, source = "Browser metadata", request = draft,
            completeness = "URL/method/request headers only. WebView does not supply request bodies or response messages through this callback."))
    }
    fun send(compare: Boolean = false) {
        val snapshot = state.value; val project = currentProject() ?: return message("Create a project first")
        if (snapshot.busy) return
        requestJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, comparison = "", message = "Sending to ${project.origin}…") }
            try {
                if (compare) {
                    val draft = snapshot.draft.copy(headers = snapshot.draft.headers.filterNot { it.name.equals("Cookie", true) || it.name.equals("Authorization", true) })
                    val a = engine.execute(project, "A", withIdentity(draft, project.id, "A"), "Identity comparison"); record(a)
                    val b = engine.execute(project, "B", withIdentity(draft, project.id, "B"), "Identity comparison"); record(b)
                    mutable.update { it.copy(selected = b, comparison = ResponseComparer.compare(a, b), message = "Separate session jars used; explicit Cookie/Authorization headers omitted.") }
                } else {
                    val response = engine.execute(project, snapshot.identity, withIdentity(snapshot.draft, project.id, snapshot.identity)); record(response)
                    mutable.update { it.copy(selected = response, message = "HTTP ${response.status} · ${response.durationMs} ms${if (response.response.truncated) " · body truncated" else ""}") }
                }
            } catch (e: CancellationException) { message("Request cancelled"); throw e }
            catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                record(Exchange(projectId = project.id, identity = snapshot.identity, request = snapshot.draft, error = detail))
                message(detail)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancelRequest() { requestJob?.cancel() }
    private fun withIdentity(draft: RequestDraft, project: String, identity: String): RequestDraft {
        val configured = state.value.workspace.identityConfigs.firstOrNull { it.projectId == project && it.identity == identity }?.headers.orEmpty()
        return draft.copy(headers = draft.headers + configured.filterNot { incoming -> draft.headers.any { it.name.equals(incoming.name, true) } })
    }
    fun configureIdentity(text: String) {
        val p = currentProject() ?: return
        runCatching { DraftParser.headers(text) }.onSuccess { headers ->
            val config = IdentityConfig(p.id, state.value.identity, headers)
            mutable.update { it.copy(workspace = it.workspace.copy(identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id && c.identity == config.identity } + config), message = "Identity headers saved to the encrypted workspace. Cookie jar sessions remain memory-only.") }
        }.onFailure { message(it.message ?: "Invalid identity headers") }
    }
    fun clearSessions() {
        currentProject()?.let { p -> engine.sessions.clearProject(p.id); mutable.update { it.copy(workspace = it.workspace.copy(identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id })) } }
        message("API sessions and saved identity headers cleared. Browser profiles have separate controls.")
    }
    fun saveDraft(name: String) {
        val p = currentProject() ?: return
        runCatching {
            require(name.isNotBlank()) { "Enter a saved request name" }; DraftParser.validate(state.value.draft); ScopePolicy(p.origin).requireAllowed(state.value.draft.url)
            val saved = SavedRequest(projectId = p.id, name = name.take(100), draft = state.value.draft)
            mutable.update { it.copy(workspace = it.workspace.copy(savedRequests = (listOf(saved) + it.workspace.savedRequests).take(100)), message = "Request added to the encrypted collection") }
        }.onFailure { message(it.message ?: "Cannot save request") }
    }
    fun exportSanitized(): String = currentProject()?.let { ReportExporter.sanitized(state.value.workspace, it.id) } ?: "{}"
    fun deleteProject() {
        val p = currentProject() ?: return
        runCatching { BrowserProfiles.clearProject(p.id) }.onFailure { message(it.message ?: "Browser profiles could not be cleared"); return }
        cancelRequest(); engine.sessions.clearProject(p.id)
        mutable.update { it.copy(projectId = null, draft = RequestDraft(), selected = null, comparison = "", workspace = it.workspace.copy(
            projects = it.workspace.projects.filterNot { item -> item.id == p.id }, exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == p.id },
            findings = it.workspace.findings.filterNot { f -> f.projectId == p.id }, savedRequests = it.workspace.savedRequests.filterNot { r -> r.projectId == p.id },
            identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id }), message = "Project, API evidence and isolated browser profiles removed.") }
    }
    fun addFinding(title: String, detail: String, evidence: Exchange? = state.value.selected) {
        val project = currentProject() ?: return
        if (title.isBlank() || detail.isBlank()) return message("Finding title and details are required")
        val finding = Finding(projectId = project.id, title = title.take(200), detail = detail.take(16000), evidenceIds = listOfNotNull(evidence?.id))
        mutable.update { it.copy(workspace = it.workspace.copy(findings = (listOf(finding) + it.workspace.findings).take(Limits.FINDINGS)), message = "Observation added; not automatically verified.") }
    }
    fun clearHistory() { mutable.update { it.copy(workspace = it.workspace.copy(exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == it.projectId }), selected = null) } }
    override fun onCleared() { engine.close(); super.onCleared() }
}
