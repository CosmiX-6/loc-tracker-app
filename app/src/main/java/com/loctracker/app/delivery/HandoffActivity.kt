package com.loctracker.app.delivery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.loctracker.app.LocTrackerApp
import com.loctracker.app.R
import com.loctracker.app.data.DeliveryKind
import com.loctracker.app.data.DeliveryStatus
import com.loctracker.app.domain.Messages
import kotlinx.coroutines.launch

/**
 * Invisible trampoline behind the "ready" notifications: opens the recipient's WhatsApp chat
 * (with a prefilled location message for link updates) and records the hand-off.
 */
class HandoffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_RECORD_ID, 0)
        val container = (application as LocTrackerApp).container
        lifecycleScope.launch {
            val rec = container.repository.delivery(id)
            val recipient = rec?.let { container.repository.recipient(it.recipientId) }
            if (rec == null || recipient == null) {
                Toast.makeText(this@HandoffActivity, R.string.handoff_gone, Toast.LENGTH_LONG).show()
                finish(); return@launch
            }
            val text = if (rec.kind == DeliveryKind.LOCATION_LINK) {
                // Prefer the freshest fix over the one captured when the update was queued.
                val fix = container.hub.fix.value
                val lat = fix?.lat ?: rec.lat
                val lon = fix?.lon ?: rec.lon
                if (lat != null && lon != null) Messages.locationText(lat, lon, fix?.timeMs ?: rec.updatedAt) else null
            } else null
            val whatsapp = WhatsAppIntents.chat(this@HandoffActivity, recipient.phone, text)
            if (whatsapp == null) {
                container.repository.updateDelivery(
                    rec.copy(status = DeliveryStatus.FAILED, updatedAt = System.currentTimeMillis(), note = "WhatsApp is not installed"),
                )
                Toast.makeText(this@HandoffActivity, R.string.whatsapp_missing, Toast.LENGTH_LONG).show()
            } else {
                startActivity(whatsapp)
                container.repository.updateDelivery(
                    rec.copy(status = DeliveryStatus.OPENED_IN_WHATSAPP, updatedAt = System.currentTimeMillis(), note = null),
                )
                container.notifier.cancelReady(rec.recipientId, rec.kind)
            }
            finish()
        }
    }

    companion object {
        private const val EXTRA_RECORD_ID = "record_id"
        fun intent(context: Context, recordId: Long) =
            Intent(context, HandoffActivity::class.java).putExtra(EXTRA_RECORD_ID, recordId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
}
