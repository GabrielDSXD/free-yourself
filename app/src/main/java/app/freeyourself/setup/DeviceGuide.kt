package app.freeyourself.setup

enum class Brand { SAMSUNG, MOTOROLA, XIAOMI, OTHER }

/** Build.MANUFACTURER / Build.BRAND → família de interface (Redmi e POCO usam a mesma da Xiaomi). */
fun detectBrand(manufacturer: String, brand: String): Brand {
    val m = "$manufacturer $brand".lowercase()
    return when {
        "samsung" in m -> Brand.SAMSUNG
        "motorola" in m || "lenovo" in m -> Brand.MOTOROLA
        "xiaomi" in m || "redmi" in m || "poco" in m -> Brand.XIAOMI
        else -> Brand.OTHER
    }
}

/** Os toques de cada ajuste, a partir da tela que o app abre. */
class DeviceGuide(
    val name: String,
    val accessibility: String,
    val restricted: String,
    val battery: String,
    /** Só Xiaomi: sem inicialização automática o sistema encerra o serviço. */
    val autostart: String?,
    val dns: String,
)

fun guideFor(brand: Brand): DeviceGuide = when (brand) {
    Brand.SAMSUNG -> DeviceGuide(
        name = "Samsung",
        accessibility = "Em Acessibilidade, toque em Aplicativos instalados → Free Yourself e ative.",
        restricted = "Na tela do app, toque em ⋮ (canto superior direito) → Permitir configurações restritas e confirme com a digital ou o PIN.",
        battery = "Na tela do app, desça até Bateria e escolha Não restrito.",
        autostart = null,
        dns = "Toque em Mais configurações de conexão → DNS privado → Nome do host do provedor de DNS privado, cole e toque em Salvar.",
    )
    Brand.MOTOROLA -> DeviceGuide(
        name = "Motorola",
        accessibility = "Em Acessibilidade, toque em Apps transferidos por download → Free Yourself e ative.",
        restricted = "Na tela do app, toque em ⋮ (canto superior direito) → Permitir configurações restritas e confirme com o PIN.",
        battery = "Na tela do app, toque em Uso da bateria pelo app → Sem restrições.",
        autostart = null,
        dns = "Toque em DNS particular → Nome do host do provedor de DNS particular, cole e toque em Salvar.",
    )
    Brand.XIAOMI -> DeviceGuide(
        name = "Xiaomi",
        accessibility = "Em Acessibilidade, toque em Apps baixados → Free Yourself e ative.",
        restricted = "Na tela do app, toque em Permitir configurações restritas (ou ⋮ → Permitir configurações restritas) e confirme.",
        battery = "Na tela do app, toque em Economia de bateria → Sem restrições.",
        autostart = "Ative Free Yourself na lista de Inicialização automática. Se abrir a tela do app, ative Inicialização automática ali.",
        dns = "Em Configurações, toque em Conexão e compartilhamento → DNS privado → Nome do host do provedor, cole e toque em Salvar.",
    )
    Brand.OTHER -> DeviceGuide(
        name = "seu Android",
        accessibility = "Em Acessibilidade, procure Free Yourself (às vezes em Apps baixados ou Aplicativos instalados) e ative.",
        restricted = "Na tela do app, toque em ⋮ (canto superior direito) → Permitir configurações restritas e confirme.",
        battery = "Na tela do app, toque em Bateria → Sem restrições (ou Não otimizar).",
        autostart = null,
        dns = "Procure DNS privado (em Rede e internet ou Conexões) → Nome do host do provedor, cole e toque em Salvar.",
    )
}
