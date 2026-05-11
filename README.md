# NeXiS Worker

![Version](https://img.shields.io/badge/version-1.0.18-blue) ![Android](https://img.shields.io/badge/Android-8.0%2B-green) ![Linux](https://img.shields.io/badge/Linux-.deb-orange) ![Windows](https://img.shields.io/badge/Windows-.msi-blue)

Client applications for the NeXiS ecosystem. Available for Android (8.0+), Linux desktop, and Windows. Connects to a NeXiS Controller to provide a portable AI assistant interface, remote device control, and VM management from anywhere. All three targets share the same networking layer and register as managed devices with the Controller on first connection.

---

## Ecosystem Overview

```
NeXiS Controller  — central intelligence · SSO · management plane
        ↕ SSO / Bearer token
NeXiS Worker      — you are here · Android + Linux + Windows client
```

| Repo | Role |
|------|------|
| [nexis-controller](https://github.com/santiagotoro2023/nexis-controller) | Central AI assistant · SSO provider · management plane |
| [nexis-hypervisor](https://github.com/santiagotoro2023/nexis-hypervisor) | Per-node VM and container management |
| **nexis-worker** | Android, Linux, and Windows client — you are here |

Workers connect directly to the Controller. All paired Hypervisor nodes are accessible through the Controller's proxy — no separate credentials per node.

---

## Table of Contents

- [Supported Platforms](#supported-platforms)
- [Architecture & Stack](#architecture--stack)
- [Download & Install](#download--install)
- [Building from Source](#building-from-source)
- [Setup & First Connection](#setup--first-connection)
- [Role-Based UI](#role-based-ui)
- [Navigation & Screens](#navigation--screens)
- [AI Chat Interface](#ai-chat-interface)
- [Device Command System](#device-command-system)
- [noVNC Remote Screen](#novnc-remote-screen)
- [Hypervisor Management](#hypervisor-management)
- [Memories](#memories)
- [Scheduled Tasks](#scheduled-tasks)
- [Device Registration Payload](#device-registration-payload)
- [Connection Details](#connection-details)
- [CI / Release Pipeline](#ci--release-pipeline)

---

## Supported Platforms

| Platform | Format | Requirements |
|----------|--------|--------------|
| Android | APK (sideload) | Android 8.0+ (API 26+) |
| Linux desktop | `.deb` | Debian 12 / Ubuntu 22.04+ · x86_64 |
| Windows desktop | `.msi` | Windows 10+ · x86_64 |

---

## Architecture & Stack

| Component | Technology |
|-----------|------------|
| Android app | Kotlin · Jetpack Compose · Material 3 |
| Desktop app (Linux + Windows) | Kotlin · **Compose Multiplatform** · Material 3 |
| Build system | Gradle 8.9 · AGP 8.7 · `compileSdk 35` · `minSdk 26` |
| Desktop packaging | `targetFormats(Deb, Msi)` in Compose Multiplatform Gradle config |
| Networking | OkHttp · custom TOFU TLS trust manager (accepts self-signed certs) |
| Local storage | Android DataStore (Android) / JVM preferences (desktop) |
| Realtime | Server-Sent Events (SSE) for conversation sync and notifications |
| VNC server management | `VncServerManager` — platform-aware VNC server lifecycle |

---

## Download & Install

Pre-built APK, `.deb`, and `.msi` packages are on the [Releases](https://github.com/santiagotoro2023/nexis-worker/releases/latest) page.

### Android

```bash
# Via adb
adb install nexis-worker-<version>.apk
```

Or copy the APK to the device and sideload it via a file manager or package installer app.

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
./gradlew :desktopApp:packageMsi --no-daemon
# Output: desktopApp/build/compose/binaries/main/msi/nexis-worker-desktop-{version}.msi
```

---

## Setup & First Connection

1. Install or sideload the app on your device
2. Open the app — the setup screen appears on first launch
3. Enter your NeXiS Controller URL (e.g. `https://192.168.1.10:8443`)
4. Accept the self-signed TLS certificate prompt (**TOFU** — trust on first use)
5. Enter your Controller username and password
6. The app authenticates against the Controller and registers itself as a device
7. The device appears in the Controller's **Devices** panel

No per-Hypervisor setup is needed. All VMs from all paired Hypervisor nodes appear in the **Hypervisor** tab, proxied through the Controller.

---

## Role-Based UI

After login, the worker fetches the authenticated user's role from the Controller and adjusts the navigation accordingly:

| Role | Available Tabs |
|------|----------------|
| **admin** | Chat, Remote, Memories, History, Schedules, Devices, Hypervisor, **Commands**, Settings |
| **user** | Chat, Remote, Memories, History, Schedules, Devices, Hypervisor, Settings |

The **Commands** tab is admin-only. It lists all 13 built-in worker commands the device responds to, providing admins full visibility into the device's capabilities.

---

## Navigation & Screens

| Screen | Description |
|--------|-------------|
| **Chat** | Real-time AI conversation with streaming token output; model selector |
| **Remote** | Remote desktop control panel for devices controlled by the Controller |
| **Memories** | View and manage your persistent memory entries stored on the Controller |
| **History** | Full conversation history synced from the Controller |
| **Schedules** | View and trigger scheduled tasks defined on the Controller |
| **Devices** | All registered Worker devices; each card has a **SCREEN** button for noVNC remote access |
| **Hypervisor** | VMs and containers across all paired Hypervisor nodes; start/stop/reboot controls |
| **Commands** | *Admin only* — lists all 13 built-in commands this device responds to |
| **Settings** | Controller URL, credentials, TLS preferences, voice config |

---

## AI Chat Interface

- Real-time conversation with the Controller's local LLM (Ollama)
- Streaming token output — responses appear word-by-word as they generate
- Persistent conversation history synced across all Worker devices and the Controller web UI
- Model selector — switch between available models on the Controller
- Conversation sessions are stored on the Controller, not locally

---

## Device Command System

Registered Workers poll `GET /api/commands/pending?device_id=<id>` every 5 seconds for queued commands. After processing a command, the Worker ACKs it via `POST /api/commands/ack`.

### Supported Commands

| Command | Description |
|---------|-------------|
| `shell_exec` | Execute a shell command on the device; return stdout/stderr to the Controller |
| `screenshot` | Capture the screen and send the image to the Controller |
| `start_vnc` | Start the platform-appropriate VNC server (see table below) |
| `stop_vnc` | Stop the VNC server |
| `lock_screen` | Lock the desktop session |
| `notify` | Display a desktop notification with a configurable message |
| `open_url` | Open a URL in the system default browser |
| `set_volume` | Set the system audio volume to a value between 0 and 100 |
| `sleep` | Put the device into sleep / suspend mode |
| `wake_on_lan` | Send a Wake-on-LAN magic packet to another device |
| `probe` | Run a full diagnostics report and return it to the Controller |
| `file_read` | Read and return the contents of a file |
| `file_write` | Write content to a file at the specified path |

### VNC Server by Platform

| Platform | VNC Implementation |
|----------|-------------------|
| Linux | x11vnc |
| Windows | TightVNC or RealVNC service |
| macOS | ARDAgent (Apple Remote Desktop) |

---

## noVNC Remote Screen

Every device card in the **Devices** screen has a **SCREEN** button. The full flow when tapped:

1. Worker calls `GET /api/devices/{deviceId}/vnc/start` on the Controller
2. Controller queues a `start_vnc` command for the target Worker device
3. Target Worker polls `GET /api/commands/pending`, receives `start_vnc`, and starts the appropriate VNC server for its platform
4. Controller starts a `websockify` subprocess creating a WebSocket → VNC TCP proxy on a free port
5. Controller returns a `viewUrl` pointing to the inline noVNC HTML viewer
6. Worker opens the `viewUrl` in the system default browser — the noVNC session appears

The SCREEN button also works from the Controller web UI's `/devices` page with the same flow.

---

## Hypervisor Management

- View all VMs and containers across every paired Hypervisor node (proxied through the Controller)
- Per-VM status indicators: running, stopped, paused
- Per-VM resource chips showing vCPU count, memory allocation, and disk size
- Power controls: **Start**, **Stop**, **Reboot**, **Force-stop** — executed directly from the app
- No separate Hypervisor credentials required — all access is proxied through the Controller session

---

## Memories

- View your persistent memory entries stored on the Controller
- Memories are automatically extracted from your chat conversations on the Controller side (12 regex patterns detect personal facts: name, job, location, preferences, email, phone, etc.)
- Manual add and delete from the Memories screen
- Memories are per-user and scoped to your account on the Controller

---

## Scheduled Tasks

- View all scheduled AI prompts defined on the Controller for your account
- Trigger a schedule manually from the app
- Real-time notifications for alarms and scheduled events via SSE
- Schedule creation and deletion is managed from the Controller web UI or via AI tool tags

---

## Device Registration Payload

On first successful login, the Worker calls `POST /api/device/register` with:

```json
{
  "device_id": "<unique-uuid>",
  "hostname": "my-pc",
  "os": "windows",
  "arch": "amd64",
  "device_type": "desktop",
  "capabilities": ["screenshot", "shell", "vnc"],
  "ip": "192.168.1.10",
  "mac": "AA:BB:CC:DD:EE:FF"
}
```

The `device_id` is a stable UUID generated on first run and persisted locally. The device appears in the Controller's **Devices** page immediately after registration.

---

## Connection Details

| Property | Value |
|----------|-------|
| Protocol | HTTPS |
| TLS | TOFU — self-signed certificates accepted on first connection |
| Auth | Bearer token, 90-day TTL |
| Realtime | Server-Sent Events (SSE) for conversation sync and notifications |
| Command polling interval | Every 5 seconds |
| Streaming timeout | 300 seconds |
| Standard request timeout | 30 seconds |

---

## CI / Release Pipeline

Every version bump triggers two parallel GitHub Actions jobs:

**`build-linux-android`** (runs on `ubuntu-latest`):
```bash
./gradlew :app:assembleRelease          # Android APK
./gradlew :desktopApp:packageDeb        # Linux .deb
```

**`build-windows`** (runs on `windows-latest`):
```bash
gradle :desktopApp:packageMsi --no-daemon
# Output: desktopApp/build/compose/binaries/main/msi/nexis-worker-desktop-{version}.msi
```

All three artifacts (APK, `.deb`, `.msi`) are uploaded to the GitHub Release automatically.
