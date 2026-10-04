package app.freeyourself.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.freeyourself.FreeYourself
import app.freeyourself.dns.DnsProvider
import app.freeyourself.dns.DnsStatus
import app.freeyourself.dns.dnsStatus
import app.freeyourself.dns.label

/** O que o sistema está usando como DNS privado na rede ativa. */
private fun readDnsStatus(context: Context): DnsStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val link = cm.activeNetwork?.let(cm::getLinkProperties)
    return dnsStatus(online = link != null, serverName = link?.privateDnsServerName)
}

/** Filtro de sites pelo DNS privado do Android: o app guia a ativação e mostra o status. */
@Composable
fun DnsSection(resumes: Int) {
    val context = LocalContext.current
    val store = FreeYourself.store
    val status = remember(resumes) { readDnsStatus(context) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SectionTitle("Filtro de sites (DNS)")
    Text(
        "Bloqueia sites adultos no aparelho inteiro, em todos os apps e navegadores. Enxerga só o endereço do site, " +
            "não o conteúdo; as imagens continuam com a análise do Free Yourself.",
        style = MaterialTheme.typography.bodyMedium, color = muted,
    )
    Options("Provedor", DnsProvider.entries.map { it to it.label.substringBefore(' ') }, store.dnsProvider) { store.dnsProvider = it }
    StatRow("Status", status.label())

    val host = store.dnsProvider.host
    val action = if (status is DnsStatus.Filtering) "Trocar no Android" else "Ativar no Android"
    val open = {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DNS privado", host))
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
    if (status is DnsStatus.Filtering) OutlinedButton(onClick = open) { Text(action) } else Button(onClick = open) { Text(action) }

    if (status !is DnsStatus.Filtering) {
        Spacer(Modifier.height(8.dp))
        Text(
            "O endereço $host vai para a área de transferência. Na tela que abrir, toque em DNS privado " +
                "(na Samsung: Mais configurações de conexão → DNS privado; em outros aparelhos pode estar em Avançado), " +
                "escolha Nome do host do provedor, cole e toque em Salvar.",
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Com o filtro ligado, o Android envia as consultas de endereço ao provedor escolhido. " +
            "O Free Yourself continua sem acesso à internet e não vê essas consultas.",
        style = MaterialTheme.typography.bodyMedium, color = muted,
    )
}
