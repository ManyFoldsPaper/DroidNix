package dev.droidnix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Level
import dev.droidnix.data.EnvironmentStatus
import dev.droidnix.data.LinuxEnvironment
import dev.droidnix.ui.UiState
import dev.droidnix.ui.components.CheckRow
import dev.droidnix.ui.components.KeyValueRow
import dev.droidnix.ui.components.LevelPill
import dev.droidnix.ui.components.MonoLog
import dev.droidnix.ui.components.SectionCard
import dev.droidnix.ui.engineLabel

@Composable
fun HomeScreen(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefreshLog: () -> Unit,
    onOpenInstall: () -> Unit,
) {
    val env = state.environments.firstOrNull { it.rootDir == state.selected }
    val status = state.status
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("DroidNix", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                state.device?.let { "${it.model} · Android ${it.androidRelease} · ${it.abi}" }
                    ?: "Linux on Android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (env == null) {
            SectionCard("No Linux environment found") {
                Text(
                    "DroidNix looked in /mnt/media_rw, /storage, /data/local/droidnix and " +
                        "/data/adb/droidnix for a Linux image. Nothing was found. Connect the " +
                        "storage that holds your install, or see Install Linux.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenInstall) { Text("Open Install Linux") }
            }
        } else {
            EnvironmentCard(
                env = env,
                status = status,
                busy = state.busy,
                onStart = onStart,
                onStop = onStop,
            )
        }

        val compat = state.compat
        if (compat != null) {
            SectionCard("Device checks") {
                compat.checks.take(5).forEach { CheckRow(it) }
            }
        }

        SectionCard(
            title = "Session log",
            trailing = {
                OutlinedButton(onClick = onRefreshLog) { Text("Refresh") }
            },
        ) {
            MonoLog(state.log.ifBlank { "No log yet." })
        }

        Text(
            engineLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EnvironmentCard(
    env: LinuxEnvironment,
    status: EnvironmentStatus?,
    busy: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val running = status?.running == true
    val partial = status?.mounted == true && status.xvnc.not()
    SectionCard(
        title = env.displayName,
        trailing = {
            LevelPill(
                when {
                    running -> "RUNNING"
                    partial -> "PARTIAL"
                    else -> "STOPPED"
                },
                when {
                    running -> Level.OK
                    partial -> Level.WARN
                    else -> Level.WARN
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (running) {
                OutlinedButton(onClick = onStop, enabled = busy == null) { Text("Stop Linux") }
            } else if (partial) {
                Button(onClick = onStart, enabled = busy == null) { Text("Start desktop") }
                OutlinedButton(onClick = onStop, enabled = busy == null) { Text("Stop") }
            } else {
                Button(onClick = onStart, enabled = busy == null) {
                    Text(if (busy != null) "Working…" else "Start Linux")
                }
            }
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (partial) {
            Text(
                "The image is mounted but the desktop is not running. " +
                    "This can be left over from an interrupted session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        KeyValueRow("Desktop", env.desktopName)
        KeyValueRow("Distribution", status?.prettyName?.ifBlank { env.distroId } ?: env.distroId)
        KeyValueRow("Storage", env.rootDir)
        if (status != null) {
            KeyValueRow("Image", if (status.imagePresent) "present" else "missing")
            if (status.mounted) {
                KeyValueRow("Loop device", status.loopDevice.ifBlank { "—" })
                val used = status.diskUsedKb.toFloat()
                val total = status.diskTotalKb.toFloat()
                if (total > 0f) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (used / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        String.format(
                            "%.1f GB used of %.1f GB",
                            used / 1048576f,
                            total / 1048576f,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            KeyValueRow("Xvnc", if (status.xvnc) "running on 127.0.0.1:5901" else "not running")
            KeyValueRow("Swap", if (status.swapRunning) "active" else "off")
        }

        if (!env.hasImage) {
            Text(
                "This folder has a config but no arch.img — it cannot be started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "The desktop runs at 1280×800 on the loopback VNC port. The built-in viewer " +
                "(touch, scaling, clipboard) is the next milestone; until it lands, DroidNix " +
                "does not ship a pretend display button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
