package dev.esuite.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.esuite.app.core.observationDateFormatter
import java.time.Instant
import java.time.ZoneId

private val LocalObservationDates = compositionLocalOf { observationDateFormatter() }
private val UtcZone = ZoneId.of("UTC")

@Composable
internal fun ObservationDates(useUtc: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val locale = LocalConfiguration.current.locales[0]
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { zone = ZoneId.systemDefault() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        zone = ZoneId.systemDefault()
        onDispose { context.unregisterReceiver(receiver) }
    }
    val displayZone = if (useUtc) UtcZone else zone
    val formatter = remember(locale, displayZone) { observationDateFormatter(locale, displayZone) }
    CompositionLocalProvider(LocalObservationDates provides formatter, content = content)
}

@Composable
internal fun observedAt(timestamp: Long): String {
    val formatter = LocalObservationDates.current
    return remember(timestamp, formatter) { formatter.format(Instant.ofEpochMilli(timestamp)) }
}
