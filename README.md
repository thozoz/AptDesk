<div align="center">
  <h1>AptDesk</h1>
  <p><strong>Linux Desktop in your Browser — from your Android phone. No root required.</strong></p>
  <p>
    <a href="#features">Features</a> •
    <a href="#quick-start">Quick Start</a> •
    <a href="#how-it-works">How It Works</a> •
    <a href="#building">Building</a> •
    <a href="#faq">FAQ</a>
  </p>
  <br>
</div>

AptDesk turns any Android device into a portable Linux desktop server. Install the app, press **Start**, and open `http://<phone-ip>:8080` from any browser on your network. You get a full Ubuntu XFCE desktop with a terminal, file browser, and apt package manager — all running locally on your phone via PRoot.

No laptop required. No cloud subscription. No root access.

---

## Features

- **One-tap Linux desktop** — Press Start, open your browser. XFCE desktop with VNC via noVNC.
- **Built-in terminal** — Web-based shell via ttyd at `http://<ip>:8081`. Full bash access from any device.
- **File browser** — Upload, download, and manage files from your browser at `http://<ip>:8083/filesapp`.
- **Software manager** — Search, install, and remove apt packages from the web UI.
- **System dashboard** — Real-time CPU, RAM, disk, battery, and uptime monitoring.
- **No root** — Uses [PRoot](https://proot-me.github.io/) to run an isolated Linux filesystem without kernel modifications. Safe on stock Android.
- **Dark theme** — Material 3 dark theme with Jetpack Compose.
- **Factory reset** — Reset desktop configuration without touching personal files.
- **Works offline** — Fully local on your LAN. No internet required after initial setup.

## Quick Start

1. **Download** the latest APK from [Releases](https://github.com/thozoz/AptDesk/releases).
2. **Install** on any Android 7+ (API 26+) device.
3. **Open** the app and tap **Start Backend**.
4. **Wait** for the Ubuntu rootfs to download and extract (~2-5 minutes on first run).
5. **Open** `http://<phone-ip>:8080` from any browser on the same network.

Your desktop IP is shown on the app screen after startup.

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| Desktop | `http://<ip>:8080` | XFCE desktop via noVNC |
| Terminal | `http://<ip>:8081` | Web-based bash shell |
| Files | `http://<ip>:8083/filesapp` | File browser & manager |

## How It Works

```
┌─────────────────────────────────────────────┐
│               Android Device                  │
│  ┌─────────────────────────────────────────┐ │
│  │           AptDesk App (Kotlin + Compose) │ │
│  │  ┌───────────────────────────────────┐  │ │
│  │  │         PRoot (no root)           │  │ │
│  │  │  ┌─────────────────────────────┐  │  │ │
│  │  │  │   Ubuntu XFCE Rootfs        │  │  │ │
│  │  │  │  ┌──────┐ ┌──────┐ ┌─────┐ │  │  │ │
│  │  │  │  │Xvfb  │ │ttyd  │ │File │ │  │  │ │
│  │  │  │  │VNC   │ │bash  │ │Browser│  │  │ │
│  │  │  │  │noVNC │ │      │ │     │ │  │  │ │
│  │  │  │  └──────┘ └──────┘ └─────┘ │  │  │ │
│  │  │  │  ┌──────────────────────┐   │  │  │ │
│  │  │  │  │   Caddy Reverse Proxy │   │  │  │ │
│  │  │  │  └──────────────────────┘   │  │  │ │
│  │  │  └─────────────────────────────┘  │  │ │
│  │  └───────────────────────────────────┘  │ │
│  └─────────────────────────────────────────┘ │
│            ▲  LAN  (port 8080)                │
└─────────────┼─────────────────────────────────┘
              │
    ┌─────────┴─────────┐
    │   Any Browser     │
    │ (Phone, Tablet,   │
    │  Laptop, Desktop) │
    └───────────────────┘
```

The app downloads a prebuilt Ubuntu rootfs from GitHub Releases. On startup, it launches PRoot with the rootfs, starts Xvfb (virtual display), x0vncserver + noVNC for desktop access, ttyd for terminal, filebrowser for file management, and Caddy as the reverse proxy — all wrapped in a single foreground service.

## Building

Requires Android Studio Hedgehog or later.

```bash
git clone https://github.com/thozoz/AptDesk.git
cd AptDesk
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Building the Rootfs

The custom, optimized Ubuntu XFCE rootfs can be rebuilt and packaged using the provided scripts:

- **On Linux (Native)**:
  ```bash
  cd docker
  chmod +x build-rootfs.sh
  ./build-rootfs.sh
  ```
- **On Windows (WSL + Docker Desktop)**:
  Run the PowerShell wrapper script to automate the build inside WSL:
  ```powershell
  cd docker
  .\docker.ps1
  ```
  *(Optional: Use `-NoCache` to force rebuild without Docker cache)*


### Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Project Structure

```
AptDesk/
├── app/                    # Android app module
│   ├── src/main/java/      # Kotlin source
│   │   ├── MainActivity.kt       # Compose UI
│   │   ├── MainService.kt        # Foreground service
│   │   ├── ProotManager.kt       # PRoot lifecycle
│   │   ├── RootfsManager.kt      # Rootfs download/extract
│   │   ├── WebServer.kt          # nanohttpd REST API
│   │   ├── AptDeskState.kt       # State management
│   │   └── NetworkInfo.kt        # IP detection
│   └── jniLibs/            # PRoot ARM64 binaries
├── docker/                 # Rootfs build scripts
│   ├── Dockerfile.ubuntu-xfce    # Ubuntu XFCE rootfs build
│   ├── build-rootfs.sh           # Rootfs packaging script (runs on Linux or WSL)
│   └── docker.ps1                # PowerShell wrapper script for Windows/WSL
├── configs/
│   └── Caddyfile           # Caddy reverse proxy config
├── scripts/
│   └── setup-rootfs.sh     # Rootfs post-install setup
└── build.gradle.kts        # Root Gradle config
```

## FAQ

**Does this need root?** No. PRoot runs in userspace without any kernel modifications. Safe on stock, unrooted Android.

**Is it slow?** Terminal and file operations are snappy. Desktop GUI has some latency over noVNC, similar to any web-based remote desktop. CPU sits at ~10-20% at idle.

**Can I install any Linux package?** Yes. The built-in software manager runs `apt-get` inside the rootfs. Search, install, and remove packages from the web UI.

**Does it need internet?** Internet is needed only for the initial rootfs download. After that, everything runs locally on your LAN.

**Will it drain my battery?** The app runs as a foreground service. Expect moderate battery impact. Use the **Stop Backend** button when not in use, or keep your device plugged in for long sessions.

**Can I access it from outside my home network?** Not directly — the server binds to your LAN. For remote access, set up a VPN (Tailscale, WireGuard, etc.) to your home network.

**Does it work on tablets?** Yes. On tablets you can run AptDesk and open the browser in split-screen — desktop on one side, your work on the other.

**How do I stop it?** Open the app and tap **Stop Backend**, or swipe the notification away.

## License

[MIT](LICENSE)
