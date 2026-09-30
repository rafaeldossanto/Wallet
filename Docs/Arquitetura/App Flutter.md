---
tags: [arquitetura, flutter, app]
atualizado: 2026-09-29
---

# App Flutter

Um projeto para **Android, iOS e Web**. Tema escuro por padrão.

## Pacotes

| Pacote | Uso |
|---|---|
| `go_router` | Rotas e layout adaptativo (mesmo do Trilha) |
| `provider` | Estado (mesmo do Trilha) |
| `dio` | HTTP, com interceptores de token e de erro |
| `flutter_secure_storage` | Refresh token no celular |
| `local_auth` | Biometria no celular |
| `decimal` | Dinheiro sem `double` |
| `intl` + `flutter_localizations` | Moeda em R$, datas e textos em pt-BR (ARB) |
| `fl_chart` | Gráficos de patrimônio e gastos |

## Estrutura

```
app/lib/
  main.dart
  core/
    api/          ApiClient (dio), interceptores, códigos de erro → mensagem
    session/      SessionStore (mobile: secure storage · web: cookie)
    router/       go_router + shell adaptativo
    money/        Money (Decimal), formatação R$
    theme/        tema escuro
    l10n/         app_pt.arb
  features/
    auth/         login, cadastro
    overview/     visão geral (patrimônio, contas, cartões, investimentos)
    transactions/ extrato com filtros e busca
    cards/        cartões e faturas
    investments/  posições
    connections/  conexões e "vincular item" (modo Meu Pluggy)
    settings/     conta, biometria, sair
```

Cada feature tem `data/` (chamadas à API e modelos) e `presentation/` (telas e
`ChangeNotifier`).

## Layout adaptativo

| Largura | Navegação |
|---|---|
| < 600 px (celular) | Barra inferior |
| 600–839 px | Barra lateral compacta (`NavigationRail`) |
| ≥ 840 px (PC) | Menu lateral fixo e conteúdo em colunas |

As telas são as mesmas; só o shell muda. No PC a visão geral mostra contas, cartões e
investimentos lado a lado.

## Com quem o app fala

Só com o [[BFF]] (`http://localhost:8080` no dev; `http://10.0.2.2:8080` no emulador
Android). Toda chamada leva `X-Wallet-Client: mobile` ou `web`. As telas usam as rotas
agregadas do BFF: a visão geral inteira vem de uma chamada só (`GET /api/home`).

## Dinheiro

O BFF manda valor como string (`"1234.56"`). O app converte para `Decimal` e só
formata na tela (`NumberFormat.currency(locale: 'pt_BR', symbol: 'R$')`). Um
`double` no meio do caminho arredonda centavos.

## Sessão por plataforma

`SessionStore` é uma interface com duas implementações escolhidas por import
condicional:

- **Mobile:** guarda o refresh no `flutter_secure_storage`.
- **Web:** não guarda nada; o navegador manda o cookie. O `dio` roda com
  `withCredentials: true`.

O access token vive só em memória nos dois casos. Detalhes em [[Autenticação]].

## Conectar banco

- **Agora (Meu Pluggy):** a tela Conexões tem "Vincular conexão", que pede o
  `itemId` copiado do painel da Pluggy. Só aparece no modo `MEU_PLUGGY`.
- **Produção:** widget `flutter_pluggy_connect` no celular. No navegador a tela
  explica que a conexão se faz pelo celular (o SDK não tem versão web).

Ver [[Fluxo de Conexão]].

## Testes

- Unitários: `Money`, formatação, interceptor de refresh (uma renovação por vez).
- Widget: login, visão geral e extrato com o BFF simulado.
