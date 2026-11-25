package dev.droidnix.core

enum class Level { OK, WARN, FAIL }

data class Check(
    val title: String,
    val level: Level,
    val detail: String,
)

data class CompatibilityReport(
    val checks: List<Check>,
    val blocking: Boolean,
) {
    val failures: List<Check> get() = checks.filter { it.level == Level.FAIL }
    val warnings: List<Check> get() = checks.filter { it.level == Level.WARN }
}

object Compatibility {

    /** 1.8 GiB of *total* memory, as specified — not "available". */
    const val MIN_TOTAL_RAM_BYTES: Long = 1_932_735_283L // 1.8 * 1024^3

    /** Below this much *available* RAM we warn; the desktop idles near 1.2 GB. */
    const val WARN_AVAIL_RAM_BYTES: Long = 500L * 1024 * 1024

    fun evaluate(info: DeviceInfo): CompatibilityReport {
        val checks = mutableListOf<Check>()

        checks += if (info.rootUsable && info.magiskInstalled) {
            Check("Root access", Level.OK, info.magiskVersion?.let { "Magisk $it, su grants uid 0" } ?: "Root verified")
        } else {
            Check("Root access", Level.FAIL, "Magisk root is required")
        }

        checks += if (info.isArm64) {
            Check("CPU architecture", Level.OK, "arm64-v8a (AArch64)")
        } else {
            Check("CPU architecture", Level.FAIL, "${info.abiList} is not supported; only ARM64 is")
        }

        checks += if (info.totalRamBytes >= MIN_TOTAL_RAM_BYTES) {
            Check(
                "Memory",
                Level.OK,
                String.format("%.2f GB total (minimum 1.8 GB)", info.totalRamGb),
            )
        } else {
            Check(
                "Memory",
                Level.FAIL,
                String.format("%.2f GB total, 1.8 GB required", info.totalRamGb),
            )
        }

        checks += if (info.hasExt4) {
            Check("ext4 support", Level.OK, "Kernel can mount Linux images")
        } else {
            Check("ext4 support", Level.FAIL, "This kernel cannot mount ext4, so Linux images cannot run")
        }

        checks += if (info.freeLoopDevice != null) {
            Check("Loop devices", Level.OK, info.freeLoopDevice)
        } else {
            Check("Loop devices", Level.FAIL, "No free loop device — a reboot usually clears them")
        }

        checks += if (info.hasExfat) {
            Check("SD card filesystem", Level.OK, "exFAT supported by the kernel")
        } else {
            Check("SD card filesystem", Level.WARN, "No exFAT in the kernel; Android's own mount is used instead")
        }

        checks += Check(
            "SELinux",
            if (info.selinux.equals("Enforcing", ignoreCase = true)) Level.WARN else Level.OK,
            if (info.selinux.equals("Enforcing", ignoreCase = true)) {
                "${info.selinux} — Magisk must permit root mounts (verified at start)"
            } else info.selinux,
        )

        checks += if (info.availableRamBytes < WARN_AVAIL_RAM_BYTES) {
            Check(
                "Free memory now",
                Level.WARN,
                String.format("%.0f MB available; the desktop needs about 1.2 GB", info.availableRamBytes / 1048576.0),
            )
        } else {
            Check(
                "Free memory now",
                Level.OK,
                String.format("%.0f MB available", info.availableRamBytes / 1048576.0),
            )
        }

        checks += Check(
            "Internal storage",
            if (info.dataFreeBytes > 3L * 1024 * 1024 * 1024) Level.OK else Level.WARN,
            String.format("%.1f GB free", info.dataFreeBytes / (1024.0 * 1024 * 1024)),
        )

        return CompatibilityReport(checks, checks.any { it.level == Level.FAIL })
    }
}
