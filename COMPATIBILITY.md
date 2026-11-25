# Compatibility

DroidNix is deliberately conservative: a device that fails a blocking check is
shown a non-dismissible "not supported" screen. There is no bypass.

## Blocking requirements

| Requirement | Why | Detection |
|---|---|---|
| **ARM64** (`arm64-v8a`) | The bundled session and tested Arch Linux ARM images are AArch64. | `Build.SUPPORTED_ABIS` / runtime |
| **Magisk root** | Mounting loop images and entering a chroot requires root, done through Magisk's `su`. | `su -t 0 -c id` returns uid 0 **and** Magisk is present |
| **1.8 GiB total RAM** | Practical floor for an XFCE/Xvnc session on Android. Measured from `/proc/meminfo`. | `MemTotal >= 1,932,735,283 bytes` |
| **ext4 support** | The Linux image is an ext4 filesystem. | `/proc/filesystems` |
| **A free loop device** | The image is attached with `losetup`. | `losetup -f` |

## Non-blocking warnings

These are reported but do not block the app:

- SELinux is `Enforcing` (supported, but Magisk must allow the mounts; the app
  verifies this at start).
- Available RAM below 500 MB at that moment.
- Less than 3 GB free internal storage.
- The kernel lacks exFAT (Android's own SD mount is still used).

## Verified configuration

The project is developed and verified on exactly this device:

| Property | Value |
|---|---|
| Device | Samsung Galaxy Tab A 8.0 (2019), SM-T290 (`gtowifi`) |
| OS | LineageOS 22.2, Android 15 (API 35) |
| CPU | arm64-v8a, kernel `4.9.337-perf+` |
| RAM | 1.80 GiB total (`MemTotal` 1,888,196 kB) |
| Root | Magisk 31.1 (version code 31100), MagiskSU |
| SELinux | Enforcing |
| Internal storage | ext4, ~22 GB |
| SD card | 58 GB exFAT (removable; Android vold mounted) |
| Linux environment | Arch Linux ARM (aarch64), XFCE 4.20, TigerVNC 1.16.2, 10 GB ext4 image |
| Display | Xvnc `:1` on `127.0.0.1:5901`, 1280×800 @160 dpi |

## Tested / untested matrix

| Area | Tested | Untested / unknown |
|---|---|---|
| Android | 15 (API 35) | 8.0–14 (API 26–34) — `minSdk` allows them, but they have not been run |
| Kernel | 4.9.337 | 5.x, 6.x, 16 KB page-size kernels |
| SoC | Snapdragon 429 (SM-T290) | Other ARM64 SoCs and vendor kernels |
| Root | Magisk 31.1 | Older Magisk, KernelSU, APatch, other root implementations |
| Storage | Internal ext4; SD exFAT | F2FS internal, ext4 SD, USB-OTG, adopted storage |
| Desktop | XFCE | LXQt, MATE, LXDE, GNOME, KDE |
| Distribution | Arch Linux ARM | Debian, Ubuntu, Alpine, Fedora |

"Not tested" does not mean "known broken" — it means the project will not claim
support without evidence. Reports from additional devices are welcome; see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Storage requirements

- Images are regular files (`arch.img`) on a filesystem that supports loop
  devices. Internal storage is used through `/data/media/0/DroidNix` (the ext4
  backing view, **not** the `/sdcard` FUSE view).
- SD cards are discovered under `/mnt/media_rw/<uuid>`; DroidNix never assumes
  the UUID or the block device name.
- Let Android mount the SD card first. DroidNix falls back to mounting the card
  itself only when Android has not done so, and only after locating the real
  block device.
- A 10 GB image plus a 2 GB swapfile is the reference install; allow at least
  ~15 GB free for a comfortable environment.
- SD-card I/O is much slower than internal storage; swap on SD is slow by nature.

## Distribution and desktop support

Phase 1 supports **existing Arch Linux ARM + XFCE installations only**. Creating
a new installation is the Phase 3 wizard and is **not implemented**. Other
distributions and desktops are shown as unavailable in the UI and are not
offered.

## Display

The Linux side runs TigerVNC (RFB). The in-app viewer is **not implemented yet**
(Phase 2), so Phase 1 users view the desktop with a VNC client on the same
device at `127.0.0.1:5901`. Integrated rendering and native touch gestures are
planned, not present.

## How to check your own device

1. Open DroidNix; the root and compatibility gates run automatically.
2. Open **Settings** for the measured device report and **Collect** diagnostics.
3. If the app is blocked, the failing check and the measured value are shown on
   screen — no guessing.
