# DroidNix — Inventory of the existing, working implementation

Captured from the live device (SM-T290) on 2026-11-24, before any modification.
All facts below were observed, not assumed. Original script backups are kept
privately outside this repository. The SD card UUID is redacted as
`<SD-UUID>` throughout these documents.

## 1. Device

| Property | Observed value | Source |
|---|---|---|
| Model | SM-T290 (`gtowifi`, LineageOS 22.2) | `getprop ro.product.model/device` |
| Android | 15, API 35 | `ro.build.version.release/sdk` |
| CPU ABI | `arm64-v8a` (+ armeabi-v7a, armeabi) | `ro.product.cpu.abilist` |
| Kernel | `4.9.337-perf+` aarch64 | `uname -a` |
| RAM | 1,888,196 kB total (~1.80 GiB), ~540 MB available | `/proc/meminfo` |
| SELinux | Enforcing | `getenforce` |
| Root | Magisk **31.1:MAGISK:R** (31100), `su` usable | `magisk -v`, `su -c id` |
| Filesystems | ext4, exfat, fuse, fuseblk | `/proc/filesystems` |
| Loop devices | available (`losetup -f` → `/dev/block/loop36`) | `losetup -f` |
| Internal storage | 22 GB, 7.5 GB free | `df /data` |
| SD card | 58 GB exFAT, 25 GB free, label `<SD-UUID>` | `df`, `stat -f` |

## 2. Current Linux installation (the thing that must keep working)

```
/mnt/media_rw/<SD-UUID>/archlinux/          <- SD card, exFAT
├── arch.img                                10 GB ext4 (system + home + 2 GB swapfile)
├── mnt/                                    mount point of arch.img while running
│   ├── home/alarm/.vnc/passwd              VNC password (8 bytes, DES)
│   ├── usr/local/bin/arch-session          Xvnc + dbus + startxfce4 launcher
│   ├── etc/pacman.conf                     [options] DisableSandbox = true (kernel 4.9)
│   └── swapfile                            2 GB
├── start.sh / stop.sh / enter.sh
├── logs/{setup,session,boot}.log
└── README.md
```

Distribution: Arch Linux ARM aarch64 (official tarball, verified gzip CRC).
Desktop: XFCE 4.20. Display: **TigerVNC 1.16.2** `Xvnc :1` on `127.0.0.1:5901`,
`-SecurityTypes VncAuth -PasswordFile ~/.vnc/passwd`, geometry 1280x800, dpi 160.

### How it starts (start.sh)

1. Wait for the SD card mount; if Android's `vold` has not mounted it, mount
   `/dev/block/mmcblk1p1` (exFAT) ourselves and touch `/dev/.arch-owns-sd`.
2. `losetup` the image (reusing an existing attachment if present), mount ext4
   `noatime,commit=30` on `mnt/`.
3. Bind `/dev`, `/proc`, `/sys`, `/dev/pts`, tmpfs on `dev/shm` (256 MB), and the
   SD root on `mnt/host` (so the card appears as `/host` inside Linux).
4. `swapon /swapfile` inside the chroot.
5. Start the desktop as `alarm` via
   `nohup setsid chroot $MNT /usr/bin/su - alarm -c /usr/local/bin/arch-session`.

### How it stops (stop.sh)

- Kills only processes whose `/proc/<pid>/root` is the image (never by UID —
  see below), TERM then KILL; `swapoff`; unmount children then the image;
  `losetup -d`; releases the SD card if `/dev/.arch-owns-sd` exists.
- Measured on this device: **3 s** cold, 31 s in the previous version that
  scanned `/proc` six times.

### Hard-won device-specific facts (must not be lost)

1. **UID 1000 collision.** Arch's `alarm` = UID 1000 = Android `system`.
   `pkill -u alarm` kills `system_server` → Android framework restart.
   Processes must be identified by root filesystem, never UID.
2. **Paranoid networking.** Non-root processes may only create TCP sockets if
   in GID 3003 (`inet`); `alarm` is in `inet,net_raw` (3003/3004) inside Arch.
   Without this, Xvnc cannot bind.
3. **pacman 7 sandbox** fails on kernel 4.9 (Landlock). `DisableSandbox` in
   `[options]` of `/etc/pacman.conf` is required.
4. **`/etc/resolv.conf`** is a dangling symlink in the ArchLinuxARM tarball;
   the setup removed it.
5. **`dbus-launch --exit-with-session`** dies without a controlling TTY;
   `arch-session` starts a session bus with plain `dbus-launch --sh-syntax`.
