# Margem — Design

Data: 2026-10-04 · Status: aprovado em conversa, aguardando revisão da spec escrita

## 1. Objetivo

App Android que ajuda o usuário a manter a decisão de não consumir conteúdo adulto no celular.
Detecta conteúdo adulto na tela (localmente) e aplica **fricção progressiva**: 3 avisos, depois
bloqueios crescentes do app onde o conteúdo apareceu, com **reset diário** à meia-noite local.
O tom é de apoio ("estou te ajudando a manter sua decisão"), nunca punitivo ou moralista.

Referência de conceito/UX: Unchained (Google Play). Nenhuma interface, código ou identidade copiados.

### Critérios de sucesso

- Detecção real em navegadores e apps comuns, sem enviar nada para fora do aparelho.
- Progressão aviso → bloqueio correta, sobrevivendo a kill do processo e reboot.
- Reset diário confiável, resistente a alteração manual do relógio (dentro do mesmo boot).
- Consumo de bateria baixo (medido no aparelho real).
- UI com aparência de produto de bem-estar digital, acessível, com dark mode.

## 2. Plataforma e stack

- **Android nativo, Kotlin + Jetpack Compose.** minSdk 30 (Android 11, exigido por
  `AccessibilityService.takeScreenshot`). targetSdk = SDK estável mais recente instalado.
- **iOS fora do escopo:** não há API pública para ler ou capturar a tela de outros apps
  (Screen Time/FamilyControls só bloqueia categorias/domínios, sem análise de conteúdo).
- **Um único módulo `app`.** A lógica de regras fica no pacote `core`, Kotlin puro sem imports
  Android, testado na JVM.
- Distribuição inicial: APK local (sideload via adb). Publicação na Play Store exige declaração de
  uso de AccessibilityService e revisão do Google — documentado, não tratado agora.

### Dependências

| Dependência | Por quê |
|---|---|
| Jetpack Compose (BOM) + Material3 | UI declarativa; padrão atual do Android |
| `androidx.activity:activity-compose` | host Compose da Activity |
| `com.google.ai.edge.litert:litert` | inferência on-device do modelo NSFW (sucessor do TFLite) |
| JUnit 4 (teste) | testes JVM do núcleo |
| androidx.test (androidTest) | 1 teste instrumentado do modelo |

Não usados de propósito: Room (contadores cabem em SharedPreferences), DI (poucos objetos),
Navigation (4 telas → `when`), kotlinx.serialization (estado serializado em formato chave=valor
simples), Google Fonts downloadable (fonte empacotada; app não tem INTERNET).

### Modelo

