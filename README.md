# Free Yourself

App Android que ajuda você a manter a decisão de não consumir conteúdo adulto no celular. Quando algo adulto aparece na tela, o Free Yourself intervém com **fricção progressiva**: primeiro avisos, depois bloqueios curtos do app onde o conteúdo apareceu, cada vez mais longos no mesmo dia. À meia-noite tudo recomeça.

```
detecção → consciência → aviso → fricção → bloqueio → reflexão → novo começo
```

Tudo roda no aparelho. O app **não tem permissão de acesso à internet**.

## Como funciona

| Detecção no dia | O que acontece |
|---|---|
| 1ª | Aviso: "Conteúdo potencialmente adulto detectado" |
| 2ª | Aviso: "Este é o segundo alerta de hoje" |
| 3ª | Último aviso: as próximas detecções bloqueiam |
| 4ª | Bloqueio de 30 s |
| 5ª | 1 min |
| 6ª | 2 min |
| 7ª | 4 min |
| 8ª | 8 min |
| 9ª | 16 min |
| 10ª | 32 min |
| 11ª | 64 min |
| 12ª em diante | 2 h (teto) |

- A regra é `min(inicial × 2^(n − 4), teto)`, definida em `core/Policy.kt` (`getBlockDuration`). O bloqueio inicial (15 s / 30 s / 1 min) e o teto (30 min / 1 h / 2 h) são ajustáveis no app.
- **Carência**: depois de "Continuar mesmo assim", novas detecções só contam após 30 s. Sem isso, um conteúdo parado na tela passaria pelos três avisos em segundos.
- **Escopo do bloqueio**: só o app onde houve a detecção fica coberto. Os outros apps, as ligações e a emergência continuam funcionando.
- **Reset diário**: à meia-noite local, contadores e nível voltam ao início. Um bloqueio que esteja em andamento termina normalmente.
- **Relógio**: alterar a hora do aparelho não zera o dia nem encurta bloqueios. O tempo é ancorado no relógio monotônico do sistema (veja Limitações).

## Executar localmente

Pré-requisitos:
- JDK 17 ou mais recente.
- Android SDK com `platform-tools` e `platforms;android-37.0`. Basta o command-line tools, sem Android Studio.
- Aparelho com **Android 11+** e depuração USB ligada.

```bash
echo "sdk.dir=<caminho do Android SDK>" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Abra o Free Yourself e siga o onboarding. Para ativar a proteção, vá em **Ajustes do Android → Acessibilidade → Apps instalados → Free Yourself**.

Se o Android disser que a configuração é **restrita** (acontece com apps instalados fora da loja), vá em Informações do app → menu ⋮ → Permitir configurações restritas, e tente de novo.

## Build de release

```bash
./gradlew assembleRelease
# APK sem assinatura em app/build/outputs/apk/release/app-release-unsigned.apk
keytool -genkeypair -v -keystore free-yourself.jks -alias free-yourself -keyalg RSA -keysize 2048 -validity 10000
"$ANDROID_HOME/build-tools/<versão>/apksigner" sign --ks free-yourself.jks --out free-yourself.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## Testes

```bash
./gradlew testDebugUnitTest          # JVM, sem aparelho
./gradlew connectedDebugAndroidTest  # no aparelho conectado
```

> `connectedDebugAndroidTest` **desinstala o app** ao terminar (inclusive contadores e a ativação do serviço). Rode-o antes de instalar o APK para uso, ou reinstale e reative a proteção depois.

| Suíte | Cobre |
|---|---|
| `PolicyTest` | Avisos sem bloqueio; 4ª → 30 s, 5ª → 1 min, 6ª → 2 min; crescimento; teto nunca ultrapassado; limiares de sensibilidade |
| `GuardTest` | Contador 1→2→3; progressão; bloqueio ativo não conta de novo; carência e retorno; **reset `2026-10-04 23:59` (tentativa 8) → `2026-10-05 00:01` (contador 0)**; bloqueio que atravessa a meia-noite; **recuperação após matar o processo e após reboot**; **relógio adiantado/atrasado** sem reset nem bloqueio encurtado; fuso para oeste; teto; ajuste mudado no meio do dia; estado corrompido; histórico com lacunas |
| `UrlDetectorTest` | Domínios e subdomínios; palavras soltas e buscas **não** disparam ("nsfw", "pornô", `google.com/search?q=…`); falsos positivos ("Essex", "CPF xxx.xxx"); IDs de barra de endereço dos navegadores |
| `DnsFilterTest` | Status do filtro de DNS: sem rede, desligado, CleanBrowsing/Cloudflare reconhecidos (sem diferenciar maiúsculas), outro DNS privado |
| `FormatTest` | Durações, contador regressivo, textos do painel, dias da semana em pt-BR |
| `ImageDetectorTest` (aparelho) | Modelo carrega, saída tem 5 probabilidades, tela neutra não é adulta (com bitmap `HARDWARE`, como no screenshot real) |

