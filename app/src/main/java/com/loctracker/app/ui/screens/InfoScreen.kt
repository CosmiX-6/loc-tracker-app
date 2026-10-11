package com.loctracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loctracker.app.ui.Copy
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.theme.Danger

@Composable
fun InfoScreen(vm: MainViewModel, padding: PaddingValues) {
    val perms = rememberPermissions()
    var retention by remember { mutableIntStateOf(vm.retentionDays()) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier.statusBarsPadding().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("About & privacy", style = MaterialTheme.typography.headlineSmall)

        Text(Copy.LIMIT_TITLE, style = MaterialTheme.typography.titleMedium)
        Text(Copy.LIMIT_SUMMARY, style = MaterialTheme.typography.bodyMedium)
        Bullets(Copy.LIMIT_POINTS)

        Text("Privacy & security", style = MaterialTheme.typography.titleMedium)
        Bullets(Copy.PRIVACY_POINTS)

        PermissionsCard(perms, showBattery = true)

        Text("Keep history for", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach {
                FilterChip(retention == it, { retention = it; vm.setRetention(it) }, label = { Text("$it days") })
            }
        }
        TextButton(onClick = vm::clearHistory) { Text("Clear delivery history now") }
        Button(
            onClick = { confirmWipe = true },
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete all app data") }

        Text(
            "Map © OpenStreetMap contributors © CARTO. This app is not affiliated with or endorsed by WhatsApp.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all data?") },
            text = { Text("This stops all sharing and permanently removes recipients, sessions and history from this phone.") },
            confirmButton = { TextButton(onClick = { vm.wipeAll(); confirmWipe = false }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Bullets(points: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach {
            Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
