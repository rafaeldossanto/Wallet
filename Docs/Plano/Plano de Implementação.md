---
tags: [plano, tarefas]
atualizado: 2026-09-29
---

# Plano de Implementação

Tarefas na ordem de execução. Cada uma cabe numa sessão de execução e termina com
commit na `master`. A arquitetura está em [[Visão Geral]], [[BFF]], [[Core]],
[[App Flutter]], [[Modelo de Dados]], [[Sincronização]] e [[Autenticação]].

| Fase | Tarefas | Resultado |
|---|---|---|
| 0 · Fundação | T01–T04 | Repositório, dados reais da Pluggy capturados, core e banco de pé |
| 1 · Core | T05–T09 | API interna completa lendo o Meu Pluggy |
| 2 · BFF | T10–T11 | Porta pública com sessão por plataforma e rotas por tela |
| 3 · App | T12–T18 | Wallet no celular e no navegador do PC |
| Depois | Backlog | O que falta para abrir a outros usuários |

O app pode começar a partir da T12 em paralelo, com o BFF simulado. O BFF pode
começar na T10 assim que as rotas do core da T05 existirem.

---

## Fase 0 · Fundação

### T01 — Repositório

**Objetivo:** um repositório só para docs, BFF, core e app.

- `git init` em `Work/Wallet`, branch `master`. O cofre `Docs/` já existe.
- `.gitignore` na raiz cobrindo: `.obsidian/workspace.json`, `.trash/`,
  `**/target/`, `**/application-local.yml`, `**/.env*`, `**/*.pem`, `app/build/`,
  `app/.dart_tool/`, arquivos de IDE. Motivo: a senha de app do Gmail que vazou num
  repositório do Trilha; segredo nunca entra no git.
- `README.md` curto apontando para `Docs/Wallet.md` e dizendo que `bff/` sobe na 8080
  e `core/` na 8081.
- **Remote:** criar `github.com/rafaeldossanto/Wallet` (privado) — o Rafael cria ou
  autoriza a criação.

**Pronto quando:** primeiro commit na `master` com Docs + `.gitignore` + README, e
push para o remote.

### T02 — Spike do Meu Pluggy

**Objetivo:** ver os dados reais antes de escrever o domínio. Três regras da
[[Sincronização]] dependem disso.

**Parte do Rafael (manual, envolve credenciais):**
1. Criar conta no Meu Pluggy e conectar os bancos (até 5).
2. Em `dashboard.pluggy.ai`, criar a aplicação, habilitar o conector **MeuPluggy** e
   vincular os items. Anotar os `itemId`.
3. Criar as variáveis de ambiente do Windows `PLUGGY_CLIENT_ID` e
   `PLUGGY_CLIENT_SECRET`. **Não colar as credenciais no chat.**

**Parte da execução:**
- `core/http/pluggy.http` (HTTP Client do IntelliJ) lendo as variáveis de ambiente:
  `POST /auth`, `GET /items/{id}`, `GET /accounts?itemId=`,
  `GET /transactions?accountId=&from=&to=&pageSize=500`, `GET /bills?accountId=`,
  `GET /investments?itemId=`.
- Salvar uma resposta de cada em `core/src/test/resources/pluggy/`,
  **anonimizada**: trocar nomes, CPF, números de conta, descrições e valores por
  dados fictícios mantendo formato e sinal. Dado real do Rafael nunca vai para o git.
- Responder e registrar numa nota nova do cofre, "Spike Meu Pluggy":
  1. Sinal do valor em cartão e em conta confirma a tabela da [[Sincronização]]?
  2. `category` vem preenchida no Meu Pluggy? (Na Pluggy paga é recurso do plano
     Pro.) Define se a T18 usa a categoria da Pluggy ou uma própria.
  3. Quais tipos de investimento aparecem e com quais campos?
  4. Qual campo do item muda quando a Pluggy atualiza (`lastUpdatedAt`,
     `resourcesCollectedAt`)? Define o passo 3 da sincronização.
  5. Webhooks estão disponíveis no Meu Pluggy?

