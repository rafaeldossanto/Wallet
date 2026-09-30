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
