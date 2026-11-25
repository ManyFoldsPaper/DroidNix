#!/system/bin/sh
# DroidNix — list possible install locations with real probes.
# Output, one target per line:
#   target=<id>|label=<text>|base=<dir>|fs=<type>|total_kb=<n>|free_kb=<n>|writable=0/1|loop=<dev|none>
# Nothing here assumes a mount path: volumes are scanned under /mnt/media_rw,
# and every candidate is probed by actually creating and deleting a file.
set -u

# Exact filesystem of a path: longest matching /proc/mounts entry.
fstype_for() {
  p="$1"
  best=""
  besttype="unknown"
  while read -r _dev mnt type _rest; do
    case "$p" in
      "$mnt"|"$mnt"/*)
        if [ ${#mnt} -gt ${#best} ]; then
          best="$mnt"
          besttype="$type"
        fi
        ;;
    esac
  done < /proc/mounts
  echo "$besttype"
}

probe() {
  id="$1"
  label="$2"
  base="$3"
  # Never advertise a path we cannot create. mkdir -p also creates DroidNix/
  # on a fresh volume, which is exactly where environments will live.
  mkdir -p "$base" 2>/dev/null || return 0
  [ -d "$base" ] || return 0

  fs=$(fstype_for "$base")
  total=0
  free=0
  dfout=$(df -Pk "$base" 2>/dev/null | awk 'NR==2 {print $2" "$4}')
  if [ -n "${dfout:-}" ]; then
    total=${dfout%% *}
    free=${dfout##* }
  fi

  w=0
  t="$base/.dn-probe-$$"
  if mkdir "$t" 2>/dev/null; then
    if echo droidnix >"$t/probe" 2>/dev/null; then w=1; fi
    rm -rf "$t" 2>/dev/null
  fi

  lo=$(losetup -f 2>/dev/null || true)
  [ -n "$lo" ] || lo=none

  echo "target=$id|label=$label|base=$base|fs=$fs|total_kb=$total|free_kb=$free|writable=$w|loop=$lo"
}

# Removable volumes first: they are what most people want for a 10 GB image.
for v in /mnt/media_rw/*/; do
  v=${v%/}
  [ -d "$v" ] || continue
  mountpoint -q "$v" 2>/dev/null || continue
  uuid=$(basename "$v")
  probe "sd:$uuid" "SD card ($uuid)" "$v/DroidNix"
done

# Internal storage. /data/media/0 is the underlying ext4 view of /sdcard,
# so a loop-mounted image works there; /sdcard itself is FUSE and is not used.
probe "internal" "Internal storage" "/data/media/0/DroidNix"

echo "done=1"
