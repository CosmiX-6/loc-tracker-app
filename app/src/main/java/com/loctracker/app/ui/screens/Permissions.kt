package com.loctracker.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.loctracker.app.ui.theme.Accent
import com.loctracker.app.ui.theme.Warn

class PermissionState(
    val fine: Boolean,
    val background: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val requestFine: () -> Unit,
    val requestBackground: () -> Unit,
    val requestBatteryExemption: () -> Unit,
) {
    val allGood get() = fine && background && notifications
}

private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

fun openAppSettings(c: Context) =
    c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun rememberPermissions(): PermissionState {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) openAppSettings(ctx) // Android shows no dialog for "All the time"; the user must pick it in Settings.
    }
    return remember(tick) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        PermissionState(
            fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
            background = Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notifications = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
            requestFine = {
                val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                fineLauncher.launch(list.toTypedArray())
            },
            requestBackground = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            requestBatteryExemption = {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
fun PermissionsCard(p: PermissionState, modifier: Modifier = Modifier, showBattery: Boolean = false) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            PermRow(p.fine, "Location", "Needed to see and share where you are.") {
                if (!p.fine) Button(onClick = p.requestFine) { Text("Allow") }
            }
            PermRow(
                p.background, "Location \"All the time\"",
                "Required for scheduled starts and reliable tracking with the screen off.",
            ) { if (p.fine && !p.background) Button(onClick = p.requestBackground) { Text("Allow") } }
            PermRow(p.notifications, "Notifications", "Used for \"ready to send\" prompts and expiry alerts.") {
                if (!p.notifications) Button(onClick = p.requestFine) { Text("Allow") }
            }
            if (showBattery) {
                PermRow(p.batteryUnrestricted, "Unrestricted battery", "Stops the system from pausing tracking in the background.") {
                    if (!p.batteryUnrestricted) TextButton(onClick = p.requestBatteryExemption) { Text("Change") }
                }
            }
        }
    }
}

@Composable
private fun PermRow(ok: Boolean, title: String, detail: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (ok) Accent else Warn)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}
