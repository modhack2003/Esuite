package dev.esuite.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** States the exact lifetime of pending editor text, including that process death loses it. */
@Composable fun DraftNotice() {
    Text("Pending drafts are kept in memory for this project only. They survive switching modules and rotation, and are lost if the app is closed.",
        style = MaterialTheme.typography.bodySmall, color = Muted)
}
