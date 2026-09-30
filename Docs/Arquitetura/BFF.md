---
tags: [arquitetura, backend, bff]
atualizado: 2026-09-29
porta: 8080
repo: Work/Wallet/bff
---

# BFF

**Decidido pelo Rafael em 2026-09-29:** um BFF (Backend for Frontend) entre o app e o
[[Core]]. É a **única porta pública** do Wallet.

**Java 25 · Spring Boot 4.1 · RestClient · Caffeine · Resilience4j** — mesmo desenho
do BFF do Trilha.

## O que o BFF faz

| Papel | Como |
|---|---|
| **Porta de entrada** | Só ele fica na internet (atrás do HTTPS). O core fica numa rede interna, como na rede segmentada do Trilha |
| **Sessão por plataforma** | Recebe os tokens do core e decide a entrega: no celular, no corpo; no navegador, refresh em cookie `HttpOnly`. Ver [[Autenticação]] |
| **Rotas por tela** | Monta a resposta de uma tela juntando várias chamadas ao core em paralelo. O celular faz 1 requisição em vez de 5 |
| **Cache curto** | Caffeine por usuário, 60 s nas telas agregadas. Limpa na hora quando o usuário pede sincronização ou mexe em conexões |
| **Proteção** | CORS, limite de requisições por IP (login) e por usuário (resto), cabeçalhos de segurança |
| **Resiliência** | Timeout de 5 s para o core e circuit breaker; core fora do ar vira `503 bff.core_unavailable` |

O BFF **não guarda dado** de negócio, **não fala com a Pluggy** e **não tem banco**.

## Estrutura (igual ao Trilha)

```
bff/src/main/java/com/wallet/bff/
  auth/         SecurityConfig, usuário autenticado (resource server)
  client/       CoreClient (RestClient)
  config/       RestClientConfig, BearerPropagationInterceptor,
                TraceIdPropagationInterceptor, CacheConfig
  controller/   AuthController, HomeController, TransactionController,
                CardController, InvestmentController, InsightController,
                ConnectionController
  exception/    GlobalExceptionHandler (repassa o código do core), fallbacks
  model/dto/    request/ e response/ (os contratos do app)
  ratelimit/    limites por IP e por usuário
  service/      composição das telas e cache
```

## API pública (o contrato do app)

| Método | Rota | Chama no core |
|---|---|---|
| POST | `/api/auth/register` | `/internal/auth/register` |
| POST | `/api/auth/login` | `/internal/auth/login` e entrega por plataforma |
| POST | `/api/auth/refresh` | `/internal/auth/refresh` (web: lê o cookie) |
| POST | `/api/auth/logout` | `/internal/auth/logout` e apaga o cookie |
| GET | `/api/me` | `/internal/me` |
| GET | `/api/home` | `overview` + `accounts` + `credit-cards` + últimas 5 `transactions` + `connections`, em paralelo |
| GET | `/api/transactions` | `/internal/transactions` (mesmos filtros) |
| GET | `/api/cards` | `/internal/credit-cards` |
| GET | `/api/cards/{accountId}/bills` | `/internal/credit-cards/{accountId}/bills` |
| GET | `/api/investments` | `/internal/investments` |
| GET | `/api/insights?month=` | `spending-by-category` + `net-worth` dos últimos 6 meses |
| GET, POST, DELETE | `/api/connections...` | `/internal/connections...` e limpa o cache do usuário |
| POST | `/api/connections/{id}/sync` | `/internal/connections/{id}/sync` e limpa o cache |

## Segurança entre BFF e core

- O BFF valida o JWT (chave pública RS256) antes de qualquer coisa e **repassa o
  mesmo Bearer** ao core, que valida de novo. O BFF não consegue emitir token: só o
  core tem a chave privada.
- `X-Trace-Id` gerado no BFF e propagado, para seguir uma requisição nos logs dos dois
  serviços.
- Erro do core: status e `{ code, message }` passam intactos. O app só conhece os
  códigos.

## Configuração

| Propriedade / variável | Para quê |
|---|---|
| `WALLET_CORE_URL` | Endereço do core (`http://localhost:8081` no dev) |
| `WALLET_JWT_PUBLIC_KEY` | Chave pública para validar o JWT |
| `wallet.web.allowed-origins` | Origem do Flutter Web (CORS) |
| `wallet.cache.screen-ttl` | TTL do cache das telas (padrão 60 s) |

Porta **8080**. É o endereço que o app usa.

## Custo que o BFF traz

- Uma mudança que chega ao app passa por dois serviços (DTO no core e no BFF). É o
  que a skill de fan-out do BFF resolve no Trilha.
- Um salto de rede a mais, interno e na ordem de milissegundos.

## Testes

- Contrato HTTP das rotas públicas com o core simulado no **WireMock**.
- Entrega de token por plataforma (cookie no web, corpo no mobile).
- Cache: segunda chamada não chega ao core; sincronização limpa o cache.
- Core fora do ar vira `503 bff.core_unavailable`.

Ver [[Visão Geral]] e [[App Flutter]].
