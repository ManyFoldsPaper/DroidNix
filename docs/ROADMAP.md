# DroidNix — Development roadmap

Scope discipline: each phase ends with something installed on the device and
verified there. Nothing is marked done because it compiles.

## Phase 0 — Inspection and preservation (done)

- Read every existing script, the chroot configuration and the device facts.
- Back up all scripts (host + SD card, dated tarball).
- Document inventory, hard-won kernel/Magisk facts (`docs/INVENTORY.md`).

## Phase 1 — Real application, managing the existing install (verified on device)

Status 2026-11-25: installed on the SM-T290 and exercised end-to-end — root
gate (denied and granted), disclaimer, discovery, adopt, start/stop from fully
stopped and partially mounted states, live status after app kill, diagnostics,
and a real RFB framebuffer of the running XFCE desktop (VncAuth, 1280×800).
Remaining unverified branches: the low-RAM blocking screen (device exceeds the
threshold) and Termux helper regression (scripts untouched; re-check pending).

Goal: a signed, installable APK that can start and stop the Arch+XFCE
environment already on the SD card, with all compatibility gating, and
**without breaking the Termux workflow** (the old scripts stay in place).

Deliverables:
- Gradle/Kotlin/Compose project, Material 3, dynamic colour.
- `RootManager` proving Magisk root (not just a `su` file).
- Compatibility gate: ABI, RAM (total ≥ 1.8 GiB), storage, SELinux, loop/ext4.
- Onboarding + disclaimer with explicit acknowledgement, persisted.
- Environment discovery (legacy adoption), start/stop with live log, storage
  usage, running status.
- Parameterised, portable shell scripts (no hardcoded device paths), read from
  assets and piped to the root shell over stdin.
- Delete an environment with a typed confirmation that refuses running images.
- Settings: script/log visibility, environment paths, diagnostics export.
- Build + `adb install` + on-device verification of every listed item.

Not in this phase (and shown as such): the install wizard, the display engine.

## Phase 2 — Integrated display + input

Status 2026-11-27: **delivered and verified on the SM-T290** with release build
0.2.0. The protocol layer has JVM tests; the viewer was then exercised against
the tablet's live Xvnc session.

Implemented and verified:
- `RfbClient`: RFB 3.3/3.7/3.8, VncAuth (DES) and None, `shared=1`, Raw,
  CopyRect, Hextile and DesktopSize. Outbound messages go through a writer
  thread, server rectangles are clipped to the framebuffer and Hextile sub-rect
  sizes are decoded correctly.
- `RfbFramebuffer` + `DesktopViewModel`: decoding off the main thread, dirty
  rectangles applied to the Bitmap on the main thread, Compose invalidation.
- Touch translation: tap, long-press (right click), drag, two-finger scroll,
  pinch zoom, two-finger pan when zoomed, toolbar Fit/1:1/Pan — each observed
  on device.
- Keyboard: IME text entry and the special-key row verified in
  xfce4-terminal; sticky Ctrl/Alt.
- Clipboard: `ServerCutText` into the Android clipboard (pasted into Settings)
  and `ClientCutText` via the toolbar (read back from the X CLIPBOARD).
  `vncconfig -nowin` is started by the session script.
- Settings page for the fallback VNC password; per-environment value wins.
- Protocol tests: handshake, VncAuth vector, Raw, Hextile, clipboard, Hextile
  sub-rect sizes and oversized-rect clipping.

Still open in this phase:
- **Physical keyboard and mouse.** No hardware was available; the key map and
  pointer buttons are compiled but unverified (tracked as feature #30).
- Physical mouse wheel mapping (touch supplies wheel events today).
- Immersive mode, DPI/geometry switching, non-Latin IME input verification.

## Phase 3 — Installation wizard

Status 2026-11-25: the **storage picker is already implemented and verified**
(`docs/FEATURES.md` #14) — internal and SD targets are probed for writability
and a free loop device, and the choice persists. The wizard below still has to
create the image and populate it.

- ~~Storage picker (internal/SD) using `StatFs` + root validation, no assumptions
  about paths; free-space estimate before download.~~ DONE 0.1.5.
- Arch only: download `ArchLinuxARM-aarch64-latest.tar.gz` over HTTPS with
  SHA-256 pinning, verified before extraction.
- Create ext4 image (`mkfs.ext4 -I 256`) or directory rootfs; extract with
  progress; write `droidnix.json`; run the same setup steps that were done by
  hand (pacman DisableSandbox, resolv.conf, inet group, users, swapfile).
- XFCE package set install with real pacman progress, cancellation at safe
  points, and resume of interrupted installs.
- Explicit destructive confirmations; recovery screen with logs.

## Phase 4 — Hardening and device coverage

- Interrupted-start recovery, storage-removal detection, boot autostart opt-in.
- Compatibility matrix and real test reports for additional devices
  (different SD block names, 16 KB pages, kernels 5.x/6.x).
- Log redaction, path fuzzing tests, script checksum pinning.

## Phase 5 — More distributions / desktops / display backends

- Debian, Ubuntu, Alpine, Fedora installers as separate configuration modules
  with their own bootstrap sources and verification.
- LXQt, MATE, LXDE desktop modules with per-distro package maps.
- Alternative display backends: in-process X11 (Termux:X11-derived, NDK) or
  Wayland compositor + Android EGL surface; selectable in Settings.

## Known blockers / technical limits

- **Kernel**: chroot shares the Android kernel (4.9 here). Landlock-based
  pacman sandbox must stay disabled; newer packages may need newer syscalls.
  This cannot be fixed in userspace — it is documented, not worked around.
- **RAM**: 1.8 GiB total is the floor, not a guarantee; the desktop idles at
  ~1.2 GB with Xvnc. Swap on SD is slow by nature.
- **In-process X server**: not part of Phase 1/2; requires NDK + EGL work.
  Embedded RFB is the milestone-1 answer and is a real client, not a wrapper.
