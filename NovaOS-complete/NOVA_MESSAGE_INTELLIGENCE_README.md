# NOVA Message Intelligence — WhatsApp integration

## Implemented in this source update

- WhatsApp and WhatsApp Business notification capture through the existing `NotificationListenerService`.
- Capture is OFF by default and is only enabled after the user taps Enable in NOVA Widgets > Message Intelligence.
- Enabling capture opens Android Notification Access settings; the user must explicitly grant NOVA access there.
- All WhatsApp notifications are eligible when enabled (all-chat scope). Android only provides the notification content/preview that WhatsApp chooses to publish; this is not a hidden full-chat database reader.
- Local private-app storage, deduplication, 300-item cap, pause and clear controls.
- Share target for `text/plain`: from WhatsApp use Export chat > Without media > Share > NOVA. NOVA stores the shared export locally.
- A manual Analyze with GPT action sends up to the latest 40 local items to the configured managed backend. Nothing is uploaded merely by receiving a notification or importing a chat.
- Analysis prompt treats message text as untrusted and asks GPT to extract class/assignment/CAT notices, course codes, dates, timetable changes and urgent items. It does not create calendar events or tasks automatically.

## User setup

1. Install/build NOVA.
2. Open NOVA Widgets and scroll to **MESSAGE INTELLIGENCE · WHATSAPP**.
3. Tap Enable and grant NOVA notification access in Android Settings.
4. For older messages, export a WhatsApp chat without media and share the `.txt` content to NOVA. (WhatsApp share targets may vary by Android/WhatsApp version.)
5. Configure the managed NOVA GPT backend in AI settings. Tap **Analyze with GPT** only when you want the selected content sent for analysis.
6. Use Pause to stop future notification capture, or Clear local data to erase stored message records.

## Privacy and platform limits

- Capture is opt-in and defaults to OFF.
- Only WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`) notification packages are captured.
- Android notification access is broad system permission. NOVA's listener filters to WhatsApp packages before saving message content.
- Notification previews can be incomplete, hidden, grouped, or absent. Notification access cannot guarantee every message or full conversation history.
- Exported chats are selected by the user and imported as text. Media attachments are not imported by this text share receiver.
- Message content is stored in NOVA's app-private SharedPreferences, not encrypted at rest by this feature. Device lock and Android app sandbox still apply. Do not enable backup for this data on shared devices; production hardening should migrate to encrypted local storage and exclude this store from backup.
- GPT analysis is manual. The app does not auto-send all captured messages to the backend.
- This update does not implement automatic WhatsApp task/calendar creation, message sending, or background AI monitoring.

## Build note

The uploaded Phase 13 source archive does not include the complete Gradle wrapper/build configuration. The archive is source-only; no successful APK compilation is claimed. Integrate these files into the complete Android Studio project and compile/test there.
