---
tags: [arquitetura, sync, pluggy]
atualizado: 2026-10-21
---

# Sincronização

Como os dados saem da Pluggy e chegam ao [[Modelo de Dados]].

## Gatilhos

| Gatilho | Quando | Observação |
|---|---|---|
| `INITIAL` | Logo depois de vincular a conexão | Evento `ConnectionLinked`, em segundo plano e só depois do commit |
| `SCHEDULED` | A cada 6 h (`wallet.sync.interval`) | Pula a conexão se a Pluggy não atualizou desde a última leitura |
| `MANUAL` | Botão "Atualizar" no app | Só 15 min depois do fim da última tentativa da conexão (`429 sync.too_soon`, ver abaixo); responde `202` e roda em segundo plano |
| `WEBHOOK` | Produção | Não funciona no dev: a Pluggy não alcança o localhost |

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

## Webhooks (produção)

- Eventos úteis: `item/updated`, `item/error`, `transactions/created`,
  `transactions/updated`, `transactions/deleted`.
- A Pluggy **não assina** o webhook. Proteção: header secreto definido no cadastro
  do webhook + lista de IPs da Pluggy.
- Resposta 2xx em até 10 s: o endpoint só grava o evento (idempotente por
  `eventId`) e a sincronização roda depois.

Ver [[Fluxo de Conexão]] e [[Core]].
