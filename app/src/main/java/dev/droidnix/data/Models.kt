package dev.droidnix.data

import org.json.JSONObject

data class EnvConfig(
    val name: String = "Arch Linux",
    val distro: String = "arch",
    val desktop: String = "xfce",
    val vncPort: Int = 5901,
    val geometry: String = "1280x800",
    val dpi: Int = 160,
    val vncPassword: String = "archlinux",
    val created: Long = 0L,
) {
    fun toJson(): String = JSONObject().apply {
        put("schema", 1)
        put("name", name)
        put("distro", distro)
        put("desktop", desktop)
        put("vncPort", vncPort)
        put("geometry", geometry)
        put("dpi", dpi)
        put("vncPassword", vncPassword)
        put("created", created)
    }.toString(2)

    companion object {
        fun parse(text: String): EnvConfig? = runCatching {
            val o = JSONObject(text)
            EnvConfig(
                name = o.optString("name", "Arch Linux"),
                distro = o.optString("distro", "arch"),
                desktop = o.optString("desktop", "xfce"),
                vncPort = o.optInt("vncPort", 5901),
                geometry = o.optString("geometry", "1280x800"),
                dpi = o.optInt("dpi", 160),
                vncPassword = o.optString("vncPassword", "archlinux"),
                created = o.optLong("created", 0L),
            )
        }.getOrNull()
    }
}

/** An environment as found on disk. `config` is null for the legacy install. */
data class LinuxEnvironment(
    val rootDir: String,
    val mounted: Boolean,
    val hasImage: Boolean,
    val config: EnvConfig?,
    val legacy: Boolean,
) {
    val displayName: String get() = config?.name ?: "Arch Linux"
    val distroId: String get() = config?.distro ?: "arch"
    val desktopId: String get() = config?.desktop ?: "xfce"
    val desktopName: String get() = when (desktopId.lowercase()) {
        "xfce" -> "XFCE Desktop"
        "lxqt" -> "LXQt Desktop"
        "mate" -> "MATE Desktop"
        "lxde" -> "LXDE Desktop"
        else -> desktopId
    }
    val isRunning: Boolean get() = mounted
    val storageLabel: String get() = rootDir.substringBeforeLast('/', "")
}

/** A place Linux images can be stored, probed for real (write test, free loop). */
data class StorageTarget(
    val id: String,
    val label: String,
    val base: String,
    val fsType: String,
    val totalKb: Long,
    val freeKb: Long,
    val writable: Boolean,
    val freeLoop: String,
) {
    val usable: Boolean get() = writable && freeLoop != "none" && freeLoop.isNotBlank()
    val freeGb: Float get() = freeKb / 1048576f
    val totalGb: Float get() = totalKb / 1048576f
    val kind: String get() = if (id.startsWith("sd:")) "SD card" else "Internal"

    companion object {
        fun parse(line: String): StorageTarget? {
            if (!line.startsWith("target=")) return null
            val parts = line.split('|').mapNotNull { field ->
                val i = field.indexOf('=')
                if (i <= 0) null else field.substring(0, i) to field.substring(i + 1)
            }.toMap()
            val id = parts["target"] ?: return null
            val base = parts["base"] ?: return null
            return StorageTarget(
                id = id,
                label = parts["label"] ?: id,
                base = base,
                fsType = parts["fs"] ?: "unknown",
                totalKb = parts["total_kb"]?.toLongOrNull() ?: 0L,
                freeKb = parts["free_kb"]?.toLongOrNull() ?: 0L,
                writable = parts["writable"] == "1",
                freeLoop = parts["loop"] ?: "none",
            )
        }
    }
}

data class EnvironmentStatus(
    val running: Boolean,
    val mounted: Boolean,
    val xvnc: Boolean,
    val imagePresent: Boolean,
    val loopDevice: String,
    val distroId: String,
    val prettyName: String,
    val hasXfce: Boolean,
    val diskTotalKb: Long,
    val diskUsedKb: Long,
    val diskFreeKb: Long,
    val swapRunning: Boolean,
    val portListening: Boolean,
) {
    val diskUsedPercent: Float
        get() = if (diskTotalKb > 0) (diskUsedKb.toFloat() / diskTotalKb.toFloat()) else 0f

    companion object {
        fun from(map: Map<String, String>) = EnvironmentStatus(
            running = map["mounted"] == "1" && map["xvnc"] == "1",
            mounted = map["mounted"] == "1",
            xvnc = map["xvnc"] == "1",
            imagePresent = map["image"] == "present",
            loopDevice = map["loop"].orEmpty(),
            distroId = map["distro"].orEmpty().ifBlank { "arch" },
            prettyName = map["pretty"].orEmpty(),
            hasXfce = map["xfce"] == "1",
            diskTotalKb = map["disk_total_kb"]?.toLongOrNull() ?: 0L,
            diskUsedKb = map["disk_used_kb"]?.toLongOrNull() ?: 0L,
            diskFreeKb = map["disk_free_kb"]?.toLongOrNull() ?: 0L,
            swapRunning = map["swap_running"]?.toIntOrNull()?.let { it > 0 } ?: false,
            portListening = map["vnc_port"]?.toIntOrNull()?.let { it > 0 } ?: false,
        )
    }
}
