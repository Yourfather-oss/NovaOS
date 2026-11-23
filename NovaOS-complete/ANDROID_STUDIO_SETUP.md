# NOVA — Android Studio project setup

This archive reconstructs the missing Android Studio / Gradle project configuration around the supplied NOVA Kotlin source. It retains the launcher, settings, classic pages, GPT client, managed Node backend, WhatsApp notification listener, chat-export import, tasks, reminders, widgets, themes and wallpapers.

## Open the project

1. Extract the ZIP to a normal folder, for example `C:\\Projects\\NOVA`.
2. Open Android Studio and choose **Open**. Select the extracted `NOVA` folder (the folder containing `settings.gradle.kts`).
3. Allow Android Studio to sync Gradle. If prompted, install Android SDK Platform 35 and use JDK 17.
4. Connect the TECNO Camon 30S with USB debugging enabled, or start an emulator.
5. Select the `app` run configuration and press **Run**.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9-compatible configuration, Compose BOM 2024.12.01, compile/target SDK 35, and min SDK 26.

## Layout restoration included

- A versioned settings migration forces the requested 5-column × 6-row classic grid once. This specifically fixes devices where the earlier one-time migration had already been marked complete while the old 4-column preference remained saved.
- The recent-apps slot remains directly above search and separate from the dock. Before launch history exists, it displays a small placeholder instead of collapsing the row. As NOVA records app launches, recent app icons replace the placeholder.
- Existing dock and icon rendering are retained.

## GPT backend

The Android app expects an HTTPS endpoint ending in `/api/nova/chat`. The Node backend is in `backend/`. Deploy it separately and set `OPENAI_API_KEY` as a hosting-provider secret. `OPENAI_MODEL` defaults to `gpt-6.2-sol`. Never place the OpenAI key in Kotlin, Gradle, `local.properties`, or the APK. The previously pasted key must be revoked and replaced.

## WhatsApp Message Intelligence

- Notification capture is opt-in and requires the user to grant Android notification-listener access.
- Older messages are imported only when the user exports a chat and shares it to NOVA.
- GPT analysis is manually triggered; it does not silently upload messages.
- Stored messages are in app-private SharedPreferences and are not encrypted at rest in this prototype. Do not use this build for highly sensitive chat archives.

## Build verification status

The Gradle project files have been reconstructed and the source archive has been checked for structural consistency. This environment does not contain an Android SDK, Gradle installation, or network access to download them, so an APK build could not be run here. Android Studio will perform the first real Gradle sync/build on your Windows machine. Treat any sync/compiler errors it reports as the next items to fix; this archive is not represented as a tested APK.
