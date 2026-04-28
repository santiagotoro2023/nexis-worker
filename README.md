# NeXiS Worker

Client applications for the NeXiS ecosystem. Available for Android (8.0+) and Linux desktop. Connects to a NeXiS Controller to provide a portable AI assistant interface, device remote control, and VM management from anywhere.

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

Workers connect directly to the Controller. All paired hypervisor nodes are accessible through the Controller's proxy — no separate credentials per hypervisor node.

---

## Capabilities

**AI Interface**
- Real-time conversation with the Controller's local LLM (Ollama)
- Streaming responses with optional voice output (TTS) and voice input (STT)
- Persistent conversation history synced across all Worker devices

**Hypervisors**
- View all VMs and containers across every paired hypervisor node
- Start, stop, reboot, and force-stop VMs directly from the app
- Status indicators (running / stopped / paused) with per-VM resource chips

**Device & Ecosystem Control**
- Remote desktop actions on other connected Worker clients
- View live resource metrics from all paired NeXiS Hypervisor nodes

**Automation**
- View and trigger scheduled tasks defined on the Controller
- Real-time notifications for alarms and events

---

## Platforms

| Platform | Format | Requirements |
|----------|--------|--------------|
| Android | APK (sideload) | Android 8.0+ |
| Linux desktop | `.deb` | Debian 12 / Ubuntu 22.04+ · x86_64 |

---

## Building

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
3. Accept the self-signed certificate on first connection
4. Enter your Controller username and password
5. The app registers itself as a device — it appears in the Controller's **Devices** panel

No per-hypervisor setup. All VMs from all paired nodes appear in the **Hypervisors** tab via the Controller.

---

## Connection

- **Protocol:** HTTPS with TOFU (Trust On First Use) TLS — accepts self-signed certificates
- **Auth:** Bearer token, 90-day TTL
- **Realtime:** Server-Sent Events for conversation sync and notifications
- **Timeout:** 300 s for streaming endpoints, 30 s for standard requests

---

## Stack

| Component | Technology |
|-----------|-----------|
| Android app | Kotlin · Android SDK 26+ |
| Desktop app | Kotlin · Compose Multiplatform · Material 3 |
| Build system | Gradle 8.9 · AGP 8.7.3 |
| Networking | OkHttp · custom TOFU TLS |
| Storage | Android DataStore / JVM preferences |
