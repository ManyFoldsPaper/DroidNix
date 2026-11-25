package dev.droidnix.core

import java.io.File
import java.util.concurrent.TimeUnit

/** Result of a shell command, always captured — never thrown. */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
    val output: String get() = (stdout + stderr).trim()
}

/**
 * Runs commands. Root work goes through Magisk's `su`; nothing here assumes
 * that `su` exists, and every call has a timeout so the UI can never hang.
 */
object Shell {

    fun run(
        command: List<String>,
        timeoutSeconds: Long = 60,
        stdin: String? = null,
    ): ShellResult {
        return try {
            val process = ProcessBuilder(command).start()
            if (stdin != null) {
                runCatching {
                    process.outputStream.bufferedWriter().use { it.write(stdin) }
                }
            } else {
                runCatching { process.outputStream.close() }
            }
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread { runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } }
            val tErr = Thread { runCatching { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } } }
            tOut.isDaemon = true
            tErr.isDaemon = true
            tOut.start()
            tErr.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(3, TimeUnit.SECONDS)
            }
            tOut.join(1500)
            tErr.join(1500)
            ShellResult(
                exitCode = if (finished) process.exitValue() else -1,
                stdout = out.toString(),
                stderr = err.toString(),
            )
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Runs an inline command as root.
     *
     * Everything goes through [rootScript] (`su -c 'sh -s'`, script on stdin).
     * On this Magisk build, `su -c '<command>'` intermittently runs with a
     * restricted SELinux/mount view where /mnt/media_rw is unreadable, while
     * the stdin form works consistently. `-t 0` additionally selects Magisk's
     * global mount namespace so mounts persist between calls.
     */
    fun root(command: String, timeoutSeconds: Long = 60): ShellResult =
        rootScript(command, emptyList(), timeoutSeconds)

    /**
     * Runs a bundled script as root, fed over stdin (`sh -s`) so no copy of it
     * has to live somewhere the root SELinux domain may not read.
     */
    fun rootScript(
        script: String,
        args: List<String> = emptyList(),
        timeoutSeconds: Long = 60,
    ): ShellResult {
        val argLine = if (args.isEmpty()) "" else " " + args.joinToString(" ") { quote(it) }
        return withRoot("sh -s --$argLine", timeoutSeconds, stdin = script)
    }

    private fun withRoot(
        command: String,
        timeoutSeconds: Long,
        stdin: String? = null,
    ): ShellResult {
        for (candidate in SU_CANDIDATES) {
            if (!File(candidate).canExecute()) continue
            var result = run(listOf(candidate, "-t", "0", "-c", command), timeoutSeconds, stdin)
            if (result.exitCode == -1 && result.stdout.isBlank() &&
                result.stderr.contains("No such file", ignoreCase = true)
            ) {
                continue
            }
            if (result.output.contains("Unknown option", ignoreCase = true) ||
                result.output.contains("invalid option", ignoreCase = true) ||
                result.output.contains("unrecognized option", ignoreCase = true)
            ) {
                result = run(listOf(candidate, "-c", command), timeoutSeconds, stdin)
            }
            return result
        }
        return ShellResult(-1, "", "su was not found on this device")
    }

    /** Magisk ships su in more than one place depending on version and boot mode. */
    private val SU_CANDIDATES = listOf(
        "su",
        "/system/bin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "/product/bin/su",
    )

    /** Safe single-quote for POSIX shells. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
