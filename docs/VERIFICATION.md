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


## Phase 2 viewer checklist — 2026-11-26 (build 0.2.0)

Status: **run on hardware 2026-11-27** with release build 0.2.0; results below.
Steps 1–3 and 5–9 passed. Step 4 passed for the on-screen keyboard; the
physical-hardware half was not testable (see results).

Prerequisites: environment running (`RUNNING`), Xvnc listening on loopback.

1. **Connect.** Home → **Open desktop**. The framed XFCE session appears within
   a couple of seconds. No external VNC app is installed or launched.
2. **Pointer.** Tap a panel item (opens it). Drag a window title bar (moves it).
   Long-press the desktop (context menu opens).
3. **Scroll/zoom.** Two-finger swipe scrolls a window; pinch zooms; after
   zooming, two-finger drag pans; **Fit** and **1:1** restore the view.
4. **Keyboard.** **Keys** opens the IME; type into a terminal inside XFCE.
   Enter, Backspace, Tab, Esc and arrows work; hardware keyboard works with the
   tablet docked.
5. **Clipboard.** Select text in XFCE → paste it into an Android app. Copy on
   Android → toolbar **Paste** → paste inside XFCE with Ctrl+V.
6. **Resize.** Rotate the tablet: the view refits and letterboxes; no crash and
   no lost connection. (The remote 1280×800 framebuffer is unchanged.)
7. **Failure path.** Stop the environment from a shell while the viewer is
   open: the viewer reports the disconnect and offers Retry/Back.
8. **Background.** Leave the app and return: the session reconnects and shows
   the live framebuffer.
9. **Performance.** On the release build, `dumpsys gfxinfo` over drag/scroll:
   record janky %, median frame time and p99. Compare with XFCE idle.

Record the observations and the exact build here. Do not mark the viewer DONE
until steps 1–8 pass on the SM-T290.

## Results — 2026-11-27, DroidNix 0.2.0

Device: SM-T290, LineageOS 22.2 / Android 15 (API 35), Magisk 31100. Release
build (`assembleRelease`, R8), installed over adb. Linux side: Arch + XFCE,
Xvnc 1.16.2 at 1280×800, VncAuth. All observations below were made on the
tablet screen, not on a host client.

| Step | Result | Evidence |
|---|---|---|
| 1 Connect | Pass | Home → Open desktop rendered the live `1280×800 · alarm@localhost` XFCE desktop in-process. |
| 2 Pointer | Pass | Panel click opened a terminal; double-click opened Thunar; title-bar drag moved a window; long-press opened the desktop context menu. |
| 3 Scroll/zoom | Pass | Two-finger swipe scrolled xfce4-terminal scrollback (263 → 278 → 300 and back); pinch zoomed about the centroid; two-finger drag panned while zoomed; Fit and 1:1 restored the view; Pan mode moved the surface. |
| 4 Keyboard (on-screen) | Pass | IME typed into xfce4-terminal; Enter and the special-key row worked. Hardware key map and physical keyboard were **not** tested (none available), and sticky Ctrl/Alt was not exercised in this run. |
| 5 Clipboard | Pass | Linux → Android: a marker owned on the X CLIPBOARD appeared in Android's clipboard overlay and pasted into the Settings search field as `paste-check-9`. Android → Linux: `markerB-55`, copied in Android Settings, was sent by toolbar **Paste** and read back from the X CLIPBOARD (which had no owner before the tap). |
| 6 Resize | Pass | Rotating portrait ↔ landscape refitted and letterboxed the view; the session stayed connected and the remote framebuffer stayed 1280×800. |
| 7 Failure path | Pass | `kill` on Xvnc while the viewer was open produced "Not connected / Could not open the desktop / The desktop connection was closed." with Retry and Back. After a crash the Home screen showed PARTIAL and "Start desktop" recovered the session. |
| 8 Background | Pass | Home then relaunch returned to the live desktop; the XFCE clock had advanced (12:52 → 12:55). |
| 9 Performance | Pass | `dumpsys gfxinfo` over 8 swipe passes: 386 frames, 14 janky (3.6 %), median 9 ms, p90 17 ms, p99 23 ms; GPU p99 10 ms. |

Defects found and fixed during this run (details in `CHANGELOG.md`):
- main-thread socket write crash; Hextile sub-rect off-by-one; toolbar overlay
  made the top 160 px unreachable; zoomed canvas painted over the toolbar;
  malformed server rectangles not clipped; TigerVNC clipboard needed
  `vncconfig -nowin` in the session; handshake wording used for mid-session
  disconnects; first-launch discovery race.

Notes:
- `vncconfig` survives a normal session start and is present after a crash
  restart (`ps` shows it alongside Xvnc).
- The verification harness initially used wrong `XSelectionRequestEvent`
  offsets, so an early clipboard run always answered "failure". After fixing
  the offsets the server was confirmed to emit `ServerCutText` to an
  independent RFB client, and then to the app.
- Still open: physical keyboard/mouse, mouse-wheel mapping from a physical
  mouse, immersive mode, DPI switching, non-Latin IME.
