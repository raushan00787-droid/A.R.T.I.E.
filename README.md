# J.A.R.V.I.S. Native Android

Native Android starter app for `com.raushan.jarvis`.

GitHub Actions builds a debug APK on pushes to `main` and manual dispatch. Download the `JARVIS-native-debug-apk` artifact from the Actions run.

Included: native UI, Android speech recognition, Text-to-Speech, battery status, flashlight toggle, and shortcuts to Wi-Fi/Bluetooth/display settings.

Limitations: no Quiet Scribe AI backend integration or screen-off/background clap wake service yet. Speech and TTS depend on device services. Flashlight may require runtime camera permission.
