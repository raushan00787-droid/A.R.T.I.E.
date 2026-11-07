# A.R.T.I.E. · Chat-First Cognitive Core · v1.22

**A.R.T.I.E.** (Artificial Response Tech, Isn't Exactly-cool) is Raushan's female personal assistant, currently being rebuilt as a text-first conversational system.

## v1.22: Chat-first rebuild

The Android app now exposes only two workspaces:

- **Chat:** text messages, a locally stored conversation history, and explicit memory commands.
- **Settings:** pairing and testing the A.R.T.I.E. Cognitive Core.

The Home dashboard, Inbox, Tools, voice input/output controls, messaging actions, file workflows, device controls, notification listener, floating overlay and assistant/voice services are not exposed in this build.

## Cognitive Core is the default path

- Chat messages are sent only to the paired Neural Core bridge at `127.0.0.1:8767`.
- The bridge token is encrypted at rest with Android Keystore.
- The cleartext network policy allows only `127.0.0.1`; all other cleartext hosts remain blocked.
- **Cloud AI is disabled. There is no automatic Cloudflare Worker fallback.**
- If the bridge is not paired or is unavailable, A.R.T.I.E. reports that state instead of sending the message to a remote AI service.
- Approved memory commands remain available: `remember that ...`, `show memories`, `forget ...`, and `forget all memories`.

## Important capability boundary

The Python Neural Core currently provides context management, stored preferences and experiences, and a reasoning endpoint. It is **not yet a standalone language model** and cannot independently generate full natural-language chat replies. In this build, A.R.T.I.E. can pass a message to the paired core and report whether the local reasoning path completed, but it will not pretend that a complete conversational model already exists.

A separate local language model must be integrated and tested before full offline conversation is available. Cloud AI will remain disabled unless the owner explicitly changes that design in a later build.

## Privacy and permissions

The v1.22 manifest requests only Android's `INTERNET` permission, which is used for the loopback Cognitive Core connection. Microphone, camera, contacts, notification, overlay and foreground-service permissions have been removed from this build. Chat history and owner-approved notes remain in the app's private storage.

## Build

- Android Gradle project, compile SDK 35, minimum SDK 26, target SDK 35.
- Version name: `1.22`; version code: `23`.
- GitHub Actions publishes the debug APK as the `ARTIE-v1.22-apk` artifact.
- The hosted build must still be installed and tested on the target tablet before this rebuild should be treated as device-verified.
