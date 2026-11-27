# DroidNix documentation

This directory contains the technical documentation. The top-level
[README](../README.md) is the project overview; feature status is deliberately
kept in one place.

## Index

| Document | What it covers |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layering, state model, storage/environment model, display engine interface, safety design |
| [FEATURES.md](FEATURES.md) | **Single source of truth** for what is done, built, next or planned |
| [ROADMAP.md](ROADMAP.md) | Phase 0–5 plan and known technical blockers |
| [INVENTORY.md](INVENTORY.md) | Original installation inventory and the hard-won device/kernel/Magisk constraints |
| [VERIFICATION.md](VERIFICATION.md) | On-device checklist and dated verification results |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build environment, scripts, testing workflow and release process |

Related top-level documents:

- [COMPATIBILITY.md](../COMPATIBILITY.md) — supported hardware, requirements, tested matrix
- [CONTRIBUTING.md](../CONTRIBUTING.md) — contribution rules and PR expectations
- [SECURITY.md](../SECURITY.md) — security model and vulnerability reporting
- [CHANGELOG.md](../CHANGELOG.md) — version history

## Status at a glance

- **Current build:** 0.2.0 (Phase 2). **Verified on the SM-T290 on 2026-11-27.**
- **Last device-verified release:** 0.2.0 (Phase 2).
- **Verified on hardware:** root/compatibility gates, disclaimer, discovery,
  adoption, start/stop, partial-state recovery, live status, session log,
  diagnostics, install-location probing, environment deletion, the in-process
  RFB viewer with touch/pinch gestures, IME and special keys, clipboard in both
  directions and rotation refit.
- **Not verified:** physical keyboard and mouse (no hardware was available);
  sticky Ctrl/Alt was not exercised on device.
- **Not implemented:** new-install wizard, additional distributions/desktops,
  in-process X11, audio, multi-monitor, DPI switching.

The viewer checklist and its 2026-11-27 results are at the end of
[VERIFICATION.md](VERIFICATION.md).

When a document and the code disagree, the code wins — then fix the document.
