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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Compatibility
import dev.droidnix.ui.UiState
import dev.droidnix.ui.components.CheckRow
import dev.droidnix.ui.components.KeyValueRow
import dev.droidnix.ui.components.MonoLog
import dev.droidnix.ui.components.SectionCard

@Composable
fun SettingsScreen(
    state: UiState,
    password: String,
    onPassword: (String) -> Unit,
    onDiagnostics: () -> Unit,
    onRecheck: () -> Unit,
) {
    val device = state.device
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Device facts are measured, not assumed. Re-check after changing storage or Magisk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard("Device") {
            if (device == null) {
                Text("Not read yet.")
            } else {
                KeyValueRow("Model", "${device.model} (${device.device})")
                KeyValueRow("Android", "${device.androidRelease} · API ${device.sdk}")
                KeyValueRow("ABI", device.abiList)
                KeyValueRow("Kernel", device.kernel)
                KeyValueRow("Architecture", device.arch)
                KeyValueRow("SELinux", device.selinux)
                KeyValueRow("Total RAM", String.format("%.2f GB", device.totalRamGb))
                KeyValueRow("Available RAM", String.format("%.0f MB", device.availableRamBytes / 1048576.0))
                KeyValueRow("Internal free", String.format("%.1f GB", device.dataFreeBytes / 1073741824.0))
                KeyValueRow("ext4", if (device.hasExt4) "supported" else "missing")
                KeyValueRow("exFAT", if (device.hasExfat) "supported" else "missing")
                KeyValueRow("Free loop", device.freeLoopDevice ?: "none")
            }
        }

        SectionCard("Desktop viewer") {
            var passwordField by remember { mutableStateOf(password) }
            OutlinedTextField(
                value = passwordField,
                onValueChange = {
                    passwordField = it
                    onPassword(it)
                },
                singleLine = true,
                label = { Text("VNC password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        "Used for environments without a droidnix.json. The password is " +
                            "only sent to 127.0.0.1 and is stored in app-private storage.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            KeyValueRow("Viewer", "in-process RFB client (Compose)")
        }

        SectionCard("Root") {
            KeyValueRow("su → uid", state.root?.uid ?: "unknown")
            KeyValueRow("Magisk", state.root?.magiskVersion ?: "not detected")
            Text(
                state.root?.detail ?: "Not checked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val compat = state.compat
        if (compat != null) {
            SectionCard("Compatibility") {
                Text(
                    String.format("Minimum memory: %.1f GB total", Compatibility.MIN_TOTAL_RAM_BYTES / 1073741824.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                compat.checks.forEach { CheckRow(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRecheck) { Text("Re-check") }
                }
            }
        }

        if (state.scriptsError != null) {
            SectionCard("Script deployment") {
                Text(
                    state.scriptsError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(
            title = "Diagnostic report",
            trailing = { OutlinedButton(onClick = onDiagnostics) { Text("Collect") } },
        ) {
            MonoLog(state.diagnostics.ifBlank { "Run Diagnostics to collect a report." }, maxLines = 30)
        }

        SectionCard("About") {
            KeyValueRow("Version", "0.2.0 (milestone 2)")
            Text(
                "Bundled helper scripts are executed as root directly from the APK " +
                    "(piped over stdin), so no copies live on writable storage.\n\n" +
                    "DroidNix uses Magisk root only. It never modifies boot, recovery or your storage " +
                    "layout automatically. Destructive actions always require separate confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
