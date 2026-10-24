---
tags: [legal, lgpd, ropa]
atualizado: 2026-10-24
status: rascunho
---

# ROPA: registro das operações de tratamento

> **Versão preliminar, em revisão jurídica. Não vale como documento final.**

Registro exigido pelo art. 37 da LGPD: o que o Wallet faz com dados pessoais, uma operação por tabela. Serve de base para a [[Política de Privacidade]], o [[RIPD]] e o [[Canal do Titular]]. O que está entre colchetes ainda não se sabe.

## Quem

| | |
|---|---|
| Controlador | [RAZÃO SOCIAL], CNPJ [CNPJ], [ENDEREÇO] |
| Encarregado | [NOME E E-MAIL DO ENCARREGADO] |
| Operadores | Pluggy (Open Finance); Amazon Web Services (EC2 e SES, região [REGIÃO DA AWS]) |
| Terceiro que recebe IP | GitHub (distribuição do app e checagem de versão) |
| Titulares | Pessoas maiores de 18 anos que criam conta; pessoas que entram na lista de espera |
| Última revisão | [DATA DE VIGÊNCIA] |

Sobre dados sensíveis (art. 5º, II): o Wallet não coleta dado sensível de propósito. Mas a descrição de uma transação ou o nome de um gasto fixo pode revelar um (saúde, religião, filiação sindical, por exemplo). Ver o risco R9 no [[RIPD]].

## Resumo

| # | Operação | Base legal | Retenção |
|---|---|---|---|
| 1 | Cadastro e confirmação do e-mail | Contrato (art. 7º, V) | Até excluir a conta; não confirmada, 7 dias |
| 2 | Login, sessões e segundo fator | Contrato (art. 7º, V) | Até excluir a conta; tokens vencidos ou revogados, 30 dias |
| 3 | Proteção contra tentativas de invasão | Contrato (art. 7º, V) | [A DEFINIR] |
| 4 | Conexão com bancos pelo Open Finance | Consentimento (art. 7º, I) | Até desvincular ou excluir a conta |
| 5 | Sincronização e exibição dos dados financeiros | Consentimento (art. 7º, I) | Enquanto a conta existir; removidas pelo banco: 90 dias; registro das sincronizações: 90 dias; [A DEFINIR: prazo de conta inativa] |
| 6 | Planejamento do mês | Contrato (art. 7º, V) | Até excluir a conta |
| 7 | Auditoria das chamadas ao provedor | Contrato (art. 7º, V) [CONFIRMAR: ou legítimo interesse] | 180 dias |
| 8 | Lista de espera | Consentimento (art. 7º, I) | Pendente: 7 dias; confirmada: [A DEFINIR] |
| 9 | E-mails da conta | Contrato (art. 7º, V) | Não guardamos o conteúdo [CONFIRMAR] |
| 10 | Distribuição do app e checagem de versão | [A DEFINIR] | Fora do controle do Wallet (GitHub) |
| 11 | Registros de acesso (Marco Civil) | Obrigação legal (art. 7º, II) | 6 meses. **Pendente: não implementado** |
| 12 | Cópias de segurança | Contrato (art. 7º, V) | 14 dias |
| 13 | Exclusão da conta | Contrato (art. 7º, V); pedido do titular (art. 18, VI) | Imediata; permanece em cópias de segurança por até 14 dias |

## 1. Cadastro e confirmação do e-mail

| Campo | Descrição |
|---|---|
| Finalidade | Criar a conta e provar que o e-mail é de quem se cadastrou |
| Dados | E-mail, nome, senha (só hash BCrypt), data da confirmação do e-mail |
| Titulares | Usuários |
| Base legal | Execução de contrato (art. 7º, V) |
| Retenção | Até o usuário excluir a conta. Conta com e-mail não confirmado: apagada em 7 dias |
| Compartilhamento / operador | AWS (servidor e envio do e-mail de confirmação) |
| Transferência internacional | [A CONFIRMAR]: depende da região da AWS. Art. 33 |
| Medidas de segurança | HTTPS; senha em BCrypt; o cadastro responde sempre igual, sem revelar se o e-mail já existe; limite de links de confirmação por hora; só uma porta pública; banco em rede interna |

## 2. Login, sessões e segundo fator

