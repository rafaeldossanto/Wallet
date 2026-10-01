# Wallet

Carteira financeira para celular e PC que agrega contas, cartões e investimentos pelo
Open Finance. Nome provisório.

| Pasta | O que é | Porta |
|---|---|---|
| `Docs/` | Cofre Obsidian com a arquitetura, as decisões e o plano | — |
| `core/` | Core (Spring Boot, Maven): dados, regras e sincronização com a Pluggy | 8081 |
| `bff/` | BFF (Spring Boot, Maven): a única porta pública do app | 8080 |
| `app/` | App Flutter (Android, iOS e Web). Ainda não criado | — |

Comece por `Docs/Wallet.md`. As tarefas estão em `Docs/Plano/Plano de Implementação.md`.

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
manda `X-Wallet-Client: mobile` e o navegador `X-Wallet-Client: web` nas rotas de
`/api/auth`.

### Testar

```bash
./mvnw verify   # core simulado no WireMock, sem Docker
```

## Segredos

Segredos (senha do banco, Pluggy, chaves de JWT e de criptografia) vêm só de variáveis
de ambiente. O `.gitignore` também bloqueia `application-local.yaml`, `.env` e `.pem`.
