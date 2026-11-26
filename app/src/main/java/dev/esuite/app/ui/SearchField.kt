package dev.esuite.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import dev.esuite.app.core.atCharacterBoundary

@Composable
internal fun SearchField(query: String, update: (String) -> Unit, label: String, tag: String,
    onGo: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = query, onValueChange = { update(it.atCharacterBoundary(80)) },
        modifier = Modifier.fillMaxWidth().testTag(tag), label = { Text(label) }, singleLine = true,
        keyboardOptions = TechnicalKeyboard.copy(imeAction = if (onGo == null) ImeAction.Done else ImeAction.Go),
        keyboardActions = if (onGo == null) KeyboardActions.Default else KeyboardActions(onGo = { onGo() }),
        trailingIcon = if (query.isNotEmpty()) ({ TextButton(onClick = { update("") }) { Text("Clear") } }) else null,
    )
}
