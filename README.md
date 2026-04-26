# Nexis Worker

Client apps for [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) -- Android and Linux desktop. Streaming chat, voice I/O, file and image attachments, desktop automation, hypervisor node control, real-time cross-device sync.

Part of the Nexis ecosystem alongside [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) and [nexis-hypervisor](https://github.com/santiagotoro2023/nexis-hypervisor).

---

## Install

### Android

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `nexis-worker-X.X.X.apk`
3. Open it on your phone -- allow "install unknown apps" when prompted
4. On first launch complete the setup wizard

The app checks for updates on every launch and installs them silently.

### Linux Desktop (Debian/Ubuntu)

1. Go to [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest)
2. Download `nexis-worker-desktop_X.X.X_amd64.deb`
3. `sudo dpkg -i nexis-worker-desktop_X.X.X_amd64.deb`
4. Launch from the app menu or run `nexis-worker-desktop`

Bundles its own JVM -- no Java installation needed.

---

## First Launch

A setup wizard guides you through connecting to the Nexis Controller and optionally a Nexis Hypervisor node. You can also connect later from Settings.

**Controller URL** -- e.g. `https://nexis.yourdomain.com:8443`
**Password** -- your controller password

On connect the app exchanges the password for a persistent Bearer token and pins the server TLS certificate (TOFU). Self-signed certs are fully supported.

---

## Features

### Chat

Type or speak. Responses stream token-by-token. History stays in sync across CLI, web, Android, and desktop in real time.

Attach images and files via the paperclip. Images go to the vision model; documents are sent as text context.

### Voice

Tap the mic to dictate. Tap the speaker to enable TTS. Only the device that sent the message plays audio.

### Remote Control

Send commands to the controller PC:

| Action | Description |
|---|---|
| Screenshot | Capture and analyse the desktop |
| Open app / URL | Launch on the controller |
| Volume / Media | Playback control |
| Clipboard | Read or write |
| Notify | Desktop notification |
| Lock / Sleep | Lock screen or suspend |
| Wake-on-LAN | Wake a device by MAC address |

### Hypervisor Node

If a Nexis Hypervisor node is connected to the controller, the Hypervisor screen shows:

- Live CPU, RAM, disk, VM count, container count from the node
- VM list with start / stop / reboot controls
- Container list with start / stop / restart controls
- Command relay -- natural language commands sent directly to the hypervisor

### Device Inventory

Browse all devices registered to the controller (worker devices and hypervisor nodes) with online status and hardware info.

### Schedules, Memory, History

View and manage controller schedules, persistent memories, and past chat sessions from the app.

---

## Android-specific

**Background sync** -- a foreground service keeps the SSE connection alive. Notifications fire for new AI replies and system monitor alerts.

**Wake word** -- Settings -> toggle "hey nexis". On-device detection via Sherpa-ONNX (~15 MB model, downloads automatically).

**Share to Nexis** -- share any text or image from another app directly into the chat.

---

## Desktop-specific

**System tray** -- closing the window hides to tray. Right-click to reopen or quit.

**Auto-update** -- Settings -> App update. Downloads and installs new .deb releases via pkexec.

---

## Requirements

**Android:** 8.0+ (API 26), microphone permission, "install unknown apps" for auto-update
**Desktop:** Debian/Ubuntu x86-64, `xdotool` + `wmctrl` for full desktop automation

Both require a running [nexis-controller](https://github.com/santiagotoro2023/nexis-controller).

---

## Versioning

`NX-WRK · BUILD 1.0.0` (Android) / `NX-WRK-DT · BUILD 1.0.0` (Desktop) -- tags follow `vMAJOR.MINOR.PATCH`.

## Releases

Each tagged release produces:

- `nexis-worker-X.X.X.apk` -- Android APK
- `nexis-worker-desktop_X.X.X_amd64.deb` -- Linux desktop Debian package

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
  -dname "CN=Nexis, O=nexis, C=CH"
base64 nexis-release.jks   # paste into KEYSTORE_BASE64 secret
```
