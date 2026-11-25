package dev.droidnix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Level
import dev.droidnix.data.LinuxEnvironment
import dev.droidnix.data.StorageTarget
import dev.droidnix.ui.UiState
import dev.droidnix.ui.components.KeyValueRow
import dev.droidnix.ui.components.LevelPill
import dev.droidnix.ui.components.SectionCard
import java.util.Locale

@Composable
fun InstallScreen(
    state: UiState,
    onAdopt: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshStorage: () -> Unit,
    onSelectStorage: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onRefreshStorage() }
    val env = state.environments.firstOrNull { it.rootDir == state.selected }
    val chosen = state.storageTargets.firstOrNull { it.base == state.installBase }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Install Linux", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "This milestone manages an existing Arch Linux installation on this device. Creating a " +
                "brand-new image from inside Android is the next milestone — this screen will not fake it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(
            title = "Install location",
            trailing = {
                OutlinedButton(onClick = onRefreshStorage, enabled = !state.storageBusy) {
                    Text(if (state.storageBusy) "Checking…" else "Re-scan")
                }
            },
        ) {
            Text(
                "Each location is checked on the device: a file is written and removed, free " +
                    "space is measured, and a free loop device is required to mount a Linux image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.storageTargets.isEmpty()) {
                Text(
                    if (state.storageBusy) "Probing storage…"
                    else "No usable location found. Mount an SD card or check root access.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.storageTargets.forEach { target ->
                    StorageRow(
                        target = target,
                        selected = target.base == state.installBase,
                        onClick = { onSelectStorage(target.base) },
                    )
                }
            }
            if (chosen != null) {
                KeyValueRow("Chosen", chosen.base)
                KeyValueRow(
                    "Capacity",
                    String.format(
                        Locale.US,
                        "%.1f GB free of %.1f GB",
                        chosen.freeGb,
                        chosen.totalGb,
                    ),
                )
                KeyValueRow("Filesystem", chosen.fsType)
                KeyValueRow("Free loop device", chosen.freeLoop)
            } else if (state.storageTargets.isNotEmpty()) {
                Text(
                    "Select a location above. A location is only offered when it is writable " +
                        "and a loop device is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "The install wizard that creates and populates a new image is the next " +
                    "milestone. Your choice is verified and saved now; no image is created yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Detected installations") {
            if (state.environments.isEmpty()) {
                Text(
                    "None found. Plug in the storage holding your install and refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.environments.forEach { envItem ->
                    InstallRow(envItem, selected = envItem.rootDir == state.selected)
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onRefresh) { Text("Scan again") }
        }

        if (env != null) {
            SectionCard("Manage this installation") {
                KeyValueRow("Folder", env.rootDir)
                KeyValueRow("Image", if (env.hasImage) "arch.img found" else "arch.img missing")
                KeyValueRow("Config", if (env.config != null) "droidnix.json present" else "none (legacy install)")
                KeyValueRow("Desktop", env.desktopName)
                if (env.config == null && env.hasImage) {
                    Text(
                        "DroidNix can adopt this folder by writing a small droidnix.json next to the image. " +
                            "It does not modify, resize or format the image.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onAdopt, enabled = state.busy == null) {
                        Text(if (state.busy != null) "Working…" else "Adopt this installation")
                    }
                }
            }
        }

        SectionCard("Modules in this milestone") {
            ModuleRow("Arch Linux", "available", true)
            ModuleRow("XFCE desktop", "available", true)
            ModuleRow("Ubuntu / Debian", "not available yet", false)
            ModuleRow("Fedora", "not available yet", false)
            ModuleRow("GNOME / KDE / LXQt", "not available yet", false)
        }

        SectionCard("What is still missing") {
            Text(
                "• New-install wizard (image creation, base system, desktop packages, user setup)\n" +
                    "• Built-in desktop viewer with touch and clipboard",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StorageRow(
    target: StorageTarget,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            )
            .clickable(enabled = target.usable && !selected, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(target.label, fontWeight = FontWeight.Medium)
            Text(
                target.base,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                String.format(
                    Locale.US,
                    "%s · %.1f GB free of %.1f GB",
                    target.fsType,
                    target.freeGb,
                    target.totalGb,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            selected -> LevelPill("SELECTED", Level.OK)
            !target.writable -> LevelPill("READ-ONLY", Level.FAIL)
            target.freeLoop == "none" || target.freeLoop.isBlank() ->
                LevelPill("NO LOOP", Level.FAIL)
            else -> LevelPill("READY", Level.OK)
        }
    }
}

@Composable
private fun InstallRow(env: LinuxEnvironment, selected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(env.displayName, fontWeight = FontWeight.Medium)
            Text(
                env.rootDir,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when {
                !env.hasImage -> "no image"
                env.config == null -> "legacy"
                else -> "managed"
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ModuleRow(name: String, state: String, available: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            state,
            style = MaterialTheme.typography.labelMedium,
            color = if (available) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
