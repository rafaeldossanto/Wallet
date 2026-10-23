---
tags: [arquitetura, seguranca, auth]
atualizado: 2026-10-22
---

# Autenticação

Conta própria do Wallet (e-mail e senha, e um segundo fator opcional). A mesma conta entra no
celular, no Windows e no navegador. O [[Core]] cuida de senha, bloqueio, e-mails da conta e
tokens; o [[BFF]] decide como entregar os tokens a cada plataforma e serve a página que os links
dos e-mails abrem.

## Quem faz o quê

| | Core | BFF |
|---|---|---|
| Senha, bloqueio, tokens, 2FA | Guarda o hash, conta tentativas, emite e rotaciona, confere o código | Repassa e diz ao core de onde veio o pedido (`X-Wallet-Client-Address`) |
| E-mails da conta | Monta e manda (módulo `mail`, SMTP) | Serve a página `/email/confirm` |
| Assinatura do JWT | Tem a **chave privada** RS256 e publica a pública no JWKS | Busca a **chave pública** no JWKS do core |
| Cookie, CORS, CSRF | Não sabe que existem | Cuida de tudo |

## Cadastro com confirmação por e-mail (achado A16, desde 2026-10-22)

O cadastro responde **sempre igual**: `202` sem corpo ("enviamos um link para seu e-mail"). O
que acontece por trás depende do e-mail, e só o dono descobre, na caixa de entrada:

| O e-mail... | O core faz |
|---|---|
| é novo | Cria a conta **não confirmada** e manda o link de confirmação |
| já tem conta confirmada | Manda um aviso ao dono ("alguém tentou criar uma conta com este e-mail"), no máximo 1 por hora. A senha digitada não vai para lugar nenhum |
| tem conta **não** confirmada | O cadastro mais recente vence, com a senha dele, e vai um link novo (os anteriores param de valer) |

Por que o mais recente vence: manter o primeiro deixaria alguém se cadastrar antes com o e-mail
de outra pessoa e esperar o dono confirmar uma conta com a senha do estranho dentro.

- **Ninguém entra antes de confirmar.** No login, conta não confirmada responde
  `401 auth.invalid_credentials`, **igual a senha errada**, e conta como falha no bloqueio:
  outra resposta deixaria um estranho que cadastrou o e-mail da vítima descobrir se ela já tinha
  conta. Com a senha certa, o core reenvia o link (dentro do limite). O app diz, na mensagem de
  senha errada, "se você acabou de criar a conta, abra antes o link que enviamos".
- **O link:** `<WALLET_PUBLIC_URL>/email/confirm#token=...`, uma **página do BFF** (e não uma rota
  do app web): funciona em qualquer lugar onde o e-mail for lido, inclusive num celular que só
  tem o app Android, e não depende de o app web estar publicado. O token vai depois do `#`, que o
  navegador nunca envia: não aparece em log de acesso do BFF nem de proxy. A página só confirma
  quando a pessoa toca no botão (POST `/api/auth/email/confirm`), então leitores de e-mail que
  abrem todo link sozinhos não confirmam nada. Página com CSP restrita e `Referrer-Policy: no-referrer`.
- **O token:** 256 bits aleatórios, guardado como SHA-256 (como o refresh), vale **24 h**, uma vez
  só, e só o mais novo de cada finalidade. No máximo **3 links por hora** por conta (o do cadastro
  conta). `POST /api/auth/email/resend` pede outro, com a mesma resposta `202` para qualquer e-mail.
- Conta nunca confirmada é apagada depois de **7 dias** (limpeza noturna, `AccountHousekeeping`).
  A migration V10 marcou as contas que já existiam como confirmadas.

> [!warning] Risco que sobra
> Se alguém cadastrar o e-mail da vítima depois dela e a vítima abrir justamente esse segundo link,
> a conta nasce com a senha do estranho. A vítima não consegue entrar (percebe na hora) e a conta
> está vazia, mas ela fica sem conta até existir "esqueci a senha", que ainda não existe.

## Tokens

