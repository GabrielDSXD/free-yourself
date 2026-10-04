# Proteção contra desativação — Design

Data: 2026-10-04 · Status: aprovado em conversa

## Objetivo

Dificultar que o usuário desligue o Free Yourself num momento de impulso. Desinstalar, desligar a
acessibilidade, forçar parada e desligar o filtro de DNS passam a exigir um desafio de 7 frases;
desinstalar exige, além disso, 24 horas de espera.

## Ativação

- Item "Proteção contra desativação" nos Ajustes, disponível só com o checklist essencial completo.
- Tela de consentimento explicando: desinstalar exige frases + 24 h; modo de segurança contorna.
- Ao confirmar: `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` para um `DeviceAdminReceiver` sem
  políticas (só impede a desinstalação direta). `lockdownEnabled = true` quando o admin está ativo.
- Desligar a proteção exige o desafio (senão seria um atalho para burlar).

## Telas protegidas

O `GuardService` observa `TYPE_WINDOW_STATE_CHANGED`/conteúdo de pacotes do sistema
(`com.android.settings`, instaladores `com.google.android.packageinstaller`/`com.android.packageinstaller`,
`com.miui.securitycenter`) e, com a proteção ligada, cobre com o overlay "Protegido pelo Free Yourself":

| Ação | Reconhecimento | Liberação |
|---|---|---|
| Tela do app (Desinstalar, Forçar parada, Bateria) | janela de Ajustes contendo "Free Yourself" + "Desinstalar"/"Forçar parada" | 10 min (exceto desinstalar) |
| Diálogo de desinstalação | instalador do sistema contendo "Free Yourself" | só após 24 h |
| Administradores do dispositivo | Ajustes contendo "Free Yourself" + "administrador" | só após 24 h (faz parte de desinstalar) |
| Acessibilidade do app | Ajustes contendo "Free Yourself" + interruptor/"Usar" do serviço | 10 min |
| DNS privado | Ajustes contendo "Nome do host do provedor" e filtro DNS ativo | 10 min |

Só janelas desses pacotes do sistema são consultadas (`findAccessibilityNodeInfosByText`); nenhum
outro app é lido.

## Desafio

- Overlay: "Voltar" (BACK) e "Fazer o desafio" (abre a tela do desafio no app).
- 7 frases, uma por vez; comparação ignora maiúsculas, acentos, pontuação e espaços extras.
- Ações comuns: liberadas por 10 minutos após o desafio.
- Desinstalar: o desafio inicia uma contagem de 24 h; após ela, desinstalação liberada por 1 h.
  Durante a espera: tempo restante + "Cancelar e manter a proteção".
- Frases (fixas, num único arquivo, editáveis): mistura de compromisso e autodepreciação,
  conforme decisão do produto.

## Regras (`core/Lockdown`, Kotlin puro)

- Prazos no relógio confiável (elapsed no mesmo boot; após reboot, parede limitada ao prazo original),
  como os bloqueios do `Guard`: mudar a hora não encurta a espera.
- Estado persistido: pedido de desinstalação (fim da espera), liberações temporárias por ação.

## Limitações

Modo de segurança, adb e restauração de fábrica contornam; mudanças de layout dos Ajustes por
fabricante podem exigir ajuste; Play Store provavelmente recusa o uso de administrador do dispositivo
para impedir desinstalação.

## Testes

- JVM: espera de 24 h, janela de 1 h, liberação de 10 min, cancelamento, relógio adiantado, reboot,
  estado corrompido; conferência das frases (acentos, maiúsculas, pontuação, frase incompleta).
- Aparelho (S22 e Motorola): cada tela protegida é coberta; desafio libera; 24 h não é burlável
  pelo relógio; modo de segurança documentado.
