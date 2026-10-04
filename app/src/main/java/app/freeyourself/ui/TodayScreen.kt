package app.freeyourself.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.freeyourself.FreeYourself
import app.freeyourself.isServiceEnabled
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(resumes: Int) {
    FreeYourself.version.intValue                     // recompõe quando o Guard muda
    val guard = FreeYourself.guard
    val context = LocalContext.current
    val enabled = remember(resumes) { isServiceEnabled(context) }
    var now by remember { mutableStateOf(guard.now()) }
    LaunchedEffect(Unit) {
        while (true) { guard.tick(); now = guard.now(); delay(30_000) }
    }
    val day = guard.today
    val policy = FreeYourself.store.policy()
    val untilMidnight = msUntilReset(now, day.date)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text(greeting(now.hour), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        ProtectionStatus(enabled)
        SectionTitle("Hoje")
        DayLine(day, policy.warnings)
        Spacer(Modifier.height(8.dp))
        Text(nextStep(day, policy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        StatRow("Detecções", "${day.attempts}")
        StatRow("Avisos", "${day.warnings}")
        StatRow("Bloqueios", "${day.blocks}")
        StatRow("Tempo bloqueado", formatDuration(day.blockedMs))
        SectionTitle("Recomeça à meia-noite")
        Text(
            "Faltam ${formatDuration(untilMidnight)}. Amanhã os avisos e bloqueios voltam ao início.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
