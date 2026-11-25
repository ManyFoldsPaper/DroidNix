# DroidNix — Architecture

## Principles

1. **Shell scripts are data, not strings in Kotlin.** They live in
   `app/src/main/assets/scripts/` and are piped to the root shell over stdin
   (`su -t 0 -c 'sh -s -- <args>'`), never extracted to disk. Arguments are
   single-quoted. No shell interpolation of user-controlled values.
   (A file under `/data/local` is not an option: Magisk 31's domain is denied
   write access there; see `docs/INVENTORY.md`.)
2. **Everything device-specific is discovered at runtime**: SD mount point,
   block device, loop device, free space, SELinux state, kernel features.
3. **Two-phase privilege**: the app never assumes root. Every operation that
   needs it goes through `RootManager`, which proves `su -c id` works and
   records the Magisk version. Failure is a first-class state, not an exception.
4. **Preserve the working installation.** The existing
   `/mnt/media_rw/<uuid>/archlinux` layout is a first-class "environment"
   (read *and* written only through the same scripts, adapted, not replaced).
5. **Honest feature state.** Every screen either does real work or is absent.
   `docs/FEATURES.md` is the single source of truth.

## Layers

```
┌──────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose, Material 3)                         │
│  gates → onboarding → Home / Environments / Install /    │
│  Settings, live log drawer                               │
└───────────────┬──────────────────────────────────────────┘
                │ StateFlow<UiState>
┌───────────────▼──────────────────────────────────────────┐
│ ViewModel (MainViewModel)                                │
│  single state machine: Boot → RootGate → CompatGate →    │
│  Onboarding → Ready                                      │
└───────────────┬──────────────────────────────────────────┘
                │
┌───────────────▼──────────────┐   ┌──────────────────────┐
│ core                         │   │ engine               │
│ RootManager  (su -t 0, Magisk)│  │ EnvironmentRepo      │
│ Shell        (timeouts, EOL) │   │ ScriptStore (assets) │
│ DeviceInfo   (RAM, ABI, FS)  │   │ ChrootController     │
│ Compatibility(evaluate)      │   │  start/stop/delete   │
│ Paths        (validation)    │   │ DisplayEngine (iface)│
└───────────────┬──────────────┘   └──────────┬───────────┘
                │                             │
                ▼                             ▼
        `su -t 0 -c 'sh -s'` (stdin)   assets/scripts/*.sh
                                      (mount, chroot, session)
```

## Environment model

An environment is a self-describing directory:

```
<root>/                          e.g. /mnt/media_rw/<SD-UUID>/archlinux
├── droidnix.json                id, distro, desktop, created, version
├── arch.img                     ext4 image (or rootfs/ tree for future types)
├── mnt/                         mount point while running
├── logs/                        session.log, start.log, stop.log
└── start.sh stop.sh enter.sh    legacy per-environment helpers
```

New environments will live under `<volume>/DroidNix/<name>/`; discovery scans
that depth too, while the existing legacy folder is still found.

`EnvironmentRepository.discover()` finds candidates by scanning
`/mnt/media_rw/*/`, `/storage/emulated/0/*/`, `/data/local/droidnix/` and
`/data/adb/droidnix/` down to `<volume>/<folder>/<env>` for a directory
containing `droidnix.json` **or** `arch.img` (legacy = the current install).
`/storage/<uuid>` is deliberately not scanned: it is a second view of the same
removable volume and produced duplicate entries. Results are deduplicated by
canonical path.
Legacy environments get a `droidnix.json` written on first adoption.

Install locations come from `dn-storage.sh`: it scans mounted volumes under
`/mnt/media_rw` plus `/data/media/0/DroidNix` (the ext4 backing view of internal
storage; `/sdcard` is FUSE and is not loop-mountable), then writes and deletes
a probe file and records the exact filesystem from `/proc/mounts`, free space
and a free loop device. The choice is stored in `SharedPreferences`.
Deletion (`dn-delete.sh`) is a separate script that re-validates the path and
refuses mounted/running environments.

Runtime status is *observed*, not tracked in memory: the image mountpoint,
`losetup` attachment and `Xvnc` process are checked on every refresh, so the
app survives being killed and restarted.

## Display engine

The Linux side currently runs TigerVNC (X11, RFB protocol). The display engine
is defined as an interface so backends can be swapped:

```kotlin
interface DisplayEngine {
    suspend fun connect(host: String, port: Int, password: String): DisplaySession
}
```

Milestone 1 backend: **in-process RFB client** (`VncEngine`) rendering into a
Compose `ImageBitmap`, sending pointer/key events. No external app, no
WebView. This is a genuine integrated viewer; it is *not* an in-process X
server, because that would require shipping `libXlorie`-style native X11
(NDK, EGL, ~tens of MB) — tracked as an alternative backend in ROADMAP Phase 5,
not silently dropped.

## Safety model

- Path arguments are validated (`[A-Za-z0-9._/-]`, no `..`, absolute, exists).
- Every root command pipes the script to `su -t 0 -c 'sh -s -- <args>'`; no
  string concatenation of untrusted data into a shell.
- Destructive actions (delete environment) require typing `DELETE`; the shell
  script independently re-checks the path, refuses a running/mounted image and
  never touches anything outside the environment folder. Nothing is automatic.
- The app never touches boot/recovery partitions; Magisk is used only for `su`.
- Shutdown kills only processes whose `/proc/<pid>/root` is the image.
