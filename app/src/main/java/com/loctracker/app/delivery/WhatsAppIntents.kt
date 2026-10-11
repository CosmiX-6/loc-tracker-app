package com.loctracker.app.delivery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Builds only intents that WhatsApp documents (https://wa.me click-to-chat links). These open the
 * chat; WhatsApp never lets another app send a message or start Live Location on the user's behalf.
 */
object WhatsAppIntents {
    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun installedPackage(context: Context): String? = PACKAGES.firstOrNull {
        try {
            context.packageManager.getPackageInfo(it, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun chat(context: Context, phoneDigits: String, text: String?): Intent? {
        val pkg = installedPackage(context) ?: return null
        val url = "https://wa.me/$phoneDigits" + (text?.let { "?text=" + Uri.encode(it) } ?: "")
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
