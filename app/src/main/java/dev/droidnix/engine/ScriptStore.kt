package dev.droidnix.engine

import android.content.Context

/**
 * Reads the shell scripts bundled in assets. They are never written to disk:
 * [dev.droidnix.core.Shell.rootScript] pipes them to `su -c 'sh -s'` over stdin,
 * which sidesteps the Magisk SELinux domain's inability to read/navigate app
 * files and removes versioned copies on storage entirely.
 */
class ScriptStore(private val context: Context) {

    fun read(name: String): String? = runCatching {
        context.assets.open("scripts/$name").bufferedReader().use { it.readText() }
    }.getOrNull()

    /** Null when everything is readable, otherwise the missing script name. */
    fun missingScript(): String? = SCRIPTS.firstOrNull { read(it).isNullOrBlank() }

    companion object {
        val SCRIPTS = listOf(
            "dn-start.sh",
            "dn-stop.sh",
            "dn-status.sh",
            "dn-discover.sh",
            "dn-diagnostics.sh",
            "dn-storage.sh",
            "dn-delete.sh",
        )
    }
}
