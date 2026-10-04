package app.freeyourself.dns

/**
 * Filtro por DNS via "DNS privado" do Android (DNS-over-TLS, Android 9+). O app não consegue
 * ativá-lo nem envia nada: só sugere um provedor e lê o que o sistema está usando.
 */
enum class DnsProvider(val label: String, val host: String) {
    CLEANBROWSING("CleanBrowsing Adulto", "adult-filter-dns.cleanbrowsing.org"),
    CLOUDFLARE("Cloudflare Família", "family.cloudflare-dns.com"),
}

sealed interface DnsStatus {
    data object Offline : DnsStatus
    data object Off : DnsStatus
    data class Filtering(val provider: DnsProvider) : DnsStatus
    data class Other(val host: String) : DnsStatus
}

/** [serverName] = LinkProperties.privateDnsServerName: só não é nulo no modo estrito (hostname definido). */
fun dnsStatus(online: Boolean, serverName: String?): DnsStatus {
    if (!online) return DnsStatus.Offline
    val host = serverName?.trim().orEmpty()
    if (host.isEmpty()) return DnsStatus.Off
    return DnsProvider.entries.firstOrNull { it.host.equals(host, ignoreCase = true) }
        ?.let { DnsStatus.Filtering(it) }
        ?: DnsStatus.Other(host)
}

fun DnsStatus.label(): String = when (this) {
    DnsStatus.Offline -> "Sem internet para verificar"
    DnsStatus.Off -> "Desligado"
    is DnsStatus.Filtering -> "Ativo (${provider.label})"
    is DnsStatus.Other -> "Outro DNS privado: $host"
}
