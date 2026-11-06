# J.A.R.V.I.S. Native Android

Native Android companion app for Raushan's J.A.R.V.I.S. project.

## Native build 1.3

- Native Java interface with a cyan/gold HUD, animated reactor and Raushan's custom R signature emblem. The Iron Man mask illustration has been removed.
- Conversation panel, text composer, quick prompts, clear-chat control and Cloudflare Worker AI connection.
- EN-IN / HI-IN speech-recognition selector.
- Spoken-reply toggle and speech interruption control.
- Android TTS voice picker: lists voices exposed by the installed Android speech engine, remembers the selected voice, and keeps using it for replies instead of switching voice whenever the reply language changes. An installed voice named Heart is preferred on first launch when available.
- Speech cleanup before TTS: removes emoji, decorative symbols, Markdown formatting, code blocks, list markers and URLs so the assistant reads the sentence rather than the markup.
- Foreground clap-to-wake mode using the microphone. A clap starts speech recognition; the listener resumes after the response. Leaving the app pauses microphone capture.
- Local commands for battery, flashlight, time/date and Wi-Fi, Bluetooth, display and system settings.
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