[GantMan/nsfw_model](https://github.com/GantMan/nsfw_model) release 1.2.0,
`mobilenet_v2_140_224/saved_model.tflite` (17 MB, float32, licença MIT). Entrada 224×224 RGB
normalizada para [0,1]. Saída: 5 probabilidades `[drawings, hentai, neutral, porn, sexy]`.
Quantização para reduzir tamanho fica para depois (exige toolchain Python/TF).

## 3. Arquitetura

```
GuardService (AccessibilityService) — único ponto de integração com o sistema
 ├─ eventos de janela/conteúdo ─► TextDetector (árvore de acessibilidade × lista local)
 ├─ throttle ─► takeScreenshot ─► ImageDetector (LiteRT)   [bitmap só em memória]
 ├─ detecção positiva ─► Guard.onDetection(pkg) ─► Decision: Warning(n) | Block(d) | Ignore
 └─ OverlayController (TYPE_ACCESSIBILITY_OVERLAY; sem permissão de sobreposição)

core/ (Kotlin puro)
 ├─ Policy          — constantes centralizadas e configuráveis
 ├─ getBlockDuration(attempt, policy)
 ├─ TrustedClock    — instante confiável (parede + elapsedRealtime + boot count)
 ├─ DayState / ActiveBlock / History — dados + encode/decode chave=valor
 └─ Guard           — decisões, rollover diário, carência, retomada de bloqueio

data/Store          — SharedPreferences ⇄ estado serializado
ui/                 — Onboarding, Hoje, Semana, Ajustes, overlays (Compose)
```

`ContentDetector` é uma interface com duas implementações reais (`TextDetector`, `ImageDetector`):

```kotlin
class ScreenFrame(val pkg: String, val text: String?, val bitmap: Bitmap?)
fun interface ContentDetector { fun isAdult(frame: ScreenFrame): Boolean }
```

O serviço monta o `ScreenFrame` (texto em todo evento; bitmap só quando o throttle permite tirar
screenshot) e aplica os detectores em sequência — basta um positivo. Cada detector ignora o frame
se o campo que ele usa for nulo. Novos métodos de detecção = nova implementação, sem mudar o serviço.

## 4. Regras do núcleo

### 4.1 Policy (configuração central)

```kotlin
data class Policy(
    val warnings: Int = 3,
    val initialBlock: Duration = 30.seconds,   // Ajustes: 15s / 30s / 1min
    val growthFactor: Double = 2.0,            // fixo no código
    val maxBlock: Duration = 2.hours,          // Ajustes: 30min / 1h / 2h
    val grace: Duration = 30.seconds,          // após "Continuar mesmo assim"
    val settle: Duration = 3.seconds,          // após "Voltar"
)
```

### 4.2 Duração do bloqueio

```
getBlockDuration(attempt) =
    attempt <= warnings → 0 (aviso)
    senão → min(initialBlock × growthFactor^(attempt − warnings − 1), maxBlock)
```

Padrão: 4→30s · 5→1min · 6→2min · 7→4min · 8→8min · 9→16min · 10→32min · 11→64min · 12+→2h.

### 4.3 O que conta como tentativa

Uma detecção positiva conta como tentativa, **exceto** se:
1. um overlay (aviso ou bloqueio) está visível;
2. o pacote está sob bloqueio ativo → apenas reexibe o overlay de bloqueio, sem contar;
3. menos de `grace` desde "Continuar mesmo assim";
4. menos de `settle` desde "Voltar"/"Ir para o início".

Tentativas 1–3 → avisos (textos do brief). Tentativa ≥4 → bloqueio de `getBlockDuration(n)`
do pacote onde ocorreu.

### 4.4 Reset diário

- `DayState(date: LocalDate, attempts, warnings, blocks, blockedMs)`.
- `Guard.rollIfNeeded(now)` roda: em cada evento do serviço, ao abrir/voltar o app, em cada detecção.
- Se `localDate(trustedNow) > state.date`: o dia atual vai para `History` (últimos 7 dias) e os
  contadores zeram. Data nunca retrocede.
- Bloqueio em andamento na virada **termina normalmente**; o dia novo começa no nível 1.
- `blockedMs` soma a duração atribuída de cada bloqueio.

### 4.5 Relógio confiável

- Âncora persistida: `(wallMs, elapsedMs, bootCount)`; `bootCount = Settings.Global.BOOT_COUNT`.
- Mesmo boot: `trustedNow = anchor.wall + (elapsedNow − anchor.elapsed)` → alterar o relógio
  manualmente não afeta reset nem bloqueios.
- Após reboot: nova âncora com o relógio de parede, `max(wallNow, últimoTrustedNowSalvo)`.
- Fuso horário: `localDate` usa a zona atual sobre o instante confiável (viagens funcionam).
- Limite conhecido: mudar o relógio **e** reiniciar burla o reset. Corrigir exigiria hora de
  rede, conflitando com "sem internet".

### 4.6 Bloqueio persistente

- `ActiveBlock(pkg, endElapsedMs, endWallMs, durationMs, bootCount)`.
- Mesmo boot: restante = `endElapsed − elapsedNow`.
- Após reboot: restante = `clamp(endWall − trustedNow, 0, duration)` — nunca mais longo que o original.
- Ao terminar: estado limpo (pacote apagado), overlay mostra "Bloqueio encerrado" se visível;
  notificação neutra se o usuário não estiver no app bloqueado.

### 4.7 Estados

Sem máquina de estados explícita; derivados de `DayState` + `ActiveBlock?` + overlay visível:
IDLE, WARNING_1..3, BLOCKING, BLOCK_FINISHED. DAY_CHANGED = `rollIfNeeded`.

## 5. Detecção

### 5.1 TextDetector

- Disparo: eventos `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED`, debounce ~500ms.
- Coleta `text` + `contentDescription` da janela ativa (limite ~300 nós / ~5k chars). Pega a barra
  de URL de qualquer navegador e buscas (`?q=`).
- Normalização: minúsculas, sem acentos.
- Lista local (asset `blocklist.txt`): ~150 domínios adultos conhecidos; tokens de host
  (`porn`, `xxx`, `hentai`, `nsfw`…); termos explícitos fortes pt/en com fronteira de palavra.
- Termos ambíguos ("sexo", "nude") não disparam sozinhos.

### 5.2 ImageDetector

- Quando: após evento de conteúdo, no máximo 1×/2s; + verificação a cada 5s enquanto o mesmo app
  segue em primeiro plano (vídeo não gera eventos).
- Nunca: tela desligada/bloqueada; próprio app, launcher padrão, System UI, teclados ativos.
- Pipeline: `takeScreenshot` → `Bitmap.wrapHardwareBuffer` → cópia 224×224 ARGB → tensor float
  [0,1] → inferência → `recycle()`/`close()` imediatos.
- Sensibilidade:
  - Baixa: `porn + hentai ≥ 0.85`
  - Média (padrão): `porn + hentai ≥ 0.70`
  - Alta: `porn + hentai + 0.5·sexy ≥ 0.60`
- FLAG_SECURE → erro de screenshot ignorado (TextDetector segue).
- `ponytail:` classifica a tela inteira; miniaturas pequenas podem passar. Evolução: recortar pelos
  bounds de nós de imagem da árvore de acessibilidade.

### 5.3 Bateria

MobileNetV2 ~30–60ms CPU por inferência. Pior caso ~1 inferência/2s em uso contínuo. Medição real
no aparelho (`dumpsys batterystats`) faz parte da validação.

## 6. Privacidade e permissões

- **Sem permissão `INTERNET`** — o sistema impede qualquer envio de dados.
- Não armazena: screenshots, imagens, URLs, nomes de sites, texto lido. Histórico = contadores/dia.
  Pacote bloqueado só persiste enquanto o bloqueio está ativo.
- Sem logs com conteúdo.
- Permissões: Serviço de acessibilidade (obrigatória; explicada antes de abrir os ajustes);
  `POST_NOTIFICATIONS` (opcional, Android 13+). Nada mais.
- Notificações nunca mencionam conteúdo sexual: "O bloqueio terminou. Continue usando seu celular
  com intenção."
- Fora do escopo: impedir desativação do serviço/desinstalação ("modo estrito").

## 7. UI/UX

### 7.1 Identidade

- Nome: **Margem** (o espaço entre o impulso e a escolha). Pacote `app.margem`.
- Cores (claro): Névoa `#EEF1F4` fundo · Tinta `#1E2A3A` texto · Ardósia `#5B6B7F` secundário ·
  Lago `#2F7D78` destaque/proteção · Âmbar `#B7791F` aviso · Índigo `#3D4590` bloqueio.
  Escuro: fundo `#12161D` · superfície `#1B212B` · texto `#E6EAF0`; acentos clareados para AA.
  Sem vermelho.
- Tipografia: Manrope (OFL), empacotada; numerais tabulares no contador. Escala 40/28/20/16/14.
- Elemento memorável: círculo de respiração no bloqueio (expande 4s / contrai 6s), estático com
  "reduzir movimento".

### 7.2 Telas

- **Onboarding:** 4 telas do brief → Privacidade → Ativar acessibilidade (explicação antes de abrir
  ajustes do sistema) → Notificações (opcional) → Começar.
- **Hoje:** saudação por horário; status da proteção (com CTA "Ativar" se desligada); linha do dia
  (3 marcos de aviso + segmentos de bloqueio); "N avisos até o primeiro bloqueio"; tentativas,
  bloqueios, tempo bloqueado; "Recomeça à meia-noite (em X h Y min)".
- **Semana:** barras horizontais de tentativas nos últimos 7 dias + totais. Sem streaks/conquistas.
- **Ajustes:** proteção (atalho ao ajuste do sistema), tempo inicial, teto, sensibilidade, status do
  detector, tema (sistema/claro/escuro), vibração no aviso, privacidade, sobre.
- **Overlay de aviso:** cartão central sobre véu escuro; "Voltar" primário; "Continuar mesmo assim"
  como link de texto. Textos por tentativa conforme o brief (1: alerta; 2: segundo alerta;
  3: último aviso).
- **Overlay de bloqueio:** "Conteúdo bloqueado", motivo, contador ao vivo no círculo de respiração,
  "Hoje: Nª tentativa · Amanhã tudo recomeça", botão "Ir para o início" (GLOBAL_ACTION_HOME).
  Reaparece sempre que o app bloqueado volta ao primeiro plano. Ao zerar: "Bloqueio encerrado —
  Você pode continuar usando o dispositivo."

### 7.3 Acessibilidade

Alvos ≥48dp, `contentDescription` em elementos não textuais, contador anunciado a cada minuto,
contraste AA, escala de fonte do sistema, reduzir movimento respeitado.

## 8. Testes

1. **JVM (JUnit):** contador 1→2→3; durações crescentes a partir da 4ª; teto nunca ultrapassado;
   `2026-10-04 23:59` tentativa 8 → `2026-10-05 00:01` contador 0; carência e settle; recuperação
   de bloqueio (encode → decode → restante correto) no mesmo boot e após reboot; relógio adiantado
   e atrasado sem reset indevido nem bloqueio encurtado; TextDetector (domínios, termos, acentos,
   falsos positivos como "Essex").
2. **Instrumentado:** carregar o modelo e classificar imagem neutra como não adulta.
3. **Checklist manual via adb:** fluxo completo num navegador, overlays, bloqueio, kill do processo
   durante bloqueio, reboot, consumo de bateria.

## 9. Entregáveis

App funcional; README (execução local, build, arquitetura, permissões, detecção, limitações,
dependências e justificativas); testes automatizados; revisão final com Ponytail.

## 10. Limitações conhecidas

- Somente Android 11+; sem iOS.
- Usuário pode desativar o serviço de acessibilidade a qualquer momento (sem modo estrito).
- Janelas FLAG_SECURE não são capturadas (só texto).
- Classificação de tela inteira pode perder miniaturas pequenas.
- Mudar o relógio + reiniciar burla o reset diário.
- Play Store exige declaração/revisão do uso de AccessibilityService.
- Fabricantes com gerenciamento agressivo de bateria podem desligar o serviço; o app mostra o
  status e orienta a reativar.
