package dev.droidnix.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/** Hardware and kernel facts, measured on this device. */
data class DeviceInfo(
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val abi: String,
    val abiList: String,
    val kernel: String,
    val arch: String,
    val selinux: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val dataTotalBytes: Long,
    val dataFreeBytes: Long,
    val hasExt4: Boolean,
    val hasExfat: Boolean,
    val freeLoopDevice: String?,
    val magiskVersion: String?,
    val rootUsable: Boolean,
    val magiskInstalled: Boolean,
) {
    val isArm64: Boolean get() = abiList.split(',').any { it.trim() == "arm64-v8a" }
    val totalRamGb: Double get() = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
}

object DeviceInfoReader {

    fun read(context: Context, root: RootStatus): DeviceInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val data = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()

        val diag = Shell.root(
            "getenforce 2>/dev/null; uname -r; uname -m; " +
                "grep -qw ext4 /proc/filesystems && echo ext4=1 || echo ext4=0; " +
                "grep -qw exfat /proc/filesystems && echo exfat=1 || echo exfat=0; " +
                "losetup -f 2>/dev/null || true",
            timeoutSeconds = 30,
        )
        val lines = diag.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun flag(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.substringAfter('=') == "1"

        return DeviceInfo(
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            abiList = Build.SUPPORTED_ABIS.joinToString(","),
            kernel = lines.getOrNull(1) ?: "unknown",
            arch = lines.getOrNull(2) ?: "unknown",
            selinux = lines.firstOrNull() ?: "unknown",
            totalRamBytes = mi.totalMem,
            availableRamBytes = mi.availMem,
            dataTotalBytes = data?.let { it.blockCountLong * it.blockSizeLong } ?: 0,
            dataFreeBytes = data?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0,
            hasExt4 = flag("ext4="),
            hasExfat = flag("exfat="),
            freeLoopDevice = lines.firstOrNull { it.startsWith("/dev/block/loop") },
            magiskVersion = root.magiskVersion,
            rootUsable = root.usable,
            magiskInstalled = root.magiskInstalled,
        )
    }
}
