package app.freeyourself.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.freeyourself.FreeYourself
import app.freeyourself.core.Sensitivity
import app.freeyourself.data.ThemeMode
import app.freeyourself.isServiceEnabled
import app.freeyourself.service.GuardService

@Composable
fun SettingsScreen(resumes: Int) {
    val store = FreeYourself.store
    val context = LocalContext.current
    val enabled = remember(resumes) { isServiceEnabled(context) }
    val notifications = remember(resumes) { context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Proteção")
        ProtectionStatus(enabled)
        if (enabled) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Abrir ajustes de acessibilidade")
            }
        }

        SectionTitle("Bloqueio")
        Options("Primeiro bloqueio", listOf(15 to "15 s", 30 to "30 s", 60 to "1 min"), store.initialBlockSec) { store.initialBlockSec = it }
        Options("Bloqueio máximo", listOf(30 to "30 min", 60 to "1 h", 120 to "2 h"), store.maxBlockMin) { store.maxBlockMin = it }
        Text(sequenceText(store.policy()), style = MaterialTheme.typography.bodyMedium, color = muted)

        SectionTitle("Detecção")
        Options(
            "Sensibilidade",
            listOf(Sensitivity.LOW to "Baixa", Sensitivity.MEDIUM to "Média", Sensitivity.HIGH to "Alta"),
            store.sensitivity,
        ) { store.sensitivity = it }
        Text(
            when (store.sensitivity) {
                Sensitivity.LOW -> "Só imagens claramente explícitas."
                Sensitivity.MEDIUM -> "Equilíbrio entre proteção e alarmes falsos."
                Sensitivity.HIGH -> "Também considera imagens sugestivas. Pode gerar mais avisos."
            },
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
        StatRow("Texto e endereços", if (enabled) "Ativo" else "Desligado")
        StatRow("Análise de imagens", when {
            !enabled -> "Desligada"
            GuardService.imageReady -> "Ativa"
            else -> "Carregando"
        })
        Text("Tudo é analisado no próprio aparelho.", style = MaterialTheme.typography.bodyMedium, color = muted)

        SectionTitle("Geral")
        Options(
            "Tema",
            listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro"),
            store.theme,
        ) { store.theme = it }
        SwitchRow("Vibrar nos avisos", store.vibrate) { store.vibrate = it }
        StatRow("Notificações", if (notifications) "Permitidas" else "Desligadas")
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }) { Text("Ajustar notificações") }

        SectionTitle("Privacidade")
        Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodyMedium)

        SectionTitle("Sobre")
        Text(
            "Free Yourself 1.0\nModelo de imagem: nsfw_model, de GantMan (licença MIT).\nFonte: Manrope (SIL Open Font License).",
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
    }
}
