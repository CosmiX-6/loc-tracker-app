package com.loctracker.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loctracker.app.data.Recipient
import com.loctracker.app.domain.PhoneNumbers
import com.loctracker.app.domain.WhatsAppLive
import com.loctracker.app.ui.DAY_LABELS
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.durationLabel
import com.loctracker.app.ui.minutesOfDay
import com.loctracker.app.ui.theme.Danger

private val INTERVALS = listOf(1, 2, 5, 10, 15, 30, 60)
private val DURATIONS = listOf(15, 30, 60, 120, 240, 480)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: MainViewModel, id: Long, onDone: () -> Unit, padding: PaddingValues) {
    var original by remember { mutableStateOf<Recipient?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf(10) }
    var duration by remember { mutableStateOf(60) }
    var linkUpdates by remember { mutableStateOf(true) }
    var scheduled by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(0b0011111) }
    var startMin by remember { mutableStateOf(8 * 60) }
    var endMin by remember { mutableStateOf(18 * 60) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(id) {
        if (id != 0L) vm.recipient(id)?.let { r ->
            original = r; name = r.name; phone = "+" + r.phone; interval = r.intervalMinutes; duration = r.sessionMinutes
            linkUpdates = r.linkUpdates; scheduled = r.scheduleEnabled; days = r.scheduleDays
            startMin = r.scheduleStartMinute; endMin = r.scheduleEndMinute
        }
    }

    fun pickTime(current: Int, set: (Int) -> Unit) =
        TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, true).show()

    Column(
        Modifier.statusBarsPadding().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (id == 0L) "New recipient" else "Edit recipient", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone, { phone = it }, label = { Text("WhatsApp number") }, singleLine = true,
            placeholder = { Text("+49 170 1234567") },
            supportingText = { Text("International format with country code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Update interval", "How often a fresh map-link message is prepared, and how often GPS is sampled at most.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERVALS.forEach { FilterChip(interval == it, { interval = it }, label = { Text(durationLabel(it)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also send map-link updates", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Each one needs your tap on Send in WhatsApp. Turn off to rely on WhatsApp Live Location only.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(linkUpdates, { linkUpdates = it })
        }

        Section("Session duration", "Sharing stops automatically after this time.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATIONS.forEach { FilterChip(duration == it, { duration = it }, label = { Text(durationLabel(it)) }) }
        }
        Text(
            "WhatsApp Live Location to choose in the chat: ${WhatsAppLive.label(WhatsAppLive.optionFor(duration))}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly schedule", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Start automatically (you get a prompt to open WhatsApp). Off = start manually.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(scheduled, { scheduled = it })
        }
        if (scheduled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAY_LABELS.forEachIndexed { i, d ->
                    FilterChip(days and (1 shl i) != 0, { days = days xor (1 shl i) }, label = { Text(d) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ pickTime(startMin) { startMin = it } }, Modifier.weight(1f)) { Text("From ${minutesOfDay(startMin)}") }
                OutlinedButton({ pickTime(endMin) { endMin = it } }, Modifier.weight(1f)) { Text("Until ${minutesOfDay(endMin)}") }
            }
            if (days == 0) Text("Pick at least one day.", color = Danger, style = MaterialTheme.typography.bodySmall)
        }

        error?.let { Text(it, color = Danger) }

        Button(
            onClick = {
                val digits = PhoneNumbers.normalize(phone)
                when {
                    name.isBlank() -> error = "Please enter a name."
                    digits == null -> error = "Enter the number in international format, e.g. +49 170 1234567."
                    scheduled && days == 0 -> error = "Pick at least one day for the schedule."
                    else -> vm.save(
                        (original ?: Recipient(name = "", phone = "")).copy(
                            name = name.trim(), phone = digits, intervalMinutes = interval, sessionMinutes = duration,
                            linkUpdates = linkUpdates, scheduleEnabled = scheduled, scheduleDays = days,
                            scheduleStartMinute = startMin, scheduleEndMinute = endMin,
                        ),
                        onDone,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text("Cancel") }
        original?.let { r ->
            TextButton(onClick = { vm.delete(r); onDone() }, Modifier.fillMaxWidth()) { Text("Delete recipient", color = Danger) }
        }
    }
}

@Composable
private fun Section(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
