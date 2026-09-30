---
tags: [arquitetura, sync, pluggy]
atualizado: 2026-09-29
---

# Sincronização

Como os dados saem da Pluggy e chegam ao [[Modelo de Dados]].

## Gatilhos

| Gatilho | Quando | Observação |
|---|---|---|
| `SCHEDULED` | A cada 6 h (`wallet.sync.interval`) | Pula a conexão se a Pluggy não atualizou desde a última leitura |
| `MANUAL` | Botão "Atualizar" no app | No máximo 1 a cada 15 min por conexão (`429 sync.too_soon`) |
| `WEBHOOK` | Produção | Não funciona no dev: a Pluggy não alcança o localhost |

## Passo a passo de uma sincronização

1. **Trava a conexão** com `pg_try_advisory_lock`. Se outra sincronização estiver
   rodando, esta termina como `SKIPPED`.
2. **Lê o item** (`GET /items/{id}`). Erro de login ou consentimento vencido deixa a
   conexão em `NEEDS_ATTENTION` e para aqui.
3. **Compara a data de atualização** do item com `provider_updated_at`. Sem novidade
   num gatilho agendado, termina como `SKIPPED`.
4. **Contas** (`GET /accounts?itemId=`): atualiza saldo e limite, grava o snapshot do
   dia em `balance_snapshots`.
5. **Transações por conta**, em janela: de `último booked_on - 7 dias` até hoje (na
   primeira vez, 365 dias).
   - Insere ou atualiza por `provider_transaction_id`.
   - O que existe no Wallet dentro da janela e não veio da Pluggy recebe
     `deleted_at`.
6. **Faturas** de cada cartão (`GET /bills?accountId=`).
7. **Investimentos** (`GET /investments?itemId=`): atualiza posições; o que sumiu
   recebe `closed_at`.
8. Grava `last_synced_at`, `provider_updated_at` e o resultado em `sync_runs`.

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

## Webhooks (produção)

- Eventos úteis: `item/updated`, `item/error`, `transactions/created`,
  `transactions/updated`, `transactions/deleted`.
- A Pluggy **não assina** o webhook. Proteção: header secreto definido no cadastro
  do webhook + lista de IPs da Pluggy.
- Resposta 2xx em até 10 s: o endpoint só grava o evento (idempotente por
  `eventId`) e a sincronização roda depois.

Ver [[Fluxo de Conexão]] e [[Core]].