**Pronto quando:** fixtures anonimizadas commitadas e nota do spike com as cinco
respostas.

### T03 — Esqueleto do core

**Objetivo:** projeto Spring Boot vazio, mas com toda a infraestrutura de build e
testes. Desenho em [[Core]].

- `core/`: gerado pelo Rafael no Spring Initializr **sem dependências** — Maven,
  Spring Boot 4.1.1, Java 25, Jar, YAML, grupo `com.wallet`, pacote
  `com.wallet.core`. Porta 8081.
- **JDK 25:** baixar no IntelliJ (Project Structure > SDK). Na linha de comando, o
  `mvnw` usa o `JAVA_HOME`, que hoje vem vazio e o `java` do PATH é um JRE 8: apontar
  o `JAVA_HOME` para o JDK 25 antes de rodar.
- Dependências no `pom.xml` (versões pelo parent do Spring Boot):
  `spring-boot-starter-webmvc`, `-validation`, `-data-jpa`, `-flyway` +
  `flyway-database-postgresql`, `-actuator`, driver `postgresql`, Lombok,
  `spring-boot-configuration-processor`, **Spring Modulith 2.1.1** (BOM em
  `dependencyManagement`), e para teste `spring-boot-starter-webmvc-test`,
  `-data-jpa-test`, `spring-boot-testcontainers`, Testcontainers 2 (Postgres e
  JUnit), `spring-modulith-starter-test`. Segurança entra na T05 e WireMock na T06,
  cada uma com o que usa.
- Testes separados pelo padrão do Maven: **Surefire** roda `*Test` no `mvn test`
  (unitário, sem Docker) e **Failsafe** roda `*IT` no `mvn verify` (Testcontainers
  Postgres).
- Módulo `shared`: `Money` (wrapper de `BigDecimal` com escala 2 e
  `RoundingMode.HALF_EVEN`), `PageResponse<T>` (`items, page, pageSize, total,
  totalPages`; página fora do intervalo = 422 `page.invalid`), `DomainException`
  com código de erro, `GlobalExceptionHandler` devolvendo
  `{ "code": "...", "message": "..." }`, `Clock` injetável.
- Jackson 3 configurado para serializar `BigDecimal` como string.
- Virtual threads ligadas (`spring.threads.virtual.enabled=true`).
- `ModularityTest` com `ApplicationModules.of(CoreApplication.class).verify()`.
- Segredos só em variável de ambiente (`WALLET_DB_PASSWORD` e as próximas); o
  `application.yaml` versionado tem placeholders com padrões de dev.
- `TestCoreApplication` para subir o core contra um Postgres descartável
  (`./mvnw spring-boot:test-run`).

**Pronto quando:** `mvnw verify` verde (unitários e integração); a aplicação sobe
contra o banco da T04; `/actuator/health` responde `UP`.

**Feito em 2026-09-29.** Validado: `mvnw verify` verde (o IT precisa do Docker ligado).

### T04 — Banco local (infra)

**Objetivo:** base de desenvolvimento no Postgres 18 nativo da máquina (porta 5432).

- Script idempotente `core/db/setup-local.sql` cria o usuário `wallet` e o banco
  `wallet_dev`. A senha vai na linha de comando (`psql -v wallet_password=...`), nunca
  em arquivo, e depois na variável `WALLET_DB_PASSWORD`.
- Flyway cria o schema; nada de `ddl-auto` além de `validate`.

**Pronto quando:** o core sobe e o Flyway roda contra o `wallet_dev`. O script roda como
superusuário `postgres`, cuja senha só o Rafael tem.

**Script escrito em 2026-09-29**; falta o Rafael rodar.

---

## Fase 1 · Core

Todas as rotas do core ficam em `/internal` e só o BFF as chama.

