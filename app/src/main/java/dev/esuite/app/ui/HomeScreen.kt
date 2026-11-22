package dev.esuite.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.R
import dev.esuite.app.BuildConfig
import dev.esuite.app.Screen
import dev.esuite.app.core.Project

@Composable
internal fun HomeScreen(state: AppState, model: AppViewModel, project: Project?, navigate: (Screen) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val projects = state.workspace.projects
    var creating by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    val historyCount = remember(state.workspace.exchanges, state.projectId) {
        state.workspace.exchanges.count { it.projectId == state.projectId }
    }
    val findingCount = remember(state.workspace.findings, state.projectId) {
        state.workspace.findings.count { it.projectId == state.projectId }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_esuite), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(38.dp))
        Text("ESuite", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = { managing = !managing }, modifier = Modifier.testTag("home_settings")) {
            Icon(Icons.Outlined.Settings, contentDescription = if (managing) "Close workspace settings" else "Workspace settings")
        }
    }

    if (managing) {
        WorkspaceControls(state, model)
        Text("ESuite ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Muted)
        return
    }
    if (projects.isEmpty() || creating) {
        Text("Create a project", style = MaterialTheme.typography.titleLarge)
        Text("Choose the HTTPS origin for this workspace.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Project name") },
            modifier = Modifier.fillMaxWidth().testTag("project_name"))
        OutlinedTextField(origin, { origin = it }, singleLine = true, label = { Text("HTTPS origin") },
            placeholder = { Text("https://api.example.test") }, keyboardOptions = UrlKeyboard,
            modifier = Modifier.fillMaxWidth().testTag("project_origin"))
        Button(
            modifier = Modifier.fillMaxWidth().testTag("create_project"),
            enabled = name.isNotBlank() && origin.isNotBlank() && !state.busy,
            onClick = {
                val count = model.state.value.workspace.projects.size
                model.createProject(name, origin)
                if (model.state.value.workspace.projects.size > count) {
                    name = ""
                    origin = ""
                    creating = false
                    focus.clearFocus()
                    keyboard?.hide()
                }
            },
        ) { Text("Create project") }
        if (projects.isNotEmpty()) TextButton(onClick = { creating = false }) { Text("Cancel") }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Raised),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(project?.name ?: "Choose a project", style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            project?.let {
                Text(it.origin, style = technicalTextStyle(), color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box {
                TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("switch_project")) {
                    Text("Projects")
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    projects.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = { model.selectProject(option.id); choosing = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("New project") }, onClick = { choosing = false; creating = true },
                        modifier = Modifier.testTag("new_project"))
                }
            }
        }
    }
    if (project != null) {
        HomeDestination(Screen.Browser, "Inspect a page", navigate)
        HomeDestination(Screen.Api, "Build and review a request", navigate)
        HomeDestination(Screen.Traffic, "$historyCount exchanges", navigate)
        HomeDestination(Screen.Findings, "$findingCount observations", navigate)
    }
    HomeDestination(Screen.Tools, "Encode, import or review offline", navigate)
}

@Composable
private fun HomeDestination(screen: Screen, subtitle: String, navigate: (Screen) -> Unit) {
    Card(onClick = { navigate(screen) }, modifier = Modifier.fillMaxWidth().testTag("home_${screen.name.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = Raised)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(screen.icon, contentDescription = null, tint = Signal)
            Column(Modifier.weight(1f)) {
                Text(screen.label, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Muted)
        }
    }
}