| Campo | Descrição |
|---|---|
| Finalidade | Autenticar o usuário e manter a sessão; oferecer verificação em duas etapas |
| Dados | Refresh tokens (só hash); segredo TOTP (cifrado); códigos de recuperação (hash) |
| Titulares | Usuários |
| Base legal | Execução de contrato (art. 7º, V) |
| Retenção | Até excluir a conta. O token de renovação vale 30 dias e é apagado 30 dias depois de vencer ou de ser revogado |
| Compartilhamento / operador | AWS (servidor) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Tokens e códigos só como hash; segredo TOTP cifrado; segundo fator opcional; chave de assinatura do login fora do código; no aplicativo, tokens no armazenamento seguro do aparelho |

## 3. Proteção contra tentativas de invasão

| Campo | Descrição |
|---|---|
| Finalidade | Bloquear quem tenta adivinhar senhas e limitar abuso |
| Dados | Contadores de tentativa por hash do e-mail e por endereço IP; IP nos registros do limite de tentativas |
| Titulares | Usuários e qualquer pessoa que tente entrar |
| Base legal | Execução de contrato (art. 7º, V) [CONFIRMAR: legítimo interesse, art. 7º, IX, para quem não é usuário] |
| Retenção | [A DEFINIR]. O bloqueio e os contadores se zeram depois de um dia sem falhas |
| Compartilhamento / operador | AWS (servidor) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | E-mail em hash nos contadores; bloqueio progressivo; limite de requisições por IP e por usuário |

## 4. Conexão com bancos pelo Open Finance

| Campo | Descrição |
|---|---|
| Finalidade | Ligar a conta do usuário aos bancos que ele escolher, para ler os dados que ele autorizou |
| Dados | Identificador da conexão no provedor, situação, validade do consentimento. Nunca a senha do banco |
| Titulares | Usuários |
| Base legal | Consentimento (art. 7º, I), conforme a Resolução Conjunta nº 1/2020; sigilo bancário (LC 105/2001) |
| Retenção | Até o usuário desvincular o banco ou excluir a conta; ambos revogam a autorização na Pluggy |
| Compartilhamento / operador | Pluggy (instituição autorizada pelo Banco Central) |
| Transferência internacional | [A CONFIRMAR]: depende de onde a Pluggy hospeda os dados. Art. 33 |
| Medidas de segurança | Autorização no aplicativo do banco, sem senha no Wallet; consentimento revogável; o core só aceita chamadas do BFF, com chave compartilhada |

## 5. Sincronização e exibição dos dados financeiros

| Campo | Descrição |
|---|---|
| Finalidade | Mostrar ao usuário saldos, extratos, cartões, faturas e investimentos de todos os bancos conectados |
| Dados | Contas (instituição, tipo, últimos 4 dígitos); saldos e histórico; transações (valor, data, categoria, parcelas, descrição cifrada); cartões e faturas; investimentos e histórico |
| Titulares | Usuários (e, indiretamente, quem aparece nas descrições das transações) |
| Base legal | Consentimento (art. 7º, I); sigilo bancário (LC 105/2001) |
| Retenção | Enquanto a conta existir: saldos, histórico de saldos, transações, faturas, investimentos e o histórico deles (é o que o produto mostra). Apagado sozinho: transação que o provedor deixou de informar, 90 dias depois; registro de cada sincronização, 90 dias depois de terminar (a mais recente de cada conexão fica). [A DEFINIR: prazo de conta inativa] |
| Compartilhamento / operador | Pluggy (origem dos dados); AWS (guarda) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Descrições cifradas com AES-256-GCM; HTTPS; core e banco em rede interna; cópias de segurança com retenção de 14 dias; só os dados pertinentes são lidos |

## 6. Planejamento do mês

| Campo | Descrição |
|---|---|
| Finalidade | Gastos fixos e salário esperado, para calcular o que sobra no mês |
| Dados | Nome dos gastos fixos (ainda sem cifra: cifragem pendente), salário esperado |
| Titulares | Usuários |
| Base legal | Execução de contrato (art. 7º, V) |
| Retenção | Até excluir a conta |
| Compartilhamento / operador | AWS (guarda) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | HTTPS; rede interna. **Pendente:** cifrar o nome dos gastos fixos |

## 7. Auditoria das chamadas ao provedor

| Campo | Descrição |
|---|---|
| Finalidade | Registrar as chamadas feitas ao provedor de Open Finance, para investigar falhas e abusos |
| Dados | Registro pseudônimo das chamadas, sem valores |
| Titulares | Usuários |
| Base legal | Execução de contrato (art. 7º, V) [CONFIRMAR: ou legítimo interesse, art. 7º, IX] |
| Retenção | 180 dias; apagado todas as noites o que passou do prazo |
| Compartilhamento / operador | AWS (guarda) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Sem valores e sem descrições; apagamento automático |

