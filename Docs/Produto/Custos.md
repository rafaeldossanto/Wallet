---
tags: [produto, custos]
atualizado: 2026-09-28
---

# Custos

## Provedores de Open Finance (caminho B)

Ver [[Caminhos de Acesso]]. Preços levantados em 2026-09-28.

### Contrato direto com instituição autorizada

| Provedor | Valor | Observação |
|---|---|---|
| Meu Pluggy | R$ 0 | Uso pessoal, sem prazo, até 5 conexões, só contas do próprio titular. **Proibido** em produto para outras pessoas |
| Pluggy Dados | a partir de R$ 2.500/mês | Mínimo com volume incluso; excedente por requisição, sem tabela pública. 15 dias grátis. Fornecedor do [[Pierre]] |
| Pluggy Pagamentos | a partir de R$ 500/mês | Só se o Wallet iniciar Pix |
| Belvo | US$ 1.000/mês | Plano Launch (página oficial). TabNews relata ~R$ 6.000/mês. Growth sob consulta. Sandbox grátis |
| Tecnospeed | R$ 1.500 + R$ 540/mês | Relato no TabNews; sem preço público. Foco em ERPs e software houses |
| Celcoin | sob consulta | Cobra por transação, sem setup alto. Foco B2B |
| Klavi, Cumbuca | sob consulta | Foco em crédito e grandes empresas |

### Revendedores (ficam entre o provedor e o Wallet)

| Revendedor | Valor | Por baixo | Observação |
|---|---|---|---|
| Polp | R$ 159 / 299 / 499 por mês + consentimentos | Celcoin | Cada conexão ativa é cobrada à parte, conforme o provedor |
| Banco MCP | R$ 19,90 (1 banco), 29,90 (3), 49,90 (5) | Pluggy | ~R$ 9 por conexão extra. Pensado para uso pessoal com IA; tem API REST |

> [!warning] Revendedor é o elo que o BC quer fechar
> A cadeia fica provedor autorizado → revendedor → Wallet. A proposta de
> [[Risco Regulatório 2026]] (1x1x1 e proibição de repasse) atinge justamente esse
> elo. Serve para validar com poucos usuários, não para fundar o produto.
>
> Por conexão, o Banco MCP só é mais barato em escala pequena: por volta de 250 a
> 280 conexões ativas ele já custa o mesmo que o mínimo da Pluggy.

## Lojas e empresa

| Item | Valor | Observação |
|---|---|---|
| Apple Developer (Organização) | US$ 99/ano | Obrigatório para a App Store |
| Google Play Console | US$ 25 uma vez | Taxa única |
| CNPJ e contador | varia | Confirmar enquadramento; nem toda atividade de software cabe no MEI |

## Ponto de equilíbrio

Com um plano de R$ 39/mês como o Pro do Pierre e 15% de comissão das lojas
(R$ 33,15 líquidos), são **cerca de 76 assinantes** para pagar os R$ 2.500 da Pluggy,
sem contar servidor e outros custos.

> [!tip] Estratégia
> Construir o Wallet inteiro em cima do **Meu Pluggy** com as próprias contas do
> Rafael. Contratar o plano pago só ao abrir para outros usuários. Até lá, o Open
> Finance custa zero.

Build de iOS a partir do Windows também tem custo; ver [[Stack]].
