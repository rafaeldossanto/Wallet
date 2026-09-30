---
tags: [historico]
atualizado: 2026-09-29
---

# Linha do Tempo

## 2026-09-28

- Projeto nasce com o nome provisório **Wallet**: carteira iOS e Android no estilo
  do [[Pierre]], agregando dados via Open Finance.
- Pesquisa da regulação: [[Open Finance]], [[Caminhos de Acesso]],
  [[Consentimento]], [[Risco Regulatório 2026]], [[Outras Leis]].
- Proposta de [[Stack]] e levantamento de [[Custos]].
- Relatório publicado: https://claude.ai/artifact/DLhB6DUUTeDm7QRxm3HgAR
- Preços dos provedores e revendedores levantados em [[Custos]].
- **Decidido:** desenvolvimento e testes com o Meu Pluggy
  ([[Meu Pluggy no Desenvolvimento]]).
- **Requisito novo:** o Wallet também roda no PC. O Rafael quer abrir no computador,
  entrar com a conta e ver as finanças. Ver [[Stack]].

## 2026-09-29

- **Decidido:** Java 25 + Spring Boot 4.1 e Flutter ([[Java e Flutter]]). Ruby ficou
  de fora.
- Arquitetura desenhada: [[Visão Geral]], [[Core]], [[App Flutter]],
  [[Modelo de Dados]], [[Sincronização]], [[Autenticação]]. [[Fluxo de Conexão]]
  ganhou o fluxo do Meu Pluggy.
- Tarefas T01 a T16 em [[Plano de Implementação]]. Nenhuma linha de código ainda.
- O Rafael perguntou se um serviço basta: basta, e dividir em microsserviços não
  compensa nessa escala (registrado em [[Core]]).
- **Decidido:** um [[BFF]] entre o app e o serviço principal, que passou a se chamar
  [[Core]] e ficou fora da internet. O plano foi para T01 a T18 (T10 e T11 são o BFF).
- Pendências restantes, nenhuma bloqueando o desenvolvimento: [[Decisões Pendentes]].