### T05 — Identidade

**Objetivo:** cadastro, login e emissão de tokens. Regras em [[Autenticação]].

- Migration `users` e `refresh_tokens` ([[Modelo de Dados]]).
- `POST /internal/auth/register` `{ email, password, displayName }` → 201
  `UserResponse`. Senha de 8+ caracteres; e-mail normalizado em minúsculas;
  duplicado = 409 `auth.email_taken`.
- `POST /internal/auth/login` `{ email, password }` → `{ accessToken, expiresIn,
  refreshToken }`. Sempre no corpo: o core não sabe de cookie.
- `POST /internal/auth/refresh` `{ refreshToken }`: rotação com detecção de reuso e
  tolerância de 1 min (mesma lógica do Storage).
- `POST /internal/auth/logout` `{ refreshToken }`: revoga a família.
- `GET /internal/me`.
- Dependências novas: `spring-boot-starter-security`,
  `spring-boot-starter-security-oauth2-resource-server` (o nome antigo
  `-oauth2-resource-server` está depreciado no Boot 4.1) e, para teste,
  `spring-boot-starter-security-test`. Liberar `/actuator/health`.
- Access token **JWT RS256** de 15 min assinado com `WALLET_JWT_PRIVATE_KEY`.
  Gerar o par de chaves de dev localmente (arquivos `.pem` fora do git). Validação
  pelo resource server. Todas as rotas exigem autenticação, menos
  `/internal/auth/**` e `/actuator/health`.
- Bloqueio: 5 erros → 15 min (`auth.locked`). Mesma mensagem para e-mail inexistente
  e senha errada (`auth.invalid_credentials`).

**Pronto quando:** testes de contrato HTTP cobrindo login, refresh (inclusive reuso e
tolerância), logout, bloqueio e rota protegida sem token (401). MockMvc montado com
`springSecurity()` na mão.

**Feito em 2026-09-29:** 14 cenários em `AuthFlowIT` com relógio controlável
(`MutableClock`), verdes. Sem chave configurada o core gera uma chave RS256 temporária
e avisa no log. O usuário logado chega aos controllers por `@CurrentUserId UUID`.

### T06 — Adaptador da Pluggy

**Objetivo:** o módulo `provider` com a porta e a implementação da Pluggy. Desenho
em [[Core]].

- Dependência nova, só de teste: `org.wiremock:wiremock-standalone` 3.13.2 (a 4.x
  ainda é beta).
- Records: `ProviderItem`, `ProviderAccount`, `ProviderTransaction`,
  `ProviderBill`, `ProviderInvestment`, já com valor positivo + `Direction`.
- `PluggyFinancialDataProvider` com `RestClient`; base URL e credenciais por
  configuração; `apiKey` em cache com renovação única (2 h de validade, renova
  com 10 min de folga).
- Paginação das transações resolvida dentro do adaptador (500 por página).
- Normalização de sinal conforme a nota "Spike Meu Pluggy" (T02).
- Erros: 401/403 da Pluggy → `provider.auth_failed`; 404 → `provider.item_not_found`;
  5xx e timeout → `provider.unavailable`. Timeout de 10 s.
- Nenhuma classe fora de `provider.pluggy` importa DTO da Pluggy (o teste de
  modularidade garante).

**Pronto quando:** testes de integração com WireMock usando as fixtures da T02:
autenticação e cache do `apiKey`, paginação, sinal de cartão e de conta, cada código
de erro.

**Feito em 2026-09-29, antes da T02:**
- 14 testes com WireMock e 4 do mapper, verdes.
- As fixtures são **sintéticas**, montadas a partir dos tipos do SDK oficial
  (`pluggyai/pluggy-node`, `src/types`). A T02 troca por respostas reais anonimizadas.
- Transações pela `/v2/transactions` (cursor). A `/transactions` por página está
  depreciada no SDK.
