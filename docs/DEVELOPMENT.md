# Development

Practical notes for building, running and verifying DroidNix. Contribution rules
live in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Toolchain

| Tool | Version |
|---|---|
| JDK | 21 (required; Kotlin 2.0 tooling fails on newer JDKs) |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.13 (wrapper included) |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

Install Android SDK packages:

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

## Build

```sh
# local.properties is git-ignored
echo "sdk.dir=$ANDROID_HOME" > local.properties

./scripts/build-apk.sh release     # R8-minified, debug-signed, recommended
# or
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

`scripts/build-apk.sh` accepts:

- `DROIDNIX_JDK` — path to JDK 21 (overrides `JAVA_HOME`)
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` — SDK location
- `GRADLE_BIN` — alternate Gradle binary (defaults to `./gradlew`)

Debug builds are useful for logs but are much slower on low-end hardware. Use a
release build for any performance observation.

### Signing

Release builds use the standard Android debug keystore so they can be
sideloaded. This is a public, non-secret key and not a production identity. Do
not commit keystores or `keystore.properties` (they are git-ignored).

## Install and run

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n dev.droidnix/.MainActivity
adb logcat | grep -i droidnix
```

An emulator is enough for Compose/UI work, but **not** for the real path:
loop devices, ext4 mounts, chroots and Magisk root require a rooted ARM64
device.

## Project layout

```
app/src/main/java/dev/droidnix/
  core/       Shell, RootManager, DeviceInfo, Compatibility
  data/       Models, Settings, EnvironmentRepo
  engine/     ScriptStore, ChrootController
  ui/         MainActivity, MainScreen, MainViewModel, screens/, components/, theme/
app/src/main/assets/scripts/   dn-*.sh, executed as root over stdin
docs/                          architecture, inventory, roadmap, verification
scripts/build-apk.sh           local build entry point
```

## Shell script contract

Privileged logic lives in `app/src/main/assets/scripts/` and is executed as:

```
su -t 0 -c 'sh -s -- <quoted args>'   # script body on stdin
```

Rules:

- POSIX `sh` (Android toybox/mksh), `set -u`, quote every variable.
- Print machine-readable `key=value` lines where Kotlin parses the output.
- End successful runs with `done=1` where the caller checks for it.
- Fail with `ERROR: <reason>` and a non-zero exit code.
- Never interpolate user data into shell code; arguments arrive as `$1`, `$2`, …
- Re-validate paths inside the script; do not rely on the UI.
- Do not assume SD UUIDs, block device names or loop numbers.

When adding a script, also add its filename to `ScriptStore.SCRIPTS`, otherwise
the app's missing-script check will not know about it.

### Why stdin and `-t 0`?

- Magisk 31's domain cannot reliably read app-private files or write
  `/data/local`, so scripts are never extracted to disk.
- On the tested build, `su -c '<command>'` occasionally ran with a restricted
  view where `/mnt/media_rw` was unreadable; `sh -s` reading from stdin worked
  consistently.
- `-t 0` selects Magisk's global mount namespace so mounts persist between calls.

These constraints are documented in [INVENTORY.md](INVENTORY.md) and should not
be "simplified" without re-testing on the device.

## Verification workflow

Functional changes must be verified on a rooted ARM64 device. A reasonable
sequence:

```sh
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell su -t 0 -c 'id -u; magisk -V'

# start / stop through the UI, then observe the real state
adb shell su -t 0 -c 'pidof Xvnc; grep -c "archlinux/mnt " /proc/mounts'
adb shell sm list-volumes public

# UI performance (release build)
adb shell dumpsys gfxinfo dev.droidnix reset
# perform scroll gestures, then:
adb shell dumpsys gfxinfo dev.droidnix | grep -E "Janky frames|50th|90th|99th"
```

Record what was actually observed in [VERIFICATION.md](VERIFICATION.md),
including device, build and date. Never publish serial numbers, personal paths
or raw logs containing personal data.

## Versioning and releases

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update the version string in `SettingsScreen.kt` (About card).
3. Update `CHANGELOG.md` and the status tables in `docs/FEATURES.md`.
4. Build `./scripts/build-apk.sh release` and verify on a device.
5. Tag the commit (`git tag v0.1.x`) and, if publishing an APK, attach it to a
   GitHub Release rather than committing it.

APKs are intentionally not committed to the repository.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Kotlin/Compose compiler crash | Wrong JDK. Use JDK 21 via `DROIDNIX_JDK` or `JAVA_HOME`. |
| `SDK location not found` | Create `local.properties` with `sdk.dir=...`. |
| Stale Gradle daemon using a different JDK | Stop daemons (`./gradlew --stop`) and rebuild. |
| UI jank on a low-memory tablet | Build the release variant; debug builds are not representative. |
| Root prompt never appears | Magisk may queue a toast instead of a dialog. Open Magisk → Superuser, allow DroidNix, press **Check again**. |
