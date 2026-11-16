package dev.esuite.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.esuite.app.core.*
import dev.esuite.app.transport.ApiEngine
import dev.esuite.app.storage.WorkspaceVault
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.automation.PayloadMode
import dev.esuite.app.automation.RequestPlans
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
    val taskProgress: String = "No automation task running",
    val persistedWorkspace: Workspace? = null, val saveError: String? = null, val pendingWrites: Int = 0,
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
    private var loadJob: Job? = null
    private var workspaceGeneration = 0
    private val importer = CollectionImporter()
    init { loadWorkspace() }
    fun loadWorkspace() {
        workspaceGeneration += 1
        val generation = workspaceGeneration
        loadJob?.cancel()
        persistenceJob?.cancel()
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(ready = false) }
            try {
                val workspace = withContext(storageDispatcher) { vault.load() }
                if (generation != workspaceGeneration) return@launch
                val project = workspace.projects.firstOrNull()
                mutable.update { it.copy(workspace = workspace, projectId = project?.id, draft = RequestDraft(url = project?.origin?.plus("/").orEmpty()), ready = true,
                    selected = null, persistedWorkspace = workspace, saveError = null, pendingWrites = 0, message = "Encrypted workspace open. Requests only run when you explicitly start them.") }
                persistenceJob?.cancel()
                persistenceJob = viewModelScope.launch {
                    state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { _ ->
                        saveWorkspace()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == workspaceGeneration) message("Workspace could not be opened. No file was overwritten. ${e.message ?: e.javaClass.simpleName}") }
        }
    }
    fun saveNow() { viewModelScope.launch { saveWorkspace() } }
    // An awaitable commit result. UI acknowledgement is derived from the committed snapshot,
    // never from a generic success message belonging to an earlier edit.
    suspend fun saveWorkspace(): Boolean {
        if (!state.value.ready) return false
        val generation = workspaceGeneration
        val snapshot = state.value.workspace
        mutable.update { it.copy(pendingWrites = it.pendingWrites + 1, saveError = null) }
        return try {
            val persisted = withContext(storageDispatcher) { vault.save(snapshot) }
            if (generation == workspaceGeneration) mutable.update { current ->
                current.copy(workspace = WorkspacePersistence.reconcile(current.workspace, snapshot, persisted),
                    persistedWorkspace = persisted, saveError = null)
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val detail = if (e is WorkspacePersistence.CapacityExceeded) e.message!! else "Encrypted save could not be verified. Current edits remain in memory. Use Save now to retry."
            if (generation == workspaceGeneration) mutable.update { it.copy(saveError = detail) }
            false
        } finally {
            if (generation == workspaceGeneration) mutable.update { it.copy(pendingWrites = maxOf(0, it.pendingWrites - 1)) }
        }
    }
    private fun persistDurable() = saveNow()
    // Best effort onStop scheduling; arbitrary process death before acknowledgement can lose pending edits.
    fun flush() { if (state.value.ready) saveNow() }
    fun eraseVault() {
        workspaceGeneration += 1
        loadJob?.cancel()
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
            persistDurable()
        }.onFailure { message(it.message ?: "Invalid project") }
    }
    fun selectProject(id: String) {
        cancelRequest()
        mutable.update { current ->
            val project = current.workspace.projects.first { it.id == id }
            current.copy(projectId = id, draft = RequestDraft(url = project.origin + "/"), selected = null, comparison = "", message = "Selected ${project.name}")
        }
    }
    fun identity(value: String) {
        require(value in listOf("A", "B"))
        mutable.update { if (it.identity == value) it else it.copy(identity = value, selected = null,
            draft = CredentialPolicy.strip(it.draft, it.workspace.identityConfigs.filter { c -> c.projectId == it.projectId }),
            message = "Identity changed; observed auth headers removed. Body values remain unchanged and must be reviewed.") }
    }
    fun draft(value: RequestDraft) = mutable.update { it.copy(draft = value) }
    fun select(exchange: Exchange) = mutable.update { it.copy(selected = exchange) }
    fun replay(exchange: Exchange) = mutable.update { it.copy(draft = CredentialPolicy.strip(exchange.request, it.workspace.identityConfigs.filter { c -> c.projectId == it.projectId }), selected = exchange,
        message = if (exchange.source == "Browser metadata") "Browser bodies are unavailable. Supply missing bodies before sending. Observed auth headers were removed." else "Draft loaded with observed auth headers removed. Review body values; sending is explicit.") }
    fun record(exchange: Exchange) {
        mutable.update { if (!it.ready || it.workspace.projects.none { p -> p.id == exchange.projectId }) it else it.copy(workspace = it.workspace.copy(exchanges = (listOf(exchange) + it.workspace.exchanges).take(Limits.HISTORY))) }
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
            var attemptedIdentity = snapshot.identity
            var attemptedDraft = snapshot.draft
            try {
                if (compare) {
                    val draft = CredentialPolicy.strip(snapshot.draft, snapshot.workspace.identityConfigs.filter { it.projectId == project.id })
                    attemptedIdentity = "A"; attemptedDraft = withIdentity(draft, project.id, "A")
                    val a = engine.execute(project, "A", attemptedDraft, "Identity comparison"); record(a)
                    attemptedIdentity = "B"; attemptedDraft = withIdentity(draft, project.id, "B")
                    val b = engine.execute(project, "B", attemptedDraft, "Identity comparison"); record(b)
                    mutable.update { it.copy(selected = b, comparison = ResponseComparer.compare(a, b), message = "Separate session jars used; explicit Cookie/Authorization headers omitted.") }
                } else {
                    attemptedDraft = withIdentity(snapshot.draft, project.id, snapshot.identity)
                    val response = engine.execute(project, snapshot.identity, attemptedDraft); record(response)
                    mutable.update { it.copy(selected = response, message = "HTTP ${response.status} · ${response.durationMs} ms${if (response.response.truncated) " · body truncated" else ""}") }
                }
            } catch (e: CancellationException) { message("Request cancelled"); throw e }
            catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                val failed = Exchange(projectId = project.id, identity = attemptedIdentity, request = attemptedDraft,
                    source = if (compare) "Identity comparison" else "API Lab", error = detail)
                record(failed)
                mutable.update { it.copy(selected = failed, comparison = "", message = detail) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancelRequest() { requestJob?.cancel() }
    fun previewPayloads(inputs: List<String>, mode: PayloadMode): List<RequestDraft> {
        return RequestPlans.payloadsFromText(state.value.draft, inputs, mode).also { drafts ->
            val project = currentProject() ?: error("Create a project first")
            drafts.forEach { DraftParser.validate(it); ScopePolicy(project.origin).requireAllowed(it.url) }
        }
    }
    fun runPayloads(inputs: List<String>, mode: PayloadMode) {
        if (state.value.busy) return
        val project = currentProject() ?: return
        val identity = state.value.identity
        val drafts = runCatching { previewPayloads(inputs, mode) }.getOrElse { message(it.message ?: "Invalid plan"); return }
        requestJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, taskProgress = "Parameter task: 0/${drafts.size}") }
            try {
                for ((index, draft) in drafts.withIndex()) {
                    ensureActive()
                    val result = engine.execute(project, identity, withIdentity(draft, project.id, identity), "Parameter runner")
                    record(result); mutable.update { it.copy(selected = result, taskProgress = "Parameter task: ${index + 1}/${drafts.size}") }
                    if (result.status in listOf(429, 503)) { message("Task stopped on HTTP ${result.status}; no automatic retry."); return@launch }
                    if (index != drafts.lastIndex) delay(1000)
                }
                message("Parameter task complete; inspect results in Traffic")
            } catch (e: CancellationException) { message("Parameter task cancelled"); throw e }
            catch (e: Exception) { message("Task stopped: ${e.message}") }
            finally { mutable.update { it.copy(busy = false, taskProgress = "Task stopped / completed; captured results retained") } }
        }
    }
    fun runMacro(names: String, tokenPath: String) {
        if (state.value.busy) return
        val project = currentProject() ?: return
        val identity = state.value.identity
        val steps = runCatching {
            val requested = names.lines().filter(String::isNotBlank)
            require(requested.size in 1..10) { "Provide 1–10 exact saved-request names" }
            requested.map { name ->
                val matches = state.value.workspace.savedRequests.filter { it.projectId == project.id && it.name == name.trim() }
                require(matches.size == 1) { "Saved request '$name' is missing or ambiguous" }
                matches.single().draft.also { DraftParser.validate(it); ScopePolicy(project.origin).requireAllowed(it.url) }
            }
        }.getOrElse { message(it.message ?: "Invalid sequence"); return }
        requestJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, taskProgress = "Sequence: 0/${steps.size}") }
            try {
                var token: String? = null
                for ((index, template) in steps.withIndex()) {
                    ensureActive()
                    if ((template.url + template.body + template.headers.joinToString { it.value }).contains("{{token}}")) require(token != null) { "{{token}} is unavailable before the first extraction" }
                    val draft = token?.let { RequestPlans.applyToken(template, it) } ?: template
                    val result = engine.execute(project, identity, withIdentity(draft, project.id, identity), "Request sequence")
                    record(result); mutable.update { it.copy(selected = result, taskProgress = "Sequence: ${index + 1}/${steps.size}") }
                    require(result.status != null && result.status in 200..299) { "Sequence stopped at non-success HTTP ${result.status}" }
                    if (index == 0 && steps.size > 1 && tokenPath.isNotBlank()) token = RequestPlans.extractToken(result.response.value, tokenPath.trim())
                    if (index != steps.lastIndex) delay(1000)
                }
                message("Sequence complete. No automatic retries or unattended MFA handling.")
            } catch (e: CancellationException) { message("Sequence cancelled"); throw e }
            catch (e: Exception) { message("Sequence stopped: ${e.message}") }
            finally { mutable.update { it.copy(busy = false, taskProgress = "Task stopped / completed; captured results retained") } }
        }
    }
    fun importCurl(text: String) {
        val p = currentProject() ?: return message("Create a project first")
        runCatching { ApiImports.curl(text, p.origin) }.onSuccess { draft(it); message("cURL imported as data; no command or network request was executed.") }.onFailure { message(it.message ?: "Import failed") }
    }
    fun importOpenApi(text: String) = importCollection { project -> ApiImports.openApi(text, project) }
    fun importHar(text: String) = importCollection { project -> ApiImports.har(text, project) }
    private fun importCollection(parse: (Project) -> List<SavedRequest>) {
        val project = currentProject() ?: return message("Create a project first")
        val generation = workspaceGeneration
        viewModelScope.launch {
            try {
                val requests = importer.parse { parse(project) }
                repeat(3) {
                    ensureActive()
                    require(generation == workspaceGeneration && state.value.projectId == project.id && state.value.ready) { "Workspace or project changed; import discarded" }
                    val prepared = importer.prepare(state.value.workspace, project, requests)
                    ensureActive()
                    require(generation == workspaceGeneration && state.value.projectId == project.id && state.value.ready) { "Workspace or project changed; import discarded" }
                    val current = state.value
                    val next = prepared.apply(current.workspace)
                    if (next != null && mutable.compareAndSet(current, current.copy(workspace = next,
                            message = "Imported ${requests.size} in-scope drafts; no requests were executed. Review before sending."))) {
                        persistDurable()
                        return@launch
                    }
                }
                message("Workspace changed repeatedly; import not applied. Retry when edits settle.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "Import failed") }
        }
    }
    private fun mergeSaved(requests: List<SavedRequest>) {
        mutable.update {
            require(requests.size + it.workspace.savedRequests.size <= 100) { "Collection limit is 100 requests. Remove saved items before importing more." }
            val next = it.workspace.copy(savedRequests = requests + it.workspace.savedRequests)
            WorkspaceBudget.requireDurableCapacity(next); it.copy(workspace = next)
        }
        persistDurable()
    }
    private fun withIdentity(draft: RequestDraft, project: String, identity: String): RequestDraft {
        val configured = state.value.workspace.identityConfigs.firstOrNull { it.projectId == project && it.identity == identity }?.headers.orEmpty()
        return draft.copy(headers = draft.headers + configured.filterNot { incoming -> draft.headers.any { it.name.equals(incoming.name, true) } })
    }
    fun configureIdentity(text: String) {
        val p = currentProject() ?: return
        runCatching { DraftParser.headers(text) }.onSuccess { headers ->
            val config = IdentityConfig(p.id, state.value.identity, headers)
            runCatching {
                mutable.update {
                    val next = it.workspace.copy(identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id && c.identity == config.identity } + config)
                    WorkspaceBudget.requireDurableCapacity(next)
                    it.copy(workspace = next, message = "Identity headers updated; check encrypted-save status. Cookie jar sessions remain memory-only.")
                }
                persistDurable()
            }.onFailure { message(it.message ?: "Identity storage budget exceeded") }
        }.onFailure { message(it.message ?: "Invalid identity headers") }
    }
    fun clearSessions() {
        currentProject()?.let { p -> engine.sessions.clearProject(p.id); mutable.update { it.copy(workspace = it.workspace.copy(identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id })) }; persistDurable() }
        message("API sessions and saved identity headers cleared. Browser profiles have separate controls.")
    }
    fun transferBrowserCookies(url: String, cookies: List<String>): Boolean {
        val project = currentProject() ?: return false
        return runCatching {
            val target = ScopePolicy(project.origin).requireAllowed(url)
            val count = engine.sessions.jar(project.id, state.value.identity).replaceFromBrowser(target, cookies)
            draft(RequestDraft(url = target.toString()))
            message("Replaced API identity ${state.value.identity}'s memory-only cookie jar with $count browser cookies for this URL. Review API Lab before sending.")
        }.onFailure { message(it.message ?: "Cookie transfer failed") }.isSuccess
    }
    fun saveDraft(name: String) {
        val p = currentProject() ?: return
        runCatching {
            require(name.isNotBlank() && name.length <= 100) { "Saved request name must be 1–100 characters" }; DraftParser.validate(state.value.draft); ScopePolicy(p.origin).requireAllowed(state.value.draft.url)
            val saved = SavedRequest(projectId = p.id, name = name, draft = state.value.draft)
            mergeSaved(listOf(saved)); message("Request added to the collection; check encrypted-save status")
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
        persistDurable()
    }
    fun addFinding(title: String, detail: String, evidence: Exchange? = state.value.selected): Boolean {
        val project = currentProject() ?: return false
        return runCatching {
            require(title.isNotBlank() && title.length <= 200 && detail.isNotBlank() && detail.length <= 16000) { "Use a 1–200 character title and 1–16000 character detail" }
            // The bounded snapshot is part of the admitted record: the durable budget decides on the
            // finding WITH its retained evidence, so an accepted observation can never lose it later.
            val finding = Finding(projectId = project.id, title = title, detail = detail, evidenceIds = listOfNotNull(evidence?.id),
                evidence = listOfNotNull(evidence?.let(EvidenceRetention::snapshot)))
            mutable.update {
                require(it.workspace.findings.size < Limits.FINDINGS) { "Observation limit reached. Remove saved items before adding more." }
                val next = it.workspace.copy(findings = listOf(finding) + it.workspace.findings)
                try { WorkspaceBudget.requireDurableCapacity(next) } catch (e: IllegalArgumentException) {
                    throw if (finding.evidence.isEmpty()) e
                    else IllegalArgumentException("Retained evidence does not fit the durable budget. Remove saved observations or their evidence before adding more.")
                }
                it.copy(workspace = next, message = "Observation added; not automatically verified.")
            }
            persistDurable()
        }.onFailure { message(it.message ?: "Observation budget exceeded") }.isSuccess
    }
    fun deleteSavedRequest(id: String) { mutable.update { it.copy(workspace = it.workspace.copy(savedRequests = it.workspace.savedRequests.filterNot { r -> r.id == id })) }; persistDurable() }
    fun deleteFinding(id: String) { mutable.update { it.copy(workspace = it.workspace.copy(findings = it.workspace.findings.filterNot { f -> f.id == id })) }; persistDurable() }
    /** Drop one retained snapshot but keep its id, so the reference reports the explicit missing state. */
    fun removeEvidence(findingId: String, exchangeId: String) {
        mutable.update {
            it.copy(workspace = it.workspace.copy(findings = it.workspace.findings.map { f ->
                if (f.id == findingId) f.copy(evidence = f.evidence.filterNot { e -> e.id == exchangeId }) else f
            }), message = "Retained evidence removed. The observation keeps its reference and now reports evidence as not retained.")
        }
        persistDurable()
    }
    fun clearHistory() { mutable.update { it.copy(workspace = it.workspace.copy(exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == it.projectId }), selected = null) } }
    override fun onCleared() { engine.close(); super.onCleared() }
}
