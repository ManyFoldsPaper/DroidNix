#!/system/bin/sh
# DroidNix — list candidate Linux environments on this device.
# One machine-readable line per candidate:
#   path=<dir>|img=0/1|cfg=0/1|mounted=0/1
# Directories are discovered, never assumed: any folder under a known storage
# root that contains arch.img (a Linux image) or droidnix.json (our config).
set -u

SEEN=""

emit() {
  dir="$1"
  real=$(readlink -f "$dir" 2>/dev/null || echo "$dir")
  case " $SEEN " in
    *" $real "*) return 0 ;;
  esac
  SEEN="$SEEN $real"
  img=0; cfg=0
  [ -f "$real/arch.img" ] && img=1
  [ -f "$real/droidnix.json" ] && cfg=1
  [ "$img" = 1 ] || [ "$cfg" = 1 ] || return 0
  m=0
  mountpoint -q "$real/mnt" 2>/dev/null && m=1
  echo "path=$real|img=$img|cfg=$cfg|mounted=$m"
}

scan_root() {
  base="$1"
  [ -d "$base" ] || return 0
  for d in "$base"/*/; do
    d=${d%/}
    [ -d "$d" ] || continue
    # environment directly in the storage root (legacy: <sd>/archlinux)
    # plus one level deeper for DroidNix's own layout (<sd>/DroidNix/<name>)
    for cand in "$d" "$d"/*/ "$d"/*/*/; do
      cand=${cand%/}
      [ -d "$cand" ] || continue
      emit "$cand"
    done
  done
}

scan_root /mnt/media_rw
# /storage/<uuid> is a second view of the same removable volume; scanning it
# would list every SD install twice. Internal storage is still covered.
scan_root /storage/emulated/0
scan_root /data/local/droidnix
scan_root /data/adb/droidnix
echo "done=1"
