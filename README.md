# J.A.R.V.I.S. Native Android · v1.15

A native Android assistant for Raushan. The app is organised into separate Home, Chat, Inbox, Tools and Settings workspaces. Conversational AI uses the configured Cloudflare Worker; device actions and Inbox storage are handled locally.

## v1.13 integrated update

### Default assistant launch
- The Android VoiceInteractionSession now opens JARVIS with an explicit assistant-launch handoff.
- After owner authentication, JARVIS opens Chat and starts listening for a direct spoken command when microphone permission is granted.
- The first launch may require owner authentication and Android's microphone permission prompt.
- The assistant gesture and the user-started background "Hey JARVIS" Live Mode remain separate features.

### JARVIS Inbox and reminders
- A dedicated Inbox stores reminders and important system alerts locally on the device.
- Reminders are scheduled through Android's inexact while-idle alarm API and restored after reboot.
- When a reminder becomes due, its Inbox entry changes from SCHEDULED to DELIVERED and Android posts a notification.
- Tapping a JARVIS notification opens the Inbox. Battery-low alerts are also recorded.
- The Inbox keeps up to 100 recent entries. Entries can be marked read or dismissed.
- Android notification permission is required to display system notifications. Inbox entries are still stored locally when notifications are disabled.

### Hindi voice
- JARVIS prefers an installed Hindi voice explicitly marked as female by the active TTS engine.
- If the engine does not expose a female marker, it uses the best available installed Hindi voice. The actual voice profile depends on the Android TTS engine and installed voice data.
- English speech returns to the Android system default voice. System TTS settings remain available from the Settings workspace.

### Workspace UI
- Dedicated Home, Chat, Inbox, Tools and Settings tabs.
- Chat has a taller conversation area and more readable message bubbles.
- Device controls and permission settings are separated from the conversation.
- Home has quick navigation to the main workspaces.

## Data and storage core · v1.14
- Voice commands search filenames within a folder explicitly selected by the owner.
- JARVIS can preview UTF-8 text, Markdown, CSV, JSON, XML, HTML, logs and YAML files, and extract selectable text from PDFs (up to 25 pages / 160 KB per preview).
- It can create text files and, where the selected provider grants write access, rename, copy and move files. Every modification requires an on-screen confirmation.
- Shared storage capacity can be queried without granting broad file access.
- Scans are bounded to 1,800 folders and 10 levels of depth. Image OCR and unrestricted device-wide file search are not included.

## Existing native features
- Animated Arc Reactor home screen and dedicated voice orb.
- Cloudflare Worker AI chat integration with locally persisted bounded conversation history.
- Explicit on-device memory: `remember that ...`, `show memories`, `forget ...`.
- Local commands for flashlight, battery, Wi-Fi/Bluetooth/display settings, app launch, alarms and media controls.
- Calls open the Android dialer for review. SMS opens a prefilled composer. WhatsApp opens a share/recipient selection flow; the user confirms sending.
- Android Storage Access Framework file and folder pickers, voice-driven search and text/PDF reading, file creation, rename/copy/move (provider and write-grant permitting), storage usage reporting, and handoff to compatible viewers.
- User-started foreground Live Mode with "Hey JARVIS" wake phrase and persistent Pause/Resume/Stop controls.
- Floating Arc Reactor overlay and permission centre.

## Privacy and platform boundaries
- JARVIS does not silently send SMS or WhatsApp messages. It opens a draft/share flow for review and user-controlled sending.
- Inbox records are stored in the app's private preferences on this device.
- File access is granted through Android's system picker. It does not request unrestricted all-files access.
- JARVIS cannot read another app's private internal files.
- The custom wake-word service requires microphone permission and displays an ongoing foreground-service notification.
- Speech recognition, TTS voice inventory, default-assistant eligibility and OEM background restrictions vary by device.

## Build
- Android Gradle Plugin project, compile SDK 35, minimum SDK 26, target SDK 35.
- GitHub Actions builds a debug APK on pushes to `main`. Download the `JARVIS-native-debug-apk` artifact from a successful run.
- A successful hosted build still needs on-device tests for assistant launch, microphone handoff, Hindi voice, reminders after reboot, notifications, messaging and document providers.


## v1.15 integrated stability update
- Assistant microphone state survives Activity pause/resume. Recognition is stopped while JARVIS is not visible and resumed in the foreground; the assistant mic control can end the active assistant listening session.
- Typed links and links shared from a browser are read before AI analysis. Supported sources include public HTML, XHTML, plain text and selectable-text PDFs.
- Fetching is bounded by redirect, download, page and text limits. Private/local destinations and unsafe redirects are blocked. Login-only, script-rendered pages and scanned PDFs may not expose readable text.
- GitHub Actions now builds the JARVIS integration branch as well as main, so the candidate APK is checked before merge.
