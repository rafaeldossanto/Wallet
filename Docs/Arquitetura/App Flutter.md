---
tags: [arquitetura, flutter, app]
atualizado: 2026-10-03
---

# App Flutter

Um projeto para **Android, iOS e Web**, com **tema claro e escuro** e visual de painel
(redesenho pedido pelo Rafael em 2026-10-03, a partir de duas referências de dashboard).
Construído em 2026-10-02 (T12 a T18 do [[Plano de Implementação]]), em `Work/Wallet/app`.

## Pacotes

| Pacote | Uso |
|---|---|
| `material_ui` | O Material. No Flutter 3.47 ele saiu do framework e virou pacote; o `go_router` 18 já usa |
| `go_router` | Rotas, `StatefulShellRoute` (cada aba guarda o próprio estado) |
| `provider` | Estado (`ChangeNotifier`) |
| `dio` | HTTP, com o interceptor de token |
| `flutter_secure_storage` | Refresh token no celular e a preferência de biometria |
| `local_auth` | Biometria no celular |
| `decimal` | Dinheiro sem `double` |
| `intl` + ARB | Moeda, datas e textos em pt-BR |
| `fl_chart` | Rosca de gastos e linhas de patrimônio e investimentos |
| `shared_preferences` | A escolha de tema (`SharedPreferencesAsync`, a API atual) |

O `flutter_localizations` fica só porque o `gen-l10n` exige; os delegates usados vêm do
`material_ui` (`GlobalMaterialLocalizations.delegates`). O `fl_chart` ainda importa o
Material antigo, mas os gráficos usados (rosca e linha) não leem o tema dele, então a ponte
de compatibilidade (já depreciada) não foi necessária.

## Estrutura

```
app/lib/
  main.dart, app.dart     composição: dependências, tema, rotas, bloqueio por plataforma
  core/
    api/          ApiClient (dio), ApiException, AuthInterceptor, Json, adaptador web/io
    session/      SessionApi, SessionController, SessionStore (celular · web)
    security/     AppLock (biometria), LockScreen, IdleTimeout (web)
    router/       rotas e shell adaptativo
    state/        Loadable, LoadController, DataChanges
    models/       Account, Transaction, CreditCard, Bill
    money/        Money (Decimal) e formatação R$
    format/       datas, porcentagem, categorias da Pluggy em pt-BR
    theme/        temas claro e escuro, ThemeController, cores com significado
    l10n/         app_pt.arb e as classes geradas
    widgets/      LoadableView, SectionCard, StatTile, MoneyText, MoneyLineChart, ThemeToggle...
  features/
    auth/  overview/  calendar/  transactions/  cards/  investments/  insights/  connections/  settings/
```

Cada feature tem `data/` (chamadas ao BFF e modelos) e `presentation/` (telas e
controllers).

## Navegação

| Largura | Navegação |
|---|---|
| < 600 px (celular) | Barra inferior: Início, Extrato, Cartões, Investir e **Mais** (Gastos, Conexões, Ajustes) |
| ≥ 600 px | Trilho flutuante de botões redondos (`WalletRail`): a marca, os destinos com dica ao passar o mouse, a troca de tema e as iniciais do usuário (vão para Ajustes) |

Rotas: `/home`, `/transactions?accountId=`, `/cards`, `/cards/:id?name=` (faturas),
`/investments`, `/insights`, `/connections`, `/settings`, além de `/splash`, `/login` e
`/register`. Ninguém vê dado antes de a sessão ser confirmada; um link aberto deslogado volta
depois do login (`?from=`), menos quando a pessoa saiu de propósito.

## Com quem o app fala

Só com o [[BFF]]: `http://localhost:8080` no navegador e no iOS,
`http://10.0.2.2:8080` no emulador Android (HTTP liberado só no build de debug). Muda com
`--dart-define=WALLET_BFF_URL=`. Toda chamada leva `X-Wallet-Client: mobile` ou `web`.

## Tema

Claro e escuro; escuro por padrão. Troca no botão de sol/lua (no trilho, ou no topo da
visão geral no celular) ou em Ajustes → Aparência (Claro, Escuro, Automático). A escolha é
lida antes do primeiro quadro, então o app nunca abre no tema errado.

- **Escuro:** fundo quase preto (`#0B0B0C`), cartões em cinza escuro.
- **Claro:** fundo cinza suave (`#EEF0F4`), cartões brancos com borda fina.
- **Nos dois:** azul elétrico (`#3D5AFE`) nas ações e no cartão que puxa a tela (o
  calendário); verde-limão (`#D4F34A`) no que está selecionado (destino, dia); cartões com
  cantos de 24 px. Fora isso, cor só com significado: verde para dinheiro entrando, amarelo
  para aviso, vermelho para erro e as fatias dos gráficos.
