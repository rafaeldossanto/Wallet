---
tags: [arquitetura, backend, bff]
atualizado: 2026-10-03
porta: 8080
repo: Work/Wallet/bff
---

# BFF

**Decidido pelo Rafael em 2026-09-29:** um BFF (Backend for Frontend) entre o app e o
[[Core]]. É a **única porta pública** do Wallet.

**Java 25 · Spring Boot 4.1 · RestClient · Caffeine · Resilience4j** — mesmo desenho
do BFF do Trilha. Construído em 2026-10-01 (T10 e T11 do [[Plano de Implementação]]).

## O que o BFF faz

| Papel | Como |
|---|---|
| **Porta de entrada** | Só ele fica na internet (atrás do HTTPS). O core fica numa rede interna, como na rede segmentada do Trilha |
| **Sessão por plataforma** | Recebe os tokens do core e decide a entrega: no celular, no corpo; no navegador, refresh em cookie `HttpOnly`. Ver [[Autenticação]] |
| **Rotas por tela** | Monta a resposta de uma tela juntando várias chamadas ao core em paralelo. O celular faz 1 requisição em vez de 5 |
| **Cache curto** | Caffeine por usuário, 60 s nas telas agregadas. Limpa na hora quando o usuário pede sincronização, mexe em conexões ou quando uma sincronização termina no fundo (ver abaixo) |
| **Proteção** | CORS só para a origem do app web, limite de requisições por IP (auth) e por usuário (resto) |
| **Resiliência** | Timeout de 5 s para o core e circuit breaker; core fora do ar vira `503 bff.core_unavailable` |

O BFF **não guarda dado** de negócio, **não fala com a Pluggy**, **não tem banco** e
**não tem segredo**: só precisa saber onde está o core.

## Estrutura

```
bff/src/main/java/com/wallet/bff/
  auth/         SecurityConfig, ClientType (X-Wallet-Client), usuário autenticado
  cache/        ScreenCache (Caffeine, chave sempre com o usuário)
  client/       CoreClient (HTTP + circuit breaker), CoreApi (uma chamada por rota)
  config/       RestClientConfig, interceptors de Bearer e trace, RequestContextExecutor
  controller/   Auth, Me, Home, Screen (extrato, cartões, investimentos, insights),
                Connection
  exception/    GlobalExceptionHandler (repassa o erro do core byte a byte)
  model/dto/    request/ e response/ (os contratos do app)
  ratelimit/    janela fixa por IP (auth) e por usuário
  service/      AuthService, SessionDelivery, HomeService, ScreenService,
                ConnectionService
  trace/        X-Trace-Id
```

## API pública (o contrato do app)

| Método | Rota | Chama no core |
|---|---|---|
| POST | `/api/auth/register` | `/internal/auth/register` |
| POST | `/api/auth/login` | `/internal/auth/login` e entrega por plataforma |
| POST | `/api/auth/refresh` | `/internal/auth/refresh` (web: lê o cookie) |
| POST | `/api/auth/logout` | `/internal/auth/logout` e apaga o cookie |
| GET | `/api/me` | `/internal/me` |
| GET | `/api/home` | `overview` + `accounts` + `credit-cards` + últimas 5 `transactions` (30 dias) + `connections`, em paralelo |
| GET | `/api/transactions` | `/internal/transactions` (mesmos filtros, repassados sem interpretar) |
| GET | `/api/accounts` | `/internal/accounts` (o filtro de conta do extrato; com cache) |
| GET | `/api/cards` | `/internal/credit-cards` |
| GET | `/api/cards/{accountId}/bills` | `/internal/credit-cards/{accountId}/bills` |
| GET | `/api/investments` | `/internal/investments` |
| GET | `/api/investments/history?period=` | `net-worth` do período (1M, 3M, 6M, 1A ou TUDO = até 2 anos), só o total investido por dia; cache por período |
| GET | `/api/calendar?month=` | `/internal/insights/daily-spending` (gasto por dia do mês, mesma regra dos gastos por categoria); cache por mês |
| GET | `/api/calendar/spending?from&to&page` | `/internal/transactions?spending=true` + contas + conexões em paralelo: os gastos do dia ou do mês, com o nome da conta e do banco |
| GET | `/api/insights?month=` | `spending-by-category` do mês + `net-worth` dos últimos 6 meses |
| GET, POST, DELETE | `/api/connections...` | `/internal/connections...`; POST e DELETE limpam o cache do usuário |
| POST | `/api/connections/{id}/sync` | `/internal/connections/{id}/sync` (202) e limpa o cache |

### A home

- As cinco partes saem em paralelo em virtual threads que levam junto o usuário e o
  `X-Trace-Id` da requisição.
- Uma parte que falha fica vazia e o nome dela vai em `unavailable` (`overview`,
  `accounts`, `connections`, `creditCards`, `recentTransactions`). O app mostra o resto
  e um aviso.