## 8. Lista de espera

| Campo | Descrição |
|---|---|
| Finalidade | Avisar por e-mail quando o Wallet abrir |
| Dados | E-mail, situação da inscrição, versão do consentimento, datas |
| Titulares | Pessoas que se inscrevem na página de apresentação |
| Base legal | Consentimento (art. 7º, I): caixa desmarcada, com texto próprio, e confirmação por link no e-mail |
| Retenção | Inscrição não confirmada: 7 dias. Confirmada: [A DEFINIR] |
| Compartilhamento / operador | AWS (guarda e envio do e-mail) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Confirmação por link; limite de tentativas; HTTPS; saída da lista pelo link do e-mail |

## 9. E-mails da conta

| Campo | Descrição |
|---|---|
| Finalidade | Confirmar o e-mail, avisar de tentativa de cadastro com o seu e-mail e de bloqueio do login |
| Dados | E-mail e o link do aviso |
| Titulares | Usuários e quem tem o e-mail usado num cadastro |
| Base legal | Execução de contrato (art. 7º, V) |
| Retenção | O conteúdo não fica guardado no Wallet [CONFIRMAR]; links valem 24 horas |
| Compartilhamento / operador | AWS (envio pelo SES) |
| Transferência internacional | [A CONFIRMAR]: depende da região do SES |
| Medidas de segurança | No máximo um aviso de cada tipo por conta por hora; limite de links por hora |

## 10. Distribuição do aplicativo e checagem de versão

| Campo | Descrição |
|---|---|
| Finalidade | Entregar o aplicativo e as atualizações, e saber se há versão nova |
| Dados | Endereço IP, recebido pelo GitHub quando o aplicativo consulta a versão |
| Titulares | Quem instala o aplicativo |
| Base legal | [A DEFINIR] |
| Retenção | Definida pelo GitHub, fora do controle do Wallet |
| Compartilhamento / operador | GitHub |
| Transferência internacional | Sim, provavelmente [A CONFIRMAR]. Art. 33 |
| Medidas de segurança | O Wallet não envia dados da conta nessa consulta [CONFIRMAR] |

## 11. Registros de acesso à aplicação (Marco Civil)

| Campo | Descrição |
|---|---|
| Finalidade | Cumprir o art. 15 do Marco Civil da Internet |
| Dados | Data, hora e IP dos acessos |
| Titulares | Usuários |
| Base legal | Obrigação legal (art. 7º, II) |
| Retenção | 6 meses, em sigilo |
| Compartilhamento / operador | AWS (guarda) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | **Pendente: ainda não implementado.** Hoje os registros do servidor só trazem identificadores, e o IP aparece nos do limite de tentativas |

## 12. Cópias de segurança

| Campo | Descrição |
|---|---|
| Finalidade | Poder recuperar o banco de dados depois de uma falha |
| Dados | Todo o banco de dados, comprimido |
| Titulares | Todos os usuários |
| Base legal | Execução de contrato (art. 7º, V) |
| Retenção | Diárias, guardadas por 14 dias |
| Compartilhamento / operador | AWS (servidor e, se configurado, S3) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Arquivo com permissão restrita ao dono; as descrições continuam cifradas dentro do arquivo; a chave de cifra fica fora do arquivo |

## 13. Exclusão da conta

| Campo | Descrição |
|---|---|
| Finalidade | Atender o pedido do usuário de apagar tudo |
| Dados | Todos os dados da conta e os financeiros |
| Titulares | Usuários |
| Base legal | Execução de contrato (art. 7º, V) e direito do titular (art. 18, VI) |
| Retenção | A exclusão apaga tudo e revoga a autorização na Pluggy; o que está nas cópias de segurança sai em até 14 dias |
| Compartilhamento / operador | Pluggy (revogação) |
| Transferência internacional | [A CONFIRMAR] |
| Medidas de segurança | Feita pelo próprio usuário, logado, em Ajustes |

## Pendências deste registro

- Preencher os dados da empresa e do encarregado.
- Confirmar a região da AWS e onde a Pluggy hospeda os dados (transferência internacional, art. 33).
- Definir as retenções marcadas [A DEFINIR].
- Implementar os registros de acesso do Marco Civil.
- Cifrar o nome dos gastos fixos.
- Confirmar com o jurídico as bases legais marcadas [CONFIRMAR].
