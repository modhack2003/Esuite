package dev.esuite.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Technical editors must receive the characters the user typed. Without explicit options the IME
 * applies autocorrection and sentence capitalization, which turned `SameSite=Lax` into
 * `Same site=Lax` and `localStorage.setItem` into `local storage.setItem` on the audited phone.
 *
 * [KeyboardType.Text] is deliberate: [KeyboardType.Ascii] would restrict what can be entered, and a
 * request body or header value must still submit Unicode byte-for-byte.
 */
val TechnicalKeyboard = KeyboardOptions(
    autoCorrectEnabled = false,
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Text,
)

/** The same discipline for URL editors, with the URI layout. */
val UrlKeyboard = KeyboardOptions(
    autoCorrectEnabled = false,
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Uri,
)

/** Monospace presentation so whitespace, quoting and lookalike characters stay legible. */
@Composable fun technicalTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
