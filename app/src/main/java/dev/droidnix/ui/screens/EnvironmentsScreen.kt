package dev.droidnix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Level
import dev.droidnix.data.EnvironmentStatus
import dev.droidnix.data.LinuxEnvironment
import dev.droidnix.ui.UiState
import dev.droidnix.ui.components.KeyValueRow
import dev.droidnix.ui.components.LevelPill
import dev.droidnix.ui.components.SectionCard

@Composable
fun EnvironmentsScreen(
    state: UiState,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenDesktop: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "My environments",
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }

        if (state.environments.isEmpty()) {
            SectionCard("Nothing discovered") {
                Text(
                    "DroidNix checks /mnt/media_rw, /sdcard and /data/local/droidnix for folders " +
                        "containing arch.img or droidnix.json.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            state.environments.forEach { env ->
                EnvTile(
                    env = env,
                    selected = env.rootDir == state.selected,
                    status = if (env.rootDir == state.selected) state.status else null,
                    onClick = { onSelect(env.rootDir) },
                )
            }
            val selected = state.environments.firstOrNull { it.rootDir == state.selected }
            if (selected != null) {
                val running = state.status?.mounted == true || state.status?.xvnc == true
                SectionCard("Controls") {
                    KeyValueRow("Selected", selected.rootDir)
                    Text(
                        "State is read live from mounts, loop devices and the X server every few seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (running) {
                            Button(onClick = onOpenDesktop, enabled = state.busy == null) { Text("Open desktop") }
                            OutlinedButton(onClick = onStop, enabled = state.busy == null) { Text("Stop") }
                        } else {
                            Button(onClick = onStart, enabled = state.busy == null) { Text("Start") }
                        }
                        if (state.busy != null) Text(state.busy, style = MaterialTheme.typography.bodySmall)
                    }
                }

                SectionCard("Delete this environment") {
                    Text(
                        "Permanently deletes the folder above, including arch.img, the config, " +
                            "logs and every file inside it. Android storage and other environments " +
                            "are not touched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (running) {
                        Text(
                            "Stop the environment first — deleting a running image is refused.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = state.busy == null && !running,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete environment…")
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (confirmDelete) {
        val selected = state.environments.firstOrNull { it.rootDir == state.selected }
        DeleteDialog(
            envDir = selected?.rootDir ?: "",
            busy = state.busy != null,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun DeleteDialog(
    envDir: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete this environment permanently?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This cannot be undone. The entire folder will be removed, " +
                        "including the Linux image and all data stored inside it:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    envDir,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "No backup is made. Type DELETE below to enable the button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("Type DELETE") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed == "DELETE" && !busy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete permanently")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun EnvTile(
    env: LinuxEnvironment,
    selected: Boolean,
    status: EnvironmentStatus?,
    onClick: () -> Unit,
) {
    val running = status?.running == true
    SectionCard(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface,
            ),
        title = env.displayName,
        trailing = {
            LevelPill(
                if (running) "RUNNING" else if (selected && status == null) "CHECKING" else "STOPPED",
                if (running) Level.OK else Level.WARN,
            )
        },
    ) {
        Text(
            env.rootDir,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${env.desktopName} · ${if (env.hasImage) "image present" else "image missing"}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onClick, enabled = !selected) {
                Text(if (selected) "Selected" else "Select")
            }
        }
    }
}
