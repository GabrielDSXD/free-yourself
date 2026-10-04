package app.margem.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.margem.isServiceEnabled

private val intro = listOf(
    "Retome o controle" to "Este aplicativo ajuda você a evitar conteúdo adulto enquanto usa o celular.",
    "Avisos progressivos" to "Você receberá alguns avisos antes que os bloqueios sejam ativados.",
    "Bloqueios inteligentes" to "Quanto mais você insistir no mesmo dia, maior será o intervalo de bloqueio.",
    "Um novo começo todos os dias" to "Os bloqueios e contadores são reiniciados diariamente.",
)
private const val STEPS = 7   // 4 de introdução + privacidade + acessibilidade + notificações

@Composable
fun Onboarding(resumes: Int, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = "Etapa ${step + 1} de $STEPS" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(STEPS) { i ->
                val color = if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Box(Modifier.size(width = if (i == step) 20.dp else 6.dp, height = 6.dp).background(color, CircleShape))
            }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                when (s) {
                    in 0..3 -> Page(intro[s].first, intro[s].second)
                    4 -> Page("Sua tela não sai do aparelho", PRIVACY_TEXT)
                    5 -> AccessibilityStep(resumes)
                    else -> NotificationStep()
                }
            }
        }
        Button(
            onClick = { if (step == STEPS - 1) onDone() else step++ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (step == STEPS - 1) "Começar" else "Continuar") }
    }
}

@Composable
private fun Page(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccessibilityStep(resumes: Int) {
    val context = LocalContext.current
    val enabled = remember(resumes) { isServiceEnabled(context) }
    Page(
        "Ative a proteção",
        "Para detectar conteúdo, o Margem usa o serviço de acessibilidade do Android. Com ele, o app lê o " +
            "texto da tela (como o endereço do site) e analisa imagens da tela no próprio aparelho. " +
            "Nada é gravado e nada é enviado.",
    )
    Spacer(Modifier.height(24.dp))
    if (enabled) {
        ProtectionStatus(enabled = true)
    } else {
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
            Text("Abrir ajustes de acessibilidade")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Em Apps instalados, escolha Margem e ative. Se o Android disser que a configuração é restrita: " +
                "Informações do app → menu ⋮ → Permitir configurações restritas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationStep() {
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Page(
        "Saiba quando o bloqueio acabar",
        "Uma notificação discreta avisa que o bloqueio terminou. Ela nunca diz o motivo.",
    )
    Spacer(Modifier.height(24.dp))
    if (granted) {
        Text("Notificações permitidas.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    } else {
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else granted = true
        }) { Text("Permitir notificações") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Opcional. Dá para mudar depois em Ajustes.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
