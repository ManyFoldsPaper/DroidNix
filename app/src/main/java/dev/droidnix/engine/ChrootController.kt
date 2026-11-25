package dev.droidnix.engine

import dev.droidnix.core.Shell
import dev.droidnix.core.ShellResult

/**
 * Starts and stops environments by running the bundled scripts as root.
 * Every call is a fresh observation of the device; no state is remembered.
 */
class ChrootController(private val scripts: ScriptStore) {

    data class ActionResult(
        val ok: Boolean,
        val message: String,
    )

    fun start(
        envDir: String,
        geometry: String,
        dpi: Int,
        port: Int,
        onProgress: (String) -> Unit = {},
    ): ActionResult {
        onProgress("Starting Linux…")
        val script = scripts.read("dn-start.sh")
            ?: return ActionResult(false, "Scripts are missing from the APK")
        val result = Shell.rootScript(
            script,
            listOf(envDir, geometry, dpi.toString(), port.toString()),
            timeoutSeconds = 120,
        )
        return toResult(result)
    }

    fun stop(envDir: String, onProgress: (String) -> Unit = {}): ActionResult {
        onProgress("Stopping Linux…")
        val script = scripts.read("dn-stop.sh")
            ?: return ActionResult(false, "Scripts are missing from the APK")
        val result = Shell.rootScript(script, listOf(envDir), timeoutSeconds = 90)
        return toResult(result)
    }

    /**
     * Deletes an environment and everything in its folder. The script itself
     * also refuses unless it receives the literal word DELETE, so this cannot
     * be triggered by a stray call.
     */
    fun delete(envDir: String, onProgress: (String) -> Unit = {}): ActionResult {
        onProgress("Deleting environment…")
        val script = scripts.read("dn-delete.sh")
            ?: return ActionResult(false, "Scripts are missing from the APK")
        val result = Shell.rootScript(script, listOf(envDir, "DELETE"), timeoutSeconds = 120)
        return toResult(result)
    }

    private fun toResult(result: ShellResult): ActionResult {
        val text = result.output
        val ok = result.ok && !text.contains("ERROR:") && !text.contains("FAILED:")
        return ActionResult(ok, text.ifBlank { if (ok) "Done" else "Command failed" })
    }
}