- **Bancos:** sem logo (a Pluggy manda SVG), cada instituição aparece com as iniciais na cor
  da marca (Nubank roxo, Itaú laranja, XP preto e amarelo...; `InstitutionColors`).

## Visão geral e calendário de gastos

- Cabeçalho com "Olá, Rafael!" e a data por extenso.
- Patrimônio e mês em blocos (`StatTile`) com ícone colorido.
- **Calendário de gastos** (cartão azul): os dias do mês mais claros quanto mais se gastou.
  Sem dia selecionado, a lista ao lado mostra os gastos do **mês inteiro** (com o dia e o banco
  de cada um, paginada com "Ver mais"); tocar num dia mostra só aquele dia; tocar de novo no
  mesmo dia volta ao mês. O total vem do calendário, então lista e calendário batem.
- Arranjo: duas colunas a partir de 1000 px de conteúdo (patrimônio, mês, contas e últimas
  movimentações à esquerda; calendário, lista e cartões à direita); entre 720 e 1000 px,
  calendário e lista lado a lado; uma coluna no celular.

## Dinheiro

O BFF manda valor como texto (`"1234.56"`) e ele vira `Decimal`. A formatação
(`R$ 1.234,56`, `-R$ 12,50`, `+R$ 50,00`) é feita à mão sobre o `Decimal`, sem passar por
`double`. Só os gráficos convertem, e só para a geometria. Entrada em verde com `+`; saída na
cor normal do texto com `-`, porque vermelho faria toda compra parecer problema.

## Sessão por plataforma

- O access token vive só em memória.
- **Celular:** refresh token no `flutter_secure_storage`. Abrir o app troca o refresh guardado
  por uma sessão nova sem pedir senha.
- **Navegador:** nada guardado; o cookie `HttpOnly` vai junto (`withCredentials`).
- Um 401 renova a sessão **uma vez só**, mesmo com várias telas falhando juntas (o core
  rotaciona o refresh e trata um segundo uso como roubo), e repete a chamada.
- Restaurar sem rede, com 429 ou 5xx não apaga o token guardado: a tela inicial oferece
  tentar de novo.
- Celular: depois de 5 min fora do app, pede digital, rosto ou senha do aparelho (dá para
  desligar nos Ajustes). Navegador: a sessão acaba após 30 min sem clique, rolagem ou tecla.

Detalhes em [[Autenticação]].

## Dados que mudam por baixo

A sincronização roda no fundo do core. A tela de Conexões consulta a lista a cada 3 s
enquanto alguma conexão sincroniza (e por 30 s depois de vincular ou pedir atualização) e,
quando o `lastSyncedAt` muda, avisa as outras telas (`DataChanges`), que se atualizam
sozinhas. O BFF usa a mesma consulta para descartar o cache das telas daquele usuário.

## Conectar banco

- **Agora (Meu Pluggy):** Conexões → "Vincular conexão" pede o Item ID copiado do painel da
  Pluggy. Em debug, o diálogo lembra os IDs da Pluggy de demonstração (`demo-banco`,
  `demo-corretora`).
- **Produção:** widget `flutter_pluggy_connect` no celular (backlog).

Ver [[Fluxo de Conexão]].

## Limites conhecidos

- Logo de instituição em SVG (o formato que a Pluggy usa) não é desenhado: aparece a inicial.
  Precisa do `flutter_svg` quando houver dados reais.
- A biometria foi testada pela lógica (relógio controlado), não com uma digital cadastrada
  no emulador.
- O build de iOS não foi feito: exige macOS.

## Testes

40 testes com um BFF em memória (`test/support/fake_bff.dart`):

- Unitários: `Money`, sessão (restaurar, recusar, servidor fora), interceptor (duas chamadas
  com 401 geram **um** refresh), bloqueio por biometria, inatividade no navegador,
  conexões (acompanhamento e aviso às outras telas), patrimônio sem os dias zerados.
- Widget: login, visão geral (completa, com parte indisponível, sem conexões), shell nos três
  tamanhos e o "Mais", extrato (agrupamento por dia, filtros, segunda página), saída
  voluntária.

Além disso, verificado à mão em 2026-10-02 no Chrome (build release) e no emulador
`trilha_pixel`, contra o core e o BFF reais com a Pluggy de demonstração.

Ver [[Visão Geral]].
