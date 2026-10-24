---
tags: [legal, lgpd, ripd]
atualizado: 2026-10-24
status: rascunho
---

# RIPD: relatório de impacto à proteção de dados

> **Versão preliminar, em revisão jurídica. Não vale como documento final.**

Relatório do art. 38 da LGPD: onde o tratamento de dados do Wallet pode causar dano às pessoas, e o que já reduz ou ainda vai reduzir esse risco. Complementa o [[ROPA]]. A avaliação de probabilidade e impacto abaixo é **preliminar** e precisa ser validada por quem responde pelo tratamento.

## 1. Identificação

| | |
|---|---|
| Controlador | [RAZÃO SOCIAL], CNPJ [CNPJ] |
| Encarregado | [NOME E E-MAIL DO ENCARREGADO] |
| Responsável pela elaboração | [RESPONSÁVEL] |
| Versão e data | 0.1, [DATA DE VIGÊNCIA] |
| Por que existe | Dado financeiro de muitas pessoas, tratado em escala e com compartilhamento por Open Finance. Tende a ser tratamento de alto risco |

## 2. O tratamento em resumo

- O Wallet é uma carteira financeira pessoal **somente de leitura**: junta saldos, extratos, cartões, faturas e investimentos de vários bancos pelo Open Finance. Não movimenta dinheiro, não é banco e não recomenda investimentos. É só para maiores de 18 anos.
- Os dados vêm dos bancos por uma instituição autorizada pelo Banco Central (Pluggy), com consentimento do usuário dado no aplicativo do próprio banco.
- Ficam guardados num servidor da AWS (EC2) e, se configurado, em cópias no S3; e-mails saem pelo SES. Região: [REGIÃO DA AWS].
- Dados tratados, finalidades, bases legais e prazos: ver o [[ROPA]].
- Quem acessa os dados: o próprio usuário, no aplicativo; as pessoas autorizadas a operar o servidor [DEFINIR QUEM E COMO O ACESSO É CONTROLADO].

## 3. Necessidade e proporcionalidade

| Pergunta | Resposta hoje |
|---|---|
| A finalidade é determinada? | Sim: mostrar ao usuário os próprios dados financeiros e ajudá-lo a planejar o mês |
| Coletamos só o necessário? | Em geral sim: o Wallet só lê, não guarda a senha do banco, guarda só os 4 últimos dígitos das contas e cifra a descrição das transações. Falta decidir o que guardar de dados financeiros antigos (prazo [A DEFINIR]) |
| Existe base legal para cada operação? | Sim, por operação, no [[ROPA]] |
| O titular consegue controlar? | Em parte: corrigir, excluir a conta, desvincular bancos e sair da lista funcionam. Exportação completa e portabilidade estão pendentes |
| Há alternativa menos invasiva? | O usuário pode usar o Wallet com poucos bancos; o dado de cada banco só entra se ele conectar |

## 4. Riscos

Escala: probabilidade e impacto em **baixa**, **média** ou **alta**. O nível é a combinação dos dois.

