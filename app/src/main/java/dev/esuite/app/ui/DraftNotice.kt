package dev.esuite.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** States the exact lifetime of pending editor text, including that process death loses it. */
@Composable fun DraftNotice() {
    Text("Drafts stay in memory while you switch tabs or rotate the device. Save a request or finding to keep it after closing ESuite.",
        style = MaterialTheme.typography.bodySmall, color = Muted)
}
