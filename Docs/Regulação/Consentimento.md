---
tags: [regulacao, consentimento, ux]
atualizado: 2026-09-28
---

# Consentimento

O que "o banco" exige para liberar o acesso. Quem pede os dados precisa identificar
o cliente e obter o consentimento **antes** de compartilhar (RC 1/2020, art. 10).
Mesmo com a Pluggy fazendo a parte técnica, as telas do Wallet que levam o usuário
até lá precisam cumprir estas regras.

| Regra | Na prática | Base |
|---|---|---|
| Linguagem clara | Português simples: o que vai ser lido e para quê | art. 10, §1º, I |
| Finalidade determinada | "Mostrar seus saldos e gastos no Wallet". Nada genérico | art. 10, §1º, II |
| Prazo compatível | Sem teto de 12 meses desde a RC 7/2023; o prazo segue a finalidade | art. 10, §1º, III |
| Banco e dados discriminados | Usuário escolhe o banco e quais dados | art. 10, §1º, IV e V |
| Mudou, pede de novo | Nova finalidade, dados ou prazo = novo consentimento | art. 10, §2º |
| Proibido presumir | Sem contrato de adesão, checkbox pré-marcado ou aceite implícito | art. 10, §3º |
| Revogação a qualquer hora | Pelo Wallet ou pelo banco | art. 15 |
| Só o pertinente | Pedir só o que a finalidade usa | art. 12 |
| Gratuito | Open Finance não pode ser cobrado do usuário | RC 1/2020 |
| Senha nunca | Autenticação no app do banco, por redirecionamento | padrão do ecossistema |

## Telas que isso obriga no Wallet

- **Antes de conectar:** explicação da finalidade e escolha dos dados.
- **Contas conectadas:** lista com banco, dados liberados, validade e botão de
  desconectar (revogação).
- **Renovação:** aviso antes de vencer e renovação dentro do próprio app.

> [!note] Plano pago é permitido
> O que não pode ser cobrado é o Open Finance em si. Cobrar pelas funcionalidades,
> como o [[Pierre]] faz com o Pro, é o modelo normal.

O fluxo técnico está em [[Fluxo de Conexão]].
