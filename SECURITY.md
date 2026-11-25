# Security Policy

DroidNix runs with **root privileges by design**. Treat it, and every Linux
image you start with it, as fully trusted code running as root on your Android
device.

## Supported versions

| Version | Supported |
|---|---|
| `0.1.x` (current Phase 1) | Yes |
| Older / unversioned builds | No |

Only the latest `0.1.x` code in this repository is supported. Rebuild from
source or use a build whose checksum you can verify.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the issue, affected version, device/Android/Magisk versions and a
   reproduction that does not include personal data or secrets.

Reports are handled on a best-effort basis. There is no bug bounty and no SLA.
Please allow time for a fix before public disclosure.

## Security model and known risks

### Root is not a sandbox

DroidNix mounts an ext4 image and enters a **chroot** as root. A chroot isolates
the filesystem view, **not privileges**: `root` inside the Linux environment is
`root` on the Android device (subject only to the Android kernel and SELinux).
Consequences:

- **Only start images you trust.** Anyone who can modify `arch.img` (for
  example, by swapping the SD card) can execute arbitrary code as root the next
  time you start that environment.
- A malicious program inside the environment can access device block devices,
  Android data visible to root, and kernel interfaces unless SELinux blocks it.
- Do not use DroidNix on a device whose storage or Linux images you do not
  control.

### What the app does to reduce risk

- Root is used only through Magisk's `su`; DroidNix never installs root, unlocks
  a bootloader or touches boot/recovery.
- Environment paths are restricted to known storage roots, must be absolute and
  are rejected if they contain `..`.
- Privileged scripts are bundled in the APK and piped to `sh -s` over stdin;
  they are not extracted to writable storage, and arguments are single-quoted.
- Processes are identified by their root filesystem, never by UID (avoiding the
  UID 1000 collision with Android's `system` user).
- Deletion requires typing `DELETE`, is refused while the environment is
  mounted or running, and re-validates the path inside the shell script.
- No automatic formatting, repartitioning or wiping.
- The SD card is left to Android's `vold`; DroidNix does not mount it first.

### Network and telemetry

Phase 1 makes **no network requests** and collects **no telemetry**. The
`INTERNET` permission is declared for the planned Phase 3 installer and is not
used yet. There is no analytics, crash reporting or account system.

### Display server

The Linux desktop is served by TigerVNC bound to `127.0.0.1` (loopback only).
VncAuth with DES is legacy and weak by modern standards, and the project ships a
**well-known default VNC password** for convenience. Change it for any
environment you care about. DroidNix does not expose the VNC port to the network
and does not bundle a third-party viewer.

### Builds and signing

Local release builds are signed with the standard Android **debug keystore** so
they can be sideloaded. This is a public, non-secret key and is **not** a
production signing identity. There is no official store build. Verify the source
and build it yourself if you need a trustworthy APK.

### Secrets in the repository

The repository must never contain:

- signing keys or `keystore.properties`
- GitHub tokens, API keys or passwords
- `local.properties` (absolute SDK paths)
- device serial numbers or personal file listings
- raw logs containing personal data

If you find a committed secret, report it privately using the process above; do
not repeat it in an issue.

## Scope

In scope: DroidNix Kotlin code, bundled shell scripts, path handling, root
command construction, deletion logic and the app's manifest/permissions.

Out of scope: vulnerabilities in Arch Linux packages, TigerVNC/XFCE, Magisk,
Android itself or third-party VNC clients; device-specific firmware issues.
