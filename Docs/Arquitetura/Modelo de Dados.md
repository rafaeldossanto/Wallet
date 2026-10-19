---
tags: [arquitetura, banco]
atualizado: 2026-10-18
---

# Modelo de Dados

PostgreSQL 18, migrations com **Flyway**, nomes em inglês. Dinheiro em
`NUMERIC(19,2)`, datas de negócio em `DATE`, carimbos em `TIMESTAMPTZ`.

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "tem"
    users ||--o{ connections : "tem"
    users ||--o{ fixed_expenses : "cadastra"
    users ||--o| planning_settings : "ajusta"
    users ||--o{ dismissed_fixed_expense_suggestions : "dispensa"
    connections ||--o{ accounts : "tem"
    connections ||--o{ investments : "tem"
    connections ||--o{ sync_runs : "registra"
    accounts ||--o{ transactions : "tem"
    accounts ||--o{ balance_snapshots : "tem"
    accounts ||--o{ credit_card_bills : "tem"
```

## Tabelas

**users** — `id uuid`, `email` (único, minúsculo), `password_hash`, `display_name`,
`failed_login_count`, `locked_until`, `created_at`.

**refresh_tokens** — `id`, `user_id`, `token_hash` (SHA-256, nunca o token puro),
`family_id`, `expires_at`, `rotated_at`, `revoked_at`, `created_at`. Ver
[[Autenticação]].

**connections** — `id`, `user_id` (FK `users`, cascata), `provider` (`PLUGGY`),
`provider_item_id` (único por provedor), `institution_name`, `institution_image_url`,
`status` (`ACTIVE`, `SYNCING`, `NEEDS_ATTENTION`), `provider_updated_at`,
`last_synced_at`, `consent_expires_at`, `created_at`, `updated_at`. Desvincular
apaga a linha, e a cascata leva tudo o que foi sincronizado.

**accounts** — `id`, `connection_id` (FK, cascata), `user_id`,
`provider_account_id` (único por conexão), `kind` (`CHECKING`, `SAVINGS`,
`CREDIT_CARD`, `OTHER`), `name`, `number_last_digits`, `currency_code`, `balance`,
`credit_limit`, `available_credit`, `created_at`, `updated_at`.

> [!note] `user_id` repetido
> Contas, transações e investimentos guardam `user_id`, mesmo alcançável pela conexão.
> Toda leitura filtra pelo dono num índice, sem fazer join com a tabela de outro módulo.

**balance_snapshots** — `account_id`, `snapshot_date`, `balance`. Chave
(`account_id`, `snapshot_date`). Um por dia, gravado na sincronização. É o que permite
o gráfico de patrimônio, porque o Open Finance não devolve saldo de dias passados.

**transactions** — `id`, `account_id`, `provider_transaction_id`,
`provider_reconcile_id`, `booked_on`, `description` (**criptografada**), `amount`
(sempre positivo), `direction` (`INFLOW`/`OUTFLOW`), `status`
(`PENDING`/`POSTED`), `category`, `installment_number`, `installment_total`,
`provider_bill_id`, `deleted_at`, `created_at`, `updated_at`. Único
(`account_id`, `provider_transaction_id`). Índices (`account_id`, `booked_on`) e
(`user_id`, `booked_on`) só das não removidas. `description` é `VARCHAR(2000)`, porque
o texto cifrado em base64 ocupa mais que o original.

**credit_card_bills** — `id`, `account_id`, `provider_bill_id` (único), `due_date`,
`total_amount`, `minimum_payment`, `currency_code`.

**investments** — `id`, `connection_id`, `provider_investment_id` (único), `kind`
(`FIXED_INCOME`, `TREASURY`, `FUND`, `EQUITY`, `OTHER`), `name`, `balance`,
`amount_invested`, `due_date`, `closed_at`, `updated_at`.

**investment_snapshots** — `investment_id`, `snapshot_date`, `balance`. Chave
(`investment_id`, `snapshot_date`). Mesmo papel dos `balance_snapshots`, para o gráfico
de patrimônio. Uma posição fechada ganha um snapshot zerado no dia em que fechou, senão
seria carregada para a frente para sempre.

**sync_runs** — `id`, `connection_id`, `trigger` (`INITIAL`, `SCHEDULED`, `MANUAL`,
`WEBHOOK`), `status` (`RUNNING`, `SUCCEEDED`, `SKIPPED`, `FAILED`), `error_code`,
`started_at`, `finished_at`, `accounts_count`, `transactions_upserted`,
`transactions_deleted`. Índice único parcial: uma `RUNNING` por conexão. Ver
[[Sincronização]].

**provider_calls** — a auditoria das chamadas à Pluggy, uma linha por requisição HTTP:
`id`, `called_at`, `provider` (`PLUGGY`), `operation` (`FIND_ITEM`, `LIST_TRANSACTIONS`...),
`reason` (`LINK`, `UNLINK`, `INITIAL_SYNC`, `SCHEDULED_SYNC`, `MANUAL_SYNC`,
`WEBHOOK_SYNC`), `user_id`, `connection_id`, `provider_item_id`, `outcome`
(`SUCCESS`/`FAILURE`), `http_status`, `error_code`, `duration_ms`. Índices em `called_at` e
em (`connection_id`, `called_at`). **Sem chave estrangeira**, por isso fica fora do diagrama:
o registro precisa sobreviver ao desvínculo e à exclusão do usuário. Nenhum dado financeiro,
token ou credencial. Apagado depois de 180 dias. Ver [[Core#Auditoria das chamadas à Pluggy (desde 2026-10-18)]].

**fixed_expenses** — os gastos fixos do planejamento (o que não passa no cartão): `id`,
`user_id` (FK `users`, cascata), `name`, `amount` (maior que zero), `day_of_month` (1 a 31; nos
meses mais curtos cai no último dia), `category` (uma categoria da Pluggy, opcional),
`created_at`, `updated_at`. Repetem em todo mês. Ver [[Core#Planejamento do mês (desde 2026-10-18)]].

**planning_settings** — uma linha por usuário que ajustou o planejamento: `user_id` (chave e FK
`users`, cascata), `expected_salary` (maior que zero; vazio = prever pelo mês anterior),
`created_at`, `updated_at`.

**dismissed_fixed_expense_suggestions** (V9) — as sugestões de gasto fixo que a pessoa dispensou,
para não voltarem: `id`, `user_id` (FK `users`, cascata), `description_key` (impressão digital
HMAC das palavras da descrição dos débitos, nunca a descrição), `day_of_month` (1 a 31; a dispensa
cobre 3 dias para cada lado), `created_at`. Único por (`user_id`, `description_key`,
`day_of_month`). As sugestões em si não são gravadas: saem das transações a cada leitura. Ver
[[Core#Gastos fixos sugeridos pelo extrato (desde 2026-10-18)]].

> [!warning] Validação do Hibernate
> `ddl-auto: validate` recusa `CHAR(n)` e `TEXT` onde a entidade tem `String`, porque
> espera `VARCHAR`. Por isso o schema usa `VARCHAR` em tudo, até em `currency_code`.

## Criptografia de coluna

- `transactions.description` passa por um `AttributeConverter` com **AES-256-GCM**;
  a chave vem de `WALLET_DATA_KEY`. O valor gravado leva um prefixo de versão da chave
  (`v1:`) para permitir rotação.
- Sem chave configurada, o core usa uma temporária e avisa no log. Um valor que não
  decifra (gravado com outra chave) volta como `null` em vez de derrubar a leitura.
- Valor, data, direção e categoria ficam abertos: são o que as somas e os gráficos
  usam.
- Consequência: a busca por texto no extrato não roda em SQL. O core filtra em
  memória dentro do período pedido (um mês de uma pessoa tem centenas de linhas).

## Exclusão

- Transação que some da Pluggy recebe `deleted_at` (soft delete): a Pluggy pode apagar
  e recriar a mesma transação com outro ID. Ver [[Sincronização]].
- Excluir a conta do usuário (LGPD) apaga tudo em cascata, inclusive as conexões.

Ver também [[Core]].
