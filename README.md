# Phoebe

An Android companion for Pi coding agent. Get notified when an agent run finishes on your local network.

> Phoebe is in early development.

## Setup

Install Node.js and pnpm, then install the extension from this repository:

```sh
pnpm install
pi install ./packages/extension
```

Restart Pi or use `/reload` to load it.

Build and install the Android app with JDK 25, Android SDK platform 37, and adb:

```sh
cd packages/android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Phoebe on the same Wi-Fi network and connect when the host is found. The WebSocket port defaults to `48371`; override it with `PHOEBE_PORT` when starting Pi.

**Security:** Connections are unauthenticated and unencrypted. Use only on a trusted network.
