# NOVA Intelligence — Phase 1 integration

This update is based on the latest Phase 13 source archive (`NOVA-Phase13-Drawer-Build-Fix.zip`). It replaces the old direct Claude/Anthropic call with a managed GPT backend connection and adds voice input/output to the existing Ask NOVA widget.

## Included in this update

- `AiClient.kt`: replaces the direct Anthropic Messages API call with an HTTPS request to your own NOVA backend. The OpenAI API key is not stored in the APK.
- `Widgets.kt`: updates Ask NOVA to use the managed endpoint; adds Android speech-recognition input and Android text-to-speech output; retains the existing user-approved task proposal flow.
- `SettingsStore.kt` and `NovaApp.kt`: persist the backend URL and balanced model selection; clear the old on-device Claude key field.
- `AndroidManifest.xml`: retains internet access and the app's existing calendar/reminder permissions.
- `backend/`: a Node.js/Express starter service that calls the OpenAI Responses API on the server, with request-size limits, basic rate limiting, Helmet headers, and a health endpoint.

Default balanced model: `gpt-6.2-sol`. You can change `OPENAI_MODEL` in the backend environment if you choose another model available to your API account.

## Important: the backend must be deployed first

The Android app cannot connect until you deploy the backend and enter its public HTTPS endpoint in Ask NOVA.

1. Create a new Node.js service on a hosting provider that supports Node 20+ and HTTPS.
2. Upload the contents of the `backend/` folder.
3. Install dependencies with `npm install`.
4. Set these environment variables in the hosting dashboard:
   - `OPENAI_API_KEY` = your OpenAI API key (server secret only)
   - `OPENAI_MODEL` = `gpt-6.2-sol`
   - `PORT` = the port supplied by your host, if required
5. Start the service with `npm start`.
6. Confirm that `https://YOUR-HOST/health` returns `{ "ok": true, "service": "nova-intelligence" }`.
7. In NOVA, open Widgets → Ask NOVA and save `https://YOUR-HOST/api/nova/chat` as the backend URL.

Never put `OPENAI_API_KEY` in Kotlin, `local.properties`, the Android manifest, or the APK. Keep the backend private to your deployment, use HTTPS, monitor usage, and configure host-level access controls/quotas before exposing it publicly. The included per-IP rate limiter is a starter safeguard, not a complete account-authentication system.

## What works in this phase

- Managed GPT text conversation through the OpenAI Responses API.
- Existing local task/focus context is supplied to GPT.
- GPT can propose tasks in the existing structured reply format; NOVA still waits for the user to approve before saving them.
- Speech-to-text uses Android's installed speech-recognition activity.
- Read-aloud uses Android TextToSpeech.
- The old direct Claude API call and on-device API-key entry have been removed from the active assistant flow.

## What is not yet implemented

This is the first integration phase, not the complete autonomous assistant. Calendar event creation/editing, reminder tool calls, contacts, launching apps by natural-language command, persistent routine learning, and the Responses API function-calling execution/approval loop are still follow-on phases. The current calendar reader and reminder scheduler remain existing NOVA components; this phase does not falsely claim GPT can already control them.

Routine learning should be opt-in and transparent. This phase does not upload app-launch history or notification contents to GPT.

## Source/build note

The supplied Phase 13 archive is a source subset and does not include the complete Gradle wrapper/build files. This ZIP therefore cannot be verified as a standalone Android Studio build. Merge these files into the complete local NOVA project, then sync Gradle and build. No APK is included, and no successful compile is claimed.
