package dev.droidnix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.droidnix.core.Check
import dev.droidnix.core.Level

@Composable
fun LevelPill(text: String, level: Level, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (level) {
        Level.OK -> scheme.primaryContainer to scheme.onPrimaryContainer
        Level.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Level.FAIL -> scheme.errorContainer to scheme.onErrorContainer
    }
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun CheckRow(check: Check) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        LevelPill(
            when (check.level) {
                Level.OK -> "OK"
                Level.WARN -> "WARN"
                Level.FAIL -> "BLOCKED"
            },
            check.level,
        )
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MonoLog(text: String, maxLines: Int = 22) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        maxLines = maxLines,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
    )
}
