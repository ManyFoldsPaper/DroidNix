#!/system/bin/sh
# DroidNix — stop a Linux environment.
# Usage: dn-stop.sh <ENV_DIR>
#
# Processes are identified by /proc/<pid>/root pointing at the image.
# Never by UID: Arch's "alarm" (1000) and Android's "system" (1000) collide.
set -u
ENV="${1:?environment directory required}"
IMG="$ENV/arch.img"
MNT="$ENV/mnt"
SDDIR=$(dirname "$ENV")
SDMARK=/dev/.droidnix-owns-sd
START=$(date +%s)

if ! mountpoint -q "$MNT"; then
  echo "OK: not running"
  exit 0
fi

NAMES='Xvnc|xfce4-session|xfwm4|xfdesktop|xfce4-panel|xfsettingsd|xfce4-power-manager|xfce4-appfinder|xfce4-terminal|dbus-daemon|dbus-launch|droidnix-session|arch-session|Thunar|thunar|dolphin|falkon|baloo_file|baloo_file_extractor|ssh-agent|gpg-agent|kded6|kdeinit6|kioslave6|xdg-open'

PIDS=$(grep -lE "^($NAMES)$" /proc/[0-9]*/comm 2>/dev/null | sed 's|^/proc/||; s|/comm$||')

kill_env() {
  sig=$1
  for pid in $PIDS; do
    r=$(readlink "/proc/$pid/root" 2>/dev/null) || continue
    case "$r" in
      "$MNT"|"$MNT"/*) kill "-$sig" "$pid" 2>/dev/null ;;
    esac
  done
}

kill_env TERM
sleep 1
kill_env KILL
sleep 1

# swap
if grep -q '/swapfile' /proc/swaps 2>/dev/null; then
  chroot "$MNT" /sbin/swapoff /swapfile 2>/dev/null || true
fi

unmount_all() {
  for m in "$MNT/host" "$MNT/dev/shm" "$MNT/dev/pts" "$MNT/dev" "$MNT/proc" "$MNT/sys" "$MNT"; do
    mountpoint -q "$m" && umount "$m" 2>/dev/null
  done
}
sync
unmount_all

if mountpoint -q "$MNT"; then
  # last resort: any process whose root is the image (slow scan)
  for p in /proc/[0-9]*; do
    pid=${p#/proc/}
    r=$(readlink "$p/root" 2>/dev/null) || continue
    case "$r" in "$MNT"|"$MNT"/*) kill -9 "$pid" 2>/dev/null ;; esac
  done
  sleep 1
  unmount_all
fi
sync

for t in 1 2 3; do
  for l in $(losetup -a 2>/dev/null | grep -F "$IMG" | cut -d: -f1); do
    losetup -d "$l" 2>/dev/null
  done
  losetup -a 2>/dev/null | grep -qF "$IMG" || break
  sleep 1
done

if mountpoint -q "$MNT"; then
  echo "FAILED: the image is still mounted. Processes still using it:"
  for p in /proc/[0-9]*; do
    pid=${p#/proc/}
    r=$(readlink "$p/root" 2>/dev/null) || continue
    case "$r" in "$MNT"|"$MNT"/*) echo "  $pid $(cat "/proc/$pid/comm" 2>/dev/null)" ;; esac
  done
  exit 1
fi

if [ -e "$SDMARK" ]; then
  rm -f "$SDMARK"
  # this script lives on internal storage, so a direct umount works
  umount "$SDDIR" 2>/dev/null && echo "SD card released"
fi

echo "OK: stopped in $(( $(date +%s) - START ))s"