| # | Risco | Prob. | Impacto | Nível | Medidas existentes | Medidas pendentes |
|---|---|---|---|---|---|---|
| R1 | Vazamento do banco de dados ou de uma cópia de segurança | Média | Alta | Alto | Descrições cifradas (AES-256-GCM); senhas, tokens e códigos de recuperação só em hash; segredo do segundo fator cifrado; banco em rede interna; só uma porta pública; backups com permissão restrita | Cifrar o nome dos gastos fixos; cifrar o disco e as cópias; revisar quem acessa o servidor |
| R2 | Invasão de contas de usuários (senha vazada em outro serviço, phishing) | Média | Alta | Alto | Bloqueio progressivo de tentativas; limite por IP; segundo fator opcional; BCrypt; confirmação do e-mail; aviso ao dono quando alguém tenta se cadastrar com o e-mail dele | Avaliar tornar o segundo fator obrigatório ou incentivá-lo; avisar por e-mail em login de aparelho novo [A DEFINIR] |
| R3 | Perda ou exposição da chave de cifra das descrições (ou das chaves do login) | Baixa | Alta | Médio | Chaves fora do código e do repositório, só no ambiente do servidor | Guardar cópia da chave fora do servidor; plano de rotação de chaves |
| R4 | Incidente ou uso indevido no operador (Pluggy, AWS) | Baixa | Alta | Médio | Contratos e certificações dos operadores [CONFIRMAR]; o Wallet só pede os dados pertinentes | Formalizar o contrato de operador com cláusulas de LGPD e de comunicação de incidente [A DEFINIR] |
| R5 | Transferência internacional sem garantia (art. 33) | Média | Média | Médio | Nenhuma ainda | Confirmar a região da AWS e onde a Pluggy hospeda; escolher região no Brasil se possível; cláusulas-padrão (Res. CD/ANPD nº 19/2024) se houver envio para fora |
| R6 | Guardar dado demais por tempo demais | Média | Média | Médio | Exclusão da conta apaga tudo; conta não confirmada some em 7 dias; auditoria do provedor apagada em 180 dias; transações removidas pelo banco e registro de sincronizações apagados em 90 dias; tokens de sessão vencidos em 30 dias; cópias guardadas 14 dias | Definir o prazo de conta inativa, dos registros do servidor e da lista de espera confirmada |
| R7 | Dados continuarem chegando depois de o usuário revogar ou o consentimento vencer | Baixa | Média | Baixo | Desvincular o banco revoga a autorização na Pluggy; excluir a conta também; o Wallet guarda a validade do consentimento | Avisar antes de o consentimento vencer e renovar dentro do aplicativo |
| R8 | Nome dos gastos fixos exposto em claro | Baixa | Baixa | Baixo | Nome cifrado (AES-256-GCM) no banco e nas cópias; as linhas antigas foram cifradas na subida | Nenhuma |
| R9 | Dado sensível revelado indiretamente pelas descrições ou nomes (farmácia, igreja, sindicato, plano de saúde) | Média | Média | Médio | Descrições cifradas; nada de publicidade nem análise de perfil de terceiros | Decidir se haverá regra especial para esse tipo de dado; refletir isso na política |
| R10 | Registros de acesso do Marco Civil (art. 15) não guardados ou mal guardados | Baixa | Média | Baixo | Registro a cada login e renovação, IP cifrado, 6 meses, sem rota de leitura, sem chave estrangeira (sobrevivem à exclusão da conta) | Escrever o procedimento para ordem judicial: quem abre o banco, quem decifra o IP e quem registra a entrega |
| R11 | Direitos do titular não atendidos por completo | Média | Média | Médio | Os dados aparecem no aplicativo; corrigir, excluir, revogar e baixar tudo em arquivo (acesso e portabilidade, até 3 por dia) funcionam | Formalizar o canal e os prazos ([[Canal do Titular]]); confirmar com o jurídico que o JSON basta como portabilidade |
| R12 | Descobrir, por tentativa, quais e-mails têm conta ou estão na lista | Baixa | Baixa | Baixo | O cadastro responde sempre igual; o login recusa igual para senha errada e conta não confirmada | Conferir a mesma regra na lista de espera |
| R13 | IP de usuários nos registros do limite de tentativas | Média | Baixa | Baixo | Só os registros do limite de tentativas trazem IP; sem dados financeiros nos registros | Definir o prazo de retenção desses registros |
| R14 | Incidente não percebido ou comunicado fora do prazo | Média | Alta | Alto | Procedimento escrito em [[Incidentes]] | Definir responsáveis e monitoramento; treinar o fluxo |
| R15 | Menor de idade usando o Wallet | Baixa | Média | Baixo | O aplicativo é declarado só para maiores de 18 | Definir o aceite e o que fazer se descobrir uma conta de menor |
| R16 | Envio da lista de espera ou dos avisos para quem não pediu | Baixa | Baixa | Baixo | Consentimento por caixa desmarcada e confirmação por link; inscrição pendente expira em 7 dias | Garantir a saída da lista em um clique |

## 5. Conclusão

- Os riscos **altos** são R1, R2 e R14. O último depende de trabalho ainda pendente (procedimento de incidente em operação).
- Antes de abrir para outras pessoas, recomenda-se formalizar o **R11**, confirmar a **transferência internacional (R5)** e definir os **prazos de retenção (R6)**.
- [A CONFIRMAR: parecer do jurídico sobre a necessidade de consultar a ANPD ou de revisar este relatório com regularidade.]

## 6. Aprovação

| | |
|---|---|
| Elaborado por | [RESPONSÁVEL] |
| Revisado pelo encarregado | [NOME DO ENCARREGADO], [DATA] |
| Aprovado por | [NOME E CARGO], [DATA] |
| Próxima revisão | [DATA] ou quando o tratamento mudar (novos dados, novos operadores, nova região) |
