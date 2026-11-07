# A.R.T.I.E. · Native Android Assistant · v1.20

**A.R.T.I.E.** (Artificial Response Tech, Isn't Exactly-cool.) is a personal Android AI assistant inspired by Peter Parker's inventive, practical approach to technology. It brings conversational AI, on-device memory, voice interaction, reminders, file workflows and permission-based device controls into one native app. Conversational AI uses the configured Cloudflare Worker, while supported device actions and Inbox storage are handled locally.

This is the native Android application for A.R.T.I.E. The voice wake phrase is “Hey Artie”, and GitHub Actions publishes the debug APK as `ARTIE-native-debug-apk`.

## v1.13 integrated update

### Default assistant launch
- The Android VoiceInteractionSession now opens A.R.T.I.E. with an explicit assistant-launch handoff.
- After owner authentication, A.R.T.I.E. opens Chat and starts listening for a direct spoken command when microphone permission is granted.
- The first launch may require owner authentication and Android's microphone permission prompt.
- The assistant gesture and the user-started background "Hey Artie" Live Mode remain separate features.

### A.R.T.I.E. Inbox and reminders
- A dedicated Inbox stores reminders and important system alerts locally on the device.
- Reminders are scheduled through Android's inexact while-idle alarm API and restored after reboot.
- When a reminder becomes due, its Inbox entry changes from SCHEDULED to DELIVERED and Android posts a notification.
- Tapping a A.R.T.I.E. notification opens the Inbox. Battery-low alerts are also recorded.
- The Inbox keeps up to 100 recent entries. Entries can be marked read or dismissed.
- Android notification permission is required to display system notifications. Inbox entries are still stored locally when notifications are disabled.

### Hindi voice
- A.R.T.I.E. prefers an installed Hindi voice explicitly marked as female by the active TTS engine.
- If the engine does not expose a female marker, it uses the best available installed Hindi voice. The actual voice profile depends on the Android TTS engine and installed voice data.
- English speech returns to the Android system default voice. System TTS settings remain available from the Settings workspace.

### Workspace UI
- Dedicated Home, Chat, Inbox, Tools and Settings tabs.
- Chat has a taller conversation area and more readable message bubbles.
- Device controls and permission settings are separated from the conversation.
- Home has quick navigation to the main workspaces.

## Data and storage core · v1.14
- Voice commands search filenames within a folder explicitly selected by the owner.
- A.R.T.I.E. can preview UTF-8 text, Markdown, CSV, JSON, XML, HTML, logs and YAML files, and extract selectable text from PDFs (up to 25 pages / 160 KB per preview).
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
- User-started foreground Live Mode with "Hey Artie" wake phrase and persistent Pause/Resume/Stop controls.
- Floating Arc Reactor overlay and permission centre.

## Privacy and platform boundaries
- A.R.T.I.E. does not silently send SMS or WhatsApp messages. It opens a draft/share flow for review and user-controlled sending.
- Inbox records are stored in the app's private preferences on this device.
- File access is granted through Android's system picker. It does not request unrestricted all-files access.
- A.R.T.I.E. cannot read another app's private internal files.
- The custom wake-word service requires microphone permission and displays an ongoing foreground-service notification.
- Speech recognition, TTS voice inventory, default-assistant eligibility and OEM background restrictions vary by device.

## Build
- Android Gradle Plugin project, compile SDK 35, minimum SDK 26, target SDK 35.
- GitHub Actions builds a debug APK on pushes to `main`. Download the `A.R.T.I.E.-native-debug-apk` artifact from a successful run.
- A successful hosted build still needs on-device tests for assistant launch, microphone handoff, Hindi voice, reminders after reboot, notifications, messaging and document providers.


## v1.15 integrated stability update
- Assistant microphone state survives Activity pause/resume. Recognition is stopped while A.R.T.I.E. is not visible and resumed in the foreground; the assistant mic control can end the active assistant listening session.
- Typed links and links shared from a browser are read before AI analysis. Supported sources include public HTML, XHTML, plain text and selectable-text PDFs.
- Fetching is bounded by redirect, download, page and text limits. Private/local destinations and unsafe redirects are blocked. Login-only, script-rendered pages and scanned PDFs may not expose readable text.
- GitHub Actions now builds the existing integration branch as well as main, so the candidate APK is checked before merge.

 
## v1.17 autonomy and visual cockpit
- The Home workspace now includes an Autonomy Engine status rail showing the goal-to-plan-to-execute-to-verify workflow. The AI instruction set is explicit about using only app-supported capabilities and not claiming actions that were not verified.
- Natural contact-based WhatsApp requests are supported, including Hinglish phrasing such as “WhatsApp pe mummy ko hi bhej do”. A.R.T.I.E. searches permitted contacts, asks the owner to choose when names are ambiguous, and opens the matching WhatsApp chat with the message prefilled.
- WhatsApp sending remains owner-controlled: A.R.T.I.E. does not silently press Send. Review the recipient and message in WhatsApp, then send from that app.
- The cockpit has a subtle animated nebula/grid background, a more detailed rotating R reactor with listening/thinking/speaking states, animated workspace transitions, and message/card entrance motion.
- Version 1.17 is a visual and workflow update; Android permissions and OEM background limits still apply.


## v1.16 stability fixes
- Typed messages now receive text-only replies; speech-originated commands can receive spoken replies.
- Live speech recognition no longer forces offline-only recognition and accepts common A.R.T.I.E. wake-phrase variants.
- Reminder parsing accepts both task-first and time-first phrasing, including “remind me to study after 2 minutes”.
- Device queries include Android release/API level, Wi-Fi enabled state, and flexible shared-storage questions.
- Webpage analysis truncates extracted text to the chat request limit and accepts bare public domain links by adding HTTPS.
- Android Notification Access can mirror non-ongoing external app notifications into the local A.R.T.I.E. Inbox.


## v1.18 · Local Neural Core connection

- A.R.T.I.E. can pair with the Python Neural Core running in Termux on the same tablet.
- In Settings, use **LOCAL NEURAL CORE → PAIR / TEST**. Start `python run_bridge.py` from the Neural Core folder in Termux and paste the token printed by the bridge.
- The token is encrypted at rest with Android Keystore. The app connects only to `127.0.0.1:8767`; the network security configuration keeps cleartext traffic disabled for every other host.
- For each chat turn, the app submits the latest user message to `/v1/context` and adds the returned local context snapshot to the Cloud AI request. The Cloud AI remains the language-generation layer.
- If Termux is stopped, the bridge is unavailable, or pairing has not been completed, A.R.T.I.E. falls back to the existing Cloud AI chat path. It does not claim that the local Neural Core completed device actions.
