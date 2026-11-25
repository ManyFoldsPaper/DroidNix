#!/system/bin/sh
# DroidNix — delete a Linux environment and all of its data.
# Usage: dn-delete.sh <ENV_DIR> DELETE
#
# This is destructive and intentionally hard to trigger by accident:
#  - the caller must pass the literal confirmation word DELETE
#  - the path must sit inside a known storage root, not be a mount point
#  - the folder must look like an environment (arch.img or droidnix.json)
#  - a mounted/running environment is refused; stop it first
# It never touches Android storage, never formats anything and never deletes
# outside the environment folder.
set -u
ENV="${1:?environment directory required}"
CONFIRM="${2:-}"

real=$(readlink -f "$ENV" 2>/dev/null || echo "$ENV")
[ -d "$real" ] || { echo "ERROR: folder not found: $real"; exit 2; }

case "$real" in
  /mnt/media_rw/*/*|/data/media/0/*|/storage/emulated/0/*) ;;
  *) echo "ERROR: refusing to delete outside a storage root: $real"; exit 2 ;;
esac

if mountpoint -q "$real" 2>/dev/null; then
  echo "ERROR: that path is a mounted volume, not an environment folder"
  exit 2
fi

if [ ! -f "$real/arch.img" ] && [ ! -f "$real/droidnix.json" ]; then
  echo "ERROR: not a DroidNix environment (no arch.img or droidnix.json): $real"
  exit 2
fi

[ "$CONFIRM" = "DELETE" ] || { echo "ERROR: confirmation word missing"; exit 2; }

if mountpoint -q "$real/mnt" 2>/dev/null; then
  echo "ERROR: the environment is mounted. Stop it before deleting."
  exit 3
fi

# Processes still rooted in this image (Xvnc, XFCE, ...) mean it is running.
for r in /proc/[0-9]*/root; do
  target=$(readlink "$r" 2>/dev/null) || continue
  case "$target" in
    "$real"/mnt|"$real"/mnt/*)
      echo "ERROR: processes are still running from this environment. Stop it first."
      exit 3
      ;;
  esac
done

# Leftover mounts below the environment (should not exist after a clean stop).
grep " $real/" /proc/mounts 2>/dev/null | awk '{print $2}' | sort -r |
  while IFS= read -r m; do
    [ -n "$m" ] && umount "$m" 2>/dev/null
  done

# Detach any loop device still backed by this image.
losetup -a 2>/dev/null | grep -F "$real/arch.img" | cut -d: -f1 |
  while IFS= read -r lo; do
    [ -n "$lo" ] && losetup -d "$lo" 2>/dev/null
  done

if [ -f "$real/swapfile" ]; then
  swapoff "$real/swapfile" 2>/dev/null
fi

before=$(du -sk "$real" 2>/dev/null | awk '{print $1}')
[ -n "${before:-}" ] || before=0

rm -rf "$real" 2>/dev/null
if [ -e "$real" ]; then
  echo "ERROR: could not remove everything in $real"
  exit 4
fi

echo "OK: deleted $real (about $((before / 1024)) MB freed)"
echo "done=1"
