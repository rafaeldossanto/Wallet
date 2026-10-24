---
tags: [legal, lgpd, privacidade]
atualizado: 2026-10-24
status: rascunho
---

# Política de Privacidade

> **Versão preliminar, em revisão jurídica. Não vale como documento final.**

Última atualização: [DATA DE VIGÊNCIA]

Esta política explica quais dados o Wallet guarda sobre você, para quê, com quem os divide, por quanto tempo e como você controla tudo isso. Ela vale para o aplicativo (Android, Windows e navegador) e para a página de apresentação do Wallet.

O que está entre colchetes, como [RAZÃO SOCIAL], ainda não foi definido e será preenchido antes da publicação.

## 1. Quem é o responsável pelos seus dados

| | |
|---|---|
| Controlador | [RAZÃO SOCIAL], CNPJ [CNPJ] |
| Endereço | [ENDEREÇO] |
| Encarregado (canal do titular) | [NOME E E-MAIL DO ENCARREGADO] |

O controlador é quem decide como os dados são usados. Para qualquer pedido ou dúvida sobre seus dados, fale com o encarregado.

## 2. O que é o Wallet

O Wallet é uma carteira financeira pessoal **somente de leitura**. Ele junta saldos, extratos, cartões, faturas e investimentos de vários bancos, pelo Open Finance, para você ver tudo num lugar só.

- O Wallet **não movimenta dinheiro**: não paga contas, não faz transferências nem Pix.
- O Wallet **não é um banco**.
- O Wallet **não recomenda investimentos**. O que ele mostra é descritivo.
- O Wallet **não é para menores de 18 anos**.

## 3. Quais dados tratamos

| Grupo | Quais dados | Observação |
|---|---|---|
| Conta | E-mail, nome, senha, data em que você confirmou o e-mail | A senha nunca é guardada: guardamos só um código irreversível dela (hash BCrypt) |
| Segundo fator | Segredo do segundo fator (TOTP) e códigos de recuperação | O segredo é guardado cifrado; os códigos de recuperação, só como hash |
| Proteção do login | Contadores de tentativas de entrada, por e-mail (em hash) e por endereço IP | Servem para bloquear quem tenta adivinhar senhas |
| Sessões | Chaves de renovação do login (refresh tokens) | Guardadas só como hash |
| Conexões com bancos | Identificador da conexão no provedor, situação e validade do seu consentimento | Não guardamos usuário nem senha do banco |
| Dados financeiros | Contas (instituição, tipo e últimos 4 dígitos), saldos e histórico de saldos, transações (valor, data, categoria, parcelas e descrição), cartões e faturas, investimentos e histórico | A descrição de cada transação é guardada cifrada (AES-256-GCM) |
| Planejamento | Gastos fixos (nome) e salário esperado | O nome dos gastos fixos ainda é guardado sem cifra (cifragem pendente) |
| Auditoria do provedor | Registro das chamadas que o Wallet faz ao provedor de Open Finance | Pseudônimo, sem valores, por 180 dias |
| Lista de espera | E-mail, situação da inscrição, versão do consentimento e datas | Só para avisar quando o Wallet abrir |
| Registros técnicos | Identificadores internos; o IP aparece nos registros do limite de tentativas | Veja a seção 8 |

O Wallet **não coleta** a senha do seu banco e **não usa ferramentas de medição ou de publicidade de terceiros** no aplicativo. A página de apresentação não usa cookies nem rastreadores. A versão para navegador guarda um cookie de sessão, necessário para manter você conectado.

## 4. Para que usamos e em que base legal

| Finalidade | Dados | Base legal (LGPD) |
|---|---|---|
| Criar e manter sua conta, e entregar o serviço | Conta, sessões, segundo fator, planejamento | Execução de contrato (art. 7º, V) |
| Proteger sua conta contra tentativas de invasão | Contadores de tentativa, IP | Execução de contrato (art. 7º, V) |
| Buscar e mostrar seus dados dos bancos pelo Open Finance | Conexões e dados financeiros | Consentimento (art. 7º, I) |
| Avisar por e-mail quando o Wallet abrir | Lista de espera | Consentimento (art. 7º, I) |
| Guardar registros de acesso à aplicação, quando existirem | Registros de acesso | Obrigação legal (art. 7º, II), Marco Civil da Internet, art. 15 |

O compartilhamento de dados bancários acontece com o **seu consentimento**, por instituição autorizada, em respeito ao sigilo bancário (Lei Complementar 105/2001).

## 5. Como o Wallet se conecta aos seus bancos

A conexão é feita pelo Open Finance, por uma instituição autorizada pelo Banco Central (a Pluggy). Funciona assim:

1. Você escolhe o banco e autoriza **no aplicativo do próprio banco**. O Wallet nunca vê a sua senha.
2. O consentimento segue as regras do Open Finance (Resolução Conjunta nº 1/2020): você sabe quais dados serão lidos e para quê.
3. Você pode revogar quando quiser. No Wallet, **desvincular o banco revoga a autorização na Pluggy**. Também é possível revogar no aplicativo do banco.

## 6. Com quem compartilhamos

| Quem | Papel | O que recebe ou faz |
|---|---|---|
| Pluggy | Operador (Open Finance) | Faz a ligação com os bancos e entrega os dados que você autorizou ao Wallet |
| Amazon Web Services (AWS) | Operador (infraestrutura) | Hospeda o servidor (EC2) e envia os e-mails da conta (SES), na região [REGIÃO DA AWS] |
| GitHub | Distribuição do aplicativo | O aplicativo consulta o GitHub para saber se há versão nova, e o GitHub recebe o seu IP |

[A CONFIRMAR: o Wallet não vende dados e não os usa para publicidade.]

Fora estes casos, só compartilhamos dados se a lei ou uma autoridade exigir.

## 7. Transferência para fora do Brasil

Se os dados forem tratados fora do Brasil, isso só pode acontecer com as garantias do art. 33 da LGPD. Isso depende da região da AWS escolhida e de onde a Pluggy hospeda os dados: [A CONFIRMAR].

## 8. Por quanto tempo guardamos

| Dado | Prazo |
|---|---|
| Dados da conta e dados financeiros | Até você excluir a conta. A exclusão apaga tudo e revoga a autorização na Pluggy |
| Conta cujo e-mail não foi confirmado | 7 dias |
| Inscrição na lista de espera ainda não confirmada | 7 dias |
| Inscrição confirmada na lista de espera | [A DEFINIR] |
| Auditoria das chamadas ao provedor | 180 dias |
| Histórico de saldos, de investimentos e transações que seguem no seu banco | Ficam enquanto a conta existir: é o histórico que o Wallet mostra. [A DEFINIR: prazo de conta inativa] |
| Transações que o banco deixou de informar | 90 dias, depois são apagadas de vez |
| Registro das sincronizações com o banco | 90 dias (a mais recente de cada conexão fica) |
| Chaves de renovação do login (sessões) | Até 30 dias depois de vencerem ou de serem revogadas |
| Cópias de segurança | Diárias, guardadas por 14 dias. Um dado excluído pode permanecer nelas até esse prazo |
| Registros de acesso à aplicação (Marco Civil, art. 15) | 6 meses. **Ainda não implementado** |
| Registros técnicos do servidor | [A DEFINIR] |

## 9. Como protegemos seus dados

- Toda a comunicação usa HTTPS.
- Só uma porta é aberta à internet; o núcleo do sistema e o banco de dados ficam numa rede interna.
- Senhas são guardadas em hash BCrypt, nunca em texto.
- O segundo fator é opcional, e o login bloqueia, de forma progressiva, quem erra demais.
- As descrições das transações são cifradas no banco de dados.
- Fazemos cópias de segurança diárias, guardadas por 14 dias.
- O aplicativo guarda as chaves de acesso no armazenamento seguro do aparelho.

Nenhum sistema é totalmente seguro. Se acontecer um incidente que possa causar risco ou dano relevante, comunicamos você e a ANPD, nos termos da lei.

## 10. Seus direitos

A LGPD (art. 18) dá a você estes direitos. Veja como cada um funciona hoje:

| Direito | Como exercer hoje |
|---|---|
| Confirmar que tratamos seus dados e acessar | Os dados aparecem no aplicativo. Em Ajustes, "Seus dados", "Baixar meus dados" gera um arquivo com tudo o que o Wallet guarda sobre você |
| Corrigir dados | Em Ajustes (nome e e-mail) |
| Eliminar dados | "Excluir conta", em Ajustes |
| Portabilidade | O mesmo arquivo, em JSON, que você pode levar para outro lugar [A CONFIRMAR: formato] |
| Revogar o consentimento | Desvincular o banco no aplicativo; sair da lista de espera pelo link do e-mail; excluir a conta |
| Saber com quem compartilhamos | Esta política, seção 6 |

Para pedidos que não dá para fazer no aplicativo, escreva para [E-MAIL DO ENCARREGADO]. Você também pode reclamar à Autoridade Nacional de Proteção de Dados (ANPD).

## 11. Menores de idade

O Wallet é só para maiores de 18 anos. [A DEFINIR: o que acontece se descobrirmos que uma conta é de menor de idade.]

## 12. Mudanças nesta política

Quando a política mudar, publicamos a nova versão nesta página, com a data de vigência no topo. [A DEFINIR: como avisamos quem já tem conta, quando a mudança for importante.]

## 13. Contato

[NOME E E-MAIL DO ENCARREGADO]