6. **`/proc` scans are expensive** (~9.3 s for 635 pids); use
   `grep -l /proc/*/comm` (0.1 s).
7. **vold/fsck race.** Mounting the exFAT before Android's vold does makes
   vold's `fsck.exfat` fail; Android then shows "Issue with SD card" and its
   "fix" formats the card. Always let vold mount first.
8. **Magisk `service.d` runs every executable**, not only `*.sh`. A disabled
   script renamed to `*.off` still executed at boot.
9. **`arch-session`** needs `LIBGL_ALWAYS_SOFTWARE=1`,
   `QTWEBENGINE_DISABLE_SANDBOX=1`, `QT_QPA_PLATFORM=xcb`, and
   `XDG_RUNTIME_DIR=/tmp/runtime-alarm` (0700).
10. Image uses `-I 256` inode size; 10 GB; ~2.5 GB free.

## 3. Entry points today

| Command | What it does |
|---|---|
| `arch` (Termux helper) | `su -c 'sh $SD/archlinux/start.sh \|\| sh /data/adb/arch-boot.sh'` |
| `arch-stop` | `su -c 'sh $SD/archlinux/stop.sh'` |
| `/data/adb/arch-boot.sh` | optional Magisk boot launcher (mounts SD, runs start.sh) |
| AVNC (`com.gaurav.avnc`) | external VNC viewer → 127.0.0.1:5901 |

## 4. Constraints this imposes on DroidNix

- Must run commands as root through `su -c` (Magisk), no other mechanism.
- Must not hardcode `/dev/block/mmcblk1p1` (other devices differ).
- Must not hardcode `/mnt/media_rw/<uuid>` (must be discovered).
- Must let Android mount the SD first; only fall back if vold fails.
- Must never kill by UID, and must never `pkill -f` broad patterns.
- The display server is TigerVNC inside the chroot; an integrated viewer must
  therefore speak RFB, or a different display backend must be added later.
- 16 KB page-size devices and 4.9–6.x kernels are all plausible; the image is
  block-size 4 KiB and portable, but the desktop stack must run on older
  kernels (Mesa software rendering: `LIBGL_ALWAYS_SOFTWARE=1`).

## 5. Constraints found while verifying DroidNix on-device (2026-11-25)

11. **`/data/local` is not writable by the Magisk 31 domain.** `su -c 'mkdir
    /data/local/zzz'` fails with EACCES even as uid 0, and a directory created
    earlier (filled by a partial deploy) cannot be listed or removed either.
    Do not stage root helpers there. Same for parts of `/data/adb`.
12. **`su -c '<command>'` and `su -c 'sh -s'` are not equivalent on this
    build.** Commands passed as an argument intermittently ran with a view
    where `stat /mnt/media_rw/<SD-UUID>/archlinux` is EACCES and `mountpoint`
    reports false, while the same commands read from stdin (`sh -s`) see the
    SD card normally. DroidNix therefore pipes every root script through
    stdin. Symptom that led here: discovery found the image, but status
    reported `mounted=0` while `/proc/mounts` clearly contained the mount.
13. **Mount namespaces and `-t 0`.** MagiskSU supports `-t 0` (global mount
    namespace). Without it, mounts made inside one `su` session can be
    invisible (or torn down) in the next. All root calls use `-t 0`.
14. **Android 11+ package visibility.** `getLaunchIntentForPackage(
    "com.topjohnwu.magisk")` returns null unless the manifest declares
    `<queries><package android:name="com.topjohnwu.magisk"/></queries>`.
    Without it the "Open Magisk" button silently did nothing.
15. **Magisk su prompt can be ephemeral.** The first DroidNix request was
    rejected (`Magisk: su: request rejected (10184)`) with no visible dialog;
    Magisk queued toasts instead. The granted retry ("DroidNix was granted
    Superuser rights") worked. The app's root gate and "Check again" handle
    this state, which is why it is not treated as a fatal error.

## 6. Verified working commands (device, Magisk 31.1)

- `su -t 0 -c 'id -Z'` → `u:r:magisk:s0`
- `printf 'stat -c %i /mnt/media_rw/<SD-UUID>/archlinux\n' | su -t 0 -c 'sh -s'` → inode
- `adb forward tcp:5901 tcp:5901` + RFB VncAuth → 1280×800 `alarm@localhost`, Raw frames
- Android keeps `public:179,65 mounted <SD-UUID>` after start and stop.
