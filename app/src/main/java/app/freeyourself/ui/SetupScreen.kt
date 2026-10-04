package app.freeyourself.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.freeyourself.FreeYourself
import app.freeyourself.dns.DnsStatus
import app.freeyourself.isServiceEnabled
import app.freeyourself.setup.Brand
import app.freeyourself.setup.DeviceGuide
import app.freeyourself.setup.detectBrand
import app.freeyourself.setup.guideFor

private val deviceBrand: Brand by lazy { detectBrand(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) }
private val deviceGuide: DeviceGuide by lazy { guideFor(deviceBrand) }

/** Os toques do DNS privado para a marca deste aparelho. */
val dnsSteps: String get() = deviceGuide.dns

private fun batteryUnrestricted(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** O mínimo para a proteção funcionar e não ser desligada pelo sistema. */
fun setupComplete(context: Context): Boolean =
    isServiceEnabled(context) && batteryUnrestricted(context) &&
        (deviceGuide.autostart == null || FreeYourself.store.autostartDone)

/** Abre a primeira tela que existir; atalhos de fabricante podem não existir em todas as versões. */
private fun Context.openFirst(vararg intents: Intent) {
    for (intent in intents) if (runCatching { startActivity(intent) }.isSuccess) return
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private val xiaomiAutostart = Intent().setComponent(
    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
)

@Composable
fun SetupScreen(resumes: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("Configurar proteção", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        SetupChecklist(resumes)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Concluir") }
    }
}

/** Passo a passo das permissões, com os toques da marca deste celular e o status de cada item. */
@Composable
fun SetupChecklist(resumes: Int) {
    val context = LocalContext.current
    val store = FreeYourself.store
    var notificationAnswer by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationAnswer++ }

    val accessibility = remember(resumes) { isServiceEnabled(context) }
    val battery = remember(resumes) { batteryUnrestricted(context) }
    val dns = remember(resumes) { readDnsStatus(context) is DnsStatus.Filtering }
    val notifications = remember(resumes, notificationAnswer) {
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    val guide = deviceGuide
    var n = 0

    Text(
        "Passos para o seu ${guide.name}. Volte para cá depois de cada um: o status atualiza sozinho.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupItem(
        number = ++n, title = "Ativar a proteção", done = accessibility,
        steps = "${guide.accessibility} Se o botão estiver cinza ou aparecer \"configuração restrita\", faça o passo 2 e volte aqui.",
        action = "Abrir Acessibilidade",
    ) { context.openFirst(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    SetupItem(
        number = ++n, title = "Liberar configurações restritas", done = accessibility,
        steps = "${guide.restricted} A opção só aparece depois que você tenta ativar a proteção uma vez.",
        action = "Abrir tela do app",
    ) { context.openFirst(appDetails(context)) }
    SetupItem(
        number = ++n, title = "Bateria sem restrições", done = battery,
        steps = "${guide.battery} Sem isso o celular pode desligar a proteção em segundo plano.",
        action = "Abrir tela do app",
    ) { context.openFirst(appDetails(context)) }
    guide.autostart?.let { steps ->
        SetupItem(
            number = ++n, title = "Inicialização automática", done = store.autostartDone, steps = steps,
            action = "Abrir Inicialização automática", confirm = { store.autostartDone = true },
        ) { context.openFirst(xiaomiAutostart, appDetails(context)) }
    }
    SetupItem(
        number = ++n, title = "Filtro de sites (opcional)", done = dns,
        steps = "Bloqueia sites adultos no celular todo. O endereço vai para a área de transferência. ${guide.dns}",
        action = "Copiar endereço e abrir",
    ) { openPrivateDnsSettings(context, store.dnsProvider.host) }
    SetupItem(
        number = ++n, title = "Notificações (opcional)", done = notifications,
        steps = "Avisa quando um bloqueio termina, sem dizer o motivo.",
        action = "Permitir notificações",
    ) {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.openFirst(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}

/** Um passo: número (ou ✓), título, toques da marca e o botão que abre a tela certa. */
@Composable
private fun SetupItem(
    number: Int,
    title: String,
    done: Boolean,
    steps: String,
    action: String,
    confirm: (() -> Unit)? = null,
    open: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val mark = if (done) Modifier.background(colors.primary, CircleShape) else Modifier.border(2.dp, colors.outline, CircleShape)
        Box(
            Modifier.size(32.dp).then(mark).semantics { contentDescription = if (done) "Concluído" else "Passo $number pendente" },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$number", style = MaterialTheme.typography.labelLarge, color = if (done) colors.onPrimary else colors.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (done) {
                Text("Pronto", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            } else {
                Spacer(Modifier.height(4.dp))
                Text(steps, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = open) { Text(action) }
                    if (confirm != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = confirm) { Text("Já ativei") }
                    }
                }
            }
        }
    }
}
