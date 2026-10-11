package com.loctracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.loctracker.app.data.Recipient
import com.loctracker.app.data.ShareSession
import com.loctracker.app.domain.BatteryInfo
import com.loctracker.app.domain.GpsStatus
import com.loctracker.app.domain.PowerTier
import com.loctracker.app.domain.WhatsAppLive
import com.loctracker.app.ui.Copy
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.clock
import com.loctracker.app.ui.remaining
import com.loctracker.app.ui.theme.Accent
import com.loctracker.app.ui.theme.Danger
import com.loctracker.app.ui.theme.Info
import com.loctracker.app.ui.theme.Warn

@Composable
fun DashboardScreen(vm: MainViewModel, onAddRecipient: () -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val fix by vm.fix.collectAsState()
    val gps by vm.gps.collectAsState()
    val battery by vm.battery.collectAsState()
    val tier by vm.tier.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val recipients by vm.recipients.collectAsState()
    val now by vm.now.collectAsState()
    val perms = rememberPermissions()

    var follow by remember { mutableStateOf(true) }
    var recenter by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var guideFor by remember { mutableStateOf<ShareSession?>(null) }

    DisposableEffect(perms.fine) {
        vm.setPreviewWanted(perms.fine)
        onDispose { vm.setPreviewWanted(false) }
    }

    Box(Modifier.fillMaxSize()) {
        LiveMap(fix = fix, follow = follow, recenterTick = recenter, onUserMoved = { follow = false }, modifier = Modifier.fillMaxSize())

        // Top status row
        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GpsChip(gps, fix?.accuracyM)
                BatteryChip(battery, tier)
            }
            if (!perms.allGood) PermissionsCard(perms)
        }

        FilledTonalIconButton(
            onClick = { follow = true; recenter++ },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) { Icon(Icons.Filled.MyLocation, "Centre on me") }

        // Bottom control panel
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomPadding),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sessions.isEmpty()) {
                    Text("Not sharing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (recipients.isEmpty()) "Add someone to start sharing your location through WhatsApp."
                        else "Pick who should get your location. You finish each step inside WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { if (recipients.isEmpty()) onAddRecipient() else if (!perms.fine) perms.requestFine() else picker = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(if (recipients.isEmpty()) "Add recipient" else "Start sharing") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sharing now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { picker = true }) { Text("Add") }
                        TextButton(onClick = vm::stopAll) { Text("Stop all", color = Danger) }
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sessions, key = { it.id }) { s ->
                            SessionCard(s, now, onOpen = { guideFor = s }, onConfirm = { vm.confirmNativeLive(s.id) }, onStop = { vm.stop(s.id) })
                        }
                    }
                }
            }
        }
    }

    if (picker) {
        val running = sessions.map { it.recipientId }.toSet()
        RecipientPicker(
            recipients.filter { it.enabled && it.id !in running },
            onDismiss = { picker = false },
            onStart = { ids -> picker = false; vm.startSharing(ids) },
        )
    }
    guideFor?.let { s ->
        val minutes = WhatsAppLive.optionFor(recipients.firstOrNull { it.id == s.recipientId }?.sessionMinutes ?: 60)
        AlertDialog(
            onDismissRequest = { guideFor = null },
            title = { Text("Start Live Location for ${s.recipientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Copy.LIVE_STEPS.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n"))
                    Text(
                        "Pick ${WhatsAppLive.label(minutes)} in WhatsApp – the closest option to this session.",
                        color = Accent, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { Button(onClick = { vm.openWhatsApp(ctx, s); guideFor = null }) { Text("Open WhatsApp") } },
            dismissButton = { TextButton(onClick = { guideFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionCard(s: ShareSession, now: Long, onOpen: () -> Unit, onConfirm: () -> Unit, onStop: () -> Unit) {
    val total = (s.expiresAt - s.startedAt).coerceAtLeast(1)
    val left = (s.expiresAt - now).coerceAtLeast(0)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.recipientName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${remaining(left)} · ends ${clock(s.expiresAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Stop sharing with ${s.recipientName}", tint = Danger) }
            }
            LinearProgressIndicator(progress = { left.toFloat() / total }, Modifier.fillMaxWidth(), color = Accent, trackColor = MaterialTheme.colorScheme.outline)
            val liveEnds = s.nativeLiveEndsAt
            if (liveEnds != null && liveEnds > now) {
                Text("WhatsApp Live Location running until ${clock(liveEnds)}", color = Accent, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onOpen, Modifier.fillMaxWidth()) { Text("Open chat again") }
            } else {
                Text(
                    if (liveEnds != null) "Your WhatsApp Live Location has ended – share it again if needed."
                    else "Live Location is not started yet. Only you can start it in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall, color = Warn,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpen, Modifier.weight(1f)) { Text("Start in WhatsApp") }
                    OutlinedButton(onClick = onConfirm) { Text("I started it") }
                }
            }
        }
    }
}

@Composable
private fun RecipientPicker(options: List<Recipient>, onDismiss: () -> Unit, onStart: (Collection<Long>) -> Unit) {
    val selected: SnapshotStateList<Long> = remember { mutableStateListOf() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share with") },
        text = {
            if (options.isEmpty()) Text("Everyone is already being shared with, or no recipient is enabled.")
            else LazyColumn {
                items(options, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.id in selected, { if (it) selected.add(r.id) else selected.remove(r.id) })
                        Column {
                            Text(r.name)
                            Text("Session ${com.loctracker.app.ui.durationLabel(r.sessionMinutes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onStart(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GpsChip(status: GpsStatus, accuracy: Float?) {
    val (icon, text, tint) = when (status) {
        GpsStatus.NO_PERMISSION -> Triple(Icons.Filled.GpsOff, "No permission", Danger)
        GpsStatus.GPS_OFF -> Triple(Icons.Filled.GpsOff, "Location is off", Danger)
        GpsStatus.SEARCHING -> Triple(Icons.Filled.GpsNotFixed, "Searching…", Warn)
        GpsStatus.STALE -> Triple(Icons.Filled.GpsNotFixed, "Signal lost", Warn)
        GpsStatus.WEAK -> Triple(Icons.Filled.GpsNotFixed, "Weak ±${accuracy?.toInt() ?: "?"} m", Warn)
        GpsStatus.GOOD -> Triple(Icons.Filled.GpsFixed, "GPS ±${accuracy?.toInt() ?: "?"} m", Accent)
    }
    StatusPill(icon, text, tint)
}

@Composable
fun BatteryChip(b: BatteryInfo, tier: PowerTier) {
    val low = !b.charging && b.percent <= 15
    val text = "${b.percent}%" + when {
        tier == PowerTier.SAVER || tier == PowerTier.CRITICAL -> " · saver"
        tier == PowerTier.STATIONARY -> " · idle"
        else -> ""
    }
    StatusPill(if (low) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull, text, if (low) Danger else Info)
}
