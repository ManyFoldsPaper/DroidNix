# Changelog

All notable changes to DroidNix are documented here. The project follows
[Semantic Versioning](https://semver.org/) for its own version number.
There have been no public releases yet; all versions below are development
builds.

## [Unreleased]

Planned Phase 2 work: in-process RFB (VNC) viewer rendered in Compose, native
touch gesture translation, external keyboard/mouse and clipboard bridge.

## [0.1.5] — 2026-11-25

### Added

- **Install-location selection and probing.** Internal storage
  (`/data/media/0/DroidNix`) and every mounted SD card are offered as targets.
  Each is verified by writing and deleting a probe file, measuring exact free
  space from `/proc/mounts`, and checking for a free loop device. The choice
  persists across app restarts.
- **Delete environment.** A dedicated Danger Zone requires typing `DELETE`,
  refuses mounted/running environments and removes only the selected
  environment folder. The shell script independently validates the path and
  confirmation word.
- Discovery now scans one level deeper (`<volume>/DroidNix/<environment>`) so
  environments created by the future installer are found.

### Changed

- **Release builds are R8-minified.** This is now the recommended build and
  fixes severe UI lag on low-memory devices: measured ~3 % janky frames versus
  ~63 % in the debuggable build (median frame 12 ms vs 73 ms).
- Status polling interval increased to 6 seconds and now stops when the app is
  in the background, avoiding needless root-shell spawns.
- Dynamic colour scheme is memoised instead of recreated during recomposition.

### Fixed

- `dn-storage.sh` reports the exact filesystem from `/proc/mounts` instead of
  the generic `ext3/4` label from `stat`.

## [0.1.4] — 2026-11-25

### Added

- Diagnostics moved to its own state and a dedicated "Diagnostic report" card
  with an explicit **Collect** action.
- On-device verification record in `docs/VERIFICATION.md`.

### Changed

- **All root scripts are piped to `su -t 0 -c 'sh -s'` over stdin.** On Magisk
  31, `su -c '<command>'` intermittently ran with a restricted view where the
  SD card was unreadable; the stdin form works consistently. Scripts are no
  longer extracted to `/data/local` (not writable by the Magisk domain).
- All root calls use `-t 0` (Magisk's global mount namespace) so mounts survive
  between calls.
- Added `<queries>` for `com.topjohnwu.magisk` so "Open Magisk" works on
  Android 11+.

### Fixed

- `dn-status.sh` printed duplicate zeroes for idle swap/VNC counters.
- Discovery listed the same SD install twice via `/storage/<uuid>`; it now
  scans `/mnt/media_rw` plus internal storage and deduplicates canonical paths.
- SELinux status rendered a `DeviceInfo.toString()` dump instead of the state
  name.

## [0.1.3] — 2026-11-25

### Added

- Partial state detection: an environment whose image is mounted but whose
  Xvnc session is down is shown as **PARTIAL** with recovery actions instead of
  a misleading STOPPED.
- Separate diagnostics output from the live session log.

## [0.1.2] — 2026-11-24

### Added

- Adopt an existing installation by writing `droidnix.json` next to the image
  (never modifies, resizes or formats the image; refuses to overwrite).
- Root-based reads of `droidnix.json` and session logs, since the app process
  cannot read `/mnt/media_rw` directly.

### Fixed

- Script execution moved from `/data/local/droidnix` to app-private storage
  (later replaced entirely by stdin piping).
- Discovery deduplication and path canonicalisation.

## [0.1.0] — 2026-11-24

### Added

- Initial DroidNix application: Kotlin/Jetpack Compose Material 3 UI.
- Magisk root verification and non-dismissible root-required screen.
- Compatibility gates: ARM64, 1.8 GiB total RAM, ext4 and loop devices.
- First-run disclaimer with persisted acknowledgement.
- Environment discovery, start/stop and live status for the existing Arch
  Linux + XFCE installation.
- Bundled portable shell scripts (`dn-start.sh`, `dn-stop.sh`, `dn-status.sh`,
  `dn-discover.sh`, `dn-diagnostics.sh`).
- Device diagnostics and session log view.
