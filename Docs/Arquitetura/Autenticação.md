---
tags: [arquitetura, seguranca, auth]
atualizado: 2026-10-01
---

# Autenticação

Conta própria do Wallet (e-mail e senha). A mesma conta entra no celular e no
navegador do PC. O [[Core]] emite e rotaciona os tokens; o [[BFF]] decide como
entregá-los a cada plataforma.

## Quem faz o quê

| | Core | BFF |
|---|---|---|
| Senha, bloqueio, tokens | Guarda o hash, conta tentativas, emite e rotaciona | Só repassa |
| Assinatura do JWT | Tem a **chave privada** RS256 e publica a pública no JWKS | Busca a **chave pública** no JWKS do core |
| Cookie, CORS, CSRF | Não sabe que existem | Cuida de tudo |

## Tokens

| Token | Vida | Onde fica no celular | Onde fica no navegador |
|---|---|---|---|
| Access (JWT RS256) | 15 min | Memória | Memória |
| Refresh (opaco) | 30 dias | `flutter_secure_storage` (Keychain/Keystore) | Cookie `HttpOnly`, `Secure`, `SameSite=Strict`, só em `/api/auth` |

- O app diz quem é pelo header `X-Wallet-Client: mobile` ou `web`, e o BFF escolhe a
  entrega. No `web`, o refresh vai só no cookie e **nunca** no corpo; no `mobile`, vai
  no corpo.
- O BFF valida o JWT com a chave pública e repassa o mesmo Bearer ao core, que valida
  de novo. A chave vem de `/internal/.well-known/jwks.json`; o `kid` é o thumbprint da
  chave, então uma chave nova no core é buscada pelo BFF sem reiniciar nada.
- As rotas `/api/auth/**` **ignoram o Bearer**: o app pede refresh justamente com o
  access token vencido, e esse header não pode atrapalhar.
- No `web`, um refresh recusado pelo core (vencido, revogado ou reusado) também
  **apaga o cookie**, e o logout sempre apaga. Sem cookie, o refresh nem chega ao core
  (`401 auth.invalid_refresh_token`).
- **Rotação com detecção de reuso (no core):** cada refresh gera um novo e aposenta o
  anterior. Reusar um refresh aposentado revoga a família inteira. Tolerância de 1 min
  para o aparelho que perdeu a resposta, como no Storage.
- No app, a renovação é **uma por vez** (single-flight): duas telas pedindo refresh
  ao mesmo tempo disparariam o detector de reuso.

## Senha e tentativas (no core)

- `BCryptPasswordEncoder` via `DelegatingPasswordEncoder`, para permitir trocar o
  algoritmo depois.
- 5 erros seguidos travam o login por 15 min (`auth.locked`). Mesma resposta para
  e-mail inexistente e senha errada (`auth.invalid_credentials`).

## Proteções por plataforma

- **Celular:** biometria ao voltar ao app depois de 5 min em segundo plano
  (`local_auth`).
- **Navegador:** sessão expira após 30 min sem uso; CORS restrito à origem do app;
  `login`, `refresh` e `logout` exigem o header `X-Wallet-Client` (sem ele,
  `400 auth.client_required`), que um site de terceiros não consegue mandar junto com o
  cookie (proteção CSRF).
- **Limites no BFF:** 20 requisições por minuto por IP em `/api/auth`, 300 por usuário
  no resto (`429 rate_limit.exceeded` com `Retry-After`).

> [!warning] Antes de abrir para outras pessoas
> O login no navegador precisa de **segundo fator** (passkey ou TOTP), porque o PC
> não tem a biometria do celular. Fica fora do v1, que só o Rafael usa.

Ver [[App Flutter]].
