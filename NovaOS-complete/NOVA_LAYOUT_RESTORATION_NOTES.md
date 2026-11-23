# NOVA layout restoration + AI integration

This source update keeps the GPT and WhatsApp Message Intelligence additions while restoring the earlier launcher layout priorities.

## Home layout restoration
- A recently launched apps strip is displayed immediately above the search field. It is separate from the dock.
- The recent strip uses up to five recently launched apps and compact icons/labels.
- Extra bottom content space is reserved so the app grid does not collide with the recent strip, search field, or dock.
- The classic grid default is five columns by six rows. A one-time migration changes an existing 4x5 or 4x6 preset to 5x6, matching the previously requested five-column layout. Other grid choices are left unchanged.
- Existing classic app-page compact cell sizing and page behavior are retained.

## Widgets layout
- WhatsApp Message Intelligence actions are split into two compact utility buttons and a full-width Analyze button, avoiding three squeezed buttons in one row.
- GPT backend settings and the existing AI conversation remain in place.

## Important
- This is a source ZIP, not a compiled APK. The supplied source archive does not include the Gradle wrapper/build configuration, so an Android build could not be verified here.
- GPT still requires a deployed managed backend and a newly generated API key stored server-side. The key previously pasted in chat should be revoked.
