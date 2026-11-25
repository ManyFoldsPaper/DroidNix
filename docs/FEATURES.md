# Feature status — single source of truth

Legend: **DONE** verified on device · **BUILT** compiles/installable but not yet
verified end-to-end · **NEXT** in progress · **PLANNED** designed, not started ·
**BLOCKED** technically blocked (reason given)

Last verified build: 2026-11-25 — release build 0.1.5 on SM-T290
(LineageOS 22.2 / Android 15, Magisk 31.1). Toolchain: JDK 21, Gradle 8.13,
SDK 35. Release builds are R8-minified: scrolling is ~3 % janky frames versus
~63 % in the debuggable build (median frame 12 ms vs 73 ms).

| # | Feature | Status | Notes |
|---|---|---|---|
| 1 | Inspection of existing install | DONE | `docs/INVENTORY.md`; original script backups are kept privately outside this repository |
| 2 | Magisk detection + usable-root proof | DONE | `su -t 0 -c id` = 0 + Magisk 31100; denial screen seen live |
| 3 | ARM64 gate | DONE | 64-bit accepted on device; non-ARM64 path not testable here |
| 4 | RAM gate (1.8 GiB total) | BUILT | total 1.80 GB passes; blocking branch needs a low-RAM device |
| 5 | Storage/FS/SELinux/loop diagnostics | DONE | diagnostic report shows measured values (RAM 1888196 kB, SD 58 GB) |
| 6 | Onboarding + disclaimer, persisted | DONE | checkbox gating verified, survives app kill |
| 7 | Environment discovery (legacy SD install) | DONE | finds `/mnt/media_rw/<SD-UUID>/archlinux`; duplicates removed |
| 8 | Start / stop environment | DONE | start from stopped & partial states, stop leaves SD mounted by Android |
| 9 | Live logs | DONE | session log tail shown (XFCE/Xvnc output seen) |
| 10 | Portable scripts (no hardcoded device paths) | DONE | assets piped over stdin to `sh -s`; path validation in Kotlin |
| 11 | Adopt existing install (`droidnix.json`) | DONE | written next to `arch.img`, image untouched, refuses overwrite |
| 12 | Kill/relaunch state (live observation) | DONE | force-stop while running → relaunch shows RUNNING, same loop device |
| 13 | Install wizard (download + extract + setup) | NEXT | Phase 3, no fake progress |
| 14 | Install location selection + probe | DONE | internal (`/data/media/0/DroidNix`) and SD both offered, write/loop probes, persists |
| 15 | Delete environment (typed confirmation) | DONE | removes the env folder only; refuses while mounted/running; image + real env intact in test |
| 16 | Integrated desktop display | NEXT | Phase 2, in-process RFB (VNC) client; no external app |
| 17 | Touch gesture translation | NEXT | Phase 2 |
| 18 | External keyboard/mouse | PLANNED | Phase 2 |
| 19 | Clipboard bridge | PLANNED | Phase 2 |
| 20 | Multiple environments | PLANNED | Phase 3 (discovery + model already multi-env) |
| 21 | Debian/Ubuntu/Alpine/Fedora | PLANNED | Phase 5, per-distro modules |
| 22 | LXQt/MATE/LXDE | PLANNED | Phase 5 |
| 23 | In-process X11 (no VNC) | PLANNED | Phase 5, NDK/EGL; see ROADMAP |
| 24 | Boot autostart | PLANNED | opt-in, Phase 4 |