| Token | Vida | Onde fica no celular | Onde fica no navegador |
|---|---|---|---|
| Access (JWT RS256, `aud` = `wallet-api`) | 15 min | Memória | Memória |
| Refresh (opaco) | 30 dias | `flutter_secure_storage` (Keychain/Keystore) | Cookie `HttpOnly`, `Secure`, `SameSite=Strict`, só em `/api/auth` |

- O app diz quem é pelo header `X-Wallet-Client: mobile`, `desktop` ou `web`, e o BFF
  escolhe a entrega. No `web`, o refresh vai só no cookie e **nunca** no corpo; no `mobile`
  e no `desktop`, vai no corpo e o app guarda no cofre do sistema.
- O BFF valida o JWT com a chave pública e repassa o mesmo Bearer ao core, que valida
  de novo. A chave vem de `/internal/.well-known/jwks.json`; o `kid` é o thumbprint da
  chave, então uma chave nova no core é buscada pelo BFF sem reiniciar nada.
- **Audience (achado A14, desde 2026-10-22):** todo access token leva `aud: "wallet-api"`, e o BFF
  e o core exigem esse valor. Ele diz "sessão de um usuário na API do Wallet" (as duas pontas: o
  BFF repassa o mesmo token). Qualquer outra coisa que a mesma chave venha a assinar (um link de
  vida curta, um token de serviço) leva outra audience e não passa por sessão. Token sem `aud`
  (emitido antes) vira **401**; o app renova com o refresh, que é opaco e não muda, e ninguém é
  deslogado.
- **Usuário que não existe mais (achado L1-c, desde 2026-10-22):** JWT não se revoga, então depois
  de excluir a conta o access token ainda passaria por até 15 min. O core confere em toda
  requisição que o `sub` do token ainda existe (`ActiveUserInterceptor`, uma busca por chave
  primária) e responde `401 auth.unauthenticated` se não, em qualquer rota, inclusive uma segunda
  exclusão (o app conta esse fim como "Sua conta foi excluída"). É um interceptor do MVC e não um
  passo da cadeia de segurança para que pool cheio vire o mesmo `503 service.busy` do resto. Antes, uma escrita nessa janela (gasto
  fixo, salário) batia na FK do usuário apagado e virava 500.
- As rotas `/api/auth/**` **ignoram o Bearer**: o app pede refresh justamente com o
  access token vencido, e esse header não pode atrapalhar.
- No `web`, um refresh recusado pelo core (vencido, revogado ou reusado) também
  **apaga o cookie**, e o logout sempre apaga.
- **Rotação com detecção de reuso (no core):** cada refresh gera um novo e aposenta o
  anterior. Reusar um refresh aposentado revoga a família inteira. Tolerância de 1 min
  para o aparelho que perdeu a resposta.
- No app, a renovação é **uma por vez** (single-flight).

## Login, passo a passo (no core)

1. E-mail normalizado (minúsculo, sem espaços).
2. **Bloqueado?** Responde `423 auth.locked` com `lockedUntil` **antes de qualquer hash**.
3. **Um BCrypt**, inclusive para e-mail sem conta (contra um hash fictício, mesmo tempo).
4. Senha errada, e-mail sem conta ou conta não confirmada: `401 auth.invalid_credentials` e uma
   falha a mais no bloqueio. A falha que trava já responde `423`.
5. Segundo fator ligado: sem `code`, `401 auth.two_factor_required` (só quem acertou a senha chega
   aqui); com código errado, `401 auth.invalid_two_factor_code` e uma falha a mais no bloqueio.
6. Deu certo: zera o contador daquele endereço e emite os tokens.

## Bloqueio progressivo (achado A5, desde 2026-10-22)

O antigo "5 erros, 15 min" virava arma: bastava o e-mail da vítima para mantê-la travada. Agora
as falhas contam em **dois escopos**, guardados em `login_throttles`:

| Escopo | Chave | Trava depois de | Quem fica de fora |
|---|---|---|---|
| **Origem** | e-mail + endereço do cliente | 5 falhas | Só aquele endereço, para aquele e-mail. O dono, de qualquer outro lugar, continua entrando |
| **Conta** | e-mail, de qualquer endereço | 20 falhas | Todos. Só um ataque espalhado por vários endereços chega aqui |

