#!/system/bin/sh
# DroidNix — report the real status of an environment as key=value lines.
# Usage: dn-status.sh <ENV_DIR>
set -u
ENV="${1:?environment directory required}"
IMG="$ENV/arch.img"
MNT="$ENV/mnt"

echo "env=$ENV"
[ -f "$IMG" ] && echo "image=present" || echo "image=missing"
[ -d "$ENV" ] && echo "dir=present" || echo "dir=missing"

if mountpoint -q "$MNT" 2>/dev/null; then echo "mounted=1"; else echo "mounted=0"; fi

LOOP=$(losetup -a 2>/dev/null | grep -F "$IMG" | cut -d: -f1 | head -1)
echo "loop=${LOOP:-}"

if pidof Xvnc >/dev/null 2>&1; then echo "xvnc=1"; else echo "xvnc=0"; fi

if [ -d "$MNT/root" ]; then
  set -- $(df -k "$MNT" 2>/dev/null | tail -1)
  echo "disk_total_kb=${2:-0}"
  echo "disk_used_kb=${3:-0}"
  echo "disk_free_kb=${4:-0}"
  echo "distro=$(cat "$MNT/etc/os-release" 2>/dev/null | sed -n 's/^ID=//p' | tr -d '\"')"
  echo "pretty=$(cat "$MNT/etc/os-release" 2>/dev/null | sed -n 's/^PRETTY_NAME=//p' | tr -d '\"')"
  [ -x "$MNT/usr/bin/startxfce4" ] && echo "xfce=1" || echo "xfce=0"
fi

echo "swap_running=$(grep -c '/swapfile' /proc/swaps 2>/dev/null || true)"
echo "vnc_port=$(ss -ltn 2>/dev/null | grep -c ':5901' || true)"
echo "done=1"
