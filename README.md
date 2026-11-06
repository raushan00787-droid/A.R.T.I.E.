# J.A.R.V.I.S. Native Android

Native Android companion app for Raushan's J.A.R.V.I.S. project.

## Native build 1.7

- Reactor-first landing screen: a large animated arc reactor is the main focus when the app opens; conversation and device/document features are reached by scrolling.
- Scroll-revealed local control panel with call/dial, file picker, folder picker, folder browser, WPS Office launcher, permission center, flashlight, battery, Wi-Fi, Bluetooth, display, speech, clap wake, and volume controls.
- Cloudflare Worker AI chat integration with bounded conversation history restored from local app storage after restart.
- JARVIS persona: a female executive aide who addresses Raushan respectfully as "Sir" when natural, uses feminine Hindi/Hinglish phrasing, stays composed and observant, offers restrained dry humour, anticipates relevant next steps, and candidly flags risks without pretending to have unsupported device access.
- Female-only speech output: JARVIS selects only recognized female voice profiles. If the installed Android TTS engine does not expose a recognized female voice for the language being spoken, JARVIS keeps that reply on screen and does not fall back to a male or unidentified system voice.
- TTS voice picker shows only recognized female profiles; selected voice is saved. Android voice naming is inconsistent, so available profiles depend on the installed TTS engine and downloaded language data.
- Speech recognition modes: AUTO, EN-IN and HI-IN. AUTO lets the installed recognition service choose its default language; reliable code-mixed Hinglish support is not guaranteed by Android's standard recognizer.
- Media-volume up/down controls using Android's standard volume UI.
- Voice/text app-launch commands for WhatsApp, YouTube, Chrome, Maps, Gmail, Spotify, Instagram and WPS Office when installed.
- Contact command: say `call <contact>` or `call <number>`. Contact lookup requests Contacts permission only when needed and opens the Android dialer with the number prefilled. The user confirms the call in the dialer. The Call / Dial control opens the dial pad.
- Document access: Open Files uses Android's system document picker and supports selecting one or multiple documents. Choose Folder asks Android for access to a user-selected folder; Browse Folder lists the files/subfolders that the selected document provider exposes. Selected documents can be handed to WPS Office or another compatible app using Android's read-only URI grant.
- WPS Office can be launched from the local control panel or by voice/text command. WPS files are accessible only when they are exposed through Android's document picker/provider or a user-selected shared folder.
- Permissions: the app can request microphone, camera (flashlight), and contacts permissions when relevant. Android file/folder access is granted separately by the user through the system picker. The permission center also opens Android's app settings page.
- Privacy and platform limits: JARVIS does not request broad all-files access, read another app's private WPS data, bypass Android permission prompts, or place a call silently. Android presents the dialer so the user can review and confirm the call.
- Alarm requests open the Android Clock alarm screen with a parsed time selected. The user must confirm and save the alarm in Clock. The standard alarm intent specifies a time, not a calendar date.
- Foreground clap-to-wake mode using the microphone. Leaving the app pauses microphone capture.
- Explicit on-device memory: save notes with "remember that ...", view them with "show memories", and remove them with "forget ...". JARVIS does not silently extract personal facts from chat. Passwords, PINs, OTPs and access keys are blocked from memory.
- Android 8.0+ (min SDK 26); compile/target SDK 35.

## Build

GitHub Actions builds a debug APK on pushes to main and manual dispatch. Open the Actions tab and download the `JARVIS-native-debug-apk` artifact from a successful run.

## Known limits

- Female Hindi and English playback requires matching female profiles in the active Android text-to-speech engine. JARVIS will remain silent for a language without a recognized female profile rather than switch to a male voice. Android TTS voice names do not expose reliable gender metadata, so some female profiles may not be recognized until their engine/name is known.
- The AI conversation requires internet access and the configured Cloudflare Worker to be online.
- Clap wake works while the app is active in the foreground. It is not a screen-off or background wake-word service.
- Flashlight support depends on the device exposing a rear flash and granting camera permission.
- The document picker can access files shared by Android document providers, including compatible WPS/shared-storage sources. It cannot read WPS Office's private app database or private internal files.
- Contact calls open the Android dialer for user confirmation. Music search opens compatible streaming-app results; a tap may still be needed to start a track.
- Native device actions are limited to Android APIs and settings screens exposed by the OS; the app does not silently control protected system settings.
- A successful hosted build does not replace on-device testing of the microphone, TTS voice, flashlight, document providers and network connection.
