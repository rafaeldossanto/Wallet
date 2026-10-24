---
tags: [legal, lgpd, titular]
atualizado: 2026-10-24
status: rascunho
---

# Canal do Titular

> **Versão preliminar, em revisão jurídica. Não vale como documento final.**

Como o Wallet atende hoje cada direito do art. 18 da LGPD. Ligado à [[Política de Privacidade]], ao [[ROPA]] e ao [[Incidentes]]. O que ainda não existe aparece como **pendente**, sem disfarce.

## Como o titular nos procura

| Onde | Como |
|---|---|
| Dentro do aplicativo | Ajustes: corrigir nome e e-mail, desvincular bancos, "Excluir conta" |
| Lista de espera | Link para sair, no e-mail que recebe |
| Qualquer outro pedido | E-mail para [E-MAIL DO ENCARREGADO] |
| Reclamação | Também à ANPD, sem precisar falar antes com o Wallet |

## Os direitos, um a um

| Direito (art. 18) | Como é atendido hoje | Situação |
|---|---|---|
| I. Confirmar que existe tratamento | Quem tem conta vê no aplicativo e baixa o arquivo com tudo (Ajustes → Seus dados → Baixar meus dados); para os outros casos, por e-mail ao encarregado | Atendido |
| II. Acessar os dados | O aplicativo mostra tudo, e **Ajustes → Seus dados → Baixar meus dados** gera um arquivo JSON com o perfil, as conexões, as contas, as transações (com a descrição legível), os cartões e as faturas, os investimentos e o planejamento. Nunca traz senha, segredo do segundo fator nem token. Até 3 por dia | Atendido |
| III. Corrigir dados incompletos, inexatos ou desatualizados | Em Ajustes, nome e e-mail. Os dados bancários vêm do banco: o titular corrige no banco | Atendido |
| IV. Anonimizar, bloquear ou eliminar dados desnecessários ou excessivos | Por e-mail ao encarregado, caso a caso | Manual |
| V. Portabilidade | O mesmo arquivo: JSON legível por máquina, com versão do formato (`schemaVersion`). [A CONFIRMAR: se a ANPD ou o jurídico pedirem um formato de interoperabilidade específico] | Atendido [CONFIRMAR o formato] |
| VI. Eliminar os dados tratados com consentimento | "Excluir conta", em Ajustes: apaga tudo e revoga a autorização na Pluggy. Na lista de espera, o link do e-mail | Atendido |
| VII. Saber com quem os dados são compartilhados | A Política de Privacidade (Pluggy, AWS, GitHub) | Atendido |
| VIII. Saber que pode não dar consentimento e o que acontece | A política e as telas de conexão explicam; sem o consentimento do Open Finance o Wallet não mostra dados dos bancos | Atendido [CONFIRMAR o texto das telas] |
| IX. Revogar o consentimento | Desvincular o banco; sair da lista pelo link do e-mail; excluir a conta | Atendido |
| Oposição ao tratamento (§ 2º) | Por e-mail ao encarregado | Manual |
| Revisão de decisões automatizadas (art. 20) | O Wallet não toma decisões automatizadas que afetem o titular [CONFIRMAR]. Categorias e sugestões de gastos fixos são só apoio | Não se aplica [CONFIRMAR] |

## Pedidos feitos por e-mail

| Passo | O que fazer | Quem |
|---|---|---|
| 1. Receber | Responder que o pedido chegou e registrar data, quem pediu e o que pediu | [RESPONSÁVEL] |
| 2. Confirmar quem é | Pedir que o pedido venha do e-mail da conta, ou confirmar por link enviado a ele. Não pedir documentos sem necessidade | [RESPONSÁVEL] |
| 3. Atender | Fazer o que o pedido exige (corrigir, apagar, exportar manualmente, explicar) | [RESPONSÁVEL] |
| 4. Responder | Dizer o que foi feito, ou por que não foi possível, com a base legal | Encarregado |
| 5. Registrar | Guardar o pedido, a resposta e as datas | [RESPONSÁVEL] |

### Prazos

| O quê | Prazo (LGPD, art. 19) |
|---|---|
| Confirmação de que existe tratamento e acesso, em formato simplificado | Imediato |
| Declaração completa (origem, critérios, finalidade) | Até 15 dias da data do pedido |
| Demais direitos | [A DEFINIR: prazo interno; a ANPD ainda regulamenta] |

### Quando dizer não

Só quando a lei permitir, e sempre com a resposta justificada. Exemplos: dado que o Wallet precisa guardar por obrigação legal (como os registros de acesso do Marco Civil, quando existirem). Nas cópias de segurança, um dado excluído sai em até 14 dias.

## Pendências

| # | O que falta | Direito |
|---|---|---|
| 1 | Definir o e-mail do encarregado e quem atende o canal | Todos |
| 2 | Definir o prazo interno dos demais pedidos | IV e oposição |
| 3 | Conferir o texto das telas de conexão (item VIII) | VIII |
| 4 | Registros de acesso do Marco Civil (para saber o que se pode apagar) | VI |
| 5 | Confirmar com o jurídico se o JSON basta como portabilidade | V |