- **Progressivo:** a primeira trava dura 15 min; enquanto as falhas continuam, cada trava dura o
  dobro da anterior (30 min, 1 h, 2 h...), até 24 h. Um dia sem falhas zera tudo. Quem errou a
  senha algumas vezes volta em minutos; quem tenta adivinhar fica exponencialmente mais lento.
- **Aviso ao dono:** quando trava, o dono recebe um e-mail dizendo até quando e se foi só uma rede
  ou de qualquer lugar (no máximo 1 aviso por hora; só para conta confirmada).
- **Não revela contas:** a chave é o e-mail, não a conta, e vale igual para e-mail sem conta. Um
  `423` não confirma mais que a conta existe. As chaves são SHA-256: a tabela não guarda e-mail nem
  IP em claro, e as linhas somem um dia depois da última falha.
- **O endereço** vem do BFF no header `X-Wallet-Client-Address` (o mesmo que o rate limit usa,
  com `WALLET_TRUSTED_PROXIES`). O core só aceita chamadas do BFF (chave de gateway), então pode
  confiar nesse header.
- A senha pedida de novo (excluir conta, trocar e-mail, ligar/desligar o 2FA) conta para o mesmo
  bloqueio: senha errada é `403 auth.wrong_password` (não 401, que o app trataria como sessão vencida).

Risco que sobra: um ataque de verdade espalhado por 4 ou mais endereços ainda consegue travar a
conta para todos (é a defesa contra adivinhação distribuída); o dono é avisado por e-mail.

## Custo do BCrypt (achado A4, desde 2026-10-22)

Todo login, cadastro e confirmação de senha custa um BCrypt. Além dos 20/min por IP do BFF:

- **Limite global:** no máximo N verificações de senha ao mesmo tempo (`PasswordHasher`, padrão
  metade dos processadores, `WALLET_PASSWORD_CHECK_CONCURRENCY`). Quem espera mais de 2 s recebe
  `429 auth.busy`; sincronização e leituras continuam com CPU.
- **Por e-mail, antes do hash:** o escopo "conta" do bloqueio é checado antes do BCrypt; um e-mail
  travado não custa hash nenhum, com ou sem conta.
- O cadastro faz o hash **antes** de abrir transação: nenhuma conexão do banco fica presa
  esperando a vez do BCrypt.

## Segundo fator (TOTP, desde 2026-10-22)

TOTP (RFC 6238: HMAC-SHA1, 6 dígitos, 30 s), opcional, ligado em Ajustes. Escolhido em vez de
passkey porque funciona igual no Flutter web, no Android e no Windows, sem WebAuthn.

1. **Ligar:** a senha de novo (um access token roubado não pode amarrar a conta ao celular do
   ladrão) → QR code (`otpauth://totp/Wallet:<e-mail>?secret=...&issuer=Wallet`) e a chave para
   digitar → o primeiro código do app, que prova que o app tem o segredo → **10 códigos de
   recuperação**, mostrados uma única vez.
2. **Entrar:** o login pede o código em qualquer plataforma. Aceita o passo atual e um de cada lado
   (relógio do celular um pouco adiantado), e cada passo vale **uma vez** (`totp_last_step`). Um
   código de recuperação entra no lugar do código do app e é gasto.
3. **Desligar:** senha + código (ou código de recuperação). Código errado é `403` (com sessão aberta,
   401 faria o app achar que a sessão venceu) e conta para o bloqueio.

- O segredo fica cifrado com a `WALLET_DATA_KEY` (`DataCipher`, AES-256-GCM), como as descrições das
  transações. Os códigos de recuperação têm 80 bits (16 caracteres do base32 de Crockford, sem I, L,
  O, U) e ficam só como SHA-256.
- Se a `WALLET_DATA_KEY` mudar, o segredo não abre mais: só os códigos de recuperação entram.

## Perfil (LGPD L11, desde 2026-10-22)

- `PUT /api/me` troca o nome.
- `POST /api/me/email` pede **a senha** e manda um link para o **novo** endereço. A troca só vale
  quando esse link é aberto, e aí o endereço antigo recebe um aviso com o novo. Enquanto isso,
  `GET /api/me` traz `pendingEmail`. Se o novo endereço já tem conta, a resposta é a mesma (`202`),
  o dono dele recebe o aviso de tentativa de cadastro e nenhum link sai.

