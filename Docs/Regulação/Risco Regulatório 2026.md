---
tags: [regulacao, risco]
atualizado: 2026-09-28
---

# Risco Regulatório 2026

O caminho B de [[Caminhos de Acesso]] é o que o mercado usa hoje, e é o que o
Banco Central está revisando.

## Linha do tempo

- **16/12/2025:** debate público sobre ITPs que agregam dados e repassam a empresas
  não reguladas (transparência, segurança, responsabilidade, reciprocidade).
- **12/03/2026:** o BC apresenta ao Conselho do Open Finance a proposta de revisão.
- **31/03/2026:** fim do prazo de contribuições.
- **28/05/2026:** setor reclama de "duplo fechamento": capital alto da RC 14/2025 +
  parceria restrita. A proposta ainda não tinha ido a consulta pública formal.
- **03/09/2026:** o chefe do Departamento de Regulação do BC diz que a revisão segue,
  com **conclusão prevista para dezembro de 2026**.

## O que a proposta traz

- Todo contrato em que instituição regulada repassa dados a empresa não regulada
  vira **parceria** formal.
- Dois papéis novos: **instituição integradora** (a Pluggy) e **entidade parceira**
  (o Wallet).
- Modelo **1x1x1**: uma integradora, uma parceira, um consentimento. O consentimento
  nomeia a parceira.
- A parceira fica **proibida de repassar** os dados a terceiros.
- Requisitos técnicos e de segurança alinhados à regra de Banking as a Service.

> [!warning] Ainda não é norma
> Até 28/09/2026 não havia resolução publicada. Conferir antes de lançar.

## O que fazer desde já

1. Isolar o provedor atrás de uma interface no backend (algo como
   `FinancialDataProvider`), para trocar de provedor sem reescrever o app.
2. Nunca repassar dado de usuário a terceiros, nem para analytics ou publicidade.
3. Guardar o mínimo: ler, calcular, mostrar.

Ver [[Stack]] e [[Decisões Pendentes]].
