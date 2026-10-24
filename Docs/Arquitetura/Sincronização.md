---
tags: [arquitetura, sync, pluggy]
atualizado: 2026-10-24
---

# Sincronização

Como os dados saem da Pluggy e chegam ao [[Modelo de Dados]].

## Gatilhos

| Gatilho | Quando | Observação |
|---|---|---|
| `INITIAL` | Logo depois de vincular a conexão | Evento `ConnectionLinked`, em segundo plano e só depois do commit |
| `SCHEDULED` | A cada 6 h (`wallet.sync.interval`) | Pula a conexão se a Pluggy não atualizou desde a última leitura |
| `MANUAL` | Botão "Atualizar" no app | Só 15 min depois do fim da última tentativa da conexão (`429 sync.too_soon`, ver abaixo); responde `202` e roda em segundo plano |
| `WEBHOOK` | Um aviso da Pluggy (item atualizado, erro ou transações), só com os webhooks ligados | Pelas regras do botão: cooldown, bulkhead e uma por conexão ([[#Webhooks (desde 2026-10-24, achado A21)]]). Não funciona no dev: a Pluggy não alcança o localhost |

## Limites contra a leitura repetida (desde 2026-10-21, achado A7)

Cada sincronização é uma rodada de chamadas à Pluggy, na mesma apiKey de todos os usuários.

- **Cooldown do "Atualizar":** conta do **fim da última rodada** da conexão em `sync_runs`,
  qualquer gatilho (a `INITIAL` do vínculo e a agendada inclusive) e qualquer resultado.
  Antes contava só o sucesso (`last_synced_at`): uma conexão que nunca sincronizou, ou que
  sempre falha, podia ser lida de novo a cada toque. Rodada ainda em `RUNNING` responde
  `409 sync.in_progress`, não `429`.
- **A `INITIAL`:** vincular → desvincular → vincular relia 365 dias a cada volta. Agora o
  vínculo tem limite por usuário e janela no BFF (10 por hora, `429 rate_limit.connections`)
  e um teto de 10 conexões por usuário no core. Ver [[Fluxo de Conexão#Limites]].
- **Efeito colateral:** se a 1ª sincronização acha a Pluggy ainda coletando (`SKIPPED
  provider.updating`), o "Atualizar" só volta 15 min depois; antes disso, só a agendada.
- **Quantas ao mesmo tempo** (desde 2026-10-22, achado A11): o "Atualizar" passa pelo bulkhead
  `manual-sync` do core (`wallet.bulkhead.manual-sync`, 4 no total e 2 por usuário). A vaga é da
  rodada em segundo plano, não da requisição: fica presa até a leitura na Pluggy e a gravação
  acabarem. Sem vaga, nada começa (nenhuma rodada fica `RUNNING`): `429 rate_limit.exceeded`
  quando o usuário já tem as dele rodando, `503 service.busy` quando todas estão ocupadas, os dois
  com `Retry-After`. O executor em segundo plano usa virtual threads, então sem isso nada limitava
  quantas leituras rodavam juntas. Ver [[Core#Disponibilidade]].
- **Autorização vencida (desde 2026-10-23, LGPD L5):** conexão cuja `consent_expires_at` já passou (ou que já está
  `CONSENT_EXPIRED`) **não chama a Pluggy**, em nenhum gatilho. A agendada marca a conexão `CONSENT_EXPIRED` e a
  rodada termina `SKIPPED connection.consent_expired` (o `findSchedulable` só olha `ACTIVE` e `SYNCING`, então a
  partir daí ela nem entra na volta); o "Atualizar" responde `409 connection.consent_expired`, sem abrir rodada.
  Os dados da conexão ficam. Quando a pessoa renova ([[Fluxo de Conexão#Consentimento (desde 2026-10-23)]]), a conexão
  volta a `ACTIVE` e o `ConnectionRenewed` dispara uma atualização **pelas regras do botão**: cooldown e bulkhead
  `manual-sync`; a rodada `SKIPPED connection.consent_expired` **não conta** para o cooldown (não leu nada), e o
  que for recusado espera a agendada.

## Passo a passo de uma sincronização

1. **Abre a rodada** em `sync_runs` com status `RUNNING`. Um índice único parcial
   (`WHERE status = 'RUNNING'`) só deixa uma por conexão. A segunda rodada, de outra
   thread ou de outra instância, não entra (`INSERT ... ON CONFLICT DO NOTHING`) e
   termina como `SKIPPED` (ou `409 sync.in_progress` no botão). Uma rodada presa em
   `RUNNING` há mais de 15 min é liberada como `FAILED sync.timed_out`.
2. **Lê o item** (`GET /items/{id}`). Se o banco pede ação do usuário, a conexão vai
   para `NEEDS_ATTENTION` e para aqui. Se a Pluggy ainda está coletando, termina como
   `SKIPPED`.
3. **Compara a data de atualização** do item com `provider_updated_at`. Sem novidade
   num gatilho agendado, termina como `SKIPPED sync.nothing_new`.
4. **Lê tudo do provedor, fora de transação:**
   - as contas;
   - as transações de cada conta numa janela: de `último booked_on - 7 dias` até
     hoje, e na primeira vez 365 dias;
   - as faturas dos cartões;
   - os investimentos.
5. **Grava tudo numa transação só** (`SyncWriter`):
   - contas e o snapshot de saldo do dia;
   - transações: insere ou atualiza por `provider_transaction_id`, e o que existe na
     janela e não veio recebe `deleted_at`;
   - faturas;
   - investimentos: o que sumiu ou foi resgatado recebe `closed_at`;
   - `last_synced_at` e `provider_updated_at` da conexão.
6. **Fecha a rodada** com o resultado e as contagens.

Cada chamada à Pluggy da rodada fica em `provider_calls` com o usuário, a conexão, o gatilho
(`INITIAL_SYNC`, `SCHEDULED_SYNC`, `MANUAL_SYNC`, `WEBHOOK_SYNC`), o status e a duração: ver
[[Core#Auditoria das chamadas à Pluggy (desde 2026-10-18)]].

> [!note] Por que ler antes e gravar depois
> O banco nunca fica com uma transação aberta esperando a rede, e a gravação continua
> sendo tudo ou nada. Foi isso que substituiu o `pg_try_advisory_lock` do desenho
> original: um *advisory lock* de sessão fica preso a uma conexão do pool, e o índice
> parcial resolve a concorrência sem isso.

## Por que a janela com remoção

Pela documentação da Pluggy, uma transação pode mudar de ID quando a data, a
descrição ou o valor mudam bastante: ela é apagada e recriada. Sem reconciliar a
janela, o extrato do Wallet ficaria com a mesma compra duas vezes. A Pluggy também
reprocessa os 4 dias anteriores à última atualização; os 7 dias da janela cobrem isso
com folga.

## Sinal do valor

O Wallet grava sempre **valor positivo + direção**:

| Tipo de conta | Regra |
|---|---|
| Conta corrente e poupança | `DEBIT` → `OUTFLOW`, `CREDIT` → `INFLOW` |
| Cartão de crédito | Valor positivo é compra (`OUTFLOW`); negativo é pagamento ou estorno (`INFLOW`) |

> [!warning] Confirmar no spike
> A regra do cartão vem da documentação. O spike T00 do [[Plano de Implementação]]
> captura respostas reais e confirma antes de virar código.

## Falhas

- Pluggy fora do ar ou 5xx: `sync_runs` fica `FAILED` com `provider.unavailable`; o
  próximo ciclo tenta de novo. Nada do que já estava gravado é apagado.
- Erro no meio: a sincronização de uma conexão roda numa transação só; ou grava tudo,
  ou nada.
- **Conexão desvinculada no meio:** é ação normal do usuário, não falha. A sincronização
  confere em dois pontos, ao marcar `SYNCING` e antes de gravar, e para com log `INFO`
  e `SKIPPED connection.gone`. O `SyncWriter` trava a linha da conexão antes de gravar:
  se o desvínculo chega durante a gravação, espera o commit, e o `ON DELETE CASCADE` apaga
  o que foi gravado; se chegou antes, nada é gravado. A rodada some junto com a conexão
  (cascata em `sync_runs`), então nunca fica presa em `RUNNING`.

## Webhooks (desde 2026-10-24, achado A21)

Um aviso de "item atualizado" da Pluggy vira uma sincronização logo, em vez de esperar as 6 h. A Pluggy **não assina** o
webhook, então ele é só uma **dica**: o Wallet não confia em nada do corpo e sempre lê a Pluggy por conta própria. Do corpo
só usa `eventId`, `event` e `itemId`; o resto nem é lido nem guardado.

**Registro (global, pela API da Pluggy).** `POST /webhooks` com `event`, `url` e `headers` (os nomes e os eventos são os do SDK
oficial `pluggyai/pluggy-node`; um connect token não leva header, por isso não se usa o `webhookUrl` dele). É um **comando no
servidor**, no padrão do comunicado da lista de espera: um `ApplicationRunner` que só liga com `wallet.webhooks.register=true`,
registra e sai. Idempotente: lista o que existe e, para cada um dos seis eventos (`item/updated`, `item/error`, `item/deleted`,
`transactions/created`, `transactions/updated`, `transactions/deleted`), atualiza (`PATCH`, e reativa) o que já aponta para
`<WALLET_PUBLIC_URL>/api/webhooks/pluggy` ou cria o que falta; um webhook que aponta para outro lugar não é tocado. O header é
`X-Wallet-Webhook-Secret`. Comando e variáveis em [[Deploy#Webhooks da Pluggy (opcional)]].

**Entrada.** `POST /api/webhooks/pluggy` no BFF (pública, ver [[BFF#Webhooks da Pluggy]]) confere o segredo e repassa o corpo cru a
`POST /internal/webhooks/pluggy`, que responde **202** na hora: grava o evento em `provider_webhook_events` com
`INSERT ... ON CONFLICT (event_id) DO NOTHING` (um evento repetido é 202 e não faz mais nada) e o trata **depois do commit e fora da
thread do pedido**, como a primeira sincronização depois de vincular (`WebhookReceived`).

**Tratamento** (`WebhookProcessor`), registrado em `outcome`:

| Evento / situação | O que faz | `outcome` |
|---|---|---|
| Item que ninguém vinculou | nada | `UNKNOWN_ITEM` |
| `item/deleted` | a conexão passa a `NEEDS_ATTENTION` (o status que já existe para "o banco pede uma ação"); a agendada a pula | `ITEM_DELETED` |
| Autorização vencida | não chama a Pluggy | `CONSENT_EXPIRED` |
| Qualquer outro evento de item conhecido | pede uma sincronização com o gatilho `WEBHOOK`, **pelas regras do botão "Atualizar"** (`ManualSyncService`): o cooldown de 15 min, o bulkhead `manual-sync` e uma sincronização por conexão | `SYNC_REQUESTED` |
| ... dentro do cooldown | nada; a agendada lê depois | `SKIPPED_COOLDOWN` |
| ... uma já rodando, ou sem vaga no bulkhead | nada | `SKIPPED_BUSY` |

A sincronização do gatilho `WEBHOOK` lê sempre (não pula por "nada novo", como a agendada faz): o aviso já diz que algo mudou.
Como o `item/updated` costuma chegar logo depois da leitura que o próprio Wallet pediu, é normal que boa parte dos avisos caia no
cooldown; é o preço de nenhuma dica poder furar o limite de leitura (A7). Logs `[WEBHOOK]` só com ids, nunca o conteúdo.

**Retenção.** `provider_webhook_events` perde as linhas com mais de 30 dias, toda noite (05:05,
`wallet.retention.webhooks`; `WebhookEventHousekeeping`, no padrão do `AccountHousekeeping`).

Ver [[Fluxo de Conexão]] e [[Core]].
