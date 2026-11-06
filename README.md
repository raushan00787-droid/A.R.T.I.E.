# J.A.R.V.I.S. Native Android

Native Android companion app for Raushan's J.A.R.V.I.S. project.

## Native build 1.4

- Native Java interface with the cyan/gold HUD, animated reactor and Raushan's R signature emblem.
- Cloudflare Worker AI chat integration with bounded conversation history restored from local app storage after restart.
- Explicit on-device memory: save notes with "remember that ...", view them with "show memories", and remove them with "forget ...". JARVIS does not silently extract personal facts from chat. Relevant saved notes are added to future AI requests so the connected assistant can use them.
- Speech recognition modes: AUTO, EN-IN and HI-IN. AUTO lets the installed recognition service choose its default language; reliable code-mixed Hinglish support is not guaranteed by Android's standard recognizer.
- Android TTS voice picker, saved voice selection, speech cleanup and adjustable speech speed.
- Media-volume up/down controls using Android's standard volume UI.
- Voice/text app-launch commands for WhatsApp, YouTube, Chrome, Maps, Gmail, Spotify and Instagram when installed.
- Alarm requests open the Android Clock alarm screen with a parsed time selected. The user must confirm and save the alarm in Clock. The standard alarm intent specifies a time, not a calendar date, so it cannot guarantee a date-specific alarm for tomorrow.
- Foreground clap-to-wake mode using the microphone. Leaving the app pauses microphone capture.
- Local commands for battery, flashlight, time/date and Wi-Fi, Bluetooth, display and system settings.
- Improved AI request failure handling and clearer fallback messages.
- Runtime microphone and camera permission handling.
- Android 8.0+ (min SDK 26); compile/target SDK 35.

## Build

GitHub Actions builds a debug APK on pushes to main and manual dispatch. Open the Actions tab and download the `JARVIS-native-debug-apk` artifact from a successful run.

## Known limits

- Speech recognition and Text-to-Speech depend on Android's installed speech services and available language/voice data. A voice named Heart appears in the picker only if the active Android TTS engine exposes it.
- The AI conversation requires internet access and the configured Cloudflare Worker to be online.
- Clap wake works while the app is active in the foreground. It is not a screen-off or background wake-word service.
- Flashlight support depends on the device exposing a rear flash and granting camera permission.
- Native device actions are limited to Android APIs and settings screens exposed by the OS; the app does not silently control protected system settings.
- A successful hosted build does not replace on-device testing of the microphone, TTS voice, flashlight and network connection.
