#!/system/bin/sh
# DroidNix — device diagnostics (key=value). Read-only.
set -u
echo "model=$(getprop ro.product.model)"
echo "device=$(getprop ro.product.device)"
echo "android=$(getprop ro.build.version.release)"
echo "sdk=$(getprop ro.build.version.sdk)"
echo "abi=$(getprop ro.product.cpu.abi)"
echo "abilist=$(getprop ro.product.cpu.abilist)"
echo "kernel=$(uname -r)"
echo "arch=$(uname -m)"
echo "selinux=$(getenforce 2>/dev/null)"
echo "mem_total_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
echo "mem_avail_kb=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"

ROOT_UID=$(id -u 2>/dev/null || echo 1)
echo "root_uid=$ROOT_UID"
MAGISK=$(magisk -V 2>/dev/null || echo "")
echo "magisk=${MAGISK:-none}"
echo "magisk_dir=$([ -d /data/adb/magisk ] && echo 1 || echo 0)"

for fs in ext4 exfat; do
  if grep -qw "$fs" /proc/filesystems; then echo "fs_$fs=1"; else echo "fs_$fs=0"; fi
done
LOOP=$(losetup -f 2>/dev/null || echo "")
echo "loop_free=${LOOP:-none}"

DF=$(df -k /data 2>/dev/null | tail -1)
set -- $DF
echo "data_total_kb=${2:-0}"; echo "data_free_kb=${4:-0}"

SD=$(df -k /mnt/media_rw/* 2>/dev/null | tail -1)
set -- $SD
echo "sd_total_kb=${2:-0}"; echo "sd_free_kb=${4:-0}"
echo "done=1"