- Header `X-API-KEY`; um 401 renova a chave e tenta de novo uma vez.
- Códigos: `provider.not_configured`, `provider.auth_failed`, `provider.not_found`,
  `provider.unavailable`. Sem credenciais o core sobe normalmente.
- HTTP/1.1 fixo: o `HttpClient` do JDK tenta upgrade h2c em `http://`, e o WireMock
  reseta a conexão.
- Exigiu o `spring-boot-starter-restclient` (no Boot 4 o `RestClient.Builder` saiu do
  starter web).
- **Duas hipóteses para a T02 confirmar:**
  1. Sinal do cartão: valor positivo é compra.
  2. Datas: 00:00 UTC é uma data de calendário; qualquer outro horário é lido no fuso
     de São Paulo.

### T07 — Conexões

**Objetivo:** vincular um item da Pluggy a um usuário do Wallet.

- Migration `connections`.
- `POST /internal/connections` `{ providerItemId }`: busca o item no provedor (404 →
  `connection.item_not_found`), impede vínculo duplicado (409
  `connection.already_linked`), grava com status `ACTIVE` e dispara a primeira
  sincronização de forma assíncrona.
- `GET /internal/connections`: instituição, status, última sincronização.
- `DELETE /internal/connections/{id}`: no modo `MEU_PLUGGY` só desvincula e apaga os
  dados locais; no `PLUGGY_CONNECT` também chama `DELETE /items/{id}` (revogação).
- Um usuário só enxerga as próprias conexões: id de outro usuário → 404.
- O modo vem de `wallet.provider.connection-mode`.

**Pronto quando:** testes de integração cobrindo vincular, duplicado, item
inexistente, desvincular e isolamento entre dois usuários.

### T08 — Sincronização

**Objetivo:** o algoritmo inteiro da [[Sincronização]].

- Migrations `accounts`, `balance_snapshots`, `transactions`, `credit_card_bills`,
  `investments`, `sync_runs`.
- `AttributeConverter` AES-GCM para `transactions.description` com
  `WALLET_DATA_KEY` e prefixo de versão da chave.
- `SyncService.sync(connectionId, trigger)` seguindo os 8 passos da nota, com
  `pg_try_advisory_lock` por conexão e uma transação por conexão.
- Agendador a cada `wallet.sync.interval` (padrão 6 h), percorrendo as conexões
  `ACTIVE`. Liga e desliga por `wallet.sync.scheduler-enabled` (padrão `true`), para
  que só uma instância agende quando houver mais de uma.
- `POST /internal/connections/{id}/sync` → 202 `{ syncRunId }`; 429 `sync.too_soon`
  se houve sincronização há menos de 15 min; 409 `sync.in_progress` se já estiver
  rodando.

**Pronto quando:** testes de integração (Testcontainers + WireMock) para primeira
carga de 365 dias, carga incremental, transação que muda de ID (a antiga recebe
`deleted_at` e a nova entra), item com erro (`NEEDS_ATTENTION`), duas
sincronizações ao mesmo tempo (a segunda vira `SKIPPED`) e falha no meio (nada
gravado).

### T09 — API de leitura

**Objetivo:** os dados que o BFF vai compor nas telas. Rotas em [[Core]].

- `GET /internal/overview`: patrimônio = saldo em conta + investimentos − fatura
  aberta; entradas e saídas do mês corrente; data da última sincronização.
- `GET /internal/accounts`, `GET /internal/transactions` (filtros `from`, `to`,
  `accountId`, `direction`, `q`; busca por texto em memória no período; paginado;
  sem as transações com `deleted_at`).
- `GET /internal/credit-cards` e `GET /internal/credit-cards/{accountId}/bills`.
- `GET /internal/investments` com total por `kind`.
- `GET /internal/insights/spending-by-category?month=` e
  `GET /internal/insights/net-worth?from=&to=`.
- Toda consulta filtrada pelo usuário do JWT.

