package com.loctracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loctracker.app.domain.PhoneNumbers
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.durationLabel
import com.loctracker.app.ui.scheduleSummary

@Composable
fun RecipientsScreen(vm: MainViewModel, onEdit: (Long) -> Unit, padding: PaddingValues) {
    val recipients by vm.recipients.collectAsState()
    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
            Text("Recipients", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 16.dp))
            if (recipients.isEmpty()) {
                Text(
                    "No recipients yet. Add a WhatsApp number and choose how often, when and for how long you share with them. " +
                        "Every recipient has their own settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(recipients, key = { it.id }) { r ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onEdit(r.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text(PhoneNumbers.display(r.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "Every ${durationLabel(r.intervalMinutes)} · ${durationLabel(r.sessionMinutes)} session · ${scheduleSummary(r)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = r.enabled, onCheckedChange = { vm.setEnabled(r, it) })
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { onEdit(0) }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Add recipient")
        }
    }
}
