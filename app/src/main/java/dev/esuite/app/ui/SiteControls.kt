package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.core.Project

@Composable
internal fun SitePicker(state: AppState, site: Project?, model: AppViewModel, allowNew: Boolean = false) {
    var picking by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(allowNew && site == null) }
    if (state.workspace.projects.isNotEmpty()) {
        Box {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().testTag("site_picker")) {
                Text(site?.origin ?: "Choose a saved site", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                val duplicates = state.workspace.projects.groupingBy { it.origin }.eachCount()
                state.workspace.projects.forEach { entry ->
                    DropdownMenuItem(text = {
                        Text(if (duplicates[entry.origin] == 1) entry.origin else "${entry.origin} · ${entry.name}",
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }, onClick = { model.selectProject(entry.id); picking = false; adding = false })
                }
                if (allowNew) DropdownMenuItem(text = { Text("Open another site") }, onClick = { picking = false; adding = true },
                    modifier = Modifier.testTag("site_add"))
            }
        }
    } else if (!allowNew) {
        Text("Saved site activity appears after you use API Lab.", style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
    if (adding && allowNew) SiteEntry { address ->
        model.openSite(address).also { if (it) adding = false }
    }
}

@Composable
private fun SiteEntry(open: (String) -> Boolean) {
    var address by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (open(address)) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(address, { address = it }, singleLine = true, label = { Text("HTTPS address") },
            placeholder = { Text("https://example.com") }, keyboardOptions = UrlKeyboard.copy(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }), modifier = Modifier.fillMaxWidth().testTag("site_url"))
        Button(onClick = ::submit, enabled = address.isNotBlank(), modifier = Modifier.testTag("site_continue")) { Text("Open site") }
    }
}