## Arquitetura

```
GuardService (AccessibilityService): único ponto de integração com o sistema
 ├─ eventos de janela/conteúdo ─► UrlDetector (só a barra de endereço dos navegadores × lista local)
 ├─ throttle ─► takeScreenshot ─► ImageDetector (LiteRT)   [bitmap só em memória]
 ├─ detecção positiva ─► Guard.onDetection(pkg) ─► Warn(n) | Block | Ignore
 └─ Overlay (TYPE_ACCESSIBILITY_OVERLAY; sem permissão de sobreposição)
```

| Pacote | Responsabilidade |
|---|---|
| `core` | Regras em Kotlin puro, sem Android: `Policy`, `getBlockDuration`, `Guard` (tentativas, bloqueios, reset diário, relógio confiável, serialização) |
| `detect` | `ContentDetector`/`ScreenFrame` e as duas implementações: `UrlDetector` e `ImageDetector` |
| `data` | `Store`: SharedPreferences para o estado do `Guard` e os ajustes |
| `service` | `GuardService` (eventos, screenshots, decisões, notificação) e `Overlay` (janela Compose sobre os outros apps) |
| `ui` | Tema, onboarding, telas Hoje/Semana/Ajustes, overlays de aviso e de bloqueio |

Não há máquina de estados explícita. Os estados IDLE → WARNING_n → BLOCKING → BLOCK_FINISHED saem de `DayStats` + bloqueio ativo + overlay visível. DAY_CHANGED é o `Guard.tick()`.

## Detecção

Os dois detectores implementam `ContentDetector`. Para criar um novo método, basta escrever uma nova implementação; o serviço não muda.

**UrlDetector** (barato, instantâneo)
- **Palavras nunca disparam.** Qualquer pessoa pode escrever "nsfw" numa mensagem; o app só reage a um site adulto aberto ou a uma imagem adulta na tela.
- Roda só em navegadores (apps instalados que abrem links `https`), no máximo um evento a cada 500 ms (`notificationTimeout`).
- Lê apenas a barra de endereço, achada pelo ID da view (`url_bar`, `location_bar`, `omnibar`…; serviço com `flagReportViewIds`). Mensagens, posts e o conteúdo das páginas não são lidos.
- Extrai só o host e compara com `assets/blocklist.txt` (domínios e trechos de nome de site). Caminho e busca (`?q=…`) não contam.
- Limites: navegadores embutidos em outros apps (Instagram, por exemplo) não têm o endereço reconhecido, e buscas não disparam; nos dois casos vale o detector de imagem.

