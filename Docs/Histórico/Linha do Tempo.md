---
tags: [historico]
atualizado: 2026-10-03
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

## 2026-10-02

- O Rafael liberou T12 a T18. O app Flutter ficou pronto para Android, iOS e navegador: 40
  testes, conferido no Chrome e no emulador Android contra o core e o BFF de verdade. Ver
  [[App Flutter]].
- Flutter atualizado de 3.44.2 para 3.47.6 (vale também para o app do Trilha). O Material
  passou a vir do pacote `material_ui`.
- O core ganhou uma **Pluggy de demonstração** no `spring-boot:test-run`: dá para usar o
  app com dados sintéticos sem conta na Pluggy. Ver [[Core]].
- O teste de ponta a ponta achou furos que os testes isolados não pegavam: o BFF
  guardava em cache a home de antes da primeira sincronização; o extrato não recarregava as
  contas; o gráfico de patrimônio desenhava seis meses de zeros. Todos corrigidos e com
  teste. Ver [[BFF]] e [[Plano de Implementação]].
- Próximo passo depende do Rafael: T02 (Meu Pluggy com dados reais). Depois, o backlog.

## 2026-10-03

- Tema preto (pedido de manhã) e, à tarde, redesenho em estilo painel a partir de duas
  referências que o Rafael mandou: **tema claro e escuro**, trilho de botões redondos, blocos
  de indicadores e o **calendário de gastos** na visão geral (dia mais claro quanto mais se
  gastou; sem dia selecionado mostra o mês inteiro, tocar num dia filtra, tocar de novo volta).
  Ver [[App Flutter]].
- Core e BFF ganharam o gasto por dia e a lista de gastos de um período com o banco de cada
  item; o BFF ganhou o histórico dos investimentos por período (1M a TUDO). Ver [[BFF]] e
  [[Core]].
- A Pluggy de demonstração ganhou Nubank, Itaú e XP simulados, para ver o app com cara de uso
  real antes da T02.
- O Rafael pediu e iniciou em sessões separadas (worktrees): o gráfico dos investimentos com
  períodos, o ícone novo do app (a carteira da tela de login, branca no preto e inclinada) e
  o tratamento de desvincular durante uma sincronização.
- Desvincular uma conexão no meio da sincronização deixou de gerar `ERROR` no log: a
  sincronização percebe que a conexão sumiu ao marcar `SYNCING` e antes de gravar, para
  com `INFO` e não grava nada. Dois testes de integração reproduzem as duas corridas. Ver
  [[Sincronização]].
- As três sessões paralelas terminaram e entraram na master. Validação no fim do dia: suítes
  verdes (core 55 unitários + 58 de integração, BFF 41, app 55), ícones conferidos (RGB sem
  transparência) e a tela de investimentos testada no navegador contra a demo, com 400 dias
  de histórico inseridos no banco descartável: a variação de cada período bateu com as somas
  no banco. A dica de IDs de demonstração do app passou a citar Nubank, Itaú e XP.
- Pedidos do Rafael no fim do dia: na tela de investimentos, os tipos à esquerda e os gráficos à
  direita (evolução em cima, distribuição embaixo); roscas finas com pontas arredondadas e
  clicáveis (a fatia mostra a porcentagem e o valor no centro), também nos gastos por categoria;
  e os **logos reais dos bancos** no lugar das iniciais (o SVG que a Pluggy serve, com as
  classes CSS passadas para os elementos, porque o `flutter_svg` não lê `<style>`). Ver
  [[App Flutter]].
