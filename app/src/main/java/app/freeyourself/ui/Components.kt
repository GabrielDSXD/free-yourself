package app.freeyourself.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.freeyourself.core.DayStats
import app.freeyourself.isServiceEnabled

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun <T> Options(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(text) }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun ProtectionStatus(resumes: Int, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val enabled = remember(resumes) { isServiceEnabled(context) }
    val complete = remember(resumes) { setupComplete(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dot = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(if (enabled) "Proteção ativa" else "Proteção desligada", style = MaterialTheme.typography.bodyLarge)
        }
        if (!complete) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) "Faltam ajustes para o celular não desligar a proteção em segundo plano."
                else "O Free Yourself precisa de algumas permissões para enxergar a tela.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSetup) { Text("Concluir configuração") }
        }
    }
}

/** A linha do dia: 3 marcos de aviso, um divisor e um segmento por bloqueio. */
@Composable
fun DayLine(day: DayStats, warnings: Int) {
    val used = day.warnings.coerceAtMost(warnings)
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(24.dp).semantics(mergeDescendants = true) {
            contentDescription = "$used de $warnings avisos usados hoje, ${day.blocks} bloqueios"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(warnings) { i ->
            val mark = if (i < used) Modifier.background(colors.tertiary, CircleShape)
            else Modifier.border(2.dp, colors.outline, CircleShape)
            Box(Modifier.size(14.dp).then(mark))
        }
        Box(Modifier.width(2.dp).height(24.dp).background(colors.outline))
        val shown = day.blocks.coerceAtMost(8)
        repeat(shown) { Box(Modifier.size(width = 18.dp, height = 10.dp).background(colors.secondary, RoundedCornerShape(5.dp))) }
        if (day.blocks > shown) {
            Text("+${day.blocks - shown}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        if (day.blocks == 0) Box(Modifier.weight(1f).height(2.dp).background(colors.outlineVariant))
    }
}