**ImageDetector** (pega imagens sem texto)
- Roda em qualquer app (e nos navegadores quando o endereço não é adulto), no máximo uma vez a cada 2 s, e a cada 5 s enquanto o mesmo app continua aberto (vídeo não gera eventos).
- Nunca roda com a tela desligada ou bloqueada, nem no launcher, no teclado, na System UI ou no próprio app.
- `AccessibilityService.takeScreenshot` → bitmap reduzido para 224×224 → MobileNetV2 ([nsfw_model](https://github.com/GantMan/nsfw_model)) → 5 probabilidades (drawings, hentai, neutral, porn, sexy). O bitmap é descartado logo em seguida.
- Sensibilidade: Baixa `porn+hentai ≥ 0,85` · Média `≥ 0,70` · Alta `porn+hentai+0,5·sexy ≥ 0,60`.
- Janelas protegidas (FLAG_SECURE, como apps de banco) recusam o screenshot. Nesses casos vale só o endereço, quando é um navegador.

**Filtro de sites por DNS** (opcional, nos Ajustes)
- Usa o **DNS privado** nativo do Android (DNS-over-TLS, Android 9+) apontado para um filtro público: CleanBrowsing Adulto (`adult-filter-dns.cleanbrowsing.org`, padrão; força a busca segura no Google/Bing) ou Cloudflare Família (`family.cloudflare-dns.com`, adulto + malware).
- Vale para o aparelho inteiro, todos os apps, sem VPN e sem bateria extra. Enxerga só o domínio, não o conteúdo: as imagens continuam com o `ImageDetector`.
- O app não consegue ativar o DNS privado sozinho: o botão "Ativar no Android" copia o hostname e abre "Rede e internet"; o status (lido de `LinkProperties.privateDnsServerName`) atualiza ao voltar.

**Bateria**: cada inferência custa ~30–60 ms de CPU. A medição real no aparelho (`dumpsys batterystats`) ainda está **pendente**: será registrada aqui depois da sessão de validação no dispositivo.

## Permissões

| Permissão | Para quê | O que **não** faz |
|---|---|---|
| Serviço de acessibilidade (obrigatória) | Ler a barra de endereço dos navegadores, tirar screenshots em memória e desenhar os avisos/bloqueios por cima dos apps | Não grava nada, não envia nada, não lê notificações nem senhas (campos de senha não expõem texto) |
| Notificações (opcional, Android 13+) | Avisar que um bloqueio terminou | Nunca cita o motivo do bloqueio |
| Estado da rede (`ACCESS_NETWORK_STATE`, normal, sem pedido ao usuário) | Ler qual DNS privado o sistema está usando, para mostrar o status do filtro | Não dá acesso à internet; não lê o tráfego nem os sites visitados |

O Free Yourself **não declara**: `INTERNET`, sobreposição (`SYSTEM_ALERT_WINDOW`), armazenamento, serviço em primeiro plano ou inicialização no boot. As permissões de serviço em primeiro plano que o LiteRT declara são removidas no manifest.

## Privacidade

> O conteúdo da tela é analisado no próprio aparelho, em memória, e descartado em seguida. O Free Yourself não grava capturas, imagens, sites ou textos: guarda só os contadores de cada dia. O app não tem permissão de acesso à internet, então nada pode ser enviado.

O que fica salvo em SharedPreferences privadas, sem backup na nuvem (`allowBackup=false`):
- Contadores dos últimos 7 dias (avisos, bloqueios, tempo bloqueado).
- Os ajustes.
- O bloqueio ativo (pacote do app e horário de término). É apagado quando o bloqueio termina.

**Filtro de DNS:** quando você ativa o DNS privado, é o Android (não o Free Yourself) que passa a enviar as consultas de endereço ao provedor escolhido. Esse provedor fica sabendo quais domínios o aparelho consulta, como acontece com qualquer DNS. O app só lê o nome do servidor configurado.

## Limitações da plataforma

- **Só Android 11+.** `takeScreenshot` para serviços de acessibilidade existe a partir da API 30.
- **Sem iOS.** O iOS não oferece API pública para ler ou capturar a tela de outros apps. O Screen Time bloqueia categorias e domínios, mas não analisa conteúdo.
- **O usuário pode desativar o serviço** nos ajustes do Android a qualquer momento. Não há "modo estrito": seria uma barreira contra a própria pessoa e é a parte mais sensível das políticas da Play Store.
- **Janelas FLAG_SECURE** não são capturadas. Nelas só o texto é analisado.
- **A imagem é avaliada como tela inteira.** Miniaturas pequenas num feed se diluem e podem passar. Evolução: recortar pelos limites das imagens que a árvore de acessibilidade já informa.
- **DNS privado**: o usuário pode desligá-lo nos ajustes do Android, e navegadores com "DNS seguro" próprio configurado manualmente (ex.: Chrome com um provedor escolhido) o ignoram. Ele só enxerga domínios.
- **Picture-in-picture**: ao tocar "Ir para o início" durante um bloqueio, players com PiP automático podem continuar o vídeo numa janela flutuante. O bloqueio pede o foco de áudio, e a maioria dos players pausa, mas o PiP em si não é coberto.
- **Mudar o relógio e reiniciar o aparelho** burla o reset diário. Fechar essa brecha exigiria hora de rede, o que conflita com "sem internet".
- **Play Store**: o uso de AccessibilityService para fins que não são de acessibilidade exige declaração e revisão do Google.
- **Gerenciadores de bateria agressivos** de alguns fabricantes podem desligar o serviço. O app mostra o status e orienta a reativar.

## Dependências

| Dependência | Por quê |
|---|---|
| Jetpack Compose (BOM 2026.09.00) + Material3 | UI declarativa, padrão atual do Android; inclui os componentes de navegação, segmentos e switches usados |
| `androidx.activity:activity-compose` 1.13.0 | Host Compose da Activity e pedido de permissão de notificação |
| `com.google.ai.edge.litert:litert` 1.4.2 | Inferência on-device do modelo (`org.tensorflow.lite.Interpreter`). A 2.x foi descartada porque traz Play Asset Delivery e WorkManager (serviços, receivers, acesso à rede) sem necessidade |
| JUnit 4.13.2 | Testes JVM do núcleo |
| androidx.test runner 1.7.0 / ext-junit 1.3.0 | O teste instrumentado do modelo |
| Modelo [nsfw_model](https://github.com/GantMan/nsfw_model) 1.2.0 (MIT) | Classificador NSFW MobileNetV2 já em TFLite; licença em [`third_party/nsfw_model/LICENSE`](third_party/nsfw_model/LICENSE) |
| Fonte [Manrope](https://github.com/googlefonts/manrope) (OFL) | Tipografia empacotada (sem download); licença em [`third_party/manrope/OFL.txt`](third_party/manrope/OFL.txt) |

Não usados de propósito:
- Room: os contadores cabem em SharedPreferences.
- Injeção de dependência: são poucos objetos.
- Navigation: 3 abas resolvem com um `when`.
- Bibliotecas de serialização: o estado é um mapa chave→texto.
- Google Fonts para download: o app não tem internet.
