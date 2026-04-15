# nexis-worker

Client apps for [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) — an Android app and a Debian desktop app. Streaming chat, voice I/O, file/image attachments, desktop automation, system monitoring, real-time cross-device sync.

---

## Install

### Android

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `app-release.apk`
3. Open it on your phone — allow "install unknown apps" when prompted
4. On first launch, grant the permission and connect to your controller

> The app checks for updates on every launch and installs them silently in the background.

### Linux desktop (Debian/Ubuntu)

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `nexis-worker-desktop_*.deb`
3. Install: `sudo dpkg -i nexis-worker-desktop_*.deb`
4. Launch from your app menu or run `nexis-worker-desktop`

> The desktop app bundles its own JVM — no Java installation needed. It checks for updates automatically from Settings → App update.

---

## First launch

**Server URL** — e.g. `https://nexis.yourdomain.com:8443`  
**Password** — your controller password (default `Asdf1234!`, change it)

On connect, the app:
- Exchanges your password for a persistent Bearer token
- Pins the server's TLS certificate (TOFU) — self-signed certs are fully supported
- Loads your conversation history

---

## Chat

Type or speak a message. Responses stream token-by-token. If another device is actively talking to NeXiS, a typing indicator appears — history stays in sync across CLI, web, Android, and desktop.

### Attachments

**Android:** tap the paperclip — choose Camera, Gallery, or File/Document.  
**Desktop:** drag any file onto the window, or use the paperclip menu (file picker or screenshot).

Images go directly to the vision model. Documents are sent as text context.

### Voice input (mic)

**Android:** tap the mic button — Android speech recognition transcribes locally and sends the text.  
**Desktop:** tap the mic button — records at 16kHz, sends WAV to the controller's Whisper endpoint for transcription, sends the result as a message.

### Voice output (TTS)

Tap the speaker icon. The controller synthesizes speech (GlaDOS-style) and streams audio chunks. Toggle per-device — only the device that sent the message plays audio.

### Model switching

Tap **Model** in the top bar. Only models installed on the controller are shown. Switching affects all connected devices.

---

## Desktop-specific features

### System tray

Closing the window hides the app to the system tray instead of quitting. The tray icon shows a balloon notification when:
- A new AI response arrives while the window is hidden
- A system monitor alert fires (CPU/memory/disk threshold exceeded)

Right-click the tray icon → **Open NeXiS** or **Quit**.

### Auto-update (desktop)

Settings → **App update** → Check for updates. If a newer `.deb` is available:
1. Progress bar shows download percentage
2. Tap **Download & install** — a system password dialog appears (`pkexec`)
3. Restart the app to apply

---

## Android-specific features

### Background sync

A foreground service keeps the SSE sync connection alive while the app is minimised. Notifications fire for:
- New AI replies (tap to open the app)
- System monitor alerts (CPU/memory/disk threshold exceeded on the controller)

The service reconnects automatically with exponential back-off — battery-friendly, no aggressive polling.

### Always-on wake word ("Hey Nexis")

Settings → toggle **hey nexis**. A background service listens for the wake word using [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — fully on-device, no audio leaves the phone. The detection model (~15 MB) downloads automatically on first use.

When triggered: the app opens and the mic activates.

### Share to NeXiS

Share any text or image from another app directly into the chat:
- **Text:** pre-fills the input field for editing before sending
- **Image:** sends immediately with "What is this?" as the prompt

---

## Remote control

The Remote screen sends commands to the controller PC via the daemon:

| Action | Description |
|---|---|
| Screenshot | Capture the desktop → vision analysis |
| Open app / URL | Launch something on the controller |
| Volume | Set output volume |
| Media | Play / pause / next / previous |
| Clipboard | Read or write the controller's clipboard |
| Notify | Send a desktop notification |
| Lock / Sleep | Lock screen or suspend the machine |
| Wake-on-LAN | Wake another device by MAC address |

---

## Settings

| Setting | Description |
|---|---|
| **Controller** | Connected server URL |
| **Controller health** | Live stats — model, voice, memory count, uptime |
| **Certificate** | Pinned TLS fingerprint. Use "forget certificate" if the server cert was regenerated |
| **Re-authenticate** | Enter a new password to refresh the token without disconnecting |
| **App update** | Check for and install a newer release (desktop) |
| **Disconnect** | Clear token and return to the connect screen |

---

## Requirements

**Android:** 8.0+ (API 26), microphone permission, "install unknown apps" permission (for auto-update)  
**Desktop:** Debian/Ubuntu x86-64, `xdotool` + `wmctrl` for full desktop automation support

Both require a running [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) reachable over HTTPS.

---

## CI / releasing

Every push to `main` triggers `.github/workflows/release.yml`:

1. Sets up JDK 21 (Temurin)
2. Builds signed Android APK — `:app:assembleRelease`
3. Builds Debian package — `:desktopApp:packageDeb`
4. Creates a GitHub Release tagged `v<unix_timestamp>` with both artifacts attached

Both apps compare `BuildConfig.VERSION_TIMESTAMP` against the latest release tag on startup to detect updates.

### Required GitHub Secrets

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `nexis`) |
| `KEY_PASSWORD` | Key password |

```bash
keytool -genkeypair -keystore nexis-release.jks -storetype JKS \
  -alias nexis -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=NeXiS, O=nexis, C=CH"
base64 nexis-release.jks   # paste into KEYSTORE_BASE64 secret
```
