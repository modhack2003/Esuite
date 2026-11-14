package dev.esuite.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.esuite.app.core.*
import dev.esuite.app.transport.ApiEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppState(
    val workspace: Workspace = Workspace(), val projectId: String? = null, val identity: String = "A",
    val draft: RequestDraft = RequestDraft(), val selected: Exchange? = null, val busy: Boolean = false,
    val message: String = "Create a project to start. No requests are sent automatically.", val comparison: String = "",
)

class AppViewModel : ViewModel() {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    val engine = ApiEngine()
    private var requestJob: Job? = null
    fun message(text: String) = mutable.update { it.copy(message = text.take(4000)) }
    fun currentProject(): Project? = state.value.workspace.projects.firstOrNull { it.id == state.value.projectId }
    fun createProject(name: String, origin: String) {
        runCatching {
            require(name.isNotBlank() && name.length <= 100) { "Enter a project name (up to 100 characters)" }
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
                    val a = engine.execute(project, "A", draft, "Identity comparison"); record(a)
                    val b = engine.execute(project, "B", draft, "Identity comparison"); record(b)
                    mutable.update { it.copy(selected = b, comparison = ResponseComparer.compare(a, b), message = "Separate session jars used; explicit Cookie/Authorization headers omitted.") }
                } else {
                    val response = engine.execute(project, snapshot.identity, snapshot.draft); record(response)
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
    fun clearSessions() { currentProject()?.let { engine.sessions.clearProject(it.id) }; message("API sessions cleared. Browser sessions have separate controls.") }
    fun addFinding(title: String, detail: String, evidence: Exchange? = state.value.selected) {
        val project = currentProject() ?: return
        if (title.isBlank() || detail.isBlank()) return message("Finding title and details are required")
        val finding = Finding(projectId = project.id, title = title.take(200), detail = detail.take(16000), evidenceIds = listOfNotNull(evidence?.id))
        mutable.update { it.copy(workspace = it.workspace.copy(findings = (listOf(finding) + it.workspace.findings).take(Limits.FINDINGS)), message = "Observation added; not automatically verified.") }
    }
    fun clearHistory() { mutable.update { it.copy(workspace = it.workspace.copy(exchanges = it.workspace.exchanges.filterNot { e -> e.projectId == it.projectId }), selected = null) } }
    override fun onCleared() { engine.close(); super.onCleared() }
}
