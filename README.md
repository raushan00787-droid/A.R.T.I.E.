# J.A.R.V.I.S. Native Android · v1.11

A native Android assistant for Raushan. The interface is reactor-first; the Android app handles device actions locally and delegates conversational AI to the configured Cloudflare Worker.

## v1.11 integrated update

### Live voice mode
- A user-started microphone foreground service keeps JARVIS Live Mode available after the Activity is minimized.
- The service listens for the spoken wake phrase **“Hey JARVIS”**. After a wake phrase it accepts follow-up turns for a short conversation window, then returns to wake-phrase mode.
- The persistent notification includes **Pause**, **Resume**, and **Stop** actions. The microphone session is started from the visible app after microphone permission is granted.
- The service pauses recognition while JARVIS speaks so it does not mistake its own voice for a command.
- Android speech recognition is used with the offline-recognition preference enabled. This is a preference, not a guarantee: the recognizer selected by the device may still use network services or may not provide reliable continuous recognition on every device.
- This is a software wake-word loop, not a dedicated low-power hardware hotword/DSP. Screen-off behavior, battery use, vendor battery management and recognizer availability vary by device. It may need the user to reopen JARVIS after the OS or user force-stops the app.

### Android system voice
- JARVIS uses the voice and text-to-speech engine configured in Android's **Text-to-speech output** settings.
- The app no longer forces a particular gender, voice profile or TTS vendor. The system voice settings shortcut opens Android's speech settings where available.
- Spoken Hindi/English quality depends on the languages and voices installed in the active system TTS engine. The app does not silently switch to a different voice when one language is unavailable.

### Default assistant role
- JARVIS declares Android's VoiceInteractionService and session entry points and includes a control to request the Android **Default digital assistant app** role.
- Android owns the role-selection prompt. Once selected, the system assistant gesture/button can launch JARVIS.
- The Android assistant role and the custom “Hey JARVIS” Live Mode wake loop are separate features. Choosing JARVIS as the default assistant does not give third-party apps access to Google's hardware hotword system.

### Messaging and proactive alerts
- Voice/text commands such as `text +91XXXXXXXXXX: Hello` open the Android SMS composer with a prefilled draft. A contact-name target can be resolved after Contacts permission is granted.
- Commands such as `whatsapp: Hello` open WhatsApp's share flow so the user can choose a recipient and send. JARVIS does **not** silently send SMS or WhatsApp messages.
- Reminder syntax: `remind me in 10 minutes to take a break` or `remind me in 2 hours to check the download`. Reminders use Android's inexact while-idle alarm API and post a notification when due.
- A battery-low receiver can post a JARVIS notification. Notification delivery requires Android notification permission and notifications enabled for the app.

## Existing native features
- Large animated Arc Reactor landing screen; conversation and controls remain reachable by scrolling.
- Cloudflare Worker AI chat integration with locally persisted bounded conversation history.
- Explicit on-device memory: `remember that ...`, `show memories`, `forget ...`. The app blocks passwords, PINs, OTPs and access keys from saved notes.
- Local commands for flashlight, battery, Wi-Fi/Bluetooth/display settings, app launch, alarms and media controls.
- Call commands resolve a contact (with Contacts permission) and open the Android dialer. The user confirms the call.
- Android Storage Access Framework file and folder pickers, selected-document handoff to WPS Office or another compatible viewer, and user-selected folder browsing.
- Floating Arc Reactor overlay with its own foreground notification.
- Permission center for microphone, camera/flashlight, contacts and notifications.

## Privacy and platform boundaries
- JARVIS requests permissions when a feature needs them. File/folder access is granted through Android's system picker; it does not request unrestricted all-files access.
- It cannot read WPS Office's private app database or other apps' private internal files.
- Calls open the dialer for review. SMS opens a prefilled composer. WhatsApp opens a share/recipient selection flow. The user confirms sending.
- The custom wake-word service requires microphone permission and displays an ongoing foreground-service notification while running. Stop it from the notification or the app.
- The speech recognizer may use network resources depending on the Android recognition provider despite the offline preference. AI replies require internet access and the configured Worker to be available.
- Device/OEM battery restrictions may stop background work. Users may need to allow JARVIS to run in the background in Android battery settings.

## Build
- Android Gradle Plugin project, compile SDK 35, minimum SDK 26, target SDK 35.
- GitHub Actions builds a debug APK on pushes to `main` and on manual workflow dispatch. Download the `JARVIS-native-debug-apk` artifact from a successful run.
- A successful hosted build still needs on-device testing of the actual wake phrase, microphone behavior with the screen off, installed system TTS voices, notification permission, assistant-role eligibility, calls, messaging and document providers.
