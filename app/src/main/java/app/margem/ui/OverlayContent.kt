package app.margem.ui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.margem.Margem
import kotlinx.coroutines.delay

@Composable
fun WarningOverlay(level: Int, onBack: () -> Unit, onContinue: () -> Unit) {
    val (title, body) = when (level) {
        1 -> "Conteúdo potencialmente adulto detectado" to
            "O Margem identificou algo na tela que pode ser conteúdo adulto. Se não é isso que você quer ver agora, é só voltar."
        2 -> "Este é o segundo alerta de hoje" to
            "Você já recebeu um aviso anteriormente. Considere sair deste conteúdo antes que um bloqueio seja aplicado."
        else -> "Último aviso" to
            "Novas tentativas de acessar conteúdo adulto começarão a ativar bloqueios temporários."
    }
    val view = LocalView.current
    LaunchedEffect(Unit) { if (Margem.store.vibrate) view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).safeDrawingPadding().padding(24.dp), Alignment.Center) {
        Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        val dot = if (i < level) colors.tertiary else colors.outline
                        Box(Modifier.size(8.dp).background(dot, CircleShape))
                    }
                    Text("Aviso $level de 3", style = MaterialTheme.typography.labelLarge, color = colors.tertiary)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Voltar") }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuar mesmo assim", color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun BlockOverlay(attempts: Int, remaining: () -> Long, onHome: () -> Unit, onClose: () -> Unit) {
    var left by remember { mutableLongStateOf(remaining()) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(250); left = remaining() }
    }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        // Rolável: com fonte grande o botão de saída não pode ficar fora da tela.
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (left > 0) {
                Text("Conteúdo bloqueado", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                BreathingCircle(formatClock(left), remainingLabel(left))
                Spacer(Modifier.height(12.dp))
                Text("Respire com o círculo", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Text(
                    "Você recebeu este bloqueio porque conteúdo adulto foi detectado neste app. " +
                        "Ele fica fechado até o contador zerar. Use este tempo para sair desse conteúdo.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Hoje: ${attempts}ª detecção. À meia-noite tudo recomeça.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ir para o início") }
            } else {
                Text("Bloqueio encerrado", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Você pode continuar usando o dispositivo.",
                    style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Fechar") }
            }
        }
    }
}

/** Expande em 4 s e contrai em 6 s. Estático se o usuário removeu animações do sistema. */
@Composable
private fun BreathingCircle(label: String, spoken: String) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scale = if (still) 1f else {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0.82f,
            targetValue = 0.82f,
            animationSpec = infiniteRepeatable(keyframes {
                durationMillis = 10_000
                0.82f at 0 using FastOutSlowInEasing
                1f at 4_000 using FastOutSlowInEasing
                0.82f at 10_000
            }),
            label = "scale",
        ).value
    }
    val ring = MaterialTheme.colorScheme.secondary
    Box(Modifier.size(240.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * scale
            drawCircle(ring.copy(alpha = 0.12f), radius = r)
            drawCircle(ring, radius = r, style = Stroke(width = 2.dp.toPx()))
        }
        Text(
            label,
            style = MaterialTheme.typography.displayLarge,
            // clearAndSet: o texto muda a cada segundo, mas o leitor de tela só ouve a frase por minuto.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}
