package com.loctracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.loctracker.app.data.DeliveryKind
import com.loctracker.app.data.DeliveryRecord
import com.loctracker.app.data.DeliveryStatus
import com.loctracker.app.ui.Copy
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.dateTime
import com.loctracker.app.ui.theme.Accent
import com.loctracker.app.ui.theme.Danger
import com.loctracker.app.ui.theme.Info
import com.loctracker.app.ui.theme.Muted
import com.loctracker.app.ui.theme.Warn

@Composable
fun HistoryScreen(vm: MainViewModel, padding: PaddingValues) {
    val items by vm.history.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(padding).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Delivery history", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text("Clear") }
        }
        Text(Copy.DELIVERY_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        if (items.isEmpty()) Text("Nothing yet. Prompts and results appear here once you start sharing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.id }) { HistoryRow(it) }
        }
    }
}

private fun label(s: DeliveryStatus) = when (s) {
    DeliveryStatus.QUEUED -> "Queued" to Muted
    DeliveryStatus.WAITING_FIX -> "Waiting for GPS" to Warn
    DeliveryStatus.WAITING_NETWORK -> "Waiting for network" to Warn
    DeliveryStatus.READY -> "Ready – tap the notification" to Info
    DeliveryStatus.OPENED_IN_WHATSAPP -> "Opened in WhatsApp" to Accent
    DeliveryStatus.SKIPPED -> "Skipped" to Muted
    DeliveryStatus.FAILED -> "Failed" to Danger
}

@Composable
private fun HistoryRow(r: DeliveryRecord) {
    val (statusText, color) = label(r.status)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (r.kind == DeliveryKind.NATIVE_LIVE_HANDOFF) "Live Location · " else "Map link · ") + r.recipientName,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                Text(statusText, color = color, style = MaterialTheme.typography.labelLarge)
            }
            Text(dateTime(r.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val extra = listOfNotNull(
                r.note,
                if (r.attempts > 0) "${r.attempts} ${if (r.attempts == 1) "attempt" else "attempts"}" else null,
                r.accuracyM?.let { "±${it.toInt()} m" },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB4BFCD))
        }
    }
}
