# NeXiS Worker

Client applications for the NeXiS ecosystem. Available for Android (8.0+) and Linux desktop. Connects to a NeXiS Controller to provide a portable AI assistant interface, remote device control, and VM management from anywhere.

---

## Ecosystem

```
┌──────────────────────────────────────────────────────────────┐
│  NeXiS Controller   — authentication · AI · management plane │
│    ↕ SSO / Bearer token                                       │
│  NeXiS Worker       — you are here · mobile + desktop client │
└──────────────────────────────────────────────────────────────┘
```

| Repo | Role |
|------|------|
| [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) | Central intelligence layer · SSO provider |
| [nexis-hypervisor](https://github.com/santiagotoro2023/nexis-hypervisor) | Per-node VM and container management |
| **nexis-worker** | Android and desktop client — you are here |

Workers connect directly to the Controller. All paired Hypervisor nodes are accessible through the Controller's proxy — no separate credentials per node.

---

## What It Is

NeXiS Worker is a Kotlin Multiplatform application with two targets:

- **Android app** (Jetpack Compose) — sideloadable APK for Android 8.0+
- **Linux desktop app** (Compose Multiplatform) — `.deb` package for Debian 12 / Ubuntu 22.04+

Both targets share the same networking layer, and both register as managed devices with the Controller on first connection.

---

## Features

### AI Interface
- Real-time conversation with the Controller's local LLM (Ollama)
- Streaming token output
- Persistent conversation history synced across all Worker devices and the Controller web UI
- Model selector

### Remote Device Control
- The Controller can issue commands to connected Worker devices
- Workers register with the Controller, then poll `GET /api/devices/commands` for queued actions
- Remote desktop access via VNC (Worker can start a VNC server; Controller connects via the Remote page)
- Device status reporting back to the Controller

### Hypervisor Management
- View all VMs and containers across every paired Hypervisor node (via the Controller proxy)
- Start, stop, reboot, and force-stop VMs directly from the app
- Status indicators (running / stopped / paused) with per-VM resource chips

### Automation
- View and trigger scheduled tasks defined on the Controller
- Real-time notifications for alarms and scheduled events via SSE

### Settings
- Controller URL and credentials stored locally
- Trust-on-first-use (TOFU) TLS — accepts self-signed certificates
- Connection status indicator always visible

---

## Supported Platforms

| Platform | Format | Requirements |
|----------|--------|--------------|
| Android | APK (sideload) | Android 8.0+ (API 26+) |
| Linux desktop | `.deb` | Debian 12 / Ubuntu 22.04+ · x86_64 |

---

## Download

Pre-built APK and `.deb` packages are available on the [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest) page.

### Android

```bash
# via adb
adb install nexis-worker-<version>.apk
```

Or sideload the APK directly on the device via a file manager / package installer.

### Linux Desktop

```bash
sudo dpkg -i nexis-worker-desktop_<version>_amd64.deb
```

---

## Building from Source

```bash
git clone https://github.com/santiagotoro2023/nexis-worker
cd nexis-worker
```

**Android APK:**
```bash
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

**Linux desktop `.deb`:**
```bash
./gradlew :desktopApp:packageDeb
# Output: desktopApp/build/compose/binaries/main/deb/nexis-worker-desktop_*.deb
```

---

## Setup

1. Install or sideload the app
2. Open the app — enter your NeXiS Controller URL (e.g. `https://192.168.1.10:8443`)
3. Accept the self-signed TLS certificate prompt (TOFU — trust on first use)
4. Enter your Controller username and password
5. The app registers itself as a device — it appears in the Controller's **Devices** panel

No per-Hypervisor setup is needed. All VMs from all paired nodes appear in the **Hypervisors** tab, proxied through the Controller.

---

## Device Registration

On first successful login, the Worker calls:

```http
POST /api/devices/register
Authorization: Bearer <token>

{
  "device_id": "<unique-device-id>",
  "name": "<device-name>",
  "type": "worker",
  "platform": "android" | "linux"
}
```

The Controller stores the registration and the device appears in the **Devices** page.

---

## Command Polling

Registered Workers poll the Controller periodically for queued commands:

```http
GET /api/devices/commands
Authorization: Bearer <token>
```

The Controller can queue the following command types:

| Command | Description |
|---------|-------------|
| `screenshot` | Capture and return a screenshot |
| `launch` | Launch an application by name |
| `open_url` | Open a URL in the default browser |
| `notify` | Send a desktop / system notification |
| `volume_set` | Set system volume to a given level |
| `volume_up` / `volume_down` | Adjust volume by step |
| `vnc_start` | Start VNC server for remote desktop access |
| `vnc_stop` | Stop VNC server |
| `lock_screen` | Lock the device screen |
| `wake_screen` | Wake the device screen |
| `get_status` | Report device status (battery, network, etc.) |

---

## Connection Details

| Property | Value |
|----------|-------|
| Protocol | HTTPS |
| TLS | TOFU — self-signed certificates accepted on first connection |
| Auth | Bearer token, 90-day TTL |
| Realtime | Server-Sent Events (SSE) for conversation sync and notifications |
| Streaming timeout | 300 seconds |
| Standard timeout | 30 seconds |

---

## Stack

| Component | Technology |
|-----------|------------|
| Android app | Kotlin · Jetpack Compose · Material 3 |
| Desktop app | Kotlin · Compose Multiplatform · Material 3 |
| Build system | Gradle 8.9 · AGP 8.7 · `compileSdk 35` · `minSdk 26` |
| Networking | OkHttp · custom TOFU TLS trust manager |
| Storage | Android DataStore (Android) / JVM preferences (desktop) |

---

## Navigation

| Section | Description |
|---------|-------------|
| Chat | AI conversation with streaming output |
| Hypervisor | VMs and containers across all paired nodes |
| Schedules | Automated tasks defined on the Controller |
| History | Conversation history |
| Devices | Other registered Worker devices |
| Remote | Remote desktop control |
| Settings | Controller URL, credentials, TLS, preferences |
