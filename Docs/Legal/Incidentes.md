---
tags: [legal, lgpd, incidentes]
atualizado: 2026-10-23
status: rascunho
---

# Procedimento de incidente de segurança

> **Versão preliminar, em revisão jurídica. Não vale como documento final.**

O que fazer quando dados pessoais podem ter sido acessados, perdidos, alterados ou divulgados sem autorização. Base: art. 48 da LGPD e a Resolução CD/ANPD nº 15/2024. Os nomes e contatos ficam como [RESPONSÁVEL] até serem definidos. Ligado ao [[ROPA]], ao [[RIPD]] (riscos R1, R2, R4 e R14) e ao [[Canal do Titular]].

## Em uma frase

Detectar, conter, avaliar, **comunicar a ANPD e os titulares em até 3 dias úteis** quando houver risco ou dano relevante, registrar e revisar.

## O que conta como incidente

- Acesso ao banco de dados, ao servidor ou a uma cópia de segurança por quem não devia.
- Vazamento de chave (cifra das descrições, assinatura do login, chave entre o BFF e o core, senha do banco de dados, credenciais do SMTP ou da Pluggy).
- Conta de usuário invadida em escala, ou falha que mostra dados de uma pessoa a outra.
- Incidente avisado por um operador (Pluggy, AWS) que envolva dados do Wallet.
- Perda do servidor ou das cópias de segurança sem recuperação.

Na dúvida, trate como incidente e avalie.

## Quem faz o quê

| Papel | Quem | O que faz |
|---|---|---|
| Responsável pelo incidente | [RESPONSÁVEL] | Conduz o procedimento do início ao fim e decide |
| Responsável técnico | [RESPONSÁVEL] | Investiga, contém e recupera |
| Encarregado | [NOME E E-MAIL DO ENCARREGADO] | Avalia o risco com o jurídico, fala com a ANPD e com os titulares |
| Jurídico | [RESPONSÁVEL] | Revisa as comunicações e os prazos |

## Os passos

### 1. Detectar

| O quê | Quem | Como |
|---|---|---|
| Receber o aviso: de um usuário, de um operador, dos registros do servidor ou de um alerta | [RESPONSÁVEL] | Canal de contato: [NOME E E-MAIL DO ENCARREGADO] |
| Anotar na hora: data e hora em que se ficou sabendo, quem avisou, o que foi dito | [RESPONSÁVEL] | Abrir o registro do incidente (passo 5) |
| Definir como o Wallet será avisado de anomalias (alertas, revisão dos registros) | [RESPONSÁVEL] | [A DEFINIR] |

O prazo de 3 dias úteis conta do momento em que o Wallet **sabe que o incidente afetou dados pessoais**. Por isso anote a hora com cuidado.

### 2. Conter

Primeiras horas. Quem faz: responsável técnico.

- Isolar o que foi atingido (parar o serviço afetado, bloquear o acesso suspeito).
- Trocar as credenciais que possam ter vazado:
  - chave entre o BFF e o core (`WALLET_GATEWAY_KEY`);
  - senha do banco de dados;
  - chaves de assinatura do login (derruba as sessões, sem perder dados);
  - credenciais do SMTP e da Pluggy;
  - chave da AWS, se for o caso.
- Se contas de usuários foram atingidas: invalidar as sessões e bloquear as contas afetadas.
- Se uma conexão com banco foi atingida: desvincular o item na Pluggy.
- **Não apagar** registros e provas: guarde cópia dos registros do servidor antes de mexer.
- A chave de cifra das descrições (`WALLET_DATA_KEY`) só deve ser trocada com um plano, porque trocá-la sem reprocessar o que foi gravado torna as descrições ilegíveis.

### 3. Avaliar

Quem faz: responsável técnico levanta os fatos; encarregado e jurídico decidem.

| Pergunta | Onde achar |
|---|---|
| Quais dados foram afetados? | [[ROPA]] (a operação envolvida) |
| Quantas pessoas, e quem são? | Registros do servidor; banco de dados |
| Os dados estavam cifrados ou em hash? | Descrições, senhas, tokens e segredo do segundo fator, sim; o nome dos gastos fixos, não (pendente) |
| Há risco ou dano relevante aos titulares (fraude, discriminação, exposição financeira)? | Decisão do encarregado com o jurídico |
| O incidente continua? | Responsável técnico |

Se há risco ou dano relevante, siga para o passo 4. Se não há, **registre a decisão e os motivos** (passo 5), sem comunicar.

### 4. Comunicar

**Prazo: 3 dias úteis**, a partir de saber que o incidente afetou dados pessoais (Resolução CD/ANPD nº 15/2024).

| A quem | Quem faz | Como |
|---|---|---|
| ANPD | Encarregado | Formulário de comunicação de incidente no site da ANPD |
| Titulares afetados | Encarregado, com o jurídico | E-mail e aviso no aplicativo, em linguagem simples |
| Operadores envolvidos | Responsável técnico | Pedir informações e medidas a Pluggy e AWS |
| Outras autoridades, se couber (por exemplo, polícia ou o banco do titular) | Jurídico | [A DEFINIR] |

O que a comunicação precisa dizer (art. 48, §1º):

- a natureza dos dados pessoais afetados;
- as informações sobre os titulares envolvidos;
- as medidas técnicas e de segurança usadas para proteger os dados;
- os riscos relacionados ao incidente;
- os motivos da demora, se não se comunicou dentro do prazo;
- as medidas que foram ou serão adotadas para reverter ou mitigar os efeitos.

Se faltar informação no prazo, comunique o que se sabe e **complemente depois**, em vez de esperar.

Ao titular, diga também o que ele pode fazer (trocar a senha, ativar o segundo fator, desvincular bancos, ficar atento a golpes que usem seus dados).

### 5. Registrar

Quem faz: responsável pelo incidente. Mesmo que não haja comunicação, **todo incidente é registrado**.

O registro tem:

- data e hora em que se soube; data e hora do incidente;
- o que aconteceu, as causas e os dados e titulares afetados;
- as medidas de contenção, com horário;
- a avaliação de risco e a decisão de comunicar ou não, com os motivos;
- as comunicações feitas (a quem, quando, o texto);
- as medidas tomadas depois.

Guarde por no mínimo [A CONFIRMAR: 5 anos, conforme a Resolução CD/ANPD nº 15/2024], em local restrito.

### 6. Revisar

Em até [A DEFINIR: 30 dias] depois de encerrado:

- reunir a equipe e conversar sobre a causa raiz, sem procurar culpado;
- listar o que falhou e o que funcionou, e abrir tarefas com prazo e responsável;
- atualizar o [[RIPD]] e o [[ROPA]];
- testar o procedimento uma vez por ano [A DEFINIR].

## Contatos de emergência

| | |
|---|---|
| Responsável pelo incidente | [RESPONSÁVEL] |
| Encarregado | [NOME E E-MAIL DO ENCARREGADO] |
| Jurídico | [RESPONSÁVEL] |
| Suporte da AWS | [A DEFINIR] |
| Contato da Pluggy | [A DEFINIR] |
