# Wallet

[![Core](https://github.com/rafaeldossanto/Wallet/actions/workflows/core.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/core.yml)
[![BFF](https://github.com/rafaeldossanto/Wallet/actions/workflows/bff.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/bff.yml)
[![App](https://github.com/rafaeldossanto/Wallet/actions/workflows/app.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/app.yml)

Carteira financeira para celular e PC que agrega contas, cartões e investimentos pelo
Open Finance. Nome provisório.

| Pasta | O que é | Porta |
|---|---|---|
| `Docs/` | Cofre Obsidian com a arquitetura, as decisões e o plano | — |
| `core/` | Core (Spring Boot, Maven): dados, regras e sincronização com a Pluggy | 8081 |
| `bff/` | BFF (Spring Boot, Maven): a única porta pública do app | 8080 |
| `app/` | App Flutter (Android, iOS e Web) | 5000 no navegador |

Comece por `Docs/Wallet.md`. As tarefas estão em `Docs/Plano/Plano de Implementação.md`.

## Ver tudo funcionando sem conta na Pluggy

Com o Docker ligado, em três terminais:

```bash
cd core && ./mvnw spring-boot:test-run
```

```bash
cd bff && ./mvnw spring-boot:run
```

```bash
cd app && flutter run -d chrome --web-port 5000
```

Sem `PLUGGY_CLIENT_ID` definido, o `test-run` do core sobe um Postgres descartável e uma
**Pluggy de demonstração** com dados sintéticos gerados a partir da data de hoje. No app,
crie uma conta, vá em Conexões → Vincular conexão e use os Item IDs `demo-nubank`, `demo-itau`
e `demo-xp` (bancos com cara de reais: conta, poupança, cartões com faturas e parcelas,
investimentos) ou os básicos `demo-banco` e `demo-corretora`. Com
`PLUGGY_CLIENT_ID` e `PLUGGY_CLIENT_SECRET` definidos, o mesmo comando fala com a Pluggy de
verdade. Tudo some quando o core para.

## Core

Precisa do **JDK 25** no `JAVA_HOME` e do Docker (testes de integração).

### Banco local (uma vez)

```bash
psql -U postgres -h localhost -v wallet_password=SUA_SENHA -f core/db/setup-local.sql
```

Depois crie a variável de ambiente `WALLET_DB_PASSWORD` com a mesma senha.

### Chaves do JWT

Sem chave configurada, o core gera uma chave temporária a cada inicialização e avisa no log.
O BFF busca a chave pública no próprio core (JWKS) e pega a nova sozinho, então isso basta
para desenvolver. O único efeito é que toda sessão acaba quando o core reinicia. Para ter
chaves fixas:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out wallet-jwt-private.pem
openssl pkey -in wallet-jwt-private.pem -pubout -out wallet-jwt-public.pem
```

Guarde os arquivos fora do repositório e aponte `WALLET_JWT_PRIVATE_KEY` e
`WALLET_JWT_PUBLIC_KEY` para os caminhos. O BFF não precisa de nenhuma das duas.

### Chave da criptografia de dados

A descrição das transações é gravada criptografada (AES-256-GCM). Sem chave, o core usa uma
temporária e avisa no log: o que for gravado fica ilegível depois de reiniciar. Gere uma
vez e guarde em `WALLET_DATA_KEY`:

```bash
openssl rand -base64 32
```

### Rodar

```bash
cd core
./mvnw spring-boot:run
```

Sem banco local, dá para subir contra um Postgres descartável no Docker:
`./mvnw spring-boot:test-run`.

### Testar

```bash
./mvnw test     # unitários (*Test), sem Docker
./mvnw verify   # unitários + integração (*IT) com Testcontainers
```

## BFF

Precisa do **JDK 25** no `JAVA_HOME`. Não tem banco nem segredo: só precisa saber onde está
o core.

| Variável | Padrão | Para quê |
|---|---|---|
| `WALLET_CORE_URL` | `http://localhost:8081` | Endereço do core |
| `WALLET_WEB_ALLOWED_ORIGINS` | `http://localhost:5000` | Origem do Flutter Web (CORS com cookie) |

### Rodar

Com o core já no ar:

```bash
cd bff
./mvnw spring-boot:run
```

O app web roda na porta 5000 (`flutter run -d chrome --web-port 5000`). O celular
manda `X-Wallet-Client: mobile`, o app de Windows `X-Wallet-Client: desktop` e o navegador
`X-Wallet-Client: web` nas rotas de `/api/auth`.

### Testar

```bash
./mvnw verify   # core simulado no WireMock, sem Docker
```

## App

Flutter 3.47 ou mais novo (o Material vem do pacote `material_ui`). Um projeto para
Android, iOS, navegador e Windows (app instalável); só fala com o BFF.

```bash
cd app
flutter test       # unitários e de tela, com o BFF simulado
flutter analyze
flutter run -d chrome --web-port 5000       # PC, no navegador
flutter run -d emulator-5554                # emulador Android (fala com o BFF em 10.0.2.2:8080)
flutter run -d windows                      # app de Windows (exige o Visual Studio com C++)
```

O endereço do BFF muda com `--dart-define=WALLET_BFF_URL=https://...`. A porta 5000 do
navegador é a origem que o BFF aceita no CORS (`WALLET_WEB_ALLOWED_ORIGINS`).

### App de Windows

O instalador `Wallet-Setup-<versão>.exe` é gerado pelo workflow **Desktop** do GitHub Actions a
cada mudança no `app/`: abra a última execução em Actions → Desktop e baixe o arquivo em
*Artifacts*. Uma tag `v<versão>` (a mesma do `pubspec.yaml`) também publica o instalador em
*Releases*.

- Instala só para o seu usuário, sem pedir administrador; atalho no Menu Iniciar.
- O instalador não é assinado: na primeira vez o Windows avisa "O Windows protegeu o
  computador" (Mais informações → Executar assim mesmo).
- Até o deploy, o app instalado fala com o BFF deste PC (`localhost:8080`): core e BFF precisam
  estar rodando. Com a variável de repositório `WALLET_BFF_URL` definida no GitHub, o build
  aponta para ela.
- Para rodar com `flutter run -d windows` é preciso o Visual Studio Community (2022 ou 2026) com a carga
  "Desenvolvimento para desktop com C++" (só o compilador é usado).

## CI

O GitHub Actions roda a suíte de cada parte a cada push na `master` e em pull request,
só quando a pasta dela muda (`.github/workflows/`):

| Workflow | O que roda |
|---|---|
| Core | `./mvnw -B verify` com JDK 25: unitários e integração (Postgres no Docker do runner) |
| BFF | `./mvnw -B verify` com JDK 25 (core simulado no WireMock) |
| App | Flutter 3.47.6: traduções geradas batendo com o `.arb`, `flutter analyze` e `flutter test` |
| Desktop | Em Windows: compila o app, monta o instalador (Inno Setup) e anexa o `.exe` à execução; numa tag `v*`, publica nos Releases |

Nenhum workflow precisa de segredo. Quando um teste falha, os relatórios ficam 7 dias como
artefato da execução.

## Segredos

Segredos (senha do banco, Pluggy, chaves de JWT e de criptografia) vêm só de variáveis
de ambiente. O `.gitignore` também bloqueia `application-local.yaml`, `.env` e `.pem`.
