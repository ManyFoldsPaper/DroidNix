package dev.droidnix.data

import android.util.Base64
import dev.droidnix.core.Shell
import dev.droidnix.engine.ScriptStore

/**
 * Finds and inspects environments by running the bundled shell scripts.
 * Nothing is cached across calls: mount state, loop devices and Xvnc are
 * observed live, so the app is correct even if it was killed and restarted.
 */
class EnvironmentRepo(
    private val scripts: ScriptStore,
) {

    fun discover(): List<LinuxEnvironment> {
        val script = scripts.read("dn-discover.sh") ?: return emptyList()
        val result = Shell.rootScript(script, timeoutSeconds = 45)
        return result.stdout.lines().mapNotNull { line ->
            if (!line.startsWith("path=")) return@mapNotNull null
            val parts = line.split('|').mapNotNull { field ->
                val i = field.indexOf('=')
                if (i <= 0) null else field.substring(0, i) to field.substring(i + 1)
            }.toMap()
            val path = parts["path"] ?: return@mapNotNull null
            LinuxEnvironment(
                rootDir = path,
                mounted = parts["mounted"] == "1",
                hasImage = parts["img"] == "1",
                config = if (parts["cfg"] == "1") readConfig(path) else null,
                legacy = parts["cfg"] != "1",
            )
        }.distinctBy { it.rootDir }
    }

    fun status(envDir: String): EnvironmentStatus? {
        val script = scripts.read("dn-status.sh") ?: return null
        val result = Shell.rootScript(script, listOf(envDir), timeoutSeconds = 40)
        if (!result.stdout.contains("done=1")) return null
        val map = result.stdout.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
        return EnvironmentStatus.from(map)
    }

    fun diagnostics(): String {
        val script = scripts.read("dn-diagnostics.sh") ?: return "Scripts are missing from the APK"
        return Shell.rootScript(script, timeoutSeconds = 60).output
    }

    /** Probes every storage location the app could install into. */
    fun storageTargets(): List<StorageTarget> {
        val script = scripts.read("dn-storage.sh") ?: return emptyList()
        val result = Shell.rootScript(script, timeoutSeconds = 45)
        return result.stdout.lines().mapNotNull { StorageTarget.parse(it) }
    }

    fun readLog(envDir: String, lines: Int = 200): String {
        val path = "$envDir/logs/session.log"
        val result = Shell.root("tail -n $lines ${Shell.quote(path)} 2>/dev/null", 25)
        return result.stdout.ifBlank { "No log yet." }
    }

    fun readConfig(envDir: String): EnvConfig? {
        // The image folder is on removable storage, which the app process cannot
        // read directly, so the config is fetched through the root shell.
        val result = Shell.root("cat ${Shell.quote("$envDir/droidnix.json")} 2>/dev/null", 20)
        if (!result.ok || result.stdout.isBlank()) return null
        return runCatching { EnvConfig.parse(result.stdout) }.getOrNull()
    }

    /**
     * Writes droidnix.json next to an existing image so DroidNix can manage it.
     * Never touches the image itself; refuses to overwrite an existing config.
     */
    fun writeConfig(envDir: String, config: EnvConfig): Result<Unit> = runCatching {
        val target = "$envDir/droidnix.json"
        val exists = Shell.root("test -e ${Shell.quote(target)} && echo exists", 15)
        if (exists.stdout.contains("exists")) error("droidnix.json already exists in this folder")
        val b64 = Base64.encodeToString(config.toJson().toByteArray(), Base64.NO_WRAP)
        val result = Shell.root(
            "echo ${Shell.quote(b64)} | base64 -d > ${Shell.quote(target)} && echo written",
            25,
        )
        if (!result.stdout.contains("written")) error(result.output.ifBlank { "could not write config" })
    }
}