- **401 em qualquer parte derruba a tela inteira:** a sessão acabou, não adianta meia
  home. Se as cinco falham, o erro sobe como veio.
- Só a home **completa** entra no cache. Uma home pela metade nunca é reaproveitada.
- `institutions` agrupa as contas (sem cartão) por conexão, na ordem das conexões.

### Valores

Dinheiro passa como texto do core até o app (`"1234.50"`). O BFF nunca faz conta com
dinheiro, então não tem como arredondar errado.

## Segurança entre BFF e core

- O BFF valida o JWT antes de qualquer coisa e **repassa o mesmo Bearer** ao core, que
  valida de novo. O BFF não consegue emitir token: só o core tem a chave privada.
- A chave pública vem do **JWKS do core** (`/internal/.well-known/jwks.json`), buscada
  no primeiro uso e guardada em cache. O BFF sobe mesmo com o core fora do ar, e não
  existe chave para configurar nele.
- O `kid` da chave é o thumbprint dela (RFC 7638). Chave nova, `kid` novo: o BFF só
  rebusca o JWKS quando vê um `kid` desconhecido, então com `kid` fixo ele ficaria até
  5 min recusando os tokens de um core reiniciado com chave temporária. Achado e
  corrigido em 2026-10-01, com teste dos dois lados.
- As rotas `/api/auth/**` **ignoram o Bearer**: o app pede refresh justamente quando o
  access token venceu, e o Spring recusaria esse header vencido antes de chegar ao
  refresh.
- `X-Trace-Id` gerado no BFF (ou aceito do app, se for curto e só com letras, números e
  hífen) e propagado, para seguir uma requisição nos logs dos dois serviços.
- Erro do core: status e `{ code, message }` passam intactos. O app só conhece os
  códigos. Os do próprio BFF: `auth.client_required`, `auth.invalid_refresh_token`,
  `auth.unauthenticated`, `rate_limit.exceeded`, `bff.core_unavailable`,
  `request.invalid_parameter`, `request.malformed`, `route.not_found`.

## Configuração

| Propriedade / variável | Para quê |
|---|---|
| `WALLET_CORE_URL` | Endereço do core (`http://localhost:8081` no dev) |
| `WALLET_WEB_ALLOWED_ORIGINS` | Origem do Flutter Web (`http://localhost:5000` no dev) |
| `wallet.web.cookie-secure` | Flag `Secure` do cookie; ligada também no dev, porque o navegador aceita em `localhost` |
| `wallet.cache.screen-ttl` | TTL do cache das telas (padrão 60 s) |
| `wallet.rate-limit.*` | Janela (1 min), 20 por IP em `/api/auth`, 300 por usuário no resto |

Porta **8080**. É o endereço que o app usa.

> [!note] Uma instância só
> O limite de requisições e o cache ficam na memória do BFF. Com mais de uma instância,
> os dois iriam para o Redis.

### Cache e sincronização no fundo

A sincronização termina no core, fora da vista do BFF. Achado rodando o app contra a Pluggy
de demonstração: a home montada logo depois de vincular (antes da primeira sincronização)
entrava no cache e ficava vazia por um minuto. Desde 2026-10-02:

- Toda vez que o BFF vê as conexões do usuário (`GET /api/connections`, que o app consulta
  enquanto espera a sincronização, e a própria home), ele guarda o estado delas (ids, última
  sincronização, status). Se mudou desde a última vez, descarta as telas em cache daquele
  usuário.
- Uma home com conexão sincronizando ou que nunca sincronizou não entra no cache.
- Sincronização agendada (a cada 6 h) com o app fechado: no pior caso, a tela fica até 60 s
  velha.

## Custo que o BFF traz

- Uma mudança que chega ao app passa por dois serviços (DTO no core e no BFF). É o
  que a skill de fan-out do BFF resolve no Trilha.
- Um salto de rede a mais, interno e na ordem de milissegundos.

## Testes

36 testes, todos com o core simulado no **WireMock** e tokens assinados de verdade:

- `BffEdgeTest`: token ausente, vencido, de outra chave ou de outro emissor; Bearer e
  trace chegando ao core; erro do core intacto; core fora do ar e circuito aberto; CORS.
- `BffRoutesTest`: entrega por plataforma (atributos do cookie, refresh rotacionando o
  cookie, refresh recusado apagando o cookie, Bearer vencido no refresh), composição da
  home (completa, com parte falhando, 401), cache por usuário limpo pela sincronização,
  home parcial fora do cache, home durante a primeira sincronização fora do cache,
  sincronização no fundo descartando o cache, filtros do extrato com `&` e `+`, insights,
  cartões, contas e conexões.
- `RateLimitTest`: orçamento por usuário e login por IP.
- `CoreKeyRotationTest`: chave nova no core é aceita sem reiniciar o BFF.

Ver [[Visão Geral]] e [[App Flutter]].
