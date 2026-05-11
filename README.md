# NeXiS Worker

Client applications for the NeXiS ecosystem. Available for Android (8.0+), Linux desktop, and Windows. Connects to a NeXiS Controller to provide a portable AI assistant interface, remote device control, and VM management from anywhere.

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
| **nexis-worker** | Android, Linux, and Windows client — you are here |

Workers connect directly to the Controller. All paired Hypervisor nodes are accessible through the Controller's proxy — no separate credentials per node.

---

## What's New in v1.0.17

- **VNC server command handler** — worker polls for `start_vnc` and `stop_vnc` commands from the Controller; `VncServerManager` starts x11vnc on Linux, TightVNC/RealVNC service on Windows, and ARDAgent on macOS
- **Open Screen button** — each device card in the Devices screen now has a **SCREEN** button; tapping it calls the Controller's VNC start API and opens the noVNC viewer URL in the device's default browser
- **Windows MSI build** — `targetFormats(Deb, Msi)` is configured in Gradle; a dedicated `windows-latest` CI job builds the MSI; every VERSION bump triggers both APK + `.deb` (Linux/Android) and `.msi` (Windows) builds automatically

---

## What It Is

NeXiS Worker is a Kotlin Multiplatform application with three targets:

- **Android app** (Jetpack Compose) — sideloadable APK for Android 8.0+
- **Linux desktop app** (Compose Multiplatform) — `.deb` package for Debian 12 / Ubuntu 22.04+
- **Windows desktop app** (Compose Multiplatform) — `.msi` installer for Windows

All targets share the same networking layer, and all register as managed devices with the Controller on first connection.

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
- **VNC remote screen** — Worker responds to `start_vnc` / `stop_vnc` commands from the Controller; `VncServerManager` starts the appropriate VNC server for the platform (x11vnc on Linux, TightVNC/RealVNC on Windows, ARDAgent on macOS); Controller proxies the VNC stream via websockify and serves an inline noVNC viewer
- **SCREEN button** — each device card shows a SCREEN button that calls the Controller VNC start API and opens the noVNC viewer in the default browser
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
| Windows desktop | `.msi` | Windows 10+ · x86_64 |

---

## Download

Pre-built APK, `.deb`, and `.msi` packages are available on the [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest) page.

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

### Windows Desktop

Double-click the `.msi` installer and follow the setup wizard.

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

**Windows `.msi`:**
```bash
./gradlew :desktopApp:packageMsi
# Output: desktopApp/build/compose/binaries/main/msi/nexis-worker-desktop_*.msi
```

> CI automatically builds both Linux/Android and Windows targets on every VERSION bump using `ubuntu-latest` and `windows-latest` runners respectively.

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
  "platform": "android" | "linux" | "windows"
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
| `start_vnc` | Start platform-appropriate VNC server (x11vnc / TightVNC / RealVNC / ARDAgent) |
| `stop_vnc` | Stop the VNC server |
| `lock_screen` | Lock the device screen |
| `wake_screen` | Wake the device screen |
| `get_status` | Report device status (battery, network, etc.) |

### VNC Server Behaviour by Platform

| Platform | VNC Implementation |
|----------|-------------------|
| Linux | x11vnc |
| Windows | TightVNC or RealVNC service |
| macOS | ARDAgent (Apple Remote Desktop) |

Once the VNC server is running, the Controller starts a `websockify` WebSocket→TCP proxy and serves an inline noVNC viewer. The **SCREEN** button in the Devices screen calls the Controller's VNC start API and opens the viewer URL in the default browser.

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
| Desktop app (Linux + Windows) | Kotlin · Compose Multiplatform · Material 3 |
| Build system | Gradle 8.9 · AGP 8.7 · `compileSdk 35` · `minSdk 26` · `targetFormats(Deb, Msi)` |
| Networking | OkHttp · custom TOFU TLS trust manager |
| Storage | Android DataStore (Android) / JVM preferences (desktop) |
| VNC | `VncServerManager` — platform-aware VNC server lifecycle |

---

## Navigation

| Section | Description |
|---------|-------------|
| Chat | AI conversation with streaming output |
| Hypervisor | VMs and containers across all paired nodes |
| Schedules | Automated tasks defined on the Controller |
| History | Conversation history |
| Devices | Other registered Worker devices — each card has a SCREEN button for noVNC remote access |
| Remote | Remote desktop control |
| Settings | Controller URL, credentials, TLS, preferences |
