# DroidNix

**An open-source Linux environment manager for Magisk-rooted Android devices.**

DroidNix is a native Android app (Kotlin + Jetpack Compose, Material 3) that
finds, adopts, starts, stops and deletes real Linux chroots stored in disk
images on Android storage. It does not emulate Linux, ship fake progress or
require a terminal: the device checks, root handling and shell work are real and
observed live.

![Status](https://img.shields.io/badge/status-Phase%202-yellow)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

> **Project status — Phase 2 (v0.2.0, device verification pending).**
> DroidNix **manages existing Arch Linux + XFCE installations** and now includes
> an **in-process RFB desktop viewer** rendered in Compose, with touch gestures,
> an on-screen/hardware keyboard and a clipboard bridge. That viewer is built and
> covered by protocol tests, but it has **not yet been run on the tablet** — the
> last on-device-verified build remains v0.1.5. The **new-install wizard** is
> still planned for Phase 3 and is not implemented. The UI does not present
> buttons for features that do not exist.

---

## Download

The latest APK and source archives are attached to the
[**v0.1.5 release**](https://github.com/ManyFoldsPaper/DroidNix/releases/tag/v0.1.5):

| Asset | Notes |
|---|---|
| `DroidNix-0.1.5-release.apk` | R8-minified release build, debug-signed for sideloading |
| `DroidNix-0.1.5-source.tar.gz` / `.zip` | Source export of the `v0.1.5` tag |

Verify the APK checksum before installing
(`7067b2b1…ef9609`); full checksums are listed in the release notes. Building
from source is described under [Build from source](#build-from-source).

---

## What works today (verified on real hardware)

Everything below was exercised on a rooted Samsung SM-T290 running Android 15
and Magisk 31.1. See [docs/VERIFICATION.md](docs/VERIFICATION.md) for the
device test record.

| Capability | Status | Notes |
|---|---|---|
| Magisk root verification | Done | Proves a usable root shell (`uid 0` **and** Magisk present), not merely that `su` exists. Non-dismissible blocking screen when root is unavailable. |
| Compatibility gate | Done | ARM64-only, minimum **1.8 GiB total RAM**, ext4 support, free loop device. Blocking failures cannot be bypassed. |
| Device diagnostics | Done | Model, Android/API, ABI, kernel, architecture, SELinux, RAM, storage, ext4/exFAT, free loop, Magisk — measured, not assumed. |
| First-run disclaimer | Done | Explicit acknowledgement checkbox; persisted across app restarts. |
| Environment discovery | Done | Scans removable volumes and internal storage for `arch.img` / `droidnix.json`; canonicalises paths and removes duplicates. |
| Adopt an existing install | Done | Writes a small `droidnix.json` next to the image; never resizes, formats or modifies the image. Refuses to overwrite an existing config. |
| Start / stop a chroot | Done | Loop-mounts the image, bind-mounts `/dev`, `/proc`, `/sys`, `/dev/pts`, `dev/shm`, starts Xvnc + XFCE; stop releases processes, swap, mounts and loop devices. Android keeps the SD card mounted. |
| Live status | Done | Mount, loop device, Xvnc, swap, disk usage, distro and desktop are read from the running system on every refresh. No cached state. |
| Partial-state recovery | Done | Detects "image mounted but desktop not running" and offers recovery instead of reporting a false RUNNING/STOPPED. |
| Session log | Done | Tails the environment's `logs/session.log`. |
| Survives app kill | Done | Force-stopping and relaunching the app re-observes the real state. |
| Install-location probing | Done | Lists internal storage and mounted SD cards, writes/removes a probe file, measures free space and checks for a free loop device. The choice persists. |
| Delete an environment | Done | Requires typing `DELETE`; refuses mounted/running environments; removes only that environment folder. Never formats storage. |

## Phase 2 display — built, awaiting on-device verification

The **Open desktop** button launches an in-process RFB (VNC) client. No external
VNC app, browser or X server is involved; pixels are decoded in Kotlin and drawn
straight into Compose.

| Capability | State | Notes |
|---|---|---|
| RFB 3.3 / 3.7 / 3.8 handshake | Built | VncAuth (DES) and None; shares the session (`shared=1`). |
| Encodings | Built | Hextile (preferred), Raw, CopyRect, DesktopSize. |
| Touch pointer | Built | Tap = left click, long-press = right click, drag = button-held drag. |
| Scroll and zoom | Built | Two-finger scroll at fit scale; pinch zoom; two-finger pan when zoomed; toolbar Fit/1:1/Pan. |
| Keyboard | Built | On-screen IME text, Backspace/Enter/arrows/Tab/Esc row, hardware key mapping, sticky Ctrl/Alt. |
| Clipboard | Built | Linux → Android automatically; toolbar **Paste** sends Android → Linux. |
| Framebuffer safety | Built | Network thread decodes into its own buffer; only a private dirty-rectangle copy is applied to the Bitmap on the main thread. |
| Protocol tests | Passing | Mock RFB server: handshake, VncAuth vector, Raw, Hextile, clipboard. |

**Not yet verified on hardware.** The viewer compiles and its protocol layer is
tested on the JVM, but the 2026-11-26 build has not been exercised against the
tablet's real Xvnc yet. Until it is, v0.1.5 remains the last device-verified
release and v0.2.0 is not published.

Known limitations of this viewer:

- Clipboard and typed text use RFB's ISO-8859-1/Latin-1 path plus Unicode
  keysyms. Latin text and most shortcuts work; non-Latin IME input inside X
  depends on the server's keymap and is not guaranteed.
- Mouse wheel from a physical USB mouse is not mapped separately; the
  two-finger touch gesture provides wheel events.
- Server-rendered desktops only. An in-process X11 backend (no VNC process) is
  still Phase 5.
- No audio, no multi-monitor and no automatic resolution/DPI switching yet.

## Planned, not implemented

- **New-install wizard** (download, verify, create image, bootstrap, install
  packages). *Phase 3.*
- **Additional distributions and desktops** (Debian, Ubuntu, Alpine, Fedora;
  LXQt, MATE, LXDE). *Phase 5.*
- **In-process X11 backend** (no VNC process). *Phase 5.*
- **Boot autostart.** *Phase 4.*

The full, honest status list is [docs/FEATURES.md](docs/FEATURES.md). The plan is
[docs/ROADMAP.md](docs/ROADMAP.md).

## Requirements

- **ARM64** Android device (`arm64-v8a`). Other ABIs are blocked.
- **Android 8.0+** (API 26). Developed and tested on Android 15 (API 35).
- **Magisk root** with a working `su`. Other root solutions are untested.
- **1.8 GiB total RAM** minimum. This is a floor, not a performance guarantee;
  an XFCE/Xvnc session idles around 1.2 GB.
- Kernel support for **ext4** and **loop devices**.
- An existing Arch Linux ARM + XFCE image (`arch.img`) with a
  `start.sh`-equivalent session, or one you adopt.

Full details and the tested/untested matrix: [COMPATIBILITY.md](COMPATIBILITY.md).

## How it works

```
Compose UI (Material 3)
        │  StateFlow<UiState>
MainViewModel ──> EnvironmentRepo / ChrootController / RootManager
        │                    │
        │                    └── POSIX shell scripts bundled in APK assets
        ▼
Shell (su -t 0 -c 'sh -s -- <args>')
        │  scripts arrive on stdin (never extracted to disk)
        ▼
Android kernel: loop device, ext4 mount, bind mounts, chroot, Xvnc
```

Design rules that shape the code:

- **Scripts are data.** All privileged logic lives in
  `app/src/main/assets/scripts/` and is piped to Magisk's shell over stdin. No
  root helper is written to writable storage and no user value is interpolated
  into a shell string without quoting.
- **Everything is discovered.** SD mount points, block devices, loop devices,
  free space and SELinux state are read at runtime; nothing is hardcoded.
- **Processes are identified by root filesystem**, never by UID. Arch's `alarm`
  user is UID 1000, which collides with Android's `system` user.
- **State is observed, not remembered.** Mount, loop and Xvnc state survive app
  restarts because they are read back from the device.
- **Honest UI.** A screen either performs real work or is absent.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and
[docs/INVENTORY.md](docs/INVENTORY.md).

## Build from source

Requirements: **JDK 21**, **Android SDK 35** (`platforms;android-35`,
`build-tools;35.0.0`, `platform-tools`), and a network connection for Gradle
dependencies.

```sh
git clone https://github.com/ManyFoldsPaper/DroidNix.git
cd DroidNix

# Point Gradle at your SDK (this file is intentionally git-ignored)
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Optimised release APK (R8-minified, debug-signed for sideloading)
./scripts/build-apk.sh release
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or use Gradle directly:

```sh
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

`scripts/build-apk.sh` also supports `DROIDNIX_JDK` (path to JDK 21) and
`ANDROID_HOME`/`ANDROID_SDK_ROOT`.

> **Signing:** local release builds are signed with the standard Android debug
> keystore so they can be sideloaded. That is **not** a production signing
> identity and must not be used for store distribution. See
> [SECURITY.md](SECURITY.md).

The release build is R8-minified. On the 2 GB test tablet that is what makes
the UI smooth: measured ~3 % janky frames versus ~63 % in a debuggable build
(median frame 12 ms vs 73 ms).

## Usage (Phase 1)

1. Install the APK and open DroidNix.
2. Accept the first-run disclaimer.
3. Grant Magisk superuser access when prompted. If the prompt is missed, open
   Magisk → Superuser and allow DroidNix, then press **Check again**.
4. Plug in the storage that holds your existing Arch Linux image. DroidNix finds
   it under a mounted volume or internal storage.
5. On **Install**, adopt a legacy install if it has no `droidnix.json`.
6. On **Home** or **My environments**, start the environment. Status, loop
   device, swap and Xvnc are read live.
7. Press **Open desktop** to view the session in the built-in viewer. Drag with
   one finger to move the pointer, long-press for a right click, two-finger swipe
   to scroll and pinch to zoom. The toolbar exposes **Keys**, **Fit**, **1:1**,
   **Pan**, **Ctrl**, **Alt** and **Paste**.
8. **Stop** shuts the session down without unmounting Android's SD card.
9. **Delete this environment** removes the selected folder after typing
   `DELETE`; it refuses while the environment is running.

## Safety model

- DroidNix never installs root, unlocks a bootloader, or modifies boot/recovery.
- No automatic formatting, repartitioning or wiping. Adopting writes one small
  JSON file and refuses to overwrite.
- Deletion is explicitly confirmed, path-validated and refused while the
  environment is mounted or running.
- Install locations are probed (write + free loop device) before being offered.
- The app does not use telemetry and makes no network requests in Phase 1. The
  `INTERNET` permission is declared for the planned Phase 3 installer.
- Root access is inherently powerful. Review the shell scripts before running
  DroidNix on a device you care about.

## Documentation

| Document | Contents |
|---|---|
| [docs/README.md](docs/README.md) | Documentation index |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, state model, display engine interface, safety design |
| [docs/FEATURES.md](docs/FEATURES.md) | Single source of truth for feature status |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phases 0–5 and known blockers |
| [docs/INVENTORY.md](docs/INVENTORY.md) | Device findings and hard-won kernel/Magisk constraints |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | On-device verification checklist and results |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Supported devices, requirements, tested matrix |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Build, test and contribution rules |
| [SECURITY.md](SECURITY.md) | Threat model and vulnerability reporting |

## Contributing

Contributions are welcome, especially device compatibility reports, fixes for
the shell layer and the RFB viewer. Read
[CONTRIBUTING.md](CONTRIBUTING.md) first. The project's rule is simple: no
feature is marked done until it is verified on a real device.

## Security

Please report vulnerabilities privately via GitHub Security Advisories. See
[SECURITY.md](SECURITY.md). Do not open a public issue for a security problem.

## License

Licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) and
[NOTICE](NOTICE).

## Disclaimer

DroidNix is experimental software that operates with root privileges. Running a
Linux desktop on a low-memory Android device can make Android unstable. Back up
your data. You are responsible for your device, your warranty and any data loss.
DroidNix does not ship or license Arch Linux packages; Linux software is
installed and licensed by you inside your environment.
