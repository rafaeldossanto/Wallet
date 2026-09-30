---
tags: [moc, wallet]
atualizado: 2026-09-28
---

# Wallet

Nome provisório (ver [[Outras Leis]], seção do nome).

Carteira financeira para **iOS, Android e PC**, no estilo do [[Pierre]]: o usuário
conecta os bancos pelo Open Finance e vê num lugar só os saldos, extratos, cartões e
investimentos de todas as instituições. A mesma conta abre no celular e no navegador
do computador.

> [!info] Estado: arquitetura planejada, nenhuma linha de código
> Regulação pesquisada em 2026-09-28. Em 2026-09-29: Java 25 + Spring Boot 4.1 e
> Flutter decididos ([[Java e Flutter]]), com [[BFF]] na frente do [[Core]].
> Arquitetura desenhada e tarefas escritas em [[Plano de Implementação]]. Desenvolvimento com o Meu Pluggy
> ([[Meu Pluggy no Desenvolvimento]]).

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
