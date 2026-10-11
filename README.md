# Loc Tracker

Android app (Kotlin, Jetpack Compose) that helps you share your location with several people through
WhatsApp – each with their own interval, weekly schedule and session length – with a dark, map-first dashboard.

## Read this first: what WhatsApp does and does not allow

You asked for WhatsApp's **native Live Location** (not just a link). Here is the honest constraint:

> **No Android API, intent, deep link or public WhatsApp API can start Live Location or send a message on a
> user's behalf.** WhatsApp Live Location can only be started by a person tapping
> *Attach › Location › Share live location › Send* inside the chat.

So this app is a **co-pilot, not an autopilot**:

| What | Who does it |
|---|---|
| Decide *when* (manual or weekly schedule), *who*, *how long* | App |
| Open the exact recipient chat at the right time (`wa.me` deep link) and tell you which duration to pick | App |
| Press *Share live location* / *Send* | **You** (WhatsApp requires it) |
| Keep Live Location running, visible on the recipient's map, even with this app closed | **WhatsApp** |
| Remind you ~5 min before WhatsApp's share ends, and to stop it when the session expires | App |
| Prepare a fresh Google-Maps-link message every interval; you tap the notification, then *Send* | App + you |
| Auto-expire the session and stop GPS tracking | App |

Not done on purpose: driving WhatsApp's UI with an Accessibility Service or similar automation. It would make
fully automatic sending technically possible, but it violates WhatsApp's terms (risk of your number being banned),
is fragile, and is a privacy hazard. The WhatsApp Business Cloud API can send automatic messages, but only *static*
messages from a business number – it cannot start Live Location – so it is not used. The delivery layer
(`DeliveryProcessor`) is isolated so such a channel could be added later.

"Opened in WhatsApp" in the history means the chat was opened with your message ready; WhatsApp never tells
other apps whether you pressed Send.

## Features

- Multiple recipients, each with independent: update interval (1–60 min), session duration (15 min–8 h),
  weekly schedule (days + time window, may cross midnight), and map-link updates on/off.
- Dark live map (OpenStreetMap data, CARTO Dark tiles; no API key), accuracy circle, GPS status
  (permission / location off / searching / weak / good / signal lost) and battery status.
- Start/stop per recipient or all at once; persistent notification with *Stop all* while tracking.
- Automatic expiry (AlarmManager + service check, survives process death and reboot).
- Delivery history with status, attempts and notes; configurable retention; clear / wipe all data.
- Battery-aware tracking (`BatteryPolicy`): full accuracy → slower when standing still → balanced when
  battery ≤ 15 % or battery saver → minimal at ≤ 5 %; fused provider, interval-capped sampling.
- Retry handling: an update waits (WAITING_FIX / WAITING_NETWORK) when there is no fresh GPS fix or no
  validated network, retried with exponential backoff (15 s → 4 min, 6 attempts) in the service loop and via
  WorkManager (network-constrained, survives the service dying), then marked FAILED. A newer update always
  supersedes an unsent older one so a stale position is never sent.

## Privacy & security

- No backend, no analytics, no accounts. Location only leaves the phone via WhatsApp, by your tap.
- Room database encrypted with SQLCipher; the 256-bit passphrase is wrapped by an Android Keystore AES-GCM key
  and stored in no-backup storage. `allowBackup=false` plus backup/transfer exclusion rules.
- Coordinates are held in memory (`LocationHub`) and only written to the encrypted DB when an update is prepared.
- Location is collected only while a session is active, under a visible foreground-service notification.
- Map tiles are fetched over HTTPS from CARTO/OSM (they see your IP and tiles viewed). Cleartext traffic disabled.

## Architecture

```
ui/            Compose screens + single MainViewModel (state from Flows)
service/       TrackingService (foreground, location type), SessionManager (start/stop/expire),
               SessionScheduler (weekly windows -> alarms), AppReceiver/BootReceiver, Notifier
delivery/      DeliveryProcessor (queue, retry, supersede), RetryWorker, HandoffActivity, WhatsAppIntents
location/      LocationHub (in-memory state), LocationTracker (fused provider wrapper)
domain/        Pure, unit-tested logic: Schedule, BatteryPolicy, GpsStatus, PhoneNumbers, Messages
data/          Room entities/DAOs, SQLCipher + Keystore key, Repository
```

Extension points: a new delivery channel (e.g. Business Cloud API, SMS) plugs in beside `DeliveryProcessor`;
`Repository` is the single storage seam (sync/export); recipients/sessions are keyed entities ready for groups
or shared tracking links.

## Build & run

Requires JDK 17+ and the Android SDK (platform 35). Put `sdk.dir=/path/to/sdk` in `local.properties`.

```
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # schedule, battery policy, GPS status, phone parsing, backoff
./gradlew :app:lintDebug
```

First run: allow location, then "All the time" (needed for scheduled starts and screen-off tracking),
notifications, and set battery to *Unrestricted* from the About tab. Exact alarms (Settings › Alarms & reminders)
make scheduled starts punctual; without it Android may delay them by minutes.

## Status / not yet verified

The code compiles, unit tests pass and the R8 release build succeeds. It has **not been run on a device or
emulator**, so on-device behaviour (SQLCipher init, background starts on specific OEM ROMs, WhatsApp hand-off)
still needs a manual test pass. Vendors such as Xiaomi/Huawei/Samsung add extra background limits; use
the battery-unrestricted setting.
