package app.freeyourself.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freeyourself.FreeYourself

@Composable
fun WeekScreen() {
    val version = FreeYourself.version.intValue
    val days = remember(version) { FreeYourself.guard.lastDays() }
    val max = days.maxOf { it.attempts }.coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text("Últimos 7 dias", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        days.forEachIndexed { i, d ->
            val isToday = i == days.lastIndex
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {
                    contentDescription = "${weekday(d.date)}: ${d.attempts} detecções"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    weekday(d.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp),
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
                Box(Modifier.weight(1f).height(12.dp).background(colors.surfaceVariant, RoundedCornerShape(6.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(d.attempts / max.toFloat()).background(colors.primary, RoundedCornerShape(6.dp)))
                }
                Text("${d.attempts}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            }
        }
        if (days.all { it.attempts == 0 }) {
            Spacer(Modifier.height(8.dp))
            Text("Nenhuma detecção nos últimos 7 dias.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        SectionTitle("Na semana")
        StatRow("Detecções", "${days.sumOf { it.attempts }}")
        StatRow("Bloqueios", "${days.sumOf { it.blocks }}")
        StatRow("Tempo bloqueado", formatDuration(days.sumOf { it.blockedMs }))
        Spacer(Modifier.height(24.dp))
        Text(
            "Estes números ficam só neste aparelho. Nenhuma tela, site ou imagem é registrada.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
        )
    }
}
