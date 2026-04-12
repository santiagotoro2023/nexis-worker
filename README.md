# nexis-worker

Android companion app for [nexis-controller](https://github.com/santiagotoro2023/nexis-controller). Connect to your home AI from anywhere — streaming chat, voice output, phone microphone input, model switching, and real-time sync across all your devices.

---

## Install

**No Android Studio or developer tools required.**

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `app-release.apk`
3. Open the APK on your phone — allow installation from this source when prompted
4. Done

> The app checks for updates on every launch and installs them automatically in the background.

---

## First launch

**One-time permission screen** — tap "Grant permission" and enable "Allow from this source" in the Android settings that open. Return to the app. This only happens once.

**Connect screen:**

- **Server URL** — your controller address, e.g. `https://nexis.yourdomain.com:8443`
- **Password** — your controller password (default `Asdf1234!`, change it)

Tap **Connect**. The app:
1. Exchanges your password for a persistent Bearer token — you won't need to re-enter the password unless you change it
2. Pins the server's TLS certificate (TOFU) — self-signed certs are fully supported; no CA needed
3. Loads your conversation history from the controller

---

## Chat

Type a message and tap send, or use the mic button for voice input. Responses stream token-by-token in real time.

**If another device is actively talking to NeXiS**, a typing indicator appears automatically — conversation history stays in sync across CLI, web, and app.

### Message features

- AI responses render markdown — headings, bullet lists, bold, inline code
- Code blocks are visually distinct with a language label and a **Copy** button
- Long-press any AI message to select and copy text

---

## Voice output

Tap the **speaker icon** in the top bar to toggle. When on, the controller synthesizes speech (GlaDOS-style voice) and streams audio chunks to your phone. Chunks play in arrival order with no gaps.

Voice output is session-scoped — audio only plays on the device that sent the message.

---

## Voice input

Tap the **mic button** next to the text field. Speak — your phone transcribes what you say using Android's built-in speech recognition and sends the text as a message. No audio is sent to the server.

---

## Model selection

Tap **Model** in the top bar. Only models installed on the controller are shown. Switching model affects all connected devices.

---

## Always-on wake word ("Hey Nexis")

When enabled in Settings, a background service listens for the wake word using [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — fully on-device, no audio ever leaves the phone.

**No setup required.** The detection model (~15 MB) downloads automatically on first use. No accounts, no API keys.

Open Settings → "hey nexis" → toggle on. A persistent notification appears while the service is running. When "Hey Nexis" is detected, the app opens and the mic activates automatically.

---

## Settings

Tap the gear icon. The settings screen scrolls if content exceeds the screen.

| Setting | Description |
|---|---|
| **controller** | Shows the connected server URL |
| **certificate** | Shows the pinned TLS fingerprint. Use "forget certificate" if the server cert was regenerated — the app will re-pin on the next connection |
| **re-authenticate** | Enter your new controller password to get a fresh token, without disconnecting |
| **hey nexis (wake word)** | Toggle always-on wake word detection (no setup required) |
| **disconnect** | Clears the saved token and returns to the Connect screen |

---

## Requirements

- Android 8.0 (API 26) or newer
- nexis-controller running and reachable over HTTPS
- "Install unknown apps" permission (for auto-update, granted once on first launch)
- Microphone permission (for voice input and wake word)

---

## Auto-update

On every launch the app checks GitHub for a newer release. If found:

1. A loading screen shows: **"Downloading update… 42%"**
2. The APK installs silently — no tap needed
3. The app restarts on the new version

If the check or download fails the app opens normally on the current version.

---

## CI / releasing (for developers)

Every push to `main` triggers `.github/workflows/release.yml`:

1. Builds a release APK signed with the keystore stored in GitHub Secrets
2. Creates a GitHub Release tagged `v<unix_timestamp>`
3. Attaches the APK as a release asset

The running app compares `BuildConfig.VERSION_TIMESTAMP` to the latest release tag. If the release is newer, it downloads and installs the APK silently via Android's PackageInstaller.

### Required GitHub Secrets

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `nexis`) |
| `KEY_PASSWORD` | Key password |

Generate a keystore once (use JKS format — PKCS12 has compatibility issues with the Android build tools):

```bash
keytool -genkeypair -keystore nexis-release.jks -storetype JKS \
  -alias nexis -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=NeXiS, O=nexis, C=CH"
base64 nexis-release.jks   # paste into KEYSTORE_BASE64 secret
```
