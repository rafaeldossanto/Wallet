# Wallet

Carteira financeira para celular e PC que agrega contas, cartões e investimentos pelo
Open Finance. Nome provisório.

| Pasta | O que é | Porta |
|---|---|---|
| `Docs/` | Cofre Obsidian com a arquitetura, as decisões e o plano | — |
| `core/` | Core (Spring Boot, Maven): dados, regras e sincronização com a Pluggy | 8081 |
| `bff/` | BFF (Spring Boot, Maven): a única porta pública do app. Ainda não criado | 8080 |
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
Isso basta para desenvolver só o core, mas os tokens morrem quando ele reinicia e o BFF não
consegue validá-los. Para ter chaves fixas:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out wallet-jwt-private.pem
openssl pkey -in wallet-jwt-private.pem -pubout -out wallet-jwt-public.pem
```

Guarde os arquivos fora do repositório e aponte `WALLET_JWT_PRIVATE_KEY` e
`WALLET_JWT_PUBLIC_KEY` para os caminhos. O BFF recebe só a pública.

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

Segredos (senha do banco, Pluggy, chaves de JWT e de criptografia) vêm só de variáveis
de ambiente. O `.gitignore` também bloqueia `application-local.yaml`, `.env` e `.pem`.
