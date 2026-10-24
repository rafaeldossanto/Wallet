---
tags: [arquitetura, backend, bff]
atualizado: 2026-10-24
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
| **Proteção** | CORS só para a origem do app web, limite de requisições por IP (auth e lista de espera), por usuário (resto) e limites baixos próprios para vincular banco, pedir token do widget e buscar texto no extrato |
| **Resiliência** | Timeout de 5 s para o core; circuit breaker e bulkhead **por grupo de rotas** (ver [[#Grupos de rotas]]). Core fora do ar vira `503 bff.core_unavailable`; grupo cheio, `503 service.busy` |

O BFF **não guarda dado** de negócio, **não fala com a Pluggy**, **não tem banco** e
**não tem segredo**: só precisa saber onde está o core.

## Estrutura

```
bff/src/main/java/com/wallet/bff/
  auth/         SecurityConfig, ManagementSecurityConfig (saúde), ClientType (X-Wallet-Client),
                usuário autenticado
  cache/        ScreenCache (Caffeine, chave sempre com o usuário)
  client/       CoreClient (HTTP), CoreApi (uma chamada por rota, cada uma no seu grupo),
                CoreRouteGroup, CoreCallGuards (circuito + bulkhead por grupo)
  config/       RestClientConfig, interceptors de Bearer e trace, RequestContextExecutor
  controller/   Auth, Me, Home, Screen (extrato, cartões, investimentos, insights),
                Calendar, Connection, Planning, Waitlist
  exception/    GlobalExceptionHandler (repassa o erro do core byte a byte)
  model/dto/    request/ e response/ (os contratos do app)
  ratelimit/    janela fixa por IP (auth) e por usuário
  service/      AuthService, SessionDelivery, HomeService, ScreenService,
                CalendarService, ConnectionService, PlanningService, ScreenMonth
  trace/        X-Trace-Id
```

## API pública (o contrato do app)

| Método | Rota | Chama no core |
|---|---|---|
| POST | `/api/auth/register` | `/internal/auth/register` (corpo com `acceptTerms: true`; sem ele o core responde 400 `validation.failed`): **202 sem corpo** para qualquer e-mail (o core manda o link de confirmação ou um aviso ao dono) |
| POST | `/api/auth/login` | `/internal/auth/login` e entrega por plataforma. Corpo `{email, password, code?}`: `code` só depois de `401 auth.two_factor_required` |
| POST | `/api/auth/email/confirm` | `/internal/auth/email/confirm` com `{token}`: chamada pela página `/email/confirm`; responde `{result: ACCOUNT_CONFIRMED \| EMAIL_CHANGED}` ou `422 auth.link_invalid` |
| POST | `/api/auth/email/resend` | `/internal/auth/email/resend` com `{email}`: 202 para qualquer e-mail |
| POST | `/api/auth/password/forgot` | `/internal/auth/password/forgot` com `{email}`: **202 sem corpo** para qualquer e-mail (o core manda o link só para conta confirmada) |
| POST | `/api/auth/password/reset` | `/internal/auth/password/reset` com `{token, newPassword}`: **204**; link inválido, vencido ou usado é **400 `auth.link_invalid`**. Chamada pela página `/password/reset`; derruba todas as sessões da conta |
| GET | `/password/reset` | Página estática do BFF (`static/password`) que o link de "Esqueci minha senha" abre: lê o token depois do `#`, pede a senha nova duas vezes e troca ao tocar no botão |
| GET | `/api/me/export` | `/internal/me/export` (grupo `SCREENS`): o JSON do core, sem mexer nele, como download (`Content-Disposition: attachment; filename="wallet-meus-dados-AAAA-MM-DD.json"`, data de Brasília, e `Cache-Control: no-store`). **3 por dia por usuário** (`wallet.rate-limit.export-limit` e `export-window`): passou, 429 `rate_limit.exceeded`. LGPD, acesso e portabilidade; ver [[Core#Exportação dos dados (L9 e L10)]] |
| POST | `/api/me/terms` | `/internal/me/terms` com `{version}`: **204**; versão que não é a corrente, **409 `legal.terms_outdated`**, relayado. O aceite dos Termos de Uso e da Política de Privacidade (L15); ver [[Autenticação#Aceite dos Termos de Uso e da Política de Privacidade (L15, desde 2026-10-24)]]. Só o app obriga: nenhuma rota do BFF exige o aceite |
| PUT | `/api/me/password` | `/internal/me/password` com `{currentPassword, newPassword}`: **204** e todas as sessões caem (a atual também). Senha atual errada: 403 `auth.wrong_password`; nova igual à atual: 400 `validation.failed`. Conta no limite das rotas que pedem a senha de novo |
| POST | `/api/me/two-factor/recovery-codes` | `/internal/me/two-factor/recovery-codes` com `{password, code}`: **200** `{recoveryCodes}` (10 novos, trocam os antigos, mostrados uma vez). 2FA desligado: 409 `two_factor.not_enabled`. Conta no limite das rotas que pedem a senha de novo |
| GET | `/email/confirm` | Página estática do próprio BFF (`static/email`) que os links dos e-mails abrem: lê o token depois do `#` e confirma ao tocar no botão |
| POST | `/api/waitlist` | `/internal/waitlist` com `{email}`: **202 sem corpo** para qualquer e-mail. Pública, sem token. O corpo é `{email, consent}`; `consent` tem de ser `true` e `email` válido (`@NotBlank @Email @Size(max=320)`), senão **400 `validation.failed`** (a única validação que o BFF faz sozinho: o consentimento é dele) |
| POST | `/api/waitlist/confirm` | `/internal/waitlist/confirm` com `{token}`: **204**; link vencido, desconhecido ou trocado: 400 `waitlist.invalid_token`. Chamada pela página `/waitlist/confirm` |
| POST | `/api/waitlist/unsubscribe` | `/internal/waitlist/unsubscribe`: **204**, o token no corpo JSON `{token}` **ou** na query `?token=`. O botão de um clique dos provedores de e-mail (RFC 8058) manda `application/x-www-form-urlencoded` com `List-Unsubscribe=One-Click` e o token na query |
| GET | `/waitlist/confirm`, `/waitlist/unsubscribe` | Páginas estáticas do BFF (`static/waitlist`) que os links da lista de espera abrem ([[#Páginas dos links]]) |
| POST | `/api/webhooks/pluggy` | `/internal/webhooks/pluggy` com o corpo cru: **202** sem corpo. Pública, sem token, só com `wallet.webhooks.enabled` (senão 404) e o segredo em `X-Wallet-Webhook-Secret` (senão 401); lista de IPs opcional (403); 60 por minuto por IP. Ver [[#Webhooks da Pluggy]] |
| POST | `/api/auth/refresh` | `/internal/auth/refresh` (web: lê o cookie) |
| POST | `/api/auth/logout` | `/internal/auth/logout` e apaga o cookie |
| GET | `/api/me` | `/internal/me`: `{id, email, displayName, createdAt, pendingEmail, twoFactorEnabled, termsAcceptanceRequired, termsVersion, termsUrl, privacyUrl}` |
| PUT | `/api/me` | `/internal/me` com `{displayName}` |
| POST | `/api/me/email` | `/internal/me/email` com `{newEmail, password}`: 202; a troca só vale depois do link mandado ao novo endereço |
| POST | `/api/me/two-factor/setup` | `/internal/me/two-factor/setup` com `{password}`: `{secret, otpauthUri}` |
| POST | `/api/me/two-factor/enable` | `/internal/me/two-factor/enable` com `{code}`: `{recoveryCodes}` (mostrados uma vez) |
| DELETE | `/api/me/two-factor` | `/internal/me/two-factor` com `{password, code}` (204) |
| DELETE | `/api/me` | `/internal/me` com `{"password"}` no corpo, apaga o cookie de refresh e o cache de telas do usuário (204). Exclusão da conta (LGPD); senha errada é 403 `auth.wrong_password`. Espera o core por até 2 min (`wallet.core.slow-read-timeout`): o core revoga cada conexão na Pluggy antes de responder |
| GET | `/api/home` | `overview` + `accounts` + `credit-cards` + últimas 5 `transactions` (30 dias) + `connections`, em paralelo |
| GET | `/api/transactions` | `/internal/transactions` (mesmos filtros, repassados sem interpretar). Com `q`: 30 por minuto por usuário (`wallet.rate-limit.search-limit`) e grupo `SEARCH` |
| GET | `/api/accounts` | `/internal/accounts` (o filtro de conta do extrato; com cache) |
| GET | `/api/cards` | `/internal/credit-cards` |
| GET | `/api/cards/{accountId}/bills` | `/internal/credit-cards/{accountId}/bills` |
| GET | `/api/investments` | `/internal/investments` |
| GET | `/api/investments/history?period=` | `net-worth` do período (1M, 3M, 6M, 1A ou TUDO = até 2 anos), só o total investido por dia; cache por período |
| GET | `/api/calendar?month=` | `/internal/insights/daily-spending` (gasto por dia do mês, mesma regra dos gastos por categoria); cache por mês. `month` validado no BFF ([[#O mês das telas mensais]]) |
| GET | `/api/calendar/spending?from&to&page` | `/internal/transactions?spending=true` + contas + conexões em paralelo: os gastos do dia ou do mês, com o nome da conta e do banco |
| GET | `/api/insights?month=` | `spending-by-category` do mês + `net-worth` dos últimos 6 meses. `month` validado no BFF |
| GET, POST, DELETE | `/api/connections...` | `/internal/connections...`; POST e DELETE limpam o cache do usuário |
| POST | `/api/connections/{id}/sync` | `/internal/connections/{id}/sync` (202) e limpa o cache |
| POST | `/api/connections/connect-token` | `/internal/connections/connect-token`: o token do widget Pluggy Connect no Android |
| GET | `/api/planning?month=` | `/internal/planning/month` + `connections` em paralelo: o planejamento do mês com o nome e o logo do banco de cada fatura; cache por mês. `month` validado no BFF |
| GET | `/livez` | Nada: liveness da memória, `{"status":"UP"}`, sem token e fora do rate limit ([[#Saúde]]) |
| GET, POST | `/api/planning/fixed-expenses` | `/internal/planning/fixed-expenses`; POST limpa o planejamento em cache |
| PUT, DELETE | `/api/planning/fixed-expenses/{id}` | `/internal/planning/fixed-expenses/{id}`; limpam o planejamento em cache |
| PUT, DELETE | `/api/planning/expected-salary` | `/internal/planning/expected-salary`; limpam o planejamento em cache |
| GET | `/api/planning/fixed-expense-suggestions` | `/internal/planning/fixed-expense-suggestions`, sem cache |
| DELETE | `/api/planning/fixed-expense-suggestions/{id}` | `/internal/planning/fixed-expense-suggestions/{id}`; o planejamento em cache fica |

### Páginas dos links

O BFF serve quatro páginas estáticas, as únicas em HTML, abertas pelos links dos e-mails; todas com o
mesmo visual (`/email/confirm.css`), a CSP `script-src 'self'` e `Referrer-Policy: no-referrer`. Nenhuma
age no GET: scanners de e-mail abrem todo link, então só o **botão** faz o POST.

| Página | Link | Botão | Depois |
|---|---|---|---|
| `/email/confirm` | `…/email/confirm#token=…` | Confirmar e-mail | Conta confirmada ou e-mail trocado |
| `/waitlist/confirm` | `…/waitlist/confirm#token=…` | Confirmar meu e-mail | "Pronto! Você está na lista do Wallet." ou "Este link venceu ou já foi usado." |
| `/waitlist/unsubscribe` | `…/waitlist/unsubscribe?token=…` | Sair da lista | "Pronto, você saiu da lista. Não vamos mais te mandar e-mails." |
| `/password/reset` | `…/password/reset#token=…` | Trocar senha (depois de "Nova senha" e "Repita a nova senha") | "Senha trocada! Agora é só entrar no app do Wallet com a senha nova." ou "Este link venceu ou já foi usado..." |

O token de confirmação vem depois do `#` (o navegador não o envia) e sai da barra de endereço assim
que é lido. O de descadastro vai na **query**, porque o botão de um clique dos provedores faz POST
nessa URL exata (RFC 8058); a página também o tira da barra e posta em `/api/waitlist/unsubscribe?token=…`.
A landing page (`site/`) chama `POST /api/waitlist` de **outra origem**, sem credenciais: a origem dela
tem de estar em `WALLET_WEB_ALLOWED_ORIGINS` (lista separada por vírgula), senão o navegador bloqueia o
formulário (CORS).

### Webhooks da Pluggy

`POST /api/webhooks/pluggy`: pública (sem JWT) e **não assinada** pela Pluggy, então é uma porta para dicas, fechada por um
segredo. A rota só existe com `wallet.webhooks.enabled=true`; desligado (o padrão), responde **404**.

1. **Limite por IP**, antes de tudo: `wallet.rate-limit.webhook-limit`, 60 por minuto, 429 `rate_limit.exceeded`.
2. **Lista de IPs** (opcional, `WALLET_PLUGGY_WEBHOOK_IPS`: IPs ou CIDR; vazia = não confere), pelo mesmo `ClientAddress` do rate
   limit (proxies confiáveis incluídos). Fora dela: **403** `webhook.forbidden`.
3. **Segredo** no header `X-Wallet-Webhook-Secret` contra `WALLET_PLUGGY_WEBHOOK_SECRET`, em **tempo constante** (SHA-256 dos dois
   lados e `MessageDigest.isEqual`, como o core faz com a chave do gateway). Sem ele ou errado: **401** `webhook.unauthorized`, e o
   core não é chamado.
4. Repassa o **corpo cru** a `POST /internal/webhooks/pluggy` (grupo `READS`), sem interpretar, e responde **202** sem corpo. Tudo
   o que o core faz com o aviso está em [[Sincronização#Webhooks (desde 2026-10-24, achado A21)]].

Com `SPRING_PROFILES_ACTIVE=prod` e os webhooks ligados, o segredo é obrigatório e precisa de 32 caracteres ou mais: sem ele o BFF
não sobe, e a mensagem cita `WALLET_PLUGGY_WEBHOOK_SECRET`. Fora de produção, ligado e sem segredo, ele sobe e recusa todo aviso
com 401. A rota ignora um `Authorization` que venha junto.

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

### O planejamento (desde 2026-10-18)

- O core manda o plano pronto, totais e "outros lançamentos" de cada fatura incluídos; o BFF só
  junta o banco de cada cartão (`institutionName`, `institutionImageUrl`) pelas conexões, lidas
  em paralelo. Sem as conexões, o plano volta sem os nomes e não entra no cache.
- Cache por usuário e mês. Criar, alterar ou excluir um gasto fixo e mexer no salário esperado
  descartam **todos os meses** do planejamento daquele usuário (`ScreenCache.evictScreen`), sem
  tocar nas outras telas. Ler as conexões também alimenta a detecção de sincronização no fundo
  (abaixo), e um plano montado com conexão sincronizando não entra no cache.
- **Sugestões de gasto fixo** só passam: o core calcula a lista e o valor de cada uma. Sem cache,
  porque muda a cada gasto fixo criado e a cada sugestão dispensada. Dispensar não mexe no plano,
  então o plano em cache continua; adicionar uma é criar o gasto fixo, que já limpa o plano. O id
  é opaco para o BFF e vai ao core como variável de URI, codificado (`a b?c` vira `a%20b%3Fc`).

### O mês das telas mensais (desde 2026-10-22)

Achado A13: `/api/calendar`, `/api/insights` e `/api/planning` repassavam `month` como texto,
e cada texto diferente era uma entrada nova no cache e uma rodada de chamadas ao core. Um
usuário passeando por milhares de meses vazios (ou por `2026-1`, `2026-01`, `2026-001`...)
empurrava as telas dos outros para fora do cache (10 mil entradas no total). Agora o BFF lê
`month` como `yyyy-MM` estrito, a mesma regra do core, e só aceita de **10 anos atrás a 1 ano à
frente** do mês de São Paulo (`ScreenMonth`): os dados do Open Finance no Brasil começam em 2021
e o Wallet lê um ano para trás na primeira sincronização; o planejamento e o calendário podem
olhar adiante. Fora disso, ou fora do formato: 400 `request.invalid_parameter` (o mesmo código
do core, que o app traduz como filtro inválido), sem chamar o core. O cache guarda o mês na
forma canônica.

### Valores

Dinheiro passa como texto do core até o app (`"1234.50"`). O BFF nunca faz conta com
dinheiro, então não tem como arredondar errado.

## Grupos de rotas

Achado A12: um circuit breaker só guardava todas as chamadas ao core. Uma rota lenta (um vínculo
esperando a Pluggy, uma busca pesada estourando os 5 s) abria o circuito e **o app inteiro**
respondia 503 por 10 s. Desde 2026-10-22 cada chamada do `CoreApi` diz o seu grupo
(`CoreRouteGroup`), e cada grupo tem **um circuit breaker e um bulkhead seus** (`CoreCallGuards`):

| Grupo | Rotas do core | Bulkhead (chamadas em voo) |
|---|---|---|
| `AUTH` | cadastro, login, refresh, logout (cada login é um BCrypt), os links dos e-mails e a lista de espera | 16 |
| `READS` | `me`, conexões (lista), contas, cartões, faturas, investimentos, extrato sem `q`, gastos fixos e salário esperado | 64 |
| `SCREENS` | overview, gasto por categoria e por dia, patrimônio, planejamento do mês, sugestões | 32 |
| `SEARCH` | extrato com `q` | 16 |
| `PROVIDER` | vincular, desvincular, token do widget, sincronizar e excluir a conta (o core fala com a Pluggy) | 16 |

- **Circuito por grupo:** mesma regra de antes (janela de 20, mínimo 10, 50% de falha, 10 s
  aberto, só conta `ResourceAccessException`: erro de I/O e timeout). Um grupo que falha abre só o
  seu circuito: com a Pluggy fora do ar, vincular dá `503 bff.core_unavailable` e o extrato
  continua.
- **Bulkhead por grupo:** quantas chamadas daquele grupo podem estar em voo ao mesmo tempo
  (`wallet.core.max-concurrent-calls.*`). Cheio responde na hora **503 `service.busy`** com
  `Retry-After: 1` só para as rotas do grupo (na home, a parte daquele grupo vai para
  `unavailable` e o resto aparece). O bulkhead fica **por fora** do circuito: chamada recusada por
  falta de vaga não chega ao circuito, então não conta nem como sucesso nem como falha, e chamada
  recusada pelo circuito aberto devolve a vaga na hora.
- Os números são tetos do total, maiores que os bulkheads do core de propósito: quem decide por
  usuário é o core (ver [[Core#Disponibilidade]]); o BFF só impede que um grupo lento acumule
  chamadas sem fim. `PROVIDER` é pequeno porque cada chamada pode segurar uma requisição à Pluggy
  por segundos.
- **Por que Resilience4j aqui e código próprio no core:** o BFF já usava o circuit breaker do
  Resilience4j e o bulkhead de semáforo dele faz exatamente o teto total; o core precisa de um teto
  por usuário, que o Resilience4j não tem, e é uma classe de 40 linhas (`Bulkhead`).

## Saúde

Achado A18, desde 2026-10-22: `/actuator/health` era público e ficava fora do rate limit.

| Onde | O quê | Quem alcança |
|---|---|---|
| `GET /livez` na porta pública (8080) | Liveness: só `{"status":"UP"}`, da memória | Qualquer um, sem token: é o que um balanceador consulta |
| `GET /actuator/health` e `/actuator/health/readiness` na porta de gestão | Detalhes (disco, liveness, readiness) | Só a própria máquina |

- **Porta de gestão:** `WALLET_BFF_MANAGEMENT_PORT` (padrão **9080**), sempre em `127.0.0.1`. Na
  porta pública `/actuator/**` não existe (401 como qualquer rota sem sessão).
- `/livez` fica **fora do rate limit** de propósito: responde da memória (custa menos que a
  contagem do próprio limitador) e limitar só faria o balanceador achar que o BFF caiu durante um
  ataque. As duas têm uma cadeia de segurança própria (`ManagementSecurityConfig`), antes da
  principal; o `RateLimitFilter` não tem mais exceção para `/actuator`.
- A readiness do BFF não consulta o core: com o core fora, o BFF continua respondendo
  `503 bff.core_unavailable`, que o app sabe mostrar.
- A subida falha se a porta de gestão for a porta pública.

## Segurança entre BFF e core

- O BFF valida o JWT antes de qualquer coisa e **repassa o mesmo Bearer** ao core, que
  valida de novo. O BFF não consegue emitir token: só o core tem a chave privada.
- **Chave de gateway (desde 2026-10-19):** toda chamada ao core, inclusive login, cadastro e a
  busca do JWKS, leva o header `X-Gateway-Key` (`GatewayKeyInterceptor`) com o segredo
  `WALLET_GATEWAY_KEY`. O core recusa com 403 quem não manda. É o único segredo do BFF.
- A chave pública vem do **JWKS do core** (`/internal/.well-known/jwks.json`), buscada
  no primeiro uso e guardada em cache. O BFF sobe mesmo com o core fora do ar, e não
  existe chave de JWT para configurar nele.
- O `kid` da chave é o thumbprint dela (RFC 7638). Chave nova, `kid` novo: o BFF só
  rebusca o JWKS quando vê um `kid` desconhecido, então com `kid` fixo ele ficaria até
  5 min recusando os tokens de um core reiniciado com chave temporária. Achado e
  corrigido em 2026-10-01, com teste dos dois lados.
- As rotas `/api/auth/**` **ignoram o Bearer**: o app pede refresh justamente quando o
  access token venceu, e o Spring recusaria esse header vencido antes de chegar ao
  refresh.
- **Audience (desde 2026-10-22):** o BFF exige `aud: "wallet-api"` no access token, como o core.
  Token sem ela (emitido antes) ou com outra é 401 e o app renova.
- **Endereço do cliente para o core (desde 2026-10-22):** toda chamada feita na thread da
  requisição leva `X-Wallet-Client-Address` (`ClientAddressPropagationInterceptor`), o mesmo
  endereço do rate limit. O core usa no bloqueio de login por endereço; como só o BFF chega ao core
  (chave de gateway), o core confia nesse header.
- **Página de confirmação (desde 2026-10-22):** `GET /email/confirm` encaminha para
  `static/email/confirm.html` (com `confirm.js` e `confirm.css`). Toda resposta do BFF leva
  `Content-Security-Policy` restrita (só script, estilo e chamadas da própria origem, nada em frame)
  e `Referrer-Policy: no-referrer`.
- `X-Trace-Id` gerado no BFF (ou aceito do app, se for curto e só com letras, números e
  hífen) e propagado, para seguir uma requisição nos logs dos dois serviços.
- Erro do core: status e `{ code, message }` passam intactos. O app só conhece os
  códigos. Os do próprio BFF: `auth.client_required`, `auth.invalid_refresh_token`,
  `auth.unauthenticated`, `rate_limit.exceeded`, `rate_limit.connections`, `bff.core_unavailable`,
  `service.busy` (503, grupo de rotas cheio, com `Retry-After`; o core usa o mesmo código para os
  bulkheads dele e para o pool cheio), `request.invalid_parameter`, `request.malformed`,
  `request.too_large` (413), `route.not_found`. O app ainda não tem frase própria para
  `service.busy`: mostra a genérica ("Algo deu errado. Tente de novo."), que serve.

## Configuração

| Propriedade / variável | Para quê |
|---|---|
| `WALLET_CORE_URL` | Endereço do core (`http://localhost:8081` no dev) |
| `WALLET_GATEWAY_KEY` | Segredo enviado ao core em `X-Gateway-Key`, o **mesmo** valor do core. Vazio = chave de desenvolvimento; com `SPRING_PROFILES_ACTIVE=prod`, vazia, curta (< 32) ou a de desenvolvimento impedem a subida |
| `WALLET_WEB_ALLOWED_ORIGINS` | Origens permitidas no CORS, separadas por vírgula: o Flutter Web (`http://localhost:5000` no dev) **e a landing page**, que chama `/api/waitlist` de outra origem, sem credenciais (ex.: `https://wallet.example.com,https://site.example.com`) |
| `wallet.web.cookie-secure` | Flag `Secure` do cookie; ligada também no dev, porque o navegador aceita em `localhost` |
| `wallet.cache.screen-ttl` | TTL do cache das telas (padrão 60 s) |
| `wallet.rate-limit.*` | Janela (1 min), 20 por IP em `/api/auth/**` (a recuperação de senha incluída), 20 por usuário nas rotas que pedem senha ou código de novo (`/api/me/email`, `PUT /api/me/password`, `/api/me/two-factor/**` com os códigos novos, `DELETE /api/me`), 300 por usuário no resto |
| `wallet.jwt.audience` | `aud` exigida nos tokens, `wallet-api` (a mesma do core) |
| `wallet.rate-limit.link-limit` / `link-window` | `POST /api/connections` por usuário, além do geral: 10 por hora (cada vínculo faz o core ler até um ano do banco na Pluggy). Passou: 429 `rate_limit.connections` |
| `wallet.rate-limit.connect-token-limit` / `connect-token-window` | `POST /api/connections/connect-token` por usuário, além do geral: 10 por hora (cada token é uma chamada à Pluggy). Passou: 429 `rate_limit.connections` |
| `wallet.rate-limit.export-limit`, `.export-window` | A exportação dos dados (`GET /api/me/export`) por usuário, além do geral: 3 por 24 h (cada uma faz o core ler tudo o que o usuário tem). Passou: 429 `rate_limit.exceeded` |
| `wallet.rate-limit.search-limit` | Busca por texto (`GET /api/transactions` com `q`) por usuário e janela, além do geral: 30 por minuto (cada busca faz o core decifrar o mês). Passou: 429 `rate_limit.exceeded` |
| `wallet.rate-limit.waitlist-limit` / `waitlist-window` | `/api/waitlist/**` (inscrição, confirmação e descadastro, **juntos**) por **IP**, em vez do geral: 5 por hora. Rotas públicas, e cada inscrição manda um e-mail ao endereço digitado. Passou: 429 `rate_limit.exceeded` (o mesmo do auth), `Retry-After: 3600` |
| `WALLET_PLUGGY_WEBHOOKS_ENABLED` | `wallet.webhooks.enabled`, padrão `false`: liga `POST /api/webhooks/pluggy` (desligado, 404). Ver [[#Webhooks da Pluggy]] |
| `WALLET_PLUGGY_WEBHOOK_SECRET` | `wallet.webhooks.secret`: o que a Pluggy manda em `X-Wallet-Webhook-Secret`; o mesmo valor do core (que registra os webhooks). Com `prod` e webhooks ligados, obrigatório e com 32+ caracteres |
| `WALLET_PLUGGY_WEBHOOK_IPS` | `wallet.webhooks.allowed-ips`: IPs ou CIDR de onde a Pluggy chama, separados por vírgula; vazio = não confere |
| `wallet.rate-limit.webhook-limit` | `/api/webhooks/**` por **IP**, na janela geral (1 min): 60. Passou: 429 `rate_limit.exceeded` |
| `wallet.core.max-concurrent-calls.*` | Bulkhead de cada grupo de rotas (`auth` 16, `reads` 64, `screens` 32, `search` 16, `provider` 16). Ver [[#Grupos de rotas]] |
| `WALLET_BFF_MANAGEMENT_PORT` | `management.server.port`, padrão 9080, sempre em `127.0.0.1`: detalhes da saúde e readiness. Igual à porta pública impede a subida |
| `WALLET_MAX_BODY_SIZE` | `wallet.http.max-body-size`, padrão `256KB`, o mesmo filtro do core: `Content-Length` maior recebe 413 `request.too_large` antes da segurança; corpo chunked é cortado durante a leitura |
| `WALLET_TRUSTED_PROXIES` | `wallet.rate-limit.trusted-proxies`: proxies reversos (IPs ou CIDR) cujo `X-Forwarded-For` vale para o rate limit. Vazio = IP da conexão |

**IP do cliente no rate limit (desde 2026-10-19):** o `forward-headers-strategy` é `none`.
No modo `framework`, o Spring aplicava o `X-Forwarded-For` de qualquer cliente, e mudar o
header a cada requisição dava um limite novo a cada vez. Agora o `ClientAddress` usa o IP
da conexão, a não ser que ela venha de um proxy em `trusted-proxies`. Nesse caso ele lê o
header da direita para a esquerda, pula os proxies confiáveis e o primeiro endereço que não
é deles é o cliente; o que o cliente escreveu à esquerda é ignorado. Endereço que não é IP
literal (nada de DNS por requisição) conta no próprio proxy.

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

## Logs sem dado pessoal (L12)

Desde 2026-10-24. Um IP é dado pessoal, e a mensagem de uma exceção pode citar o pedido ou a resposta do core.

- **`[RATE-LIMIT]`:** o log não traz mais o IP. Traz o **pseudônimo** dele (`IpPseudonyms`): HMAC-SHA256 do endereço,
  com uma chave derivada da `WALLET_GATEWAY_KEY` (por um propósito próprio, `wallet-ip-log-v1`), e só os 12 primeiros
  caracteres hexadecimais. O mesmo IP dá o mesmo texto, então um abuso ainda se correlaciona entre as linhas, mas
  sem a chave ninguém volta ao endereço. O limite em si conta o IP de verdade, na memória. Usuário continua pelo id.
- **Erro inesperado:** o `GlobalExceptionHandler` grava o trace id e a classe e a pilha de cada exceção da cadeia, **sem
  mensagem** (`ExceptionLog.describe`).
- Testes: `IpPseudonymsTest`, `RateLimitTest#theLogNamesAnAddressByItsPseudonymNeverByTheAddress` e
  `GlobalExceptionHandlerTest`.

## Custo que o BFF traz

- Uma mudança que chega ao app passa por dois serviços (DTO no core e no BFF). É o
  que a skill de fan-out do BFF resolve no Trilha.
- Um salto de rede a mais, interno e na ordem de milissegundos.

## Testes

106 testes (2026-10-22), todos com o core simulado no **WireMock** e tokens assinados de verdade:

- `BffEdgeTest`: token ausente, vencido, de outra chave ou de outro emissor; Bearer e
  trace chegando ao core; erro do core intacto; core fora do ar e circuito aberto; CORS.
- `BffRoutesTest`: entrega por plataforma (atributos do cookie, refresh rotacionando o
  cookie, refresh recusado apagando o cookie, Bearer vencido no refresh), composição da
  home (completa, com parte falhando, 401), cache por usuário limpo pela sincronização,
  home parcial fora do cache, home durante a primeira sincronização fora do cache,
  sincronização no fundo descartando o cache, filtros do extrato com `&` e `+`, insights,
  cartões, contas e conexões, calendário (mês em cache, período com nome da conta e do
  banco, dia que volta mesmo sem os nomes, data inválida recusada) e o histórico dos
  investimentos por período, com cache por período.
- `PlanningRoutesTest`: o plano com o banco de cada cartão, cache por usuário e mês, gasto fixo e
  salário esperado descartando todos os meses em cache, sincronização no fundo descartando o
  plano, plano sem os nomes ou montado durante uma sincronização fora do cache, códigos do core
  repassados, sugestões de gasto fixo (lista sempre do core, dispensa sem descartar o plano, id
  codificado) e todas as rotas exigindo sessão.
- `RateLimitTest`: orçamento por usuário, login por IP e os limites próprios de vincular e do
  token do widget (caminho com `%63` não escapa: o filtro compara o caminho decodificado, como o
  Spring MVC roteia).
- `PasswordRoutesTest` (desde 2026-10-23): "esqueci" sem sessão e com 202 sem corpo, a troca repassada com o token,
  a nova senha e o endereço de onde veio, o link vencido do core repassado, a página servida com CSP e sem
  referrer, a troca de senha e os códigos novos com o Bearer do usuário (e 401 sem sessão), a recusa 403
  do core repassada; no `RateLimitTest`, a troca de senha e os códigos novos contando como as rotas que pedem a senha de novo.
- `WebhookTest`, `WebhookDisabledTest` e `WebhookSecretTest` (desde 2026-10-24): segredo certo (o corpo vai ao core como veio, sem
  `Authorization`), errado e ausente (401, o core não é chamado), IP fora da lista (403), desligado (404), recusa do core repassada,
  limite por IP (errar o segredo conta) e um Bearer qualquer que não atrapalha; a regra do segredo (32 caracteres, `prod`, tempo
  constante) e a lista de IPs/CIDR no `ClientAddressTest`.
- `WaitlistTest` (desde 2026-10-23): consentimento ausente ou `false` e e-mail inválido dão 400
  `validation.failed` sem chegar ao core; inscrição, confirmação e erro do core repassados; descadastro
  com o token no corpo, na query e no POST de formulário do botão de um clique; 5 por hora por IP
  (o sexto dá 429 e outro IP passa); as duas páginas servidas (CSP, sem descadastrar no GET) e o CORS
  da origem permitida.
- `CoreKeyRotationTest`: chave nova no core é aceita sem reiniciar o BFF.
- `CoreRouteGroupsTest`: um grupo falhando abre só o próprio circuito (desvincular com a conexão
  caindo abre `PROVIDER`, `/api/me` continua), grupo cheio responde `service.busy` só para as suas
  rotas (busca cheia, extrato sem `q` passa) e não conta no circuito.
- `ScreenMonthRoutesTest` e `ScreenMonthTest`: `month` fora do formato ou da janela nunca chega
  ao core nas três telas mensais; limites da janela pelo mês de São Paulo.
- `SearchRateLimitTest`: limite próprio da busca, `q` em branco é o extrato normal, `/livez` nunca
  leva 429.
- `HealthEndpointsTest` (servidor de verdade, as duas portas) e `ManagementSecurityConfigTest`.

Ver [[Visão Geral]] e [[App Flutter]].
