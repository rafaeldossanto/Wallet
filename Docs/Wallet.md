---
tags: [moc, wallet]
atualizado: 2026-10-19
---

# Wallet

Nome provisório (ver [[Outras Leis]], seção do nome).

Carteira financeira para **Android, PC e iOS**, no estilo do [[Pierre]]: o usuário
conecta os bancos pelo Open Finance e vê num lugar só os saldos, extratos, cartões,
faturas e investimentos de todas as instituições, e quanto sobra do salário no mês. A
mesma conta abre no tablet Android, no navegador e no app instalável de Windows. O
iPhone está pausado (só o `.ipa` sem assinatura, para o AltStore).

> [!success] Estado em 2026-10-19: T03 a T20 prontos, versão v0.1.4 publicada
> Core, BFF e o [[App Flutter]] (Android, navegador, Windows e iOS) no GitHub, com 244
> testes no core, 88 no BFF e 181 no app. A `dev` entrou na `main` em três PRs (#1 a #3):
> Android com Pluggy Connect e atualização pelo próprio app, auditoria das chamadas à
> Pluggy, planejamento do mês com gastos fixos sugeridos, correções de segurança e LGPD e
> exclusão de conta. A **v0.1.4** saiu nos Releases com instalador de Windows, APK e `.ipa`.
> Falta: spike com dados reais (T02, depende do Meu Pluggy), os segredos de assinatura do
> Android no GitHub (o APK da v0.1.4 saiu com a chave de debug) e o backlog antes de abrir
> para outras pessoas. Ver [[Plano de Implementação]].

> [!info] Histórico do estado
> Regulação pesquisada em 2026-09-28. Em 2026-09-29: Java 25 + Spring Boot 4.1 e
> Flutter decididos ([[Java e Flutter]]), com [[BFF]] na frente do [[Core]].
> Arquitetura desenhada e tarefas escritas em [[Plano de Implementação]]. Desenvolvimento com o Meu Pluggy
> ([[Meu Pluggy no Desenvolvimento]]). Core pronto em 2026-09-30, BFF em 2026-10-01 e
> app em 2026-10-02. Redesenho em painel em 2026-10-03, app de Windows em 2026-10-04 e
> 2026-10-05 (v0.1.0 a v0.1.2), ícone novo em 2026-10-12 (v0.1.3). Em 2026-10-18, a
> `master` virou `main` + `dev`, e chegaram o APK do tablet, o Pluggy Connect, a auditoria
> da Pluggy e o planejamento do mês. Em 2026-10-19, segurança, LGPD e a v0.1.4
> ([[Linha do Tempo]]).

> [!warning] O Wallet não se conecta direto aos bancos
> Só instituição autorizada pelo Banco Central participa do Open Finance. O Wallet
> consome os dados por meio de um provedor autorizado, como o Pierre faz com a
> Pluggy. Ver [[Caminhos de Acesso]].

## Regulação

- [[Open Finance]]: norma-mãe, papéis e quem pode participar
- [[Caminhos de Acesso]]: os três jeitos de chegar aos dados e por que só um serve
- [[Consentimento]]: o que a tela de liberação precisa cumprir
- [[Risco Regulatório 2026]]: a revisão das parcerias que o BC conclui em dezembro
- [[Outras Leis]]: LGPD, sigilo bancário, Marco Civil, CVM, lojas

## Produto

- [[Pierre]]: a referência, como funciona e o que ele terceiriza
- [[Custos]]: provedores, lojas e a estratégia de desenvolver sem pagar

## Arquitetura

- [[Visão Geral]]: componentes, princípios, repositório e ambientes
- [[BFF]]: a porta pública, sessão por plataforma e rotas por tela
- [[Core]]: módulos, porta do provedor, API interna e convenções
- [[App Flutter]]: estrutura, layout adaptativo, dinheiro e sessão por plataforma
- [[Modelo de Dados]]: tabelas e criptografia de coluna
- [[Sincronização]]: como os dados saem da Pluggy e chegam ao banco
- [[Autenticação]]: tokens no celular e no navegador
- [[Stack]]: as escolhas e as alternativas descartadas
- [[Fluxo de Conexão]]: como uma conta de banco entra no Wallet

## Plano

- [[Plano de Implementação]]: tarefas T01 a T18 e o backlog

## Decisões e histórico

- [[Java e Flutter]]
- [[Meu Pluggy no Desenvolvimento]]
- [[Decisões Pendentes]]
- [[Linha do Tempo]]
- [[Fontes]]

Relatório navegável da pesquisa: https://claude.ai/artifact/DLhB6DUUTeDm7QRxm3HgAR
Página da arquitetura: https://claude.ai/artifact/EZ15rUDPWjTS16vR7XNhEY
