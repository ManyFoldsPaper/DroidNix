# Pull request

## Summary

<!-- What does this change and why? -->

## Verification

- Device / Android / Magisk / kernel:
- Build variant (release/debug) and version:
- What was tested:

<!-- Functional changes must be verified on a rooted ARM64 device. -->

## Checklist

- [ ] I did not commit secrets, signing keys, `local.properties` or personal logs.
- [ ] New privileged logic lives in `app/src/main/assets/scripts/` and is
      registered in `ScriptStore.SCRIPTS`.
- [ ] Paths, block devices and loop numbers are discovered at runtime.
- [ ] Processes are identified by `/proc/<pid>/root`, never by UID.
- [ ] Destructive actions require explicit confirmation and re-validate in shell.
- [ ] `docs/FEATURES.md`, `CHANGELOG.md` and related docs are updated.
- [ ] I have stated what I did **not** test.

## Related issues

<!-- Fixes #... -->
