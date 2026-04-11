# nexis-worker

Android companion app for [nexis-controller](https://github.com/santiagotoro2023/nexis-controller). Connect to your home AI from anywhere — streaming chat, GlaDOS voice playback, phone microphone input, and model switching, all from your phone.

---

## How to install

**You do not need Android Studio or any developer tools.**

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `nexis-worker-vXXXX.apk`
3. Open the APK on your phone — Android will ask you to allow installation from this source. Allow it.
4. Done.

> The app auto-updates itself on every launch — you never need to manually download another APK.

---

## First launch

When you open the app for the first time it shows:

**One-time setup screen** — tap "Grant permission" and enable **"Allow from this source"** in the Android settings page that opens. Come back to the app. This is the only time you'll ever need to do this — after that, updates install completely automatically in the background.

Then you'll see the **Connect screen**:

- **Server URL** — your nexis-controller address, e.g. `nexis.toroag.ch` (no `https://` needed, the app adds it)
- **Password** — your nexis-controller password (default `Asdf1234!`, you should change it)

Tap **Connect**. The app exchanges your password for a persistent token — you won't need to enter the password again unless you change it.

---

## Using the app

### Chat
Type a message and tap send. Responses stream in token-by-token, just like on the web UI.

### Voice output (TTS)
Tap the **speaker icon** in the top bar to toggle. When enabled, the controller synthesizes speech (GlaDOS voice) and streams the audio to your phone automatically. Audio chunks play in order as they arrive.

### Voice input (STT)
Tap the **mic button** next to the text field. Speak — the phone's built-in speech recognition transcribes what you say and sends it as a message. No audio is sent to the server; only the transcribed text.

### Switch model
Tap **Model** in the top bar to pick between available LLMs (Fast / Deep / Code). Only models installed on the controller are selectable.

---

## Auto-update

Every time you open the app it checks GitHub for a new release. If one is found:

1. A loading screen appears: **"Downloading update… 42%"**
2. It installs automatically (no tap needed)
3. The app restarts on the new version

The whole process takes a few seconds on a good connection. If the check fails (no internet, GitHub down), the app opens normally on the current version.

---

## Settings

Tap the gear icon → **Settings**:

- **Re-authenticate** — if you changed your controller password, enter the new one here to get a fresh token. The old token stops working when a new one is issued.
- **Disconnect** — clears the saved token and returns to the Connect screen.

---

## Requirements

- Android 8.0 (API 26) or newer
- nexis-controller running and accessible over HTTPS
- "Install unknown apps" permission granted once (for auto-update)

---

## How the auto-update CI works (for developers)

Every push to `main` triggers `.github/workflows/release.yml`:

1. Builds a release APK signed with the keystore stored in GitHub Secrets
2. Creates a GitHub Release tagged `v<unix_timestamp>`
3. Attaches the APK

The version code is the Unix timestamp, so it's always increasing. The running app compares `BuildConfig.VERSION_TIMESTAMP` against the latest release tag — if the release is newer, it downloads and installs silently.

### Required GitHub Secrets

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 nexis-release.jks` output |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `nexis`) |
| `KEY_PASSWORD` | Key password |

Generate a keystore once:
```bash
keytool -genkey -v -keystore nexis-release.jks \
  -alias nexis -keyalg RSA -keysize 2048 -validity 36500
base64 nexis-release.jks   # paste output into KEYSTORE_BASE64 secret
```
