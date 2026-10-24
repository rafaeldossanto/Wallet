---
tags: [arquitetura, backend, core]
atualizado: 2026-10-23
porta: 8081
repo: Work/Wallet/core
---

# Core

O serviço que **é dono dos dados e das regras**. Só o [[BFF]] conversa com ele; o
core não fica exposto à internet.

**Java 25 · Spring Boot 4.1 · Maven · PostgreSQL 18 · Flyway · Spring Modulith**

## Módulos

Monolito modular com **Spring Modulith**: cada subpacote de `com.wallet.core` é um
módulo, e um teste (`ApplicationModules.of(...).verify()`) quebra o build se um
módulo usar a parte interna de outro.

| Módulo | Responsabilidade | Depende de |
|---|---|---|
| `identity` | Usuários, cadastro com confirmação por e-mail, login, bloqueio progressivo, segundo fator (TOTP), perfil, emissão e rotação de tokens | `mail`, `connection` (só para revogar ao excluir a conta), `shared` |
| `mail` | Envio de e-mail: a porta `EmailSender` (pública, na raiz do pacote), o SMTP e o envio de desenvolvimento no log. E-mail com link de descadastro (o `waitlist` usa) e envio síncrono, `sendNow` (o comunicado) | `shared` |
| `waitlist` | Lista de espera da landing page: inscrição com confirmação por e-mail (double opt-in), descadastro em um clique, limpeza dos não confirmados e o comando do comunicado de abertura. Ver [[#Lista de espera]] | `mail`, `shared` |
| `connection` | Conexões com bancos (items da Pluggy), vincular, desvincular | `provider`, `shared` |
| `banking` | Contas, saldos, transações, cartões e faturas | `shared` |
| `investment` | Posições de investimento | `shared` |
| `sync` | Orquestra a leitura do provedor e grava nos módulos de dados | `provider`, `connection`, `banking`, `investment` |
| `provider` | Porta `FinancialDataProvider`, o adaptador da Pluggy e a auditoria das chamadas | `shared` |
| `insight` | Visão geral, patrimônio, gasto por categoria | `banking`, `investment` |
| `planning` | Planejamento do mês: salário − faturas − gastos fixos, os gastos fixos e o salário esperado | `banking`, `shared` |
| `privacy` | A exportação dos dados do usuário (LGPD, acesso e portabilidade): monta o arquivo só com as portas públicas dos outros módulos (`UserProfiles`, `ConnectionRegistry`, `BankingQueries`, `InvestmentQueries`, `PlanningExports`). Ver [[#Exportação dos dados (L9 e L10)]] | `identity`, `connection`, `banking`, `investment`, `planning`, `shared` |
| `shared` | Dinheiro, erros, paginação, criptografia de coluna, relógio, chaves de texto | — |

Dentro de cada módulo, o mesmo layout do Trilha: `controller`, `service`,
`repository`, `entity`, `dto`, `mapper`.

## Por que core + BFF, e não mais serviços

- A carga do core é quase toda **espera de rede** (Pluggy e Postgres). Com **virtual
  threads** do Java 25 (`spring.threads.virtual.enabled=true`), milhares de chamadas
  esperando custam pouca memória.
- Ordem de grandeza: 10 mil usuários com 2 conexões cada, sincronizando a cada 6 h,
  dão perto de **1 sincronização por segundo**, de 5 a 10 chamadas HTTP cada. Um
  processo numa VPS de 2 vCPU aguenta. O que cresce primeiro é a conta da Pluggy.
- Separar sincronização e dados em serviços próprios exigiria mensageria entre eles e
  dados que não batem por alguns instantes, sem ganho nessa escala.

**Preparado para crescer sem reescrever:**

1. **Sem estado:** JWT + refresh no banco. Dá para rodar 2 instâncias.
2. **Agendador desligável** (`wallet.sync.scheduler-enabled`). Com 2 instâncias, só
   uma agenda; o lock por conexão já impede sincronização duplicada.
3. **Mesmo jar em dois papéis:** se a sincronização pesar, uma cópia roda só o
   agendador e a outra só atende o BFF.
4. **Fronteiras do Spring Modulith:** um módulo que precise virar serviço já não
   depende do interior dos outros.

## Porta do provedor

```java
public interface FinancialDataProvider {
    ProviderItem findItem(String itemId);
    List<ProviderAccount> listAccounts(String itemId);
    List<ProviderTransaction> listTransactions(String accountId, LocalDate from, LocalDate to);
    List<ProviderBill> listBills(String accountId);
    List<ProviderInvestment> listInvestments(String itemId);
}
```

- Os `Provider*` são records do Wallet, não da Pluggy. Os DTOs da Pluggy ficam em
  `provider.pluggy` e não saem de lá.
- `PluggyFinancialDataProvider` usa `RestClient`, faz `POST /auth` e guarda o
  `apiKey` em memória (vale 2 h; renova com 10 min de folga, uma renovação por vez).
- **Normalização do sinal** acontece aqui: o Wallet grava valor positivo + direção
  (`INFLOW`/`OUTFLOW`). Na Pluggy o cartão tem sinal invertido em relação à conta.
  Ver [[Sincronização]].
- Paginação da Pluggy (máx. 500 por página) é resolvida dentro do adaptador.
- Testado com **WireMock**, usando as respostas capturadas no spike (T02).

## Auditoria das chamadas à Pluggy (desde 2026-10-18)

Toda requisição HTTP que o core faz à Pluggy vira uma linha em `provider_calls` e uma
linha de log `[PROVIDER_CALL]`, para monitorar o uso e investigar problemas.

| O quê | Campo | Valores |
|---|---|---|
| Quando | `called_at` | Início da chamada |
| Quem | `user_id` | O usuário do Wallet, quando há um |
| Qual conexão | `connection_id`, `provider_item_id` | Vazio no vínculo, antes de a conexão existir |
| O quê | `operation` | `AUTH`, `FIND_ITEM`, `LIST_ACCOUNTS`, `LIST_TRANSACTIONS`, `LIST_BILLS`, `LIST_INVESTMENTS`, `DELETE_ITEM`, `CREATE_CONNECT_TOKEN` |
| Por quê | `reason` | `CONNECT` (token do Pluggy Connect), `LINK`, `UNLINK`, `ACCOUNT_DELETION` (revogação antes de excluir a conta), `INITIAL_SYNC`, `SCHEDULED_SYNC`, `MANUAL_SYNC`, `WEBHOOK_SYNC` |
| Resultado | `outcome`, `http_status`, `error_code` | `SUCCESS` ou `FAILURE`; status vazio quando não veio resposta |
| Duração | `duration_ms` | Do envio até ler o corpo inteiro |

- **Nunca grava** valores, descrições, corpo de requisição ou resposta, `apiKey` ou
  credenciais. O registro só tem os campos acima.
- **Uma linha por requisição:** cada página de uma lista paginada conta, e a renovação do
  `apiKey` aparece como `AUTH` com o motivo da chamada que a provocou (um 401 aparece como a
  chamada recusada, o `AUTH` e a nova tentativa).
- **Como:** um interceptor no `RestClient` da Pluggy (`PluggyAuditInterceptor`) vê todas as
  chamadas, inclusive o `POST /auth`, sem mudar o `PluggyClient`. Um endpoint novo é gravado
  desde a primeira chamada; se ainda não tem nome na tabela (`PluggyOperations`), vira
  método + recurso sem ids (`GET /connectors/201` → `GET_CONNECTORS`).
- **Quem e por quê** vêm do `ProviderCallContext`, um `ScopedValue` do Java 25 que a
  sincronização e o vínculo/desvínculo abrem em volta das chamadas. Chamada fora de um
  contexto é gravada mesmo assim, sem usuário, conexão e motivo.
- **Transação própria** (`REQUIRES_NEW`, via `JdbcTemplate`): o vínculo chama a Pluggy dentro
  da transação dele, e o registro fica mesmo quando o vínculo dá rollback. Falha ao gravar
  vira `WARN` e nunca derruba sincronização nem vínculo.
- **Códigos de erro:** `provider.auth_failed` (401/403), `provider.not_found` (404),
  `provider.rate_limited` (429), `provider.unavailable` (outros 4xx e 5xx). Sem resposta:
  `provider.unreachable` (conexão recusada, DNS ou tempo de conexão esgotado: é assim que um
  bloqueio de saída aparece), `provider.timeout` (conectou e não respondeu a tempo) e
  `provider.connection_failed` (a conexão caiu no meio).
- **Log:** uma linha por chamada, `INFO` no sucesso e `WARN` na falha, sem os campos vazios.
  Os campos também vão como key-values do SLF4J, que o log estruturado do Spring Boot
  (`logging.structured.format.console=ecs` ou `logstash`) transforma em campos JSON quando a
  plataforma de logs for escolhida.

```
[PROVIDER_CALL] provider=PLUGGY operation=LIST_ACCOUNTS outcome=SUCCESS status=200 durationMs=84 reason=MANUAL_SYNC userId=… connectionId=… itemId=…
```

- **Falha no log do adaptador** (desde 2026-10-21, achado A20): o `PluggyClient` registra só o
  status e o código (`[PLUGGY] Request failed: status=500 code=provider.unavailable`; sem
  resposta, `no response, code=provider.timeout`), nunca a mensagem da exceção, que traz o corpo
  da resposta da Pluggy e pode ecoar o que foi enviado ou descrever a conta da pessoa.

- **Retenção:** 180 dias (`wallet.provider.audit.retention`), apagados toda noite às 04:30
  de São Paulo (`wallet.provider.audit.cleanup-cron`), em todas as instâncias.
- **Sem chave estrangeira** de propósito: o registro de um desvínculo, ou das chamadas de um
  usuário que excluiu a conta, sobrevive às linhas que ele cita. Depois da exclusão, o
  `user_id` não aponta para ninguém, e some com os 180 dias.

## API interna

Tudo em `/internal`, JSON em inglês, dinheiro como string (`"1234.56"`), datas
ISO-8601. Paginação `{ items, page, pageSize, total, totalPages }`. Toda rota, menos
login, cadastro, refresh, os links dos e-mails e a lista de espera (`/internal/auth/**` e
`/internal/waitlist/**`), exige o JWT do usuário, que o BFF repassa.

**Chave de gateway (desde 2026-10-19):** toda requisição, inclusive login, cadastro, refresh e
o JWKS, também precisa do header `X-Gateway-Key` com o segredo compartilhado com o BFF
(`WALLET_GATEWAY_KEY`). Sem ele, ou com o valor errado, o `GatewayKeyFilter` responde **403**
`gateway.forbidden` antes de qualquer outra coisa (sem ler token, sem BCrypt, sem banco). Só a
liveness, `/livez`, fica aberta (desde 2026-10-22; ver [[#Saúde]]). 403 e não 401 de propósito: o BFF trata o 401 do core como
sessão vencida e o app deslogaria o usuário por um erro de configuração do servidor. A
comparação é em tempo constante (SHA-256 dos dois lados + `MessageDigest.isEqual`). Assim o
core não depende de estar numa rede interna: quem alcança a porta sem a chave não consegue nada.
A chave de desenvolvimento está neste repositório público, então o core só a aceita escutando no
loopback (o padrão, `127.0.0.1`); com `WALLET_CORE_ADDRESS` apontando para a rede, ele não sobe
sem uma `WALLET_GATEWAY_KEY` real.

| Método | Rota | O que faz |
|---|---|---|
| POST | `/internal/auth/register` | **202 sem corpo** para qualquer e-mail: e-mail novo vira conta não confirmada e recebe o link; e-mail com conta recebe um aviso ao dono. Ver [[Autenticação#Cadastro com confirmação por e-mail (achado A16, desde 2026-10-22)]] |
| POST | `/internal/auth/login` | `{email, password, code?}` → `{ accessToken, expiresIn, refreshToken }`. Lê `X-Wallet-Client-Address` (do BFF) para o bloqueio por endereço. Conta não confirmada responde como senha errada; com 2FA, sem `code` é `401 auth.two_factor_required` |
| POST | `/internal/auth/email/confirm` | `{token}` do link: `{result: ACCOUNT_CONFIRMED}` ou `{result: EMAIL_CHANGED}`; link desconhecido, vencido, usado ou trocado por outro é `422 auth.link_invalid`; endereço tomado no meio do caminho é `409 auth.email_taken` |
| POST | `/internal/auth/email/resend` | `{email}`: outro link para conta não confirmada (até 3 por hora); 202 para qualquer e-mail |
| POST | `/internal/auth/refresh` | Troca o refresh por um par novo |
| POST | `/internal/auth/logout` | Revoga a família do refresh |
| GET | `/internal/me` | Usuário logado: `{id, email, displayName, createdAt, pendingEmail, twoFactorEnabled}` |
| PUT | `/internal/me` | `{displayName}`: troca o nome |
| POST | `/internal/me/email` | `{newEmail, password}`: 202; manda o link ao novo endereço e a conta só muda quando ele é aberto (o antigo recebe aviso). Senha errada é 403 `auth.wrong_password` |
| POST | `/internal/me/two-factor/setup` | `{password}`: segredo novo, ainda desligado → `{secret, otpauthUri}` |
| POST | `/internal/me/two-factor/enable` | `{code}`: o primeiro código liga e devolve `{recoveryCodes}` (10, mostrados uma vez); código errado é 422 `two_factor.invalid_code` |
| DELETE | `/internal/me/two-factor` | `{password, code}` (código do app ou de recuperação): desliga (204); código errado é 403 `auth.invalid_two_factor_code` e conta para o bloqueio |
| DELETE | `/internal/me` | Exclui a conta e tudo dela (204; com o token de uma conta já excluída, 401 como qualquer rota), com `{"password"}` no corpo: senha errada é 403 `auth.wrong_password` e conta para o bloqueio. Revoga as conexões na Pluggy antes; ver [[Modelo de Dados#Exclusão]] |
| GET | `/internal/me/export` | Tudo o que o Wallet guarda do usuário, num JSON (módulo `privacy`; ver [[#Exportação dos dados (L9 e L10)]]) |
| GET | `/internal/connections` | Conexões do usuário e o estado de cada uma |
| POST | `/internal/connections` | Vincula um item (`{ "providerItemId": "..." }`). Item de outra pessoa, vinculado ou não, responde como inexistente (404 `connection.item_not_found`); só o dono ouve 409 `connection.already_linked`. Até 10 conexões por usuário (422 `connection.limit_reached`). Ver [[Fluxo de Conexão#Limites]] |
| POST | `/internal/connections/connect-token` | Token do widget Pluggy Connect para o usuário (`{ "connectToken": "..." }`, 30 min, uma conexão) |
| DELETE | `/internal/connections/{id}` | Desvincula (em produção, revoga na Pluggy) |
| POST | `/internal/connections/{id}/sync` | Pede sincronização agora: 15 min depois do fim da última tentativa da conexão, qualquer gatilho e resultado (429 `sync.too_soon`). Bulkhead `manual-sync` |
| GET | `/internal/overview` | Patrimônio, saldo, dívida do cartão, investimentos, entradas e saídas do mês, última sincronização. Bulkhead `screens` |
| GET | `/internal/accounts` | Contas com saldo, lista plana com `connectionId` (o BFF agrupa por instituição) |
| GET | `/internal/transactions` | Extrato com filtros `from`, `to` (padrão: mês corrente, máx. 366 dias), `accountId`, `direction`, `q`, paginado. Com `q`: máx. 93 dias, até 5000 transações lidas e bulkhead `search` ([[#Busca por texto]]) |
| GET | `/internal/credit-cards` | Cartões com limite, disponível e a próxima fatura a pagar |
| GET | `/internal/credit-cards/{accountId}/bills` | Faturas do cartão, da mais recente para a mais antiga |
| GET | `/internal/investments` | Posições abertas e total por tipo |
| GET | `/internal/insights/spending-by-category?month=2026-09` | Gasto por categoria no mês (padrão: mês corrente) |
| GET | `/internal/insights/net-worth?from=&to=` | Patrimônio dia a dia (padrão: últimos 30 dias, máx. 731). Lê só os snapshots do período e um anterior por conta e posição. Bulkhead `screens` |
| GET | `/internal/planning/month?month=2026-10` | O planejamento do mês (padrão: mês corrente): salário, faturas com as compras, gastos fixos e o que sobra. Bulkhead `screens` |
| GET, POST | `/internal/planning/fixed-expenses` | Lista e cria gastos fixos (`{ name, amount, dayOfMonth, category }`) |
| PUT, DELETE | `/internal/planning/fixed-expenses/{id}` | Altera e exclui um gasto fixo |
| PUT, DELETE | `/internal/planning/expected-salary` | Define o salário esperado (`{ amount }`) ou volta à previsão automática |
| GET | `/internal/planning/fixed-expense-suggestions` | Débitos que se repetem todo mês nas contas e ainda não são gastos fixos. Bulkhead `screens` |
| DELETE | `/internal/planning/fixed-expense-suggestions/{id}` | Dispensa uma sugestão para sempre (204, também se já estava dispensada) |

O core **não sabe** se o usuário está no celular ou no navegador: sempre devolve os
tokens no corpo. Cookie e CORS são assunto do [[BFF]].

### Regras dos números

- **Patrimônio** = saldo das contas + investimentos abertos − saldo dos cartões.
- **Entradas e saídas do mês** contam só as contas bancárias. A compra no cartão entra
  quando a fatura é paga; contar as duas coisas duplicaria o gasto.
- **Gasto por categoria** soma as saídas de todas as contas, menos o pagamento de fatura
  vindo da conta (categoria `Credit card payment` da Pluggy, a confirmar na T02).
- **Histórico:** dia sem snapshot repete o último valor conhecido. Antes da primeira
  sincronização o valor é zero. Desde 2026-10-22 (achado A13) a consulta lê **só o período e,
  por conta e por posição, o último snapshot antes dele** (é ele que dá o valor do primeiro dia),
  com `JOIN LATERAL ... LIMIT 1` sobre a chave primária `(id, snapshot_date)`. Antes lia todo o
  histórico até o fim do período: com anos de snapshots, cada gráfico de 30 dias trazia milhares
  de linhas. O resultado é o mesmo; `ReadApiIT` confere com três anos de snapshots diários.
- **Busca por texto:** ignora maiúsculas e acentos e roda em memória, porque a descrição fica
  cifrada (AES-256-GCM): o banco não consegue filtrar. Ver [[#Busca por texto]].

Erros de leitura: `period.invalid`, `period.too_long` e `page.invalid` (422),
`request.invalid_parameter` (400) e `account.not_found` (404). Das rotas com bulkhead e do pool
cheio: `rate_limit.exceeded` (429) e `service.busy` (503), os dois com `Retry-After: 1`
([[#Disponibilidade]]).

### Busca por texto

Achado A10: a busca carregava **todas** as transações do período (até 366 dias) como entidades,
decifrava cada descrição e só então paginava, e refazia tudo a cada página. Desde 2026-10-22:

| Limite | Valor | Por quê |
|---|---|---|
| Janela | **93 dias** com `q` (sem `q` continua 366) | O app busca um mês por vez (o filtro do extrato é mensal); um trimestre dá folga a quem chama a API sem reler um ano |
| Linhas lidas | **5000** por busca (`BankingReadService.MAX_SEARCHED_ROWS`) | Um mês cheio tem centenas; 5000 é uma pessoa com várias contas no trimestre inteiro e custa milissegundos de decifração. Passou disso: 422 `period.too_long` ("diminua o período"), **nunca** um resultado pela metade: uma busca que pula linhas diria "nada encontrado" sobre o que não leu |
| O que lê | Só `id` e `description` (`TransactionDescriptions`, Criteria com projeção; o conversor decifra) | Duas colunas em vez da entidade inteira; a entidade só é carregada para os ids **da página** pedida, na ordem do extrato. O `total` continua exato, então a paginação do app não muda |
| Ao mesmo tempo | Bulkhead `search`: 3 no total, 2 por usuário | Cada busca segura uma conexão do banco enquanto decifra |
| Frequência | BFF: 30 buscas por minuto por usuário (`wallet.rate-limit.search-limit`), além das 300 gerais | O app espera 400 ms depois da última tecla; 30 por minuto é muito mais do que alguém digita |

Por que não um índice de busca: guardar a descrição de outro jeito (texto puro, trigramas ou
tokens com hash) abriria mão de parte da cifra ou exigiria uma migration e uma reescrita do
sincronizador, para um problema que os limites acima já deixam pequeno. Fica como opção se um
dia a busca precisar cobrir anos.

## Disponibilidade

Achados A11, A13 e A18, de 2026-10-22: "um usuário pesado derruba todos".

### Pool do banco e bulkheads (A11)

Com virtual threads nada limita quantas requisições rodam juntas, então o pool do Hikari era o
único teto, e estava no padrão (10) sem ninguém ter escolhido. Uma rajada de leituras caras
esvaziava o pool para todo mundo, e cada requisição esperava até 30 s por uma conexão.

- **Pool explícito:** `spring.datasource.hikari.maximum-pool-size` = `WALLET_DB_POOL_SIZE`
  (padrão 10, contra os 100 `max_connections` do Postgres por instância) e
  `connection-timeout` de **3 s**, abaixo dos 5 s que o BFF espera o core. Sem conexão livre
  nesse tempo, o core responde **503 `service.busy`** com `Retry-After: 1`
  (`GlobalExceptionHandler`, pela `SQLTransientConnectionException` do Hikari) em vez de deixar
  o BFF estourar o tempo e contar isso como "core fora do ar" no circuito.
- **Bulkheads** (`Bulkhead`, `Bulkheads`, `wallet.bulkhead.*`): um teto de quantas requisições
  de cada tipo caro rodam ao mesmo tempo, **no total e por usuário**. O teto por usuário é o que
  impede um usuário de ocupar todas as vagas; o total é o que deixa conexões para o resto.
  Cheio responde na hora, sem fila (esperar só empurraria a requisição para o timeout do BFF):

| Bulkhead | Rotas | Total | Por usuário |
|---|---|---|---|
| `search` | `/internal/transactions` com `q` | 3 | 2 |
| `screens` | `/internal/overview`, `/internal/insights/net-worth`, `/internal/planning/month`, `/internal/planning/fixed-expense-suggestions` (leem vários módulos ou períodos longos) | 5 | 3 |
| `manual-sync` | `POST /internal/connections/{id}/sync`, segurando a vaga até a rodada em segundo plano acabar | 4 | 2 |

  `search` + `screens` (8) ficam abaixo do pool (10): login, leituras simples e as gravações da
  sincronização sempre acham conexão. Usuário com a parte dele ocupada: **429
  `rate_limit.exceeded`** (o código que o app já traduz como "muitas requisições seguidas");
  todas as vagas ocupadas: **503 `service.busy`**. Os dois com `Retry-After: 1`. Gasto por
  categoria e por dia ficam de fora: são um `GROUP BY` de um mês no índice `(user_id, booked_on)`.
- O BFF tem os seus bulkheads, por grupo de rotas e só no total (ver [[BFF#Grupos de rotas]]): o
  core decide por usuário.

### Saúde

Achado A18: `/actuator/health` era público, ficava fora da chave de gateway e consultava o banco
a cada chamada. Desde 2026-10-22:

| Onde | O quê | Quem alcança |
|---|---|---|
| `GET /livez` na porta do core (8081) | Liveness: só `{"status":"UP"}`, da memória, sem banco | Qualquer um que alcance a porta, sem chave (é barata de propósito) |
| `GET /actuator/health` na porta de gestão | Tudo, com detalhes: banco, disco, liveness, readiness | Só a própria máquina |
| `GET /actuator/health/readiness` na porta de gestão | Pronto para receber requisições: `readinessState` + banco | Só a própria máquina |

- **Porta de gestão:** `management.server.port` = `WALLET_CORE_MANAGEMENT_PORT` (padrão
  **9081**), escutando **só em `127.0.0.1`**. Quem precisa da readiness (systemd, um script, o
  `HEALTHCHECK` de um container) roda na máquina; para o resto há `/livez`.
- As duas têm uma cadeia de segurança própria (`ManagementSecurityConfig`, antes da principal): o
  `GatewayKeyFilter` não tem mais exceção para a saúde, e na porta do core `/actuator/**` não
  existe (sem a chave, 403 como qualquer rota).
- **A subida falha** se a porta de gestão for a mesma do core: aí os detalhes e a consulta ao
  banco voltariam a ser públicos.
- Nenhum workflow do CI nem script do repositório usava o health; os testes passaram a usar
  `/livez`.

## Convenções (herdadas do Trilha)

- Controller devolve o **DTO direto**, nunca `ResponseEntity`; status diferente de
  200 via `@ResponseStatus`.
- `Objects.isNull/nonNull` com import estático; enum com `.equals()`.
- IDs `UUID` gerados **no mapper**, nunca `@GeneratedValue`.
- Mappers `@UtilityClass` com métodos estáticos.
- Um `GlobalExceptionHandler`: o service lança exceção de domínio, o handler traduz
  para HTTP com um **código de erro** estável (`connection.not_found`,
  `sync.too_soon`, `auth.invalid_credentials`...). O BFF repassa o código e o app
  traduz para português.
- DTO de criação rígido (`@NotBlank`, `@Email`...).

> [!warning] Armadilhas do Spring Boot 4
> Jackson 3 (`tools.jackson.databind`), starters renomeados (`spring-boot-starter-webmvc`)
> e `@AutoConfigureMockMvc` sem `springSecurity()` — montar o MockMvc na mão nos
> testes de segurança.

## Configuração

| Propriedade / variável | Para quê |
|---|---|
| `WALLET_DB_URL`, `WALLET_DB_USERNAME` | Banco. Padrão: `jdbc:postgresql://localhost:5432/wallet_dev` e `wallet` |
| `WALLET_DB_PASSWORD` | Senha do banco. Só em variável de ambiente |
| `WALLET_DB_POOL_SIZE` | `spring.datasource.hikari.maximum-pool-size`, padrão 10. Os bulkheads `search` + `screens` precisam ficar abaixo dele; a espera por conexão é 3 s (`connection-timeout`, em ms) e depois 503 `service.busy` |
| `WALLET_CORE_MANAGEMENT_PORT` | `management.server.port`, padrão 9081, sempre em `127.0.0.1`: `/actuator/health` com detalhes e a readiness. Igual à porta do core impede a subida |
| `wallet.bulkhead.search`, `.screens`, `.manual-sync` | `max-concurrent` e `max-per-user` de cada bulkhead (padrões 3/2, 5/3 e 4/2). Ver [[#Disponibilidade]] |
| `PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET` | Credenciais do Meu Pluggy. **Só em variável de ambiente**, nunca no git |
| `WALLET_JWT_PRIVATE_KEY` | Chave privada RS256 que assina os access tokens. Só o core tem |
| `WALLET_DATA_KEY` | Chave AES da criptografia de coluna (e do segredo do segundo fator) |
| `WALLET_PUBLIC_URL` | `wallet.auth.email-links.base-url`: endereço público do BFF, para onde os links dos e-mails apontam (`<url>/email/confirm#token=...`). Vazio = `http://localhost:8080` (dev); com `prod`, obrigatória e https |
| `WALLET_SMTP_HOST`, `WALLET_SMTP_PORT`, `WALLET_SMTP_USERNAME`, `WALLET_SMTP_PASSWORD`, `WALLET_SMTP_SECURITY` | Servidor SMTP de qualquer provedor (Amazon SES: `email-smtp.<região>.amazonaws.com`, 587, credenciais SMTP de um usuário IAM). `SECURITY`: `STARTTLS` (padrão, exigido, não só oferecido), `SSL` (465) ou `NONE` (só servidor local de teste; recusado com `prod`). Sem host: fora de `prod` o e-mail vai para o log; com `prod`, o core não sobe |
| `WALLET_MAIL_FROM` | Remetente (`Wallet <no-reply@dominio>`), obrigatório com SMTP |
| `WALLET_PASSWORD_CHECK_CONCURRENCY` | `wallet.auth.password-checks.concurrency`: BCrypts ao mesmo tempo (0 = metade dos processadores). Quem espera mais de 2 s recebe 429 `auth.busy` |
| `wallet.auth.lockout.*` | Bloqueio progressivo: 5 falhas por e-mail + endereço, 20 por e-mail de qualquer endereço, 15 min dobrando até 24 h, zera depois de um dia sem falhas |
| `wallet.auth.email-links.*` | Links dos e-mails: 24 h, até 3 por hora por conta |
| `wallet.auth.unconfirmed-account-ttl` / `notice-interval` / `cleanup-cron` | Conta não confirmada apagada em 7 dias; no máximo 1 aviso de cada tipo por hora; limpeza às 04:15 |
| `wallet.waitlist.*` | Lista de espera: `base-url` (`WALLET_PUBLIC_URL`, a mesma dos e-mails da conta), `confirm-ttl` 24 h, `resend-interval` 1 h, `pending-ttl` 7 dias, `cleanup-cron` 04:45 |
| `wallet.waitlist.announce.id`, `.file`, `.rate-per-second` | O comando do comunicado ([[#Comunicado de abertura]]). `id` e `file` não ficam no `application.yaml`: só existem na linha de comando da execução (ou `WALLET_WAITLIST_ANNOUNCE_ID` / `_FILE`); `rate-per-second` (`WALLET_WAITLIST_ANNOUNCE_RATE_PER_SECOND`) é 1 por padrão |
| `wallet.jwt.audience` | `aud` de todo access token, `wallet-api`, exigida aqui e no BFF |
| `WALLET_GATEWAY_KEY` | Segredo compartilhado com o BFF (`X-Gateway-Key`), o **mesmo** valor nos dois. Vazio = chave de desenvolvimento conhecida; com `SPRING_PROFILES_ACTIVE=prod`, vazia, curta (< 32) ou a de desenvolvimento impedem a subida |
| `WALLET_CORE_ADDRESS` | Interface onde o core escuta (`server.address`). Padrão `127.0.0.1`, com o BFF na mesma máquina; com o BFF em outro host, `0.0.0.0` e uma `WALLET_GATEWAY_KEY` real (a chave de desenvolvimento só vale no loopback) |
| `WALLET_MAX_BODY_SIZE` | `wallet.http.max-body-size`, padrão `256KB`. `Content-Length` maior recebe 413 `request.too_large` sem o corpo ser lido; corpo chunked é cortado nesse tamanho durante a leitura (`RequestBodyLimitFilter`). O Tomcat usa o mesmo teto em `max-swallow-size` e `max-http-form-post-size` |
| `WALLET_CONNECTION_MODE` | `wallet.provider.connection-mode`: `MEU_PLUGGY` (dev, padrão) ou `PLUGGY_CONNECT`. Com `SPRING_PROFILES_ACTIVE=prod`, qualquer coisa que não seja `PLUGGY_CONNECT` impede a subida, com a variável na mensagem (achado A6): o `MEU_PLUGGY` deixa qualquer usuário vincular item sem dono |
| `wallet.provider.max-connections-per-user` | Teto de conexões por usuário (padrão 10). A seguinte responde 422 `connection.limit_reached` |
| `wallet.provider.oauth-redirect-uri` | Para onde o banco devolve o usuário depois de autorizar no Pluggy Connect: o deep link do app Android (`com.wallet.wallet://pluggy-connect`) |
| `wallet.retention.banking.*`, `wallet.retention.sync.*`, `wallet.retention.identity.*` | Prazos da limpeza noturna: transações removidas pelo provedor 90 d, sincronizações terminadas 90 d (a última de cada conexão fica), tokens de renovação 30 d, mais o `cleanup-cron` de cada uma. Ver [[#Retenção (L8, desde 2026-10-24)]] |
| `wallet.sync.interval` | Intervalo do polling (padrão 6 h) |
| `wallet.sync.scheduler-enabled` | Liga o agendador nesta instância (padrão `true`) |
| `wallet.sync.manual-cooldown` | Espera entre o fim da última sincronização de uma conexão (qualquer gatilho e resultado) e o próximo "Atualizar" (padrão 15 min) |
| `wallet.provider.audit.retention` | Quanto tempo o registro de cada chamada à Pluggy fica (padrão 180 dias) |
| `wallet.provider.audit.cleanup-cron` | Quando os registros vencidos são apagados (padrão 04:30, horário de São Paulo) |

Porta **8081**. Todo segredo vem de variável de ambiente; o `application.yaml`
versionado só tem placeholders com padrões de desenvolvimento. O `./mvnw spring-boot:test-run`
sobe o core no mesmo Postgres local do `spring-boot:run`, sem criar container (desde 2026-10-21;
antes ele criava um Postgres descartável no Docker). Fora dos testes de integração, nada no core
cria container. Com `SPRING_PROFILES_ACTIVE=prod` o core também não inventa segredo: sem
`WALLET_GATEWAY_KEY`, sem as chaves do JWT, sem `WALLET_DATA_KEY`, sem SMTP (`WALLET_SMTP_HOST` e
`WALLET_MAIL_FROM`) ou sem `WALLET_PUBLIC_URL` em https, ele não sobe (`ProductionProfile`).

## E-mail (módulo `mail`, desde 2026-10-22)

- **API:** `EmailSender.send(OutgoingEmail)`. Enfileira: dentro de uma transação, o e-mail só sai
  **depois do commit** (um link nunca chega para um token desfeito); depois, numa virtual thread
  (`applicationTaskExecutor`), então o tempo do SMTP nunca aparece no tempo da resposta. Falha de
  envio fica no log (`[MAIL]`, só o tipo do e-mail, nunca endereço nem texto) e não volta a quem chamou.
- **Transporte:** `SmtpMailTransport` (`JavaMailSender` do `spring-boot-starter-mail`), genérico;
  `DevelopmentMailTransport` escreve no log fora de produção, com aviso de que é só dev (a única
  exceção à regra de não logar token). `MailConfig` escolhe na subida.
- **Envio síncrono (desde 2026-10-23):** `EmailSender.sendNow(OutgoingEmail)` entrega na thread de
  quem chama e **lança** `MailDeliveryException` se o servidor recusar. Existe para o comunicado da
  lista de espera: o `send` enfileirado perderia o e-mail se o processo saísse antes, e quem manda
  em lote precisa saber qual falhou para não o marcar como enviado. Nunca dentro de transação.
- **Descadastro:** `OutgoingEmail.withUnsubscribeUrl(url)` põe o link no rodapé e os headers
  `List-Unsubscribe` + `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 2369 e 8058, que
  Gmail e Yahoo pedem a quem manda em volume). O endpoint de descadastro fica com quem manda esse
  tipo de e-mail (o `waitlist`); e-mail de conta não tem descadastro.
- Textos em texto puro e pt-BR, sem nada digitado por estranhos. Os da conta estão em
  [[Autenticação#E-mails da conta]].

**Pluggy de demonstração (desde 2026-10-02):** sem `PLUGGY_CLIENT_ID`, o `test-run` também
sobe um servidor HTTP pequeno (`DemoPluggy`, no código de teste) que responde como a Pluggy:
`demo-banco` e `demo-corretora` (o básico) e, desde 2026-10-03, três que parecem os bancos de
uma pessoa de verdade: `demo-nubank` (conta e cartão de uso diário), `demo-itau` (salário,
aluguel, contas da casa, poupança e cartão) e `demo-xp` (seis investimentos). Esses três usam os ids
de conector reais da Pluggy (212, 201 e 202) e mandam o logo que ela serve
(`cdn.pluggy.ai/assets/connector-icons/{id}.svg`). Os nomes das
instituições são como a Pluggy os manda; pessoas, contas e valores são inventados. O histórico
começa num dia fixo (01/01/2026) para os saldos não andarem sozinhos de um dia para o outro. Os dados nascem da data de hoje, com uma semente por dia, então os
ids e valores se repetem a cada sincronização. Serve para ver o app inteiro funcionando
antes da T02. `DemoPluggyCompatibilityTest` lê a demo pelo adaptador de verdade: se o
adaptador mudar o que espera, quebra ali primeiro.

## Lista de espera (módulo `waitlist`, desde 2026-10-23)

A landing page tem o formulário "Quero ser avisado". O módulo guarda quem pediu, confirma o
e-mail (double opt-in), deixa sair com um clique e manda o comunicado de abertura. Tabela
`waitlist_entries` em [[Modelo de Dados]] (V11). Rotas internas, sem JWT (só a chave de
gateway), liberadas no `identity/config/SecurityConfig` como as de `/internal/auth/**`:

| Rota | Resposta | O que faz |
|---|---|---|
| `POST /internal/waitlist` `{email}` | **202** sem corpo, para qualquer e-mail | E-mail novo: grava `PENDING` e manda o link de confirmação (24 h). `PENDING` que já existe: manda um link novo (o antigo deixa de valer), no máximo **1 por hora**; dentro da hora, nada. `CONFIRMED`: nada. A inserção é `INSERT ... ON CONFLICT (email) DO NOTHING` e o reenvio é um `UPDATE` condicional, então 8 pedidos ao mesmo tempo mandam um e-mail só |
| `POST /internal/waitlist/confirm` `{token}` | **204** | `PENDING` vira `CONFIRMED`, grava `confirmed_at`, gera o token de descadastro e manda "Você está na lista do Wallet". Link vencido, desconhecido ou trocado por um novo: **400** `waitlist.invalid_token`. Abrir de novo o link de quem já confirmou: 204, sem novo e-mail |
| `POST /internal/waitlist/unsubscribe` `{token}` | **204** sempre | **Apaga** a linha. Token desconhecido também é 204 (ninguém descobre quais existem) |

- **Consentimento:** o cliente só manda `consent: true` (checado no BFF); o servidor grava a
  versão do texto, a constante `waitlist-v1` (`WaitlistService.CONSENT_VERSION`). Texto novo, versão nova.
- **Links** (base `WALLET_PUBLIC_URL`, a mesma do identity, e a mesma regra: em `prod` é obrigatória e https,
  `PublicUrl` no `shared`): confirmação `<url>/waitlist/confirm#token=…` (depois do `#`, que o navegador
  não envia); descadastro `<url>/waitlist/unsubscribe?token=…` (na query, porque o one-click da RFC 8058
  faz POST nessa URL exata). O descadastro vai no rodapé e nos headers `List-Unsubscribe` de todo e-mail da lista
  (`OutgoingEmail.withUnsubscribeUrl`). As páginas são do BFF ([[BFF#Páginas dos links]]).
- **Tokens:** o de confirmação é opaco (`OpaqueTokens`, 256 bits) e fica como SHA-256, e continua na
  linha depois de confirmar (abrir o link duas vezes não dá erro). O de descadastro **fica em claro**
  (`unsubscribe_token`): cada comunicado precisa reconstruir o link de cada pessoa, o que um hash não
  permite, e o token só dá o poder de apagar a própria linha, onde o e-mail já está em claro.
- **E-mails** (texto puro, pt-BR, `WaitlistEmails`): `waitlist-confirmation` ("Confirme seu e-mail na lista do
  Wallet"), `waitlist-welcome` ("Você está na lista do Wallet") e `waitlist-announcement` (o arquivo do comunicado).
  Nada digitado por quem se inscreveu entra no texto.
- **Limpeza:** `WaitlistHousekeeping`, às 04:45 (`wallet.waitlist.cleanup-cron`), apaga `PENDING` com mais de
  7 dias (`pending-ttl`). Igual ao `AccountHousekeeping`: `SchedulingConfigurer` com `TransactionTemplate`
  dentro do método (o `@Transactional` não vale para o que o agendador chama), e o teste roda pelo caminho do agendador.
- **Logs:** `[WAITLIST]`, só ids; o e-mail aparece mascarado (`EmailAddresses.mask`) e só quando um envio do comunicado falha.

### Comunicado de abertura

Não há tela de admin: é um comando, que sobe o core sem servidor web, manda e encerra o processo.

```bash
java -jar core.jar --spring.main.web-application-type=none \
  --wallet.waitlist.announce.id=abertura \
  --wallet.waitlist.announce.file=/caminho/mensagem.txt
```

- O `WaitlistAnnouncementRunner` (um `ApplicationRunner`) só existe quando `announce.id` **e** `announce.file`
  estão definidos; no core que atende a API ele não existe. Também vale `WALLET_WAITLIST_ANNOUNCE_ID` e `_FILE`.
  Rode com o mesmo ambiente do core (banco, `WALLET_SMTP_*`, `WALLET_MAIL_FROM`, `WALLET_PUBLIC_URL`).
- O arquivo é UTF-8: a **primeira linha é o assunto**, o resto é o corpo (texto puro). Arquivo sem assunto ou sem corpo para antes de mandar qualquer coisa.
- Manda para todo `CONFIRMED` cujo `last_announcement` seja diferente do id, em ordem de confirmação, a
  `wallet.waitlist.announce.rate-per-second` e-mails por segundo (padrão 1; ajuste ao limite do SMTP), cada um com o link de descadastro da própria pessoa, por `sendNow`.
- Depois de **cada** envio que o servidor aceitou grava `last_announcement` e `last_announced_at`: rodar de
  novo, ou depois de uma queda, não reenvia a quem já recebeu. Falha num e-mail: loga mascarado, não marca e segue; rodar de novo tenta só esses.
- No fim loga os totais (`enviados`, `falhas`) e sai com `SpringApplication.exit`: código **0**, ou **1** se algum envio falhou.
- Sem servidor web, as cadeias de filtros de segurança (`identity` e `ManagementSecurityConfig`) não são criadas
  (`@ConditionalOnWebApplication`); não há Tomcat nem porta aberta.

## Exportação dos dados (L9 e L10)

Desde 2026-10-24. O direito de acesso e a portabilidade da LGPD (art. 18, II e V): `GET /internal/me/export`
devolve **um JSON com tudo o que o Wallet guarda do usuário**. O módulo `privacy` não lê tabela de ninguém:
cada módulo dono expõe uma porta pública na raiz do seu pacote, e o `DataExportService` junta as partes
numa transação só, de leitura (uma conexão, um momento).

| Parte do arquivo | Porta | O que traz |
|---|---|---|
| `profile` | `identity.UserProfiles` | `id`, `email`, `displayName`, `createdAt`, `emailConfirmedAt`, `twoFactorEnabled` (sim ou não). **Nunca** hash de senha, segredo do segundo fator, códigos de recuperação nem token |
| `connections` | `connection.ConnectionRegistry.summariesOf` | Instituição, status, quando vinculou, última sincronização, validade do consentimento. Sem o id do item na Pluggy |
| `banking` | `banking.BankingQueries.export` | `accounts`, `balances` (saldo por dia), `transactions` (**descrição decifrada**; as que o provedor deixou de informar ficam de fora, como no app) e `bills` |
| `investments` | `investment.InvestmentQueries.export` | `positions` (as encerradas também, com `closedAt`) e `balances` por dia |
| `planning` | `planning.PlanningExports` | `expectedSalary` e `fixedExpenses` |

- **`schemaVersion: 1`** e `exportedAt` no alto. Os nomes dos campos dos registros de exportação (`BankingExport`,
  `InvestmentExport`, `PlanningExport`, `ConnectionSummary`, `UserProfile`) **são o esquema do arquivo**: acrescentar
  campo mantém a versão; renomear ou tirar um sobe para 2.
- Dinheiro como string (`"123.45"`), como no resto da API. Os ids são os do próprio Wallet, para o usuário ligar
  conta, transação e fatura.
- O arquivo é montado em memória. O BFF limita a 3 por dia por usuário ([[BFF]]), porque cada um lê tudo.
- Testes: `DataExportIT` (dados de todas as áreas, descrição decifrada, nada que autentique, isolamento entre
  usuários, usuário sem nada, sem token).

## Logs sem dado pessoal (L12)

Desde 2026-10-24. Dado pessoal não entra em log. No erro **inesperado**, o `GlobalExceptionHandler` grava só o trace id
(o `X-Trace-Id` que o BFF manda) e a **classe e a pilha** de cada exceção da cadeia, **sem nenhuma mensagem**
(`ExceptionLog.describe`): o driver do Postgres põe `Key (email)=(...)` na mensagem, e o leitor de JSON cita o corpo.
Os avisos de corpo ilegível e de parâmetro inválido também logam só a classe. Teste:
`GlobalExceptionHandlerTest#anUnexpectedErrorIsLoggedWithoutAnyMessage`, com uma exceção cuja mensagem tem um e-mail.

## Registros de acesso (L13)

Desde 2026-10-24. O Marco Civil da Internet (art. 15) manda a aplicação guardar, em sigilo, os registros de acesso por **6 meses**.
O módulo `identity` grava **uma linha em `access_records`** a cada **login com sucesso** (`LOGIN`) e a cada **refresh** (`REFRESH`),
com o `user_id`, a hora e o **IP cifrado** (o mesmo `DataCipher` da descrição das transações). O IP é o que o BFF mandou em
`X-Wallet-Client-Address` (o mesmo que o rate limit conta, com os proxies de confiança); sem o cabeçalho, grava `unknown`.
Login recusado e refresh recusado não gravam nada.

- **Sem chave estrangeira em `user_id`:** excluir a conta não apaga os registros, que valem 6 meses (obrigação legal, LGPD art. 16, I).
- **Nenhuma rota de leitura.** Nem a API, nem o app, nem a exportação dos dados: o acesso só acontece por **ordem judicial, direto no banco**
  (quem abre a tabela decifra o IP com a `WALLET_DATA_KEY`).
- **Limpeza:** a tarefa noturna do `AccountHousekeeping` apaga o que passou de `wallet.retention.identity.access-records-after` (**185 dias**: cobre
  qualquer 6 meses de calendário). Log `[RETENTION]` com a contagem.
- Testes: `AccessRecordsIT` (login e refresh gravam, IP cifrado, recusa não grava, exclusão da conta não apaga, limpeza pelo agendador).

## Nome dos gastos fixos cifrado (L7)

Desde 2026-10-24. O nome de um gasto fixo é texto livre ("Psicólogo") e passou a ser cifrado em `fixed_expenses.name`, como a descrição das
transações. Detalhes e o preenchimento das linhas antigas em [[Modelo de Dados#Criptografia de coluna]]. Efeito no código: o banco não ordena
mais por nome, então `FixedExpenseRepository.findOrderedByDayThenName` ordena por dia no SQL e desempata pelo nome em memória (collator
pt-BR, sem diferenciar maiúscula nem acento no desempate). Teste: `FixedExpenseEncryptionIT`.

## Retenção (L8, desde 2026-10-24)

LGPD: dado pessoal só enquanto serve. **Cada módulo limpa o que é dele**, toda noite, com uma tarefa agendada no mesmo
padrão do `AccountHousekeeping`: um `SchedulingConfigurer` que registra um `CronTask` e abre a transação **dentro** do método
(`TransactionTemplate`), porque o agendador chama o objeto e não o proxy do Spring, e um `@Transactional` seria ignorado
(a limpeza falharia toda noite por falta de transação). Roda em todas as instâncias: dois deletes das mesmas linhas não fazem mal. O log
`[RETENTION]` só traz contagens.

| Módulo | Tarefa | O que apaga | Propriedade (padrão) | Cron (hora de São Paulo) |
|---|---|---|---|---|
| `banking` | `BankingHousekeeping` | Transações com `deleted_at` (as que o provedor deixou de informar) há mais de 90 dias | `wallet.retention.banking.deleted-transactions-after` (90d) | `.cleanup-cron` (`0 50 4 * * *`) |
| `sync` | `SyncRunHousekeeping` | `sync_runs` terminadas há mais de 90 dias, **menos a mais recente de cada conexão** (o cooldown do "Atualizar" conta dela); uma em andamento nunca | `wallet.retention.sync.finished-runs-after` (90d) | `.cleanup-cron` (`0 55 4 * * *`) |
| `identity` | `AccountHousekeeping` (a tarefa que já existe, às 04:15) | `refresh_tokens` vencidos ou revogados há mais de 30 dias, e `access_records` com mais de 185 dias ([[#Registros de acesso (L13)]]). Os links de e-mail, as contas não confirmadas e os contadores de login já eram limpos ali | `wallet.retention.identity.refresh-tokens-after` (30d) e `.access-records-after` (185d) | o de `wallet.auth.cleanup-cron` |

Já tinham limpeza própria: a auditoria das chamadas à Pluggy (180 dias, [[#Auditoria das chamadas à Pluggy (desde 2026-10-18)]]) e a lista
de espera não confirmada (7 dias, [[#Lista de espera (módulo `waitlist`, desde 2026-10-23)]]).

**O que NÃO se apaga sozinho, de propósito:** o histórico de saldos (`balance_snapshots`), os snapshots de investimentos
(`investment_snapshots`), os investimentos fechados e as faturas. São o histórico que o produto mostra (patrimônio dia a dia,
evolução dos investimentos), e ficam enquanto a conta existir; a exclusão da conta apaga tudo. As transações que seguem no banco
do usuário também ficam.

**Conta inativa:** não há exclusão automática. [A DEFINIR: prazo de conta inativa]: depende de uma decisão do Rafael (e do jurídico), e
precisa avisar o dono antes de apagar.

Testes: `BankingHousekeepingIT`, `SyncRunHousekeepingIT` e `RefreshTokenRetentionIT`, todos pelo caminho do agendador (a tarefa que o
`configureTasks` registra), com o relógio nas mãos do teste: o que passou do prazo some, o que não passou fica, e a última `sync_run` de
cada conexão sempre fica.

## Calendário de gastos (desde 2026-10-03)

- `GET /internal/insights/daily-spending?month=`: o gasto de cada dia do mês, pela mesma
  regra dos gastos por categoria (saídas, sem o pagamento de fatura feito pela conta
  corrente, porque as compras já estão no cartão).
- `spending=true` no `/internal/transactions` aplica a mesma regra à lista, então os gastos de
  um dia somam exatamente o total do calendário naquele dia.

## Planejamento do mês (desde 2026-10-18)

Pedido do Rafael depois de fazer a conta à mão, com o holerite e a fatura de cada banco: quanto
sobra do salário no mês. **Sobra = salário líquido − faturas dos cartões − gastos fixos.** Toda a
conta é feita no core, com `Money`; o BFF só repassa.

- **Salário:** as entradas numa conta bancária (não cartão) com a categoria `Salary` da Pluggy
  no mês, somadas (adiantamento e restante viram um salário só); a data é a do último depósito.
  O Open Finance só vê o que cai na conta, então é o salário **líquido**. Enquanto o do mês não
  cai, vale o salário esperado que a pessoa definiu ("Ajustar") ou, sem ele, o último salário
  recebido nos três meses anteriores, previsto para o mesmo dia do mês (`predicted: true`,
  `source`: `RECEIVED`, `EXPECTED`, `PREVIOUS_MONTH` ou `NONE`). O salário esperado nunca passa
  por cima do que caiu de verdade.
- **Faturas:** de cada cartão, a fatura que **vence** no mês, com as compras. O total é o valor
  do banco, então bate sempre com o que vai ser cobrado; o que as compras listadas não explicam
  (tarifas, juros, compra que o banco não detalhou) vem em `otherCharges`, negativo quando um
  crédito reduz a fatura. Uma compra é da fatura quando a Pluggy a ligou a ela
  (`creditCardMetadata.billId`, que liga uma parcela lançada meses atrás à fatura que a cobra
  agora) ou, sem ligação, quando foi feita no ciclo dela: do dia seguinte ao fechamento anterior
  até o fechamento desta (sem data de fechamento, 7 dias antes do vencimento). O pagamento da
  fatura anterior aparece no cartão mas não entra; estorno entra, reduzindo. Compras da maior
  para a menor, estornos no fim. A regra mora no `banking` (`BillCycles`, exposta por
  `BankingQueries.billsDueIn`), dono das faturas e das transações.
- **Fatura aberta:** a Pluggy só entrega faturas fechadas. Quando a fatura do mês ainda não
  fechou, o core estima a próxima pelo ciclo da última (fecha e vence um mês depois) e lista o
  que já foi comprado nele, com `open: true`; o total é a soma das compras e ainda pode crescer.
  Só um ciclo à frente: dois meses adiante não tem fatura.
- **Gastos fixos:** o que não passa no cartão (mensalidade, aluguel, boletos). Cadastrados uma
  vez (nome, valor, dia do mês, categoria opcional da Pluggy) e repetidos em todo mês; o dia 31
  cai no último dia dos meses mais curtos. Até 100 por pessoa. Desde 2026-10-18 também vêm
  sugeridos pelo extrato (abaixo).
- **Erros:** `planning.fixed_expense_not_found` (404, também para o gasto de outra pessoa),
  `planning.fixed_expense_limit` (422), `validation.failed` (valor que não é positivo ou tem
  mais de dois decimais, dia fora de 1 a 31, nome vazio) e `request.invalid_parameter` (mês que
  não é `yyyy-MM`).
- **Os nomes dos bancos** não vêm do core: cada fatura traz o `connectionId` e o BFF põe o nome
  e o logo, como no resto.

### Gastos fixos sugeridos pelo extrato (desde 2026-10-18)

O passo seguinte combinado com o Rafael: primeiro os gastos fixos à mão, depois sugestões tiradas
dos débitos que se repetem. `GET /internal/planning/fixed-expense-suggestions` lista; adicionar é
criar o gasto fixo normalmente (o app abre o formulário preenchido); `DELETE .../{id}` dispensa.

- **Débito recorrente** (regra no `banking`, `RecurringDebits`, exposta por
  `BankingQueries.recurringDebits`): saída de conta bancária (nunca cartão) no mês corrente e nos
  três anteriores, agrupada pelas palavras da descrição sem acento, maiúscula, número ou pontuação
  (`TextKeys.key`: "PAG*CONDOMINIO 10/26 DOC 0901" vira "pag condominio doc"). Partindo do débito
  mais recente, cada mês anterior precisa de um a **até 3 dias** do mesmo dia do mês (o dia 31
  vale o último dia dos meses curtos) com valor que **não passe do dobro** do outro (luz e água
  variam com a estação). Precisa aparecer em **3 meses**. O que sobra do grupo pode formar outra
  série: dois boletos com a mesma descrição genérica em dias diferentes são duas sugestões.
- **Fora:** pagamento de fatura (`Credit card payment`: as compras já estão no cartão), entradas,
  transações excluídas, descrição que aparece **mais de 2 vezes por mês** em média (mercado
  semanal, corrida de app: hábito, não conta) e o débito que **parou** (o do mês seguinte ao último
  já devia ter saído há mais de 3 dias).
- **Valor** = média dos débitos da série, com `Money` (centavo pelo meio-par); **dia** = o mais
  comum (empate fica com o do último); **nome** = a descrição do último débito sem as palavras sem
  letra (datas, números de documento), com as palavras capitalizadas quando o banco escreveu tudo
  em maiúsculas ("Plano de Saude"), até 80 caracteres. Vêm também a categoria do último débito,
  em quantos meses apareceu e a data do último.
- **Já é gasto fixo** (não sugere): um gasto fixo a até 3 dias do mesmo dia (contando a virada do
  mês: 30 e 2 estão a 3 dias) com o mesmo nome (um tem as palavras do outro: "Aluguel" e "ALUGUEL
  APARTAMENTO") **ou** valor a até 15% ("Luz" de R$ 200 no dia 20 cobre "ENEL DISTRIBUICAO" de
  R$ 195). É o que faz a sugestão sumir depois de adicionada, mesmo com outro nome.
- **Dispensada** fica em `dismissed_fixed_expense_suggestions` com a impressão digital da chave da
  descrição (`DataCipher.fingerprint`: HMAC-SHA256 com chave derivada da `WALLET_DATA_KEY`, 32
  hex) e o dia; cobre 3 dias para cada lado, porque o dia usual pode mudar. A descrição nunca é
  gravada fora de `transactions` (lá ela é cifrada). Como a cifra, a impressão digital muda com a
  chave: com a chave temporária de desenvolvimento, as dispensas valem até o core reiniciar.
- **Id** da sugestão: `<impressão digital>-<dia>` (`9f86d081884c7d659a2feaa0c55ad015-9`). Não diz
  nada da descrição a quem o vê numa URL ou num log.
- **Erros:** `planning.fixed_expense_suggestion_not_found` (404) para id que não é uma sugestão
  atual da pessoa (formato errado, de outra pessoa, débito que parou). Dispensar de novo é 204.
- Calculadas a cada leitura, sem tabela de sugestões: lê no máximo quatro meses de transações de
  conta, decifrando as descrições em memória, como a busca do extrato.
- A Pluggy de demonstração gera seis no Itaú (aluguel, condomínio, Enel, Sabesp, Vivo e plano de
  saúde); `DemoPlanningIT` sincroniza `demo-itau` pelo adaptador de verdade e confere.

## Testes

- Unitário (`*Test`, pelo Surefire no `mvn test`): sem Docker (mappers, normalização de sinal, regras de sync,
  `RecurringDebitsTest` e `FixedExpenseSuggestionsTest` para as sugestões de gasto fixo).
- Integração (`*IT`, pelo Failsafe no `mvn verify`): Testcontainers com Postgres + WireMock para a Pluggy.
  No Docker, o banco de cada teste aparece como `wallet-core-test-db-xxxx` e é apagado no fim, mesmo
  se o Docker fechar no meio (AutoRemove). São os únicos containers que o core cria.
- Teste de modularidade do Spring Modulith.
- E-mail: `SmtpMailTransportTest` conversa com um servidor SMTP de verdade em memória (GreenMail),
  com autenticação; nos ITs, `TestMailConfiguration` troca o transporte por um que guarda os
  e-mails, e o teste lê o link como o dono leria.
- Contrato HTTP das rotas (quem acessa o quê, códigos de erro).
- Lista de espera (desde 2026-10-23): `WaitlistFlowIT` (inscrição manda o link; repetida dentro de 1 h não
  reenvia e depois de 1 h reenvia, com o relógio na mão; oito pedidos ao mesmo tempo mandam um e-mail; confirmar
  com link válido, vencido, desconhecido, trocado e duas vezes; `CONFIRMED` não recebe nada; descadastro apaga e
  token desconhecido é 204; limpeza dos `PENDING` com mais de 7 dias pelo caminho do agendador; comunicado só
  para `CONFIRMED` sem aquele id, marcação, segunda rodada sem reenvio e falha de envio sem marca),
  `AnnouncementFilesTest`, `WaitlistEmailsTest`, `QueuedEmailSenderTest` (`sendNow`) e `PublicUrlTest`.
- Disponibilidade (desde 2026-10-22): `BulkheadTest` (parte do usuário, total, vaga devolvida uma
  vez, 20 requisições simultâneas de um usuário nunca passam de 2), `ReadApiIT` (busca paginada
  com total exato, janela de 93 dias, bulkheads `search` e `screens` nas rotas, histórico de
  patrimônio com três anos de snapshots lendo só o período e um anterior), `SyncFlowIT` (o
  "Atualizar" sem vaga não abre rodada; a vaga fica presa até a rodada acabar), `DatabasePoolIT`
  (pool de 2 cheio: 503 `service.busy` e `/livez` continua respondendo), `HealthEndpointsIT`
  (servidor de verdade, as duas portas) e `ManagementSecurityConfigTest` (porta de gestão igual
  à do core impede a subida).

Modelo das tabelas em [[Modelo de Dados]].
