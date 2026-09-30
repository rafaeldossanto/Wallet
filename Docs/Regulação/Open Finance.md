---
tags: [regulacao, open-finance]
atualizado: 2026-09-28
---

# Open Finance

## Norma-mãe

**Resolução Conjunta nº 1/2020** (CMN + BCB). Alterações relevantes para o Wallet:

| Norma | O que mudou |
|---|---|
| RC 7/2023 | Acabou o teto de 12 meses do consentimento. Renovação no próprio app receptor |
| RC 14/2025 | Nova metodologia de capital mínimo (afeta quem quiser licença própria) |
| RC 15/2025 | Incluiu a portabilidade de crédito (fev/2026) |

Normas técnicas de apoio: Resolução BCB 32/2020 (requisitos técnicos), Circular
4.015/2020 (escopo de dados), Circular 4.032/2020 (governança).

## Papéis (art. 2º)

| Papel | O que faz | No Wallet |
|---|---|---|
| Transmissora de dados | Tem os dados e entrega | Itaú, Nubank, XP… |
| Receptora de dados | Pede, colhe consentimento, recebe | O provedor (Pluggy), não o Wallet |
| Detentora de conta | Mantém a conta de onde sai o pagamento | Só se houver Pix |
| Iniciadora (ITP) | Dispara pagamento sem guardar o dinheiro | Licença da Pluggy |

## Quem participa (art. 6º)

- **Obrigatório:** bancos dos segmentos S1 e S2.
- **Voluntário:** demais instituições **autorizadas pelo BC**.
- A participação voluntária exige oferecer os próprios dados como transmissora
  (art. 6º, §3º). É a reciprocidade.

> [!danger] Empresa sem autorização do BC não é participante
> Não existe cadastro de "app" no Open Finance. Por isso o Wallet depende de um
> provedor. Ver [[Caminhos de Acesso]].

## Dados disponíveis

Contas (saldo, extrato, cheque especial), cartões de crédito, operações de crédito,
câmbio e investimentos:

| Investimento | Atualização |
|---|---|
| Renda fixa bancária (CDB, LCI, LCA) | até 1 h |
| Renda fixa crédito (debêntures, CRI, CRA) | até 1 h |
| Tesouro Direto | até 1 h |
| Fundos | até 1 h |
| Renda variável (ações, FIIs) | D-1 |

Histórico de transações de pelo menos 12 meses. **Fora:** seguros (Open Insurance,
SUSEP), cripto em exchanges e instituições pequenas que não aderiram.

## Se um dia o Wallet virar participante direto

O que a Pluggy carrega hoje por quem a contrata:

- Autorização do BC e adesão à Convenção (art. 44), com contribuições
- Diretor responsável pelo compartilhamento (art. 32)
- Cadastro no Diretório Central de Participantes
- Certificados ICP-Brasil: **BRCAC** (transporte, mTLS) e **BRSEAL** (assinatura)
- Perfil de segurança FAPI (OAuth 2.0 + OpenID Connect, mTLS, DCR/DCM)
- Certificação de conformidade na OpenID Foundation e funcional por API, refeita a
  cada versão
- Jornada padronizada (guia de experiência), métricas e service desk

Ver também [[Consentimento]] e [[Risco Regulatório 2026]].
