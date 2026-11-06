# J.A.R.V.I.S. Native Android

Native Android companion app for Raushan's J.A.R.V.I.S. project.

## Current native build

- Native Java Activity and custom Canvas-based animated reactor.
- Custom angular helmet/mask illustration and cyan/gold HUD styling.
- Conversation panel, text composer, quick prompts and clear-chat control.
- Cloudflare Worker chat integration at the same endpoint used by the browser prototype.
- Android speech recognition with an EN-IN / HI-IN selector.
- Android Text-to-Speech with spoken-replies toggle and stop control.
- Local commands for battery, flashlight, time/date and settings shortcuts.
- Runtime microphone and camera permission handling.
- Android 26+; compile/target SDK 35.

## Build

GitHub Actions builds a debug APK on pushes to main and manual dispatch.
Open the Actions tab and download the `JARVIS-native-debug-apk` artifact from a successful run.

## Known limits

- Speech recognition and Text-to-Speech depend on Android's installed speech services and available language data.
- The AI conversation requires internet access and the configured Cloudflare Worker to be online.
- Flashlight support depends on the device exposing a rear flash and granting camera permission.
- Screen-off/background clap wake is not implemented. The microphone is activated only after the user taps the voice control.
- Native device actions are limited to Android APIs and settings screens exposed by the OS; this app does not silently control protected system settings.
- A successful hosted build does not replace on-device testing of the microphone, TTS voice, flashlight and network connection.
