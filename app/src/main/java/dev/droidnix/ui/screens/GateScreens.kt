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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Check
import dev.droidnix.core.CompatibilityReport
import dev.droidnix.core.Level
import dev.droidnix.ui.components.CheckRow
import dev.droidnix.ui.components.SectionCard

@Composable
fun LoadingScreen(text: String = "Checking this device…") {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("DroidNix", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Linux on your Android tablet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "DroidNix runs a real Linux system inside a container (chroot) on this device. " +
                    "Your Linux files live inside an image file on storage you choose. Android keeps running normally.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionCard("Before you continue") {
                Bullet("Root is mandatory. DroidNix uses Magisk root to mount and start Linux. Without working root it cannot run, and it will never attempt to root, unlock or modify your boot or recovery.")
                Bullet("This software is experimental. A Linux desktop can exhaust memory and make Android unstable. Keep backups of anything important.")
                Bullet("DroidNix never formats, wipes or repartitions storage on its own. Destructive actions always require a separate confirmation.")
                Bullet("Your warranty, your risk. The authors are not responsible for data loss or damage to your device.")
                Bullet("Linux software is installed by you inside the environment. DroidNix does not ship Arch packages or bypass their licences.")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text("I understand and accept these conditions", style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RootRequiredScreen(
    detail: String,
    onRetry: () -> Unit,
    onOpenMagisk: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Root access required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "DroidNix cannot start Linux without a working root shell. This is a hard requirement — there is no limited mode.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionCard("What was detected") {
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard("How to fix it") {
                Bullet("Install Magisk from the official source and patch your device's boot image.")
                Bullet("Open Magisk, then Superuser, and make sure DroidNix (and the shell) are allowed.")
                Bullet("If a prompt was denied earlier, clear the denial in Magisk's Superuser list.")
                Text(
                    "DroidNix will never try to install Magisk, unlock the bootloader, or touch boot/recovery on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("Check again") }
                OutlinedButton(onClick = onOpenMagisk) { Text("Open Magisk") }
            }
        }
    }
}

@Composable
fun CompatibilityScreen(report: CompatibilityReport, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("This device is not supported", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "One or more blocking requirements failed. DroidNix will not continue on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionCard("Requirements") {
                report.checks.forEach { check -> CheckRow(check) }
            }
            report.checks.filter { it.level == Level.FAIL }.forEach { failing: Check ->
                Text(
                    "${failing.title}: ${failing.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onRetry) { Text("Re-check device") }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
