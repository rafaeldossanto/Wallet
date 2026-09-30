# Wallet

Carteira financeira para celular e PC que agrega contas, cartões e investimentos pelo
Open Finance. Nome provisório.

| Pasta | O que é | Porta |
|---|---|---|
| `Docs/` | Cofre Obsidian com a arquitetura, as decisões e o plano | — |
| `bff/` | BFF (Spring Boot, Maven): a única porta pública do app | 8080 |
| `core/` | Core (Spring Boot, Maven): dados, regras e sincronização com a Pluggy | 8081 |
| `app/` | App Flutter (Android, iOS e Web) | — |

Comece por `Docs/Wallet.md`. As tarefas estão em `Docs/Plano/Plano de Implementação.md`.

## Rodar o core

Precisa do JDK 25 no `JAVA_HOME`.

```bash
cd core
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Credenciais (Pluggy, chaves de JWT e de criptografia) ficam em variáveis de ambiente ou
no `application-local.yaml`, que o `.gitignore` bloqueia. Nunca no git.
