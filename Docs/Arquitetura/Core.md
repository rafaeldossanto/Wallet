---
tags: [arquitetura, backend, core]
atualizado: 2026-10-02
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
| `identity` | Usuários, cadastro, login, emissão e rotação de tokens, bloqueio por tentativas | `shared` |
| `connection` | Conexões com bancos (items da Pluggy), vincular, desvincular | `provider`, `shared` |
| `banking` | Contas, saldos, transações, cartões e faturas | `shared` |
| `investment` | Posições de investimento | `shared` |
| `sync` | Orquestra a leitura do provedor e grava nos módulos de dados | `provider`, `connection`, `banking`, `investment` |
| `provider` | Porta `FinancialDataProvider` e o adaptador da Pluggy | `shared` |
| `insight` | Visão geral, patrimônio, gasto por categoria | `banking`, `investment` |
| `shared` | Dinheiro, erros, paginação, criptografia de coluna, relógio | — |

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

## API interna

Tudo em `/internal`, JSON em inglês, dinheiro como string (`"1234.56"`), datas
ISO-8601. Paginação `{ items, page, pageSize, total, totalPages }`. Toda rota, menos
login, cadastro e refresh, exige o JWT do usuário, que o BFF repassa.

| Método | Rota | O que faz |
|---|---|---|
| POST | `/internal/auth/register` | Cria conta |
| POST | `/internal/auth/login` | Devolve `{ accessToken, expiresIn, refreshToken }` |
| POST | `/internal/auth/refresh` | Troca o refresh por um par novo |
| POST | `/internal/auth/logout` | Revoga a família do refresh |
| GET | `/internal/me` | Usuário logado |
| GET | `/internal/connections` | Conexões do usuário e o estado de cada uma |
| POST | `/internal/connections` | Vincula um item (`{ "providerItemId": "..." }`) |
| DELETE | `/internal/connections/{id}` | Desvincula (em produção, revoga na Pluggy) |
| POST | `/internal/connections/{id}/sync` | Pede sincronização agora (máx. 1 a cada 15 min) |
| GET | `/internal/overview` | Patrimônio, saldo, dívida do cartão, investimentos, entradas e saídas do mês, última sincronização |
| GET | `/internal/accounts` | Contas com saldo, lista plana com `connectionId` (o BFF agrupa por instituição) |
| GET | `/internal/transactions` | Extrato com filtros `from`, `to` (padrão: mês corrente, máx. 366 dias), `accountId`, `direction`, `q`, paginado |
| GET | `/internal/credit-cards` | Cartões com limite, disponível e a próxima fatura a pagar |
| GET | `/internal/credit-cards/{accountId}/bills` | Faturas do cartão, da mais recente para a mais antiga |
| GET | `/internal/investments` | Posições abertas e total por tipo |
| GET | `/internal/insights/spending-by-category?month=2026-09` | Gasto por categoria no mês (padrão: mês corrente) |
| GET | `/internal/insights/net-worth?from=&to=` | Patrimônio dia a dia (padrão: últimos 30 dias, máx. 731) |

O core **não sabe** se o usuário está no celular ou no navegador: sempre devolve os
tokens no corpo. Cookie e CORS são assunto do [[BFF]].

### Regras dos números

- **Patrimônio** = saldo das contas + investimentos abertos − saldo dos cartões.
- **Entradas e saídas do mês** contam só as contas bancárias. A compra no cartão entra
  quando a fatura é paga; contar as duas coisas duplicaria o gasto.
- **Gasto por categoria** soma as saídas de todas as contas, menos o pagamento de fatura
  vindo da conta (categoria `Credit card payment` da Pluggy, a confirmar na T02).
- **Histórico:** dia sem snapshot repete o último valor conhecido. Antes da primeira
  sincronização o valor é zero.
- **Busca por texto:** ignora maiúsculas e acentos e roda em memória, porque a
  descrição fica cifrada. O limite de 366 dias do período é o que mantém isso barato.

Erros de leitura: `period.invalid`, `period.too_long` e `page.invalid` (422),
`request.invalid_parameter` (400) e `account.not_found` (404).

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
| `PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET` | Credenciais do Meu Pluggy. **Só em variável de ambiente**, nunca no git |
| `WALLET_JWT_PRIVATE_KEY` | Chave privada RS256 que assina os access tokens. Só o core tem |
| `WALLET_DATA_KEY` | Chave AES da criptografia de coluna |
| `wallet.provider.connection-mode` | `MEU_PLUGGY` (dev) ou `PLUGGY_CONNECT` |
| `wallet.sync.interval` | Intervalo do polling (padrão 6 h) |
| `wallet.sync.scheduler-enabled` | Liga o agendador nesta instância (padrão `true`) |

Porta **8081**. Todo segredo vem de variável de ambiente; o `application.yaml`
versionado só tem placeholders com padrões de desenvolvimento. Sem banco local, o core
sobe contra um Postgres descartável com `./mvnw spring-boot:test-run`.

**Pluggy de demonstração (desde 2026-10-02):** sem `PLUGGY_CLIENT_ID`, o `test-run` também
sobe um servidor HTTP pequeno (`DemoPluggy`, no código de teste) que responde como a Pluggy:
`demo-banco` (conta corrente, poupança e cartão com faturas e parcelas) e `demo-corretora`
(cinco investimentos). Os dados nascem da data de hoje, com uma semente por dia, então os
ids e valores se repetem a cada sincronização. Serve para ver o app inteiro funcionando
antes da T02. `DemoPluggyCompatibilityTest` lê a demo pelo adaptador de verdade: se o
adaptador mudar o que espera, quebra ali primeiro.

## Testes

- Unitário (`*Test`, pelo Surefire no `mvn test`): sem Docker (mappers, normalização de sinal, regras de sync).
- Integração (`*IT`, pelo Failsafe no `mvn verify`): Testcontainers com Postgres + WireMock para a Pluggy.
- Teste de modularidade do Spring Modulith.
- Contrato HTTP das rotas (quem acessa o quê, códigos de erro).

Modelo das tabelas em [[Modelo de Dados]].
