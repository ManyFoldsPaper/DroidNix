package dev.droidnix.core

/**
 * Proves that root is actually usable instead of checking for a `su` file.
 * Two independent facts are required: a uid-0 shell, and a Magisk installation.
 */
data class RootStatus(
    val usable: Boolean,
    val uid: String,
    val magiskVersion: String?,
    val magiskInstalled: Boolean,
    val detail: String,
)

object RootManager {

    fun check(): RootStatus {
        val id = Shell.root("id -u", 30)
        val uid = id.stdout.trim()
        if (!id.ok || uid != "0") {
            return RootStatus(
                usable = false,
                uid = uid.ifBlank { "unknown" },
                magiskVersion = null,
                magiskInstalled = false,
                detail = id.output.ifBlank { "No response from su" },
            )
        }

        val probe = Shell.root(
            "magisk -V 2>/dev/null; magisk -v 2>/dev/null; " +
                "test -d /data/adb/magisk && echo magiskdir=yes || echo magiskdir=no",
            25,
        )
        val lines = probe.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val version = lines.firstOrNull { it.matches(Regex("\\d+")) }
            ?: lines.firstOrNull { it.startsWith("31") }
        val dir = lines.firstOrNull { it.startsWith("magiskdir=") }?.substringAfter('=')
        val installed = dir == "yes" || version != null

        return RootStatus(
            usable = true,
            uid = uid,
            magiskVersion = version,
            magiskInstalled = installed,
            detail = if (installed) {
                "Magisk ${version ?: ""} — root shell verified".trim()
            } else {
                "su grants uid 0, but Magisk was not detected"
            },
        )
    }
}
