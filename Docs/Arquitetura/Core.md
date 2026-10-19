---
tags: [arquitetura, backend, core]
atualizado: 2026-10-18
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
| `provider` | Porta `FinancialDataProvider`, o adaptador da Pluggy e a auditoria das chamadas | `shared` |
| `insight` | Visão geral, patrimônio, gasto por categoria | `banking`, `investment` |
| `planning` | Planejamento do mês: salário − faturas − gastos fixos, os gastos fixos e o salário esperado | `banking`, `shared` |
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
| Por quê | `reason` | `CONNECT` (token do Pluggy Connect), `LINK`, `UNLINK`, `INITIAL_SYNC`, `SCHEDULED_SYNC`, `MANUAL_SYNC`, `WEBHOOK_SYNC` |
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

- **Retenção:** 180 dias (`wallet.provider.audit.retention`), apagados toda noite às 04:30
  de São Paulo (`wallet.provider.audit.cleanup-cron`), em todas as instâncias.
- **Sem chave estrangeira** de propósito: o registro de um desvínculo, ou das chamadas de um
  usuário que excluiu a conta, sobrevive às linhas que ele cita. Depois da exclusão, o
  `user_id` não aponta para ninguém, e some com os 180 dias.

## API interna

Tudo em `/internal`, JSON em inglês, dinheiro como string (`"1234.56"`), datas
ISO-8601. Paginação `{ items, page, pageSize, total, totalPages }`. Toda rota, menos
login, cadastro e refresh, exige o JWT do usuário, que o BFF repassa.

**Chave de gateway (desde 2026-10-19):** toda requisição, inclusive login, cadastro, refresh e
o JWKS, também precisa do header `X-Gateway-Key` com o segredo compartilhado com o BFF
(`WALLET_GATEWAY_KEY`). Sem ele, ou com o valor errado, o `GatewayKeyFilter` responde **403**
`gateway.forbidden` antes de qualquer outra coisa (sem ler token, sem BCrypt, sem banco). Só
`/actuator/health` fica aberto. 403 e não 401 de propósito: o BFF trata o 401 do core como
sessão vencida e o app deslogaria o usuário por um erro de configuração do servidor. A
comparação é em tempo constante (SHA-256 dos dois lados + `MessageDigest.isEqual`). Assim o
core não depende de estar numa rede interna: quem alcança a porta sem a chave não consegue nada.

| Método | Rota | O que faz |
|---|---|---|
| POST | `/internal/auth/register` | Cria conta |
| POST | `/internal/auth/login` | Devolve `{ accessToken, expiresIn, refreshToken }` |
| POST | `/internal/auth/refresh` | Troca o refresh por um par novo |
| POST | `/internal/auth/logout` | Revoga a família do refresh |
| GET | `/internal/me` | Usuário logado |
| GET | `/internal/connections` | Conexões do usuário e o estado de cada uma |
| POST | `/internal/connections` | Vincula um item (`{ "providerItemId": "..." }`) |
| POST | `/internal/connections/connect-token` | Token do widget Pluggy Connect para o usuário (`{ "connectToken": "..." }`, 30 min, uma conexão) |
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
| GET | `/internal/planning/month?month=2026-10` | O planejamento do mês (padrão: mês corrente): salário, faturas com as compras, gastos fixos e o que sobra |
| GET, POST | `/internal/planning/fixed-expenses` | Lista e cria gastos fixos (`{ name, amount, dayOfMonth, category }`) |
| PUT, DELETE | `/internal/planning/fixed-expenses/{id}` | Altera e exclui um gasto fixo |
| PUT, DELETE | `/internal/planning/expected-salary` | Define o salário esperado (`{ amount }`) ou volta à previsão automática |
| GET | `/internal/planning/fixed-expense-suggestions` | Débitos que se repetem todo mês nas contas e ainda não são gastos fixos |
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
| `WALLET_GATEWAY_KEY` | Segredo compartilhado com o BFF (`X-Gateway-Key`), o **mesmo** valor nos dois. Vazio = chave de desenvolvimento conhecida; com `SPRING_PROFILES_ACTIVE=prod`, vazia, curta (< 32) ou a de desenvolvimento impedem a subida |
| `WALLET_CORE_ADDRESS` | Interface onde o core escuta (`server.address`). Padrão `0.0.0.0`; num host único com o BFF ao lado, `127.0.0.1` |
| `WALLET_MAX_BODY_SIZE` | `wallet.http.max-body-size`, padrão `256KB`. `Content-Length` maior recebe 413 `request.too_large` sem o corpo ser lido; corpo chunked é cortado nesse tamanho durante a leitura (`RequestBodyLimitFilter`). O Tomcat usa o mesmo teto em `max-swallow-size` e `max-http-form-post-size` |
| `wallet.provider.connection-mode` | `MEU_PLUGGY` (dev) ou `PLUGGY_CONNECT` |
| `wallet.provider.oauth-redirect-uri` | Para onde o banco devolve o usuário depois de autorizar no Pluggy Connect: o deep link do app Android (`com.wallet.wallet://pluggy-connect`) |
| `wallet.sync.interval` | Intervalo do polling (padrão 6 h) |
| `wallet.sync.scheduler-enabled` | Liga o agendador nesta instância (padrão `true`) |
| `wallet.provider.audit.retention` | Quanto tempo o registro de cada chamada à Pluggy fica (padrão 180 dias) |
| `wallet.provider.audit.cleanup-cron` | Quando os registros vencidos são apagados (padrão 04:30, horário de São Paulo) |

Porta **8081**. Todo segredo vem de variável de ambiente; o `application.yaml`
versionado só tem placeholders com padrões de desenvolvimento. Sem banco local, o core
sobe contra um Postgres descartável com `./mvnw spring-boot:test-run`.

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
  No Docker, o banco de cada teste aparece como `wallet-core-test-db-xxxx`; o do `test-run`, como
  `wallet-core-demo-db-xxxx` (Pluggy de demonstração) ou `wallet-core-pluggy-db-xxxx` (Pluggy de verdade).
- Teste de modularidade do Spring Modulith.
- Contrato HTTP das rotas (quem acessa o quê, códigos de erro).

Modelo das tabelas em [[Modelo de Dados]].
