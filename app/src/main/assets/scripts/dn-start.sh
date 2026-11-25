#!/system/bin/sh
# DroidNix — start a Linux environment.
# Usage: dn-start.sh <ENV_DIR> [geometry] [dpi] [port]
# Must be run as root. Lives on internal storage, never inside the image.
set -u
ENV="${1:?environment directory required}"
GEOMETRY="${2:-1280x800}"
DPI="${3:-160}"
PORT="${4:-5901}"

IMG="$ENV/arch.img"
MNT="$ENV/mnt"
LOG="$ENV/logs"
SDDIR=$(dirname "$ENV")
SDMARK=/dev/.droidnix-owns-sd

say() { echo "$@"; }
fail() { say "ERROR: $*"; exit 1; }

case "$ENV" in
  /mnt/media_rw/*|/storage/*|/data/*) : ;;
  *) fail "refusing environment path outside known storage: $ENV" ;;
esac
case "$ENV" in *..*) fail "invalid path" ;; esac

mkdir -p "$MNT" "$LOG" || fail "cannot create $MNT"

# ---------- 1. storage ----------
if ! mountpoint -q "$SDDIR"; then
  UUID=$(basename "$SDDIR")
  SDBLK=""
  for v in $(sm list-volumes public 2>/dev/null | awk -v u="$UUID" '$3==u {print $1}'); do
    [ -b "/dev/block/vold/$v" ] && SDBLK="/dev/block/vold/$v"
  done
  if [ -z "$SDBLK" ]; then
    for d in /dev/block/mmcblk*p* /dev/block/sd*; do
      [ -b "$d" ] || continue
      case "$(blkid "$d" 2>/dev/null)" in
        *"$UUID"*) SDBLK="$d"; break ;;
      esac
    done
  fi
  [ -n "$SDBLK" ] || fail "storage not mounted and its block device was not found"
  mount -t exfat -o rw,noatime "$SDBLK" "$SDDIR" 2>/dev/null || \
    mount -o rw,noatime "$SDBLK" "$SDDIR" 2>/dev/null || \
    fail "cannot mount $SDBLK on $SDDIR"
  touch "$SDMARK"
fi

[ -f "$IMG" ] || fail "image not found: $IMG"

# ---------- 2. loop + rootfs ----------
LOOP=$(losetup -a 2>/dev/null | grep -F "$IMG" | cut -d: -f1 | head -1)
if [ -z "$LOOP" ]; then
  for i in 1 2 3; do
    LOOP=$(losetup -f 2>/dev/null) || { sleep 1; continue; }
    if losetup "$LOOP" "$IMG" 2>/dev/null; then break; fi
    LOOP=""
    sleep 1
  done
  [ -n "$LOOP" ] || fail "could not attach the image (losetup)"
fi
if ! mountpoint -q "$MNT"; then
  mount -t ext4 -o noatime,commit=30 "$LOOP" "$MNT" || fail "could not mount the Linux image"
fi

# ---------- 3. required kernel interfaces ----------
for d in dev proc sys; do
  mountpoint -q "$MNT/$d" || mount --bind "/$d" "$MNT/$d" || fail "bind mount /$d failed"
done
mkdir -p "$MNT/dev/shm" "$MNT/dev/pts" "$MNT/host"
mountpoint -q "$MNT/dev/shm" || mount -t tmpfs -o size=256m,mode=1777 tmpfs "$MNT/dev/shm"
mountpoint -q "$MNT/dev/pts" || mount --bind /dev/pts "$MNT/dev/pts"
mountpoint -q "$MNT/host"   || mount --bind "$SDDIR" "$MNT/host"

# ---------- 4. swap (optional, only if it exists) ----------
if [ -f "$MNT/swapfile" ] && ! grep -q '/swapfile' /proc/swaps 2>/dev/null; then
  chroot "$MNT" /sbin/swapon /swapfile 2>/dev/null || true
fi

# ---------- 5. session launcher inside the chroot ----------
cat > "$MNT/usr/local/bin/droidnix-session" <<EOF
#!/bin/bash
export XDG_RUNTIME_DIR=/tmp/runtime-alarm
mkdir -p "\$XDG_RUNTIME_DIR"; chmod 700 "\$XDG_RUNTIME_DIR"
export DISPLAY=:1
export LIBGL_ALWAYS_SOFTWARE=1
export QTWEBENGINE_DISABLE_SANDBOX=1
export QT_QPA_PLATFORM=xcb
pkill -f "Xvnc :1" 2>/dev/null
sleep 1
rm -f /tmp/.X11-unix/X1 /tmp/.X1-lock 2>/dev/null
Xvnc :1 -geometry $GEOMETRY -depth 24 -dpi $DPI \\
  -rfbport $PORT -localhost=1 -SecurityTypes VncAuth \\
  -PasswordFile "\$HOME/.vnc/passwd" -AlwaysShared \\
  > /tmp/xvnc.log 2>&1 &
for i in \$(seq 1 40); do [ -S /tmp/.X11-unix/X1 ] && break; sleep 0.3; done
eval "\$(dbus-launch --sh-syntax)"
exec startxfce4
EOF
chmod 755 "$MNT/usr/local/bin/droidnix-session"

# ---------- 6. already running? ----------
if pidof Xvnc >/dev/null 2>&1; then
  say "OK: already running"
  exit 0
fi

# ---------- 7. launch ----------
chroot "$MNT" /bin/bash -c 'rm -f /tmp/.X1-lock /tmp/.X11-unix/X1' 2>/dev/null
: > "$LOG/session.log"
nohup setsid chroot "$MNT" /usr/bin/su - alarm -c /usr/local/bin/droidnix-session \
  >>"$LOG/session.log" 2>&1 &

i=0
while [ $i -lt 40 ]; do
  if pidof Xvnc >/dev/null 2>&1; then
    say "OK: desktop is up on 127.0.0.1:$PORT"
    exit 0
  fi
  sleep 1; i=$((i+1))
done

say "FAILED: the desktop did not start. Last log lines:"
tail -15 "$LOG/session.log" 2>/dev/null
exit 1
