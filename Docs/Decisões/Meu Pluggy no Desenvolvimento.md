---
tags: [decisao, open-finance, pluggy]
atualizado: 2026-09-28
status: decidido
---

# Meu Pluggy no Desenvolvimento

**Decidido pelo Rafael em 2026-09-28:** desenvolvimento e testes do Wallet usam o
**Meu Pluggy**, com as contas dele.

## Por quê

- Custa zero e não expira. O plano Dados da Pluggy começa em R$ 2.500/mês
  ([[Custos]]), um gasto que só faz sentido quando houver usuários.
- Entrega Client ID e Client Secret da mesma `api.pluggy.ai` do plano pago. O código
  de leitura (contas, transações, cartões, investimentos) escrito agora vale em
  produção.
- Dados reais, dos bancos de verdade, em vez de sandbox com dados inventados.

## Limites que moldam o desenvolvimento

> [!warning] O widget de conexão não está no plano grátis
> No Meu Pluggy as contas são conectadas **dentro do próprio Meu Pluggy** e depois
> vinculadas à aplicação no Dashboard. O widget Pluggy Connect embutido no app, que é
> a tela "Conectar banco" do [[Fluxo de Conexão]], só existe no plano pago.
>
> Consequência: construir primeiro a leitura e as telas de dados; deixar a tela de
> conexão para o fim e testá-la nos 15 dias grátis do plano Dados.

- **Até 5 conexões**, todas do mesmo titular.
- **Atualização automática a cada 24 horas.** Sincronização em tempo real não é
  testável aqui.
- **Uso pessoal apenas.** Nenhuma outra pessoa pode conectar conta. Testar com
  amigos ou beta fechado já exige o plano pago.

## Efeito no código

O provedor fica atrás de uma interface no [[Core]] (ver [[Stack]] e
[[Risco Regulatório 2026]]). Com o Meu Pluggy a implementação é a mesma da Pluggy
paga; muda só a origem das conexões e a disponibilidade de webhooks, que não é
garantida no plano grátis.