**Pronto quando:** testes de contrato para cada rota (formato, dinheiro como string,
paginação, isolamento entre usuários) e testes unitários das somas.

---

## Fase 2 · BFF

### T10 — Esqueleto do BFF

**Objetivo:** a porta pública, com o mesmo desenho do BFF do Trilha. Desenho em
[[BFF]].

- `bff/`: gerado pelo Rafael no Initializr sem dependências, com as mesmas opções
  do core (Maven, Boot 4.1.1, Java 25, Jar, YAML), pacote `com.wallet.bff`. Porta
  8080, virtual threads ligadas.
- Dependências no `pom.xml`: `spring-boot-starter-webmvc`, `-security`,
  `-oauth2-resource-server`, `-validation`, `-actuator`, `-cache` + Caffeine,
  Resilience4j direto (não o do Spring Cloud; versão compatível com o Boot 4.1),
  Lombok; para teste, WireMock e `spring-security-test`.
- `config/`: `RestClientConfig` com o `CoreClient` apontando para `WALLET_CORE_URL`
  (timeout 5 s), `BearerPropagationInterceptor` e `TraceIdPropagationInterceptor`
  (como no Trilha), `CacheConfig` com TTL de `wallet.cache.screen-ttl`.
- `auth/SecurityConfig`: resource server com a chave pública RS256
  (`WALLET_JWT_PUBLIC_KEY`); `/api/auth/**` e `/actuator/health` abertos.
- CORS de `wallet.web.allowed-origins` com credenciais; cabeçalhos de segurança.
- `ratelimit/`: login por IP, demais rotas por usuário.
- `exception/GlobalExceptionHandler`: erro do core passa com o mesmo status e
  `{ code, message }`; core fora do ar ou circuito aberto → 503
  `bff.core_unavailable`.

**Pronto quando:** o BFF sobe na 8080 com o core na 8081; `/actuator/health` `UP`;
teste com WireMock provando que o Bearer e o `X-Trace-Id` chegam ao core e que um erro
do core passa intacto.

### T11 — Rotas do BFF

**Objetivo:** o contrato completo do app. Tabela de rotas em [[BFF]].

- Auth: `POST /api/auth/register|login|refresh|logout` e `GET /api/me`. Com
  `X-Wallet-Client: web`, o refresh vai só no cookie (`HttpOnly`, `Secure`,
  `SameSite=Strict`, `Path=/api/auth`) e o refresh lê o cookie; com `mobile`, vai no
  corpo. O `/api/auth/refresh` exige o header (proteção CSRF).
- `GET /api/home`: compõe `overview`, `accounts`, `credit-cards`, as 5 últimas
  `transactions` e `connections` em paralelo (virtual threads). Se uma parte falhar,
  a resposta vem com as outras e a parte marcada como indisponível.
- `GET /api/transactions`, `GET /api/cards`, `GET /api/cards/{accountId}/bills`,
  `GET /api/investments`, `GET /api/insights?month=` (categoria do mês + patrimônio
  dos últimos 6 meses).
- Conexões: `GET|POST|DELETE /api/connections...` e
  `POST /api/connections/{id}/sync`, todas limpando o cache do usuário.
- Cache Caffeine por usuário nas rotas de tela (`home`, `cards`, `investments`,
  `insights`); o extrato não usa cache.

**Pronto quando:** testes de contrato de cada rota com o core no WireMock: entrega do
token por plataforma, composição da home (inclusive uma parte falhando), cache
(segunda chamada não chega ao core; sincronização limpa) e isolamento entre usuários
no cache.

---

## Fase 3 · App

O app só conhece o BFF (`http://localhost:8080` no dev; no emulador Android,
`http://10.0.2.2:8080`).

### T12 — Esqueleto do app

**Objetivo:** projeto Flutter com a base de tudo. Estrutura em [[App Flutter]].

- `flutter create` em `app/` com plataformas `android`, `ios` e `web`; atualizar o
  Flutter do 3.44.2 para a estável atual antes.
