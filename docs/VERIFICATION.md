# On-device verification checklist (Phase 1)

Run when the SM-T290 is connected with USB debugging. Nothing in
`docs/FEATURES.md` moves from BUILT to DONE until the matching line passes.

## Install

```sh
adb devices -l                      # expect gtowifi / SM-T290 authorized
adb install -r app/build/outputs/apk/release/app-release.apk
```

Expected: `Success`. Launch **DroidNix** from the app drawer (not adb, so the
real cold-start path is exercised).

## Gates

1. **Disclaimer** — first launch only. Continue stays disabled until the
   acknowledgement box is ticked. Kill the app mid-way: it must show again.
2. **Root** — allow the Magisk prompt. Home must appear, Settings → Root shows
   uid 0 and `Magisk 31.x`.
   - Deny root in Magisk → Superuser, relaunch: the non-dismissible "Root
     access required" screen appears; **Check again** recovers after re-allowing.
   - No `su` file is written or installed by the app.
3. **Compatibility** — Settings shows measured totals: RAM ≈ 1.80 GB (passes
   the 1.8 GiB floor), arm64-v8a, SELinux Enforcing as WARN, ext4 present,
   free loop device listed.
4. **Blocking rule** — temporarily lower `MIN_TOTAL_RAM_BYTES` in a scratch
   build to confirm the blocking screen has no bypass button (do not ship it).

## Environment

5. **Discovery** — SD card mounted by Android (never mount it yourself first).
   My environments lists `/mnt/media_rw/<SD-UUID>/archlinux`, image present,
   "legacy" until adopted.
6. **Adopt** — Install Linux → Adopt. `droidnix.json` appears next to
   `arch.img`; the image mtime must not change. Adopting twice must refuse.
7. **Start** — Home → Start Linux. Within ~40 s status becomes RUNNING; the
   log card shows Xvnc output. Verify from a second shell:
   `adb shell su -c 'mountpoint -q /mnt/media_rw/<SD-UUID>/archlinux/mnt && echo mounted'`.
8. **No regression of Termux workflow** — with DroidNix stopped, `arch` and
   `arch-stop` still work exactly as before.
9. **Stop** — Stop Linux. RUNNING → STOPPED in a few seconds, `losetup -a` no
   longer lists the image, `/proc/swaps` no longer lists `/swapfile`, Android
   does not restart or show "Issue with SD card".
10. **State is not cached** — kill the app while running (Recents → swipe),
    relaunch: Home still reports RUNNING; start is not required again.
11. **Diagnostics** — Settings → Diagnostics produces the report with no
    `su: not found` or permission errors.

## Logs to collect if something fails

```sh
adb logcat -d | grep -iE "droidnix|AndroidRuntime" > logcat.txt
adb shell su -c 'cat /mnt/media_rw/<SD-UUID>/archlinux/logs/session.log'
```

Keep your own backup of the original scripts and images outside this
repository until every line above passes. Never commit personal backups.

## Results — 2026-11-25, SM-T290, DroidNix 0.1.4

Passed on the device:

- Install via `adb install -r`, cold start, disclaimer acknowledgement.
- Root gate: first attempt **rejected by Magisk** with no visible dialog →
  "Root access required" shown with "Permission denied"; after Magisk granted
  the retry, Home loaded with `Magisk 31100, su grants uid 0`.
- Discovery of `/mnt/media_rw/<SD-UUID>/archlinux` (legacy), no duplicate
  `/storage/<uuid>` entry after the scanner fix.
- Adopt wrote `droidnix.json` (199 bytes) and the UI switched to "present";
  the 10 GB image was not touched.
- Start from stopped state: RUNNING in <12 s, `pidof Xvnc` non-zero,
  `archlinux/mnt` in `/proc/mounts`, RFB VncAuth succeeded and XFCE rendered
  (1280×800, `alarm@localhost`, Raw frames, 77 % non-black).
- Start from PARTIAL state (image mounted, desktop down): "Start desktop"
  recovered the session.
- Stop: Xvnc gone, mount gone, loop released, `sm list-volumes` still shows
  `public:179,65 mounted` — Android keeps the card, no "Issue with SD card".
- Force-stop while running → relaunch still reports RUNNING with the same loop.
- Diagnostics report: `mem_total_kb=1888196`, `selinux=Enforcing`,
  `fs_ext4=1`, `fs_exfat=1`, `magisk=31100`, SD totals, `done=1`.

Still open:

- Low-RAM blocking screen and non-ARM64 blocking screen (needs another device
  or a scratch build with a raised threshold).
- Termux `arch` / `arch-stop` regression run (scripts were not modified).
- On-device display is still Phase 2; verification used a host RFB client
  through `adb forward`, which is the protocol the in-app client will speak.

## Results — 2026-11-25, DroidNix 0.1.5 (release build)

- **UI performance.** `dumpsys gfxinfo` over repeated fling/scroll passes:
  debug build 81 frames, 63 % janky, median 73 ms, p99 2300 ms; R8 release
  build 477 frames, 3.1 % janky, median 12 ms, p99 38 ms, 1 missed vsync.
  Polling moved from 4 s to 6 s and now stops in `ON_STOP`.
- **Install location.** Install tab probes two targets: SD
  `/mnt/media_rw/<SD-UUID>/DroidNix` (exfat, 24.7/58.2 GB) and internal
  `/data/media/0/DroidNix` (ext4, 7.4/22.4 GB). Both returned `writable=1`
  and a free loop device. Selecting the SD target persisted across a
  force-stop/relaunch (`SELECTED` restored). A 16 MB image was created,
  mkfs.ext4'd, loop-mounted and written inside `/data/media/0` as a
  standalone probe — internal storage can hold loop images.
- **Delete.** Created a throwaway environment
  `/mnt/media_rw/<SD-UUID>/DroidNix/delete-me` (50 MB `arch.img` + config +
  log). The delete dialog required typing `DELETE`; the script refused
  without it and refused the install-root path. Confirming removed exactly
  that folder; the real 10 GB `archlinux/arch.img` was unchanged.
- **Regression.** Start after the changes: RUNNING, `Xvnc` pid present, RFB
  VncAuth handshake succeeded; stop left `Xvnc`/mount gone and
  `sm list-volumes public` still `mounted <SD-UUID>`.

Notes:
- Discovery now also scans `<volume>/<folder>/<env>` so environments created
  by the future wizard under `DroidNix/` are found.
- The system IME inserted a suggestion while typing the confirmation word in
  an automated adb run; the button correctly stays disabled until the field
  is exactly `DELETE`. This is UI input noise, not an app bug.