## E-mails da conta

Módulo `mail` do core (porta `EmailSender`), SMTP genérico: qualquer provedor (Amazon SES no EC2 é
o candidato natural). Os e-mails saem **depois do commit** e numa virtual thread: o tempo do SMTP
(ou se um e-mail saiu ou não) nunca aparece no tempo da resposta. Textos em pt-BR, simples, sem nada
que um estranho tenha digitado (nem o nome).

| E-mail | Quando |
|---|---|
| Confirme seu e-mail | Cadastro e reenvio (link, 24 h) |
| Tentativa de cadastro | Cadastro (ou troca de e-mail) com um e-mail que já tem conta (1/h) |
| Conta bloqueada por um tempo | Quando o bloqueio trava (1/h) |
| Confirme seu novo e-mail | Troca de e-mail (link, 24 h) |
| O e-mail da sua conta foi trocado | Ao endereço antigo, quando a troca vale |

Sem `WALLET_SMTP_HOST`, fora de produção o e-mail vai para o **log** com aviso de que é só
desenvolvimento (a única exceção à regra de não logar token); com `SPRING_PROFILES_ACTIVE=prod` o
core não sobe sem SMTP, sem `WALLET_MAIL_FROM` ou sem `WALLET_PUBLIC_URL` (https).

## Códigos de erro da identidade

| Código | Status | Quando | O app diz |
|---|---|---|---|
| `auth.invalid_credentials` | 401 | E-mail sem conta, senha errada ou conta não confirmada (os três iguais) | "E-mail ou senha incorretos. Se você acabou de criar a conta, abra antes o link..." |
| `auth.two_factor_required` | 401 | Senha certa, 2FA ligado e sem `code` | Mostra o campo Código |
| `auth.invalid_two_factor_code` | 401 no login, 403 ao desligar | Código do app ou de recuperação errado (conta para o bloqueio) | "Código incorreto..." |
| `auth.locked` | 423 | E-mail travado (de um endereço ou de todos), com `lockedUntil` | "Tente de novo às 17:15" |
| `auth.busy` | 429 | Verificações de senha demais ao mesmo tempo | "Muita gente entrando agora..." |
| `auth.wrong_password` | 403 | Senha pedida de novo (excluir, trocar e-mail, 2FA) errada | "Senha incorreta..." |
| `auth.link_invalid` | 422 | Link desconhecido, vencido, já usado ou trocado por um mais novo | A página do link explica |
| `auth.email_taken` | 409 | Ao abrir o link de troca, o novo endereço já ganhou conta | A página do link explica |
| `auth.email_unchanged` | 422 | Troca para o e-mail que a conta já tem | "Esse já é o e-mail da conta." |
| `two_factor.invalid_code` | 422 | Primeiro código, ao ligar, não confere | "Confira o horário do celular..." |
| `two_factor.not_set_up` / `already_enabled` / `not_enabled` | 409 | Ordem errada dos passos do 2FA | Frase própria |
| `auth.unauthenticated` | 401 | Sem token, token vencido, sem a audience certa, ou de usuário que não existe mais | Renova a sessão ou volta ao login |

Sumiu: `auth.email_taken` no **cadastro** (era o 409 que revelava contas); o cadastro agora é
sempre 202.

## Proteções por plataforma

- **Celular:** biometria ao voltar ao app depois de 5 min em segundo plano (`local_auth`).
- **Navegador:** sessão expira após 30 min sem uso; CORS restrito à origem do app;
  `login`, `refresh` e `logout` exigem o header `X-Wallet-Client` (proteção CSRF). Segundo fator
  disponível desde 2026-10-22.
- **Limites no BFF:** 20 requisições por minuto por IP em `/api/auth/**` (login, cadastro,
  confirmação, reenvio), 20 por minuto **por usuário** nas rotas que pedem senha ou código de novo
  (`/api/me/email`, `/api/me/two-factor/**`, `DELETE /api/me`), 300 por usuário no resto
  (`429 rate_limit.exceeded` com `Retry-After`).

Ver [[App Flutter]] e [[Modelo de Dados]].
