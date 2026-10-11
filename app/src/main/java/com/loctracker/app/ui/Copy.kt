package com.loctracker.app.ui

/** User-facing explanations of what the app can and cannot do with WhatsApp. Kept in one place. */
object Copy {
    const val LIMIT_TITLE = "How WhatsApp sharing works"

    const val LIMIT_SUMMARY =
        "WhatsApp has no public API that lets another app start Live Location or send messages for you. " +
            "Loc Tracker therefore works like a co-pilot, not an autopilot."

    val LIMIT_POINTS = listOf(
        "Live Location is started by you, inside the WhatsApp chat (Attach › Location › Share live location). " +
            "This app opens the right chat at the right time and keeps track of the session, but it cannot press that button.",
        "Once you start it, WhatsApp itself keeps the real Live Location running and visible on the recipient's map, " +
            "even if this app is closed. WhatsApp only offers 15 minutes, 1 hour or 8 hours.",
        "Map-link updates are prepared automatically at each interval, but each one needs a tap on the notification " +
            "and on Send in WhatsApp. The app cannot see whether you pressed Send.",
        "Nothing is sent to anyone without your tap. Automatically sending messages by controlling WhatsApp's screen " +
            "(accessibility automation) would violate WhatsApp's terms and risk your number being banned, so it is not used.",
        "When a session expires this app stops tracking, but it cannot stop WhatsApp Live Location – you get a reminder to stop it in the chat.",
    )

    val PRIVACY_POINTS = listOf(
        "Your location never goes to a Loc Tracker server – there is none. It only reaches a recipient through WhatsApp, by your hand.",
        "Contacts, sessions and history are stored in a database encrypted with a key held in the Android Keystore, excluded from backups.",
        "Location is only collected while a session is active, and the persistent notification is always visible while it is.",
        "Map tiles are downloaded from OpenStreetMap / CARTO, which can see your IP address and the tiles you view, not your identity.",
    )

    const val DELIVERY_NOTE =
        "\"Opened in WhatsApp\" means the chat was opened with your message ready. WhatsApp does not tell this app whether you pressed Send."

    val LIVE_STEPS = listOf(
        "Open the chat with the button below.",
        "Tap the attach (＋ / 📎) button, then Location.",
        "Choose Share live location and pick the duration shown.",
        "Press Send. Come back and tap \"I started it\" so the app can remind you before it ends.",
    )
}
