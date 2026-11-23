package dev.esuite.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.esuite.app.core.*
import dev.esuite.app.transport.ApiEngine
import dev.esuite.app.storage.WorkspaceVault
import dev.esuite.app.storage.WorkspaceStore
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.BrowserAddress
import dev.esuite.app.browser.BrowserLogSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val message: String = "Opening saved site data…", val comparison: String = "", val ready: Boolean = false,
    val manualRequestLabel: String? = null,
    val openingWorkspace: Boolean = true,
    val persistedWorkspace: Workspace? = null, val saveError: String? = null, val pendingWrites: Int = 0,
    /**
     * ESuite browser profiles the WebView provider refused to delete because it still has them
     * loaded. Derived from the provider's own profile list on every load, never persisted.
     */
    val pendingProfileCleanup: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
class AppViewModel @JvmOverloads constructor(application: Application, private val vault: WorkspaceStore = WorkspaceVault(application)) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val importedBrowser = MutableStateFlow<BrowserActivity?>(null)
    internal val browserActivity = importedBrowser.asStateFlow()
    val engine = ApiEngine()
    /**
     * Pending editor text, in memory only and never part of [AppState.workspace], so typing cannot
     * trigger an encrypted save and nothing in this path reaches disk.
     */
    val drafts = EditorDrafts()
    private val workspacePreferences = application.getSharedPreferences("workspace_navigation", Context.MODE_PRIVATE)
    private var requestJob: Job? = null
    private val saveMutex = Mutex()
    private val storageDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var persistenceJob: Job? = null
    private var loadJob: Job? = null
    private var workspaceGeneration = 0
    // Bumped whenever the project/identity a result could be attributed to changes, so a result that
    // lands after a switch is recorded in Traffic but not applied under the wrong header.
    private var contextGeneration = 0
    private val importer = CollectionImporter()
    // One-shot report carried from eraseVault()'s profile cleanup into the next successful load
    // message, because erasing resets AppState and reloads immediately afterwards.
    private var profileCleanupNote: String? = null
    init { loadWorkspace() }
    fun loadWorkspace() {
        importedBrowser.value = null
        workspaceGeneration += 1
        contextGeneration += 1
        val generation = workspaceGeneration
        loadJob?.cancel()
        persistenceJob?.cancel()
        cancelRequest()
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(ready = false, openingWorkspace = true) }
            try {
                val workspace = withContext(storageDispatcher) { vault.load() }
                if (generation != workspaceGeneration) return@launch
                val rememberedId = workspacePreferences.getString("last_project", null)
                val project = workspace.projects.firstOrNull { it.id == rememberedId } ?: workspace.projects.firstOrNull()
                // Back on Main once the load returns, which is the documented contract for the
                // profile store: retry every ESuite profile whose project is gone, which is how a
                // profile left loaded by the previous process is finally removed.
                val sweep = BrowserProfiles.sweepOrphans(workspace.projects.map { it.id }.toSet())
                val note = profileCleanupNote?.let { " $it" }.orEmpty()
                profileCleanupNote = null
                mutable.update { it.copy(workspace = workspace, projectId = project?.id, identity = "A", draft = RequestDraft(url = project?.origin?.plus("/").orEmpty()), ready = true, openingWorkspace = false,
                    selected = null, comparison = "", persistedWorkspace = workspace, saveError = null, pendingWrites = 0, pendingProfileCleanup = sweep.pending,
                    message = note.trim()) }
                persistenceJob?.cancel()
                persistenceJob = viewModelScope.launch {
                    state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { _ ->
                        saveWorkspace()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == workspaceGeneration) mutable.update {
                    it.copy(openingWorkspace = false, message = "Could not open saved site data. ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
    fun saveNow() { viewModelScope.launch { saveWorkspace() } }
    // An awaitable commit result. UI acknowledgement is derived from the committed snapshot,
    // never from a generic success message belonging to an earlier edit.
    suspend fun saveWorkspace(): Boolean = saveMutex.withLock {
        val current = state.value
        if (!current.ready) return@withLock false
        if (current.persistedWorkspace == current.workspace && current.saveError == null) return@withLock true
        val generation = workspaceGeneration
        val snapshot = state.value.workspace
        mutable.update { it.copy(pendingWrites = it.pendingWrites + 1, saveError = null) }
        try {
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
        importedBrowser.value = null
        workspaceGeneration += 1
        contextGeneration += 1
        loadJob?.cancel()
        cancelRequest(); persistenceJob?.cancel(); engine.sessions.clearAll()
        viewModelScope.launch {
            mutable.update { it.copy(ready = false, openingWorkspace = true) }
            runCatching { withContext(storageDispatcher) { vault.erase() } }
                .onSuccess {
                    rememberProject(null)
                    drafts.clearProject(state.value.projectId)
                    // Erasing the vault loses the project index, so ESuite's own browser profiles are
                    // removed by prefix instead — the vault is not needed to decide what ESuite
                    // created. Ordered AFTER the erase succeeds (back on Main, which is the profile
                    // store's contract) so a failed erase leaves the profiles in place.
                    profileCleanupNote = BrowserProfiles.clearAllEsuiteOwned().describe()
                    mutable.value = AppState(); loadWorkspace()
                }
                .onFailure { failure -> mutable.update { it.copy(openingWorkspace = false, message = "Could not erase saved site data. ${failure.message}") } }
        }
    }
    fun message(text: String) = mutable.update { it.copy(message = text.take(4000)) }
    internal fun currentContext() = RequestContext(contextGeneration, state.value.projectId, state.value.identity)
    private fun supersededMessage(started: RequestContext, current: RequestContext): String =
        if (started.identity != current.identity)
            "A result for identity ${started.identity} arrived after you switched to ${current.identity}. It is recorded in Activity and was not applied here."
        else
            "A result arrived after the selected site changed. It is recorded in Activity and was not applied here."
    fun currentProject(): Project? = state.value.workspace.projects.firstOrNull { it.id == state.value.projectId }
    private fun rememberProject(id: String?) {
        workspacePreferences.edit { putString("last_project", id) }
    }

    fun createProject(name: String, origin: String): Project? {
        return runCatching {
            require(state.value.ready) { "Saved site data is opening." }
            require(name.isNotBlank() && name.length <= 100) { "Site name is too long" }
            require(state.value.workspace.projects.size < 20) { "Saved site limit reached (20)" }
            val scope = ScopePolicy(origin.trim())
            require(scope.target.isHttps) { "Enter an HTTPS origin" }
            val project = Project(name = name.trim(), origin = scope.canonicalOrigin)
            cancelRequest()
            importedBrowser.value = null
            contextGeneration += 1
            drafts.clearProject(state.value.projectId)
            mutable.update { it.copy(workspace = it.workspace.copy(projects = it.workspace.projects + project), projectId = project.id, identity = "A",
                draft = RequestDraft(url = project.origin + "/"), selected = null, comparison = "", message = "Site added to API Lab.") }
            rememberProject(project.id)
            persistDurable()
            project
        }.onFailure { message(it.message ?: "Invalid site") }.getOrNull()
    }
    fun openSite(address: String): Boolean {
        if (!state.value.ready) { message("Saved site data is opening."); return false }
        val text = address.trim()
        val url = BrowserAddress.https(if ("://" in text) text else "https://$text")
        if (url == null) { message("Enter a valid HTTPS address."); return false }
        val origin = BrowserAddress.origin(url)
        val current = currentProject()
        val existing = current?.takeIf { it.origin == origin }
            ?: state.value.workspace.projects.firstOrNull { it.origin == origin }
        if (existing != null) selectProject(existing.id)
        else {
            createProject(url.host.take(100), origin)
            if (currentProject()?.origin != origin) return false
        }
        replaceDraft(RequestDraft(url = url.newBuilder().fragment(null).build().toString()))
        message("")
        return true
    }

    fun selectProject(id: String) {
        val previous = state.value
        if (id == previous.projectId) return
        val project = previous.workspace.projects.firstOrNull { it.id == id } ?: return
        importedBrowser.value = null
        cancelRequest()
        contextGeneration += 1
        drafts.clearProject(previous.projectId)
        mutable.update { it.copy(projectId = id, identity = "A", draft = RequestDraft(url = project.origin + "/"), selected = null, comparison = "", message = "Selected ${project.origin}") }
        rememberProject(id)
    }
    internal fun clearBrowserActivity() { importedBrowser.value = null }

    internal fun openBrowserActivity(log: BrowserLogSnapshot, siteId: String?): Boolean {
        if (!state.value.ready || log.rows.isEmpty()) return false
        val site = if (siteId == null) {
            val host = BrowserAddress.https(log.origin)?.host ?: return false
            createProject(host.take(100), log.origin) ?: return false
        } else state.value.workspace.projects.firstOrNull { it.id == siteId && it.origin == log.origin } ?: return false
        selectProject(site.id)
        importedBrowser.value = BrowserActivity(site.id, log)
        message("Browser observations opened in Activity.")
        return true
    }

    fun identity(value: String) {
        require(value in listOf("A", "B"))
        val previous = state.value
        mutable.update { if (it.identity == value) it else it.copy(identity = value, selected = null,
            draft = CredentialPolicy.strip(it.draft, it.workspace.identityConfigs.filter { c -> c.projectId == it.projectId }),
            message = "Identity changed; observed auth headers removed. Body values remain unchanged and must be reviewed.") }
        // A retained header draft is stripped exactly as the ViewModel draft above is. Every
        // Editor.identityScoped editor is deliberately absent from this list: its key carries the
        // identity, so each identity keeps its own pending text and a switch can never carry one
        // identity's credential text into the other's editor, into the config a save writes, or into
        // the other identity's isolated cookie jar.
        if (previous.identity != value) {
            contextGeneration += 1
            val configuredNames = previous.workspace.identityConfigs.filter { it.projectId == previous.projectId }
                .flatMap { it.headers }.map { it.name }.toSet()
            drafts.transform(previous.projectId, listOf(Editor.ApiHeaders)) {
                CredentialPolicy.stripHeaderLines(it, configuredNames)
            }
        }
    }
    /**
     * Wholesale draft replacement (replay, cURL/raw import, loading a saved request) also refreshes
     * the header EDITOR, so the change is visible. Plain keystrokes in the method/URL/body editors
     * leave the headers unchanged and therefore never overwrite half-typed header text.
     */
    fun draft(value: RequestDraft) {
        val refreshHeaders = value.headers != state.value.draft.headers
        mutable.update { it.copy(draft = value) }
        if (refreshHeaders) writeHeaderEditor(value.headers)
    }
    fun replaceDraft(value: RequestDraft) {
        mutable.update { it.copy(draft = value) }
        writeHeaderEditor(value.headers)
    }
    fun select(exchange: Exchange) = mutable.update { it.copy(selected = exchange) }
    fun replay(exchange: Exchange) {
        mutable.update { it.copy(draft = CredentialPolicy.strip(exchange.request, it.workspace.identityConfigs.filter { c -> c.projectId == it.projectId }), selected = exchange,
            message = if (exchange.source == "Browser metadata") "Browser bodies are unavailable. Supply missing bodies before sending. Observed auth headers were removed." else "Draft loaded with observed auth headers removed. Review body values; sending is explicit.") }
        writeHeaderEditor(state.value.draft.headers)
    }
    private fun writeHeaderEditor(headers: List<Header>) =
        drafts.set(DraftKey.of(state.value.projectId, Editor.ApiHeaders, state.value.identity), headers.joinToString("\n") { "${it.name}: ${it.value}" })
    fun record(exchange: Exchange) {
        mutable.update { if (!it.ready || it.workspace.projects.none { p -> p.id == exchange.projectId }) it else it.copy(workspace = it.workspace.copy(exchanges = (listOf(exchange) + it.workspace.exchanges).take(Limits.HISTORY))) }
    }
    fun send(compare: Boolean = false) {
        val snapshot = state.value; val project = currentProject() ?: return message("Open a site in API Lab first")
        if (snapshot.busy) return
        val started = currentContext()
        requestJob = viewModelScope.launch {
            val identities = if (compare) "identities A and B" else "identity ${snapshot.identity}"
            mutable.update { it.copy(busy = true, manualRequestLabel = "${project.name} · $identities · ${project.origin}", selected = null, comparison = "", message = "Sending to ${project.origin}…") }
            var attemptedIdentity = snapshot.identity
            var attemptedDraft = snapshot.draft
            try {
                if (compare) {
                    val draft = CredentialPolicy.strip(snapshot.draft, snapshot.workspace.identityConfigs.filter { it.projectId == project.id })
                    attemptedIdentity = "A"; attemptedDraft = withIdentity(draft, project.id, "A")
                    val a = engine.execute(project, "A", attemptedDraft, "Identity comparison"); record(a)
                    attemptedIdentity = "B"; attemptedDraft = withIdentity(draft, project.id, "B")
                    val b = engine.execute(project, "B", attemptedDraft, "Identity comparison"); record(b)
                    // Both legs are already in Traffic; only the comparison view is context-guarded.
                    val comparison = withContext(Dispatchers.Default) { ResponseComparer.compare(a, b) }
                    val current = currentContext()
                    if (ResultAttribution.accepts(started, current))
                        mutable.update { it.copy(selected = b, comparison = comparison, message = "Separate session jars used; explicit Cookie/Authorization headers omitted.") }
                    else message(supersededMessage(started, current))
                } else {
                    attemptedDraft = withIdentity(snapshot.draft, project.id, snapshot.identity)
                    val response = engine.execute(project, snapshot.identity, attemptedDraft); record(response)
                    val current = currentContext()
                    if (ResultAttribution.accepts(started, current))
                        mutable.update { it.copy(selected = response, message = "HTTP ${response.status} · ${response.durationMs} ms${if (response.response.truncated) " · body truncated" else ""}") }
                    else message(supersededMessage(started, current))
                }
            } catch (e: CancellationException) { message("Request cancelled"); throw e }
            catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                val failed = Exchange(projectId = project.id, identity = attemptedIdentity, request = attemptedDraft,
                    source = if (compare) "Identity comparison" else "API Lab", error = detail)
                record(failed)
                val current = currentContext()
                if (ResultAttribution.accepts(started, current))
                    mutable.update { it.copy(selected = failed, comparison = "", message = detail) }
                else message(supersededMessage(started, current))
            } finally { mutable.update { it.copy(busy = false, manualRequestLabel = null) } }
        }
    }
    fun cancelRequest() { requestJob?.cancel() }

    internal suspend fun prepareImport(format: ImportFormat, input: String, project: Project): List<SavedRequest> =
        importer.parse { format.drafts(input, project) }

    internal suspend fun applyImport(format: ImportFormat, started: RequestContext, requests: List<SavedRequest>): String {
        fun requireCurrentSite(): Project {
            require(state.value.ready && ResultAttribution.accepts(started, currentContext())) {
                "Site or identity changed. Preview the import again."
            }
            return requireNotNull(currentProject()) { "Open a site in API Lab first." }
        }
        val project = requireCurrentSite()
        require(requests.isNotEmpty()) { "Select at least one draft." }
        if (format == ImportFormat.Curl) {
            val imported = requests.single()
            require(imported.projectId == project.id)
            ScopePolicy(project.origin).requireAllowed(imported.draft.url)
            replaceDraft(imported.draft)
            return "Draft loaded in API Lab. Review before sending."
        }
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val prepared = importer.prepare(state.value.workspace, project, requests)
            currentCoroutineContext().ensureActive()
            requireCurrentSite()
            val current = state.value
            val next = prepared.apply(current.workspace)
            if (next != null && mutable.compareAndSet(current, current.copy(workspace = next))) {
                return if (saveWorkspace()) {
                    "Saved ${requests.size} drafts. Open the API Lab collection to review them."
                } else "Drafts were added in memory. Use Save local data now to retry."
            }
        }
        error("Saved data changed during the import. Preview the file again.")
    }
    /**
     * Validate an immutable candidate off the UI thread and merge only its durable changes.
     * Browser history can still receive captures while JSON size is being checked.
     */
    private suspend fun admitDurable(message: String, prepare: (Workspace) -> Workspace): Boolean {
        val context = currentContext()
        try {
            repeat(3) {
                val current = state.value
                require(current.ready && ResultAttribution.accepts(context, currentContext())) {
                    "The selected site or identity changed. Reopen the original site to finish the edit."
                }
                val snapshot = current.workspace
                val candidate = withContext(Dispatchers.Default) {
                    prepare(snapshot).also(WorkspaceBudget::requireDurableCapacity)
                }
                val latest = state.value
                require(latest.ready && ResultAttribution.accepts(context, currentContext())) {
                    "The selected site or identity changed. Reopen the original site to finish the edit."
                }
                if (WorkspacePersistence.sameDurable(snapshot, latest.workspace) &&
                    mutable.compareAndSet(latest, latest.copy(
                        workspace = candidate.copy(exchanges = latest.workspace.exchanges),
                        message = message,
                    ))
                ) {
                    persistDurable()
                    return true
                }
            }
            message("Saved data is busy. Try saving again.")
        } catch (failure: IllegalArgumentException) {
            message(failure.message ?: "The edit could not be saved")
        }
        return false
    }

    private fun withIdentity(draft: RequestDraft, project: String, identity: String): RequestDraft {
        val configured = state.value.workspace.identityConfigs.firstOrNull { it.projectId == project && it.identity == identity }?.headers.orEmpty()
        return draft.copy(headers = draft.headers + configured.filterNot { incoming -> draft.headers.any { it.name.equals(incoming.name, true) } })
    }
    suspend fun configureIdentity(text: String) {
        val project = currentProject() ?: return
        val identity = state.value.identity
        val key = DraftKey.of(project.id, Editor.ApiIdentityHeaders, identity)
        val saved = admitDurable("Identity headers updated. Check the save status for confirmation.") { workspace ->
            val config = IdentityConfig(project.id, identity, DraftParser.headers(text))
            workspace.copy(identityConfigs = workspace.identityConfigs.filterNot {
                it.projectId == project.id && it.identity == identity
            } + config)
        }
        if (saved && drafts.text(key) == text) drafts.clear(key)
    }
    fun clearSessions() {
        currentProject()?.let { p -> engine.sessions.clearProject(p.id); mutable.update { it.copy(workspace = it.workspace.copy(identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id })) }; persistDurable() }
        message("API sessions and saved identity headers cleared. Use Settings to manage browser data.")
    }
    suspend fun saveDraft(name: String) {
        val project = currentProject() ?: return
        val draft = state.value.draft
        val saved = SavedRequest(projectId = project.id, name = name.trim(), draft = draft)
        admitDurable("Request saved to this site.") { workspace ->
            require(saved.name.isNotBlank() && saved.name.length <= 100) { "Use a saved request name between 1 and 100 characters." }
            require(workspace.savedRequests.size < 100) { "Collection limit is 100 requests. Remove a saved request before adding one." }
            DraftParser.validate(draft)
            ScopePolicy(project.origin).requireAllowed(draft.url)
            workspace.copy(savedRequests = listOf(saved) + workspace.savedRequests)
        }
    }
    suspend fun exportSanitized(): String? {
        val snapshot = state.value
        val project = snapshot.projectId ?: return null
        return withContext(Dispatchers.Default) { ReportExporter.sanitized(snapshot.workspace, project) }
    }

    suspend fun exportObservation(id: String): String? {
        val snapshot = state.value
        val project = snapshot.projectId ?: return null
        return withContext(Dispatchers.Default) { ReportExporter.selectedObservation(snapshot.workspace, project, id) }
    }
    fun deleteProject() {
        val p = currentProject() ?: return
        importedBrowser.value = null
        // A profile the provider still holds loaded no longer aborts the deletion: the workspace
        // deletion completes and the profile is recorded as pending cleanup with a retry path.
        val cleanup = BrowserProfiles.clearProject(p.id)
        cancelRequest(); contextGeneration += 1; engine.sessions.clearProject(p.id); drafts.clearProject(p.id)
        mutable.update { it.copy(projectId = null, draft = RequestDraft(), selected = null, comparison = "", workspace = it.workspace.copy(
            projects = it.workspace.projects.filterNot { item -> item.id == p.id }, exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == p.id },
            findings = it.workspace.findings.filterNot { f -> f.projectId == p.id }, savedRequests = it.workspace.savedRequests.filterNot { r -> r.projectId == p.id },
            identityConfigs = it.workspace.identityConfigs.filterNot { c -> c.projectId == p.id }),
            pendingProfileCleanup = (it.pendingProfileCleanup + cleanup.pending).distinct(),
            message = "Saved site data removed. ${cleanup.describe()}") }
        rememberProject(null)
        persistDurable()
    }
    /** Re-attempt every ESuite profile whose project is gone. Called from the UI thread. */
    fun retryProfileCleanup() {
        val result = BrowserProfiles.sweepOrphans(state.value.workspace.projects.map { it.id }.toSet())
        if (result.unsupported) return message(result.describe())
        mutable.update { it.copy(pendingProfileCleanup = result.pending, message = result.describe()) }
    }
    suspend fun addFinding(
        title: String, detail: String, evidence: Exchange?,
        expectedContext: RequestContext = currentContext(), findingId: String = java.util.UUID.randomUUID().toString(),
    ): Boolean {
        val project = currentProject() ?: return false
        if (!ResultAttribution.accepts(expectedContext, currentContext())) {
            message("The selected site or identity changed. Reopen the original context to save.")
            return false
        }
        return admitDurable("Observation added. Check the save status for confirmation.") { workspace ->
            require(title.isNotBlank() && title.length <= 200 && detail.isNotBlank() && detail.length <= 16000) {
                "Use a title between 1 and 200 characters and notes between 1 and 16,000 characters."
            }
            require(workspace.findings.size < Limits.FINDINGS) { "Observation limit reached. Remove a saved observation before adding one." }
            require(workspace.findings.none { it.id == findingId }) { "This observation was already added." }
            require(evidence == null || (evidence.projectId == project.id && workspace.containsEvidence(evidence))) {
                "The selected exchange is no longer available in this site’s history."
            }
            val snapshot = evidence?.let(EvidenceRetention::snapshot)
            val finding = Finding(id = findingId, projectId = project.id, title = title, detail = detail,
                evidenceIds = listOfNotNull(snapshot?.id), evidence = listOfNotNull(snapshot))
            workspace.copy(findings = listOf(finding) + workspace.findings)
        }
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
    fun clearHistory() {
        importedBrowser.value = null
        mutable.update { it.copy(workspace = it.workspace.copy(exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == it.projectId }), selected = null) }
        persistDurable()
    }
    override fun onCleared() { engine.close(); super.onCleared() }
}
