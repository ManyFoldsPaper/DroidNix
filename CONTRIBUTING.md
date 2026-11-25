# Contributing to DroidNix

Thanks for your interest. DroidNix is an experimental, root-privileged Android
app that manages real Linux chroots. Contributions are welcome, but the project
has a few non-negotiable rules.

## Ground rules

1. **No invented features.** A feature is only "done" when it runs on a real
   device and the result is recorded. `docs/FEATURES.md` is the source of truth.
2. **Do not break existing installations.** The legacy SD layout
   (`<volume>/archlinux/{arch.img,mnt,logs,...}`) is first-class and must keep
   working. Adopting must never resize, format or rewrite an image.
3. **Scripts are data, not Kotlin strings.** Privileged logic belongs in
   `app/src/main/assets/scripts/*.sh` and must be piped to root over stdin. Add
   a script to `ScriptStore.SCRIPTS` when you add a file.
4. **No hardcoded device paths.** SD UUIDs, `/mnt/media_rw/<uuid>` paths, block
   device names and loop numbers must be discovered at runtime.
5. **Never identify processes by UID.** Arch's `alarm` is UID 1000, the same as
   Android's `system`. Use `/proc/<pid>/root` against the environment mount.
6. **Destructive actions need explicit, typed confirmation** and a second check
   in the shell script itself. Never format, repartition or wipe automatically.
7. **No secrets.** Do not commit signing keys, tokens, `local.properties`,
   personal paths, device serial numbers, raw personal logs or VNC passwords.

## Development environment

- JDK 21
- Android SDK 35: `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`
- A rooted ARM64 device for anything beyond UI work (the emulator cannot run
  the loop-mount/chroot path)

```sh
git clone https://github.com/ManyFoldsPaper/DroidNix.git
cd DroidNix
echo "sdk.dir=$ANDROID_HOME" > local.properties
./scripts/build-apk.sh release
adb install -r app/build/outputs/apk/release/app-release.apk
```

`local.properties` is ignored by Git. Release builds are R8-minified and signed
with the Android debug keystore for sideloading only.

## Project layout

```
app/src/main/java/dev/droidnix/
  core/      Shell, RootManager, DeviceInfo, Compatibility
  data/      models, settings, EnvironmentRepo
  engine/    ScriptStore, ChrootController
  ui/        Compose screens, components, theme, MainViewModel
app/src/main/assets/scripts/   POSIX shell executed as root over stdin
docs/                          architecture, inventory, roadmap, verification
```

## Testing

UI-only changes can be checked on any device or emulator. Anything touching
mounts, loop devices, root, storage or scripts must be tested on a rooted ARM64
device.

Minimum manual checklist for a functional change:

1. Root gate: deny root → blocking screen; grant → app continues.
2. Discovery finds the existing environment (and does not duplicate it).
3. Start: status becomes RUNNING, Xvnc is alive, mount and loop are present.
4. Stop: mounts and loop are released; Android still shows the SD as mounted;
   no "Issue with SD card" notification.
5. Kill the app while running, relaunch: state is still observed correctly.
6. Deleting an environment still refuses while it is running.

Record results in `docs/VERIFICATION.md` (device, build, date, what was
observed). Do not include serial numbers, personal file paths or full logs.

## Code style

- Kotlin: official style (`kotlin.code.style=official`), 4-space indent.
- Compose: Material 3, small composables, no business logic in the UI layer.
- Shell: POSIX `sh` suitable for Android's toybox/mksh; quote every variable;
  fail loudly with `ERROR:`.
- Keep changes focused. Avoid drive-by reformatting of unrelated files.

## Pull requests

- Describe the user-visible change and the device/build used to verify it.
- Link the relevant `docs/FEATURES.md` row if you change feature status.
- Update `docs/` and `CHANGELOG.md` for user-visible changes.
- State explicitly what you did **not** test.

## Reporting bugs

Open a GitHub issue with:

- Device model, Android version, Magisk version, kernel version.
- DroidNix version (Settings → About).
- What you expected and what happened.
- Relevant excerpts only — remove serial numbers, personal paths, tokens and
  VNC passwords.

Security issues must go through [SECURITY.md](SECURITY.md), not public issues.

## License

By contributing you agree that your contributions are licensed under the
Apache License 2.0, the same license as the project.