- Pacotes da nota [[App Flutter]].
- `core/`: `ApiClient` (dio, base URL do BFF por `--dart-define`, header
  `X-Wallet-Client` conforme a plataforma, mapa de códigos de erro para mensagens em
  pt-BR), `Money` com `Decimal`, tema escuro, `app_pt.arb`, `go_router` com shell
  adaptativo (barra inferior < 600 px, rail de 600 a 839 px, menu lateral a partir de
  840 px).
- Telas vazias para cada feature, só para navegar.

**Pronto quando:** roda no emulador `trilha_pixel` e no Chrome (`flutter run -d
chrome`) com a navegação trocando de formato ao redimensionar a janela; testes
unitários do `Money`.

### T13 — Login no app

- Telas de login e cadastro, erros vindos dos códigos do BFF.
- `SessionStore` com import condicional (mobile: secure storage; web: cookie com
  `withCredentials`).
- Interceptor: Bearer em toda chamada; em 401, uma renovação por vez e repete a
  chamada; se a renovação falhar, volta para o login.
- Celular: biometria ao voltar depois de 5 min em segundo plano (`local_auth`).
- Web: sai da sessão após 30 min sem uso.

**Pronto quando:** login, reabrir o app sem pedir senha, expiração do access token
renovando sozinha, logout; tudo no emulador e no Chrome. Teste do interceptor com
duas chamadas simultâneas gerando um refresh só.

### T14 — Conexões no app

- Lista de conexões com instituição, status e "atualizado há X".
- Botão "Atualizar" (trata `sync.too_soon` e `sync.in_progress`).
- "Vincular conexão" pedindo o `itemId` (só no modo `MEU_PLUGGY`), com texto
  explicando onde achar o ID no painel da Pluggy.
- Desvincular com confirmação na própria tela.
- Status `NEEDS_ATTENTION` com aviso claro do que fazer.

### T15 — Visão geral

- Uma chamada a `GET /api/home`: patrimônio total, saldo em conta, fatura aberta,
  investimentos, entradas e saídas do mês, contas por instituição, últimas
  transações.
- Parte indisponível mostra aviso só naquele bloco.
- Puxar para atualizar no celular. No PC, os blocos lado a lado.

### T16 — Extrato

- Lista por dia com entrada em verde e saída neutra, seletor de mês, filtro por
  conta e por direção, busca por texto.
- Paginação com rolagem infinita.
- Parcela "3/10" quando vier da Pluggy.

### T17 — Cartões e investimentos

- Cartões: limite, disponível, fatura atual e faturas anteriores.
- Investimentos: total por tipo e lista de posições com vencimento.

### T18 — Gastos e patrimônio

- Gráfico de gasto por categoria no mês (`fl_chart`), de `GET /api/insights`. Se a
  T02 mostrar que o Meu Pluggy não traz categoria, agrupar por descrição até existir
  categorização própria.
- Gráfico da evolução do patrimônio a partir dos snapshots diários (começa vazio e
  cresce a cada dia de uso).

---

## Backlog — antes de abrir para outras pessoas

- Plano Dados da Pluggy, widget Pluggy Connect no celular e `connect_token`.
- Webhooks no core com header secreto, lista de IPs e fila de eventos idempotente. O
  BFF encaminha a rota pública do webhook para o core.
- Segundo fator no login web (passkey ou TOTP).
- LGPD: excluir conta, exportar dados, política de privacidade, termos, tela de
  consentimento com a finalidade ([[Consentimento]]).
- Deploy: VPS, HTTPS no BFF, core e Postgres só na rede interna, backups, logs sem
  dado sensível.
- CI e build de iOS (Codemagic ou runner macOS).
- CNPJ, contas de desenvolvedor nas lojas e nome definitivo
  ([[Decisões Pendentes]]).
- Reconferir a regra de parcerias do BC ([[Risco Regulatório 2026]]).
