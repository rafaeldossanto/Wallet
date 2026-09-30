---
tags: [arquitetura, stack]
atualizado: 2026-09-29
---

# Stack

> [!success] Decidido em 2026-09-29
> Java 25 + Spring Boot 4.1 no backend e Flutter no app. Ver [[Java e Flutter]].

| Camada | Escolha | Por quê |
|---|---|---|
| App | **Flutter** (Dart) para Android, iOS **e Web** | Um código só para celular e navegador do PC. Ambiente já instalado (Flutter, Android SDK, AVD) e experiência do app do Trilha. Atualizar o 3.44.2 para a estável atual |
| Backend | **Java 25 + Spring Boot 4.1**, em dois serviços: [[BFF]] (porta pública) e [[Core]] (dados e regras) | Stack que o Rafael domina. Ruby on Rails foi considerado; a comparação ficou abaixo. BFF decidido em 2026-09-29 |
| Arquitetura | **Monolito modular** | Uma pessoa no projeto. Módulos por domínio (conexões, contas, cartões, investimentos, usuários) com o provedor isolado |
| Banco | **PostgreSQL** | Dado financeiro é relacional e precisa de `NUMERIC`, transações e constraints |

## No PC: Flutter Web

Requisito do Rafael (2026-09-28): abrir o Wallet no computador, entrar com a mesma
conta e ver as finanças.

- **Navegador, não instalador.** Abre em qualquer PC, atualiza sozinho, sem
  instalador para Windows. Se um dia fizer falta, o Flutter também gera app de
  Windows e macOS do mesmo código.
- **A conta é do backend, não do aparelho.** Celular e navegador falam com o mesmo
  backend; o login é o mesmo. Nada muda na arquitetura, só na forma de guardar a
  sessão (ver segurança).
- **Custo do Flutter Web:** o primeiro carregamento é mais pesado que o de um site
  comum. Num painel usado depois do login isso pesa pouco; sem SEO a perder.

> [!warning] Conectar banco pelo PC
> O `flutter_pluggy_connect` suporta Android, iOS e macOS, **não Web**. No navegador,
> a tela "Conectar banco" teria de usar o widget JavaScript da Pluggy via interop.
> Proposta para o v1: **conectar bancos pelo celular** (onde o consentimento já cai
> no app do banco) e o PC só **ver e gerenciar**. Com o Meu Pluggy isso nem se
> aplica ainda: as conexões são feitas dentro dele ([[Meu Pluggy no Desenvolvimento]]).

## Backend: Java ou Ruby (comparação que levou à decisão)

| | Java + Spring Boot | Ruby on Rails 8 |
|---|---|---|
| Domínio do Rafael | Total | Vai aprender |
| Chamar a Pluggy | `RestClient` | Faraday / `Net::HTTP` (sem SDK oficial; é REST) |
| Webhooks | Controller | Controller no modo API |
| Sincronização agendada | `@Scheduled` / Quartz | Solid Queue (jobs recorrentes, nativo) |
| Colunas criptografadas | Converter JPA ou lib | Active Record Encryption, nativo |
| Dinheiro | `BigDecimal` | `BigDecimal` nativo |
| Tipagem | Estática | Dinâmica; compensar com testes (RSpec/Minitest) e RBS/Sorbet opcional |
| Desenvolver no Windows | Sem atrito | Usar WSL2 ou Docker (gems com extensão nativa dão atrito) |

> [!note] Por que o Rails serve aqui e não serviu no TLT
> No TLT o Rails foi descartado porque aquilo é um app desktop, e o Rails não roda no
> desktop. Aqui o backend é uma API web, que é o terreno do Rails.

## Alternativas descartadas

- **React Native:** segunda opção viável (Pluggy tem SDK; o Rafael aprende JS no
  Storage). Perde porque o ambiente e a experiência mobile estão no Flutter.
- **Front web separado (React) + Flutter no celular:** duas interfaces para manter.
  Só compensa se a web precisar de uma experiência muito diferente da do celular.
- **Nativo (Kotlin + Swift):** dobra o trabalho sem necessidade de recurso profundo
  de plataforma.
- **Kotlin Multiplatform:** compartilha lógica, mas as telas são feitas duas vezes.
- **Mais microsserviços além de BFF + core** (como os 5 do Trilha): separar
  sincronização e dados exigiria mensageria e multiplicaria deploy e memória sem
  ganho nessa escala. Ver [[Core]].

> [!warning] iOS a partir do Windows
> Compilar e publicar para iPhone exige macOS com Xcode. Desenvolver e testar no
> Android e no navegador pelo PC; para iOS, build na nuvem (Codemagic ou runners
> macOS do GitHub Actions) ou um Mac.

## Segurança desde o primeiro commit

- O app só fala com o BFF. Nenhum token de banco ou chave de API no aparelho.
- Dinheiro em `BigDecimal` / `NUMERIC`, nunca `double`.
- Colunas sensíveis criptografadas; log sem valor nem descrição de transação.
- **Celular:** biometria ao abrir (`local_auth`), token no armazenamento seguro do
  sistema, sessão curta com refresh token.
- **Navegador:** sessão em cookie `HttpOnly` + `Secure` + `SameSite` (nunca token em
  `localStorage`), proteção CSRF, expiração por inatividade. Como o navegador não tem
  a biometria do celular, o login web pede **segundo fator** (passkey ou TOTP) antes
  de abrir para outras pessoas.
- Webhooks da Pluggy não são assinados: header secreto + lista de IPs.
- Provedor atrás de interface (`FinancialDataProvider`); ver [[Risco Regulatório 2026]].

Detalhes em [[Autenticação]] e [[Sincronização]]. Fluxo em [[Fluxo de Conexão]].
