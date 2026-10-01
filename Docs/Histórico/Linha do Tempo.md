---
tags: [historico]
atualizado: 2026-10-01
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
- Build decidido: **Maven** (não Gradle). O Rafael gerou o core no Initializr; veio como
  War e foi convertido para Jar.
- Repositório no GitHub (`rafaeldossanto/Wallet`); T01, T03 e T04 feitas.
- O Rafael liberou rodar build e testes e tocar sozinho. T05 (identidade) verde.

## 2026-09-30

- T06 (adaptador da Pluggy, fixtures sintéticas do SDK oficial), T07 (conexões), T08
  (sincronização) e T09 (API de leitura), todas verdes e publicadas: 47 testes unitários
  e 53 de integração.
- Mudanças em relação ao desenho: concorrência da sincronização por índice único parcial;
  leitura do provedor fora de transação; `investment_snapshots` para o histórico de
  patrimônio; somas em SQL. Detalhes em [[Sincronização]], [[Modelo de Dados]] e [[Core]].
- BFF e Pluggy real adiados pelo Rafael. Próximos passos dependem dele: T02 (Meu Pluggy),
  T10–T11 (BFF), T12+ (app).

## 2026-10-01

- O Rafael criou o `bff/` no Initializr e liberou o BFF. T10 (porta pública: JWT,
  CORS, limite de requisições, circuit breaker, trace) e T11 (rotas do app), verdes e
  publicadas: 33 testes no BFF.
- O core passou a publicar a chave pública como JWKS; o BFF busca de lá e não tem
  nenhum segredo. `WALLET_JWT_PUBLIC_KEY` saiu do BFF.
- Dois furos achados pelos testes e corrigidos: Bearer vencido barrando o refresh, e
  `kid` fixo deixando o BFF com a chave velha depois de um reinício do core (o `kid`
  virou o thumbprint da chave). Detalhes em [[BFF]] e [[Autenticação]].
- Próximos passos dependem do Rafael: T02 (Meu Pluggy) e T12+ (app).
