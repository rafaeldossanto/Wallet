---
tags: [arquitetura, flutter, app]
atualizado: 2026-10-03
---

# App Flutter

Um projeto para **Android, iOS, Web e Windows** (app instalável, desde 2026-10-04), com **tema
claro e escuro** e visual de painel
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
| `fl_chart` | Roscas (gastos e investimentos) e linhas de patrimônio e investimentos |
| `flutter_svg` | Logos dos bancos (SVG, como a Pluggy manda) |
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

Só com o [[BFF]]: `http://localhost:8080` no navegador, no Windows e no iOS,
`http://10.0.2.2:8080` no emulador Android (HTTP liberado só no build de debug). Muda com
`--dart-define=WALLET_BFF_URL=`. Toda chamada leva `X-Wallet-Client: mobile`, `desktop` ou
`web`.

## App de Windows (desde 2026-10-04)

Pedido do Rafael: além do navegador, um app instalável como o Steam ou o Discord. É o mesmo
projeto Flutter e o mesmo código Dart; o Flutter gera o executável nativo a partir de
`app/windows/` (a casca em C++ que ele cria, como o Kotlin do Android e o Swift do iOS).

- **Sessão:** como no celular. O refresh token vai no corpo (`X-Wallet-Client: desktop`) e fica
  no cofre do Windows (`flutter_secure_storage`, cifrado com a conta do usuário).
- **Bloqueio:** depois de 5 minutos minimizado, pede o Windows Hello (PIN, rosto ou digital),
  como a digital no celular. Dá para desligar em Ajustes.
- **Janela:** abre centralizada em 1280×800 (no máximo 90% da tela), não fica menor que
  400×640, e abrir de novo pelo Menu Iniciar traz para a frente a janela que já está aberta, em
  vez de abrir outra (`windows/runner/main.cpp` e `flutter_window.cpp`).
- **Ícone:** o mesmo da carteira, num `app_icon.ico` com 8 tamanhos gerado pelo
  `tool/render_app_icon_test.dart` (de 16 a 32 px a carteira ocupa mais do quadro, para não sumir
  na barra de tarefas).
- **Instalador:** Inno Setup (`windows/installer/wallet.iss`). Instala só para o usuário, em
  `%LOCALAPPDATA%ProgramsWallet`, sem pedir administrador, como o Discord; atalho no Menu
  Iniciar e, se quiser, na área de trabalho; desinstala por Configurações → Aplicativos.
  Atualizar é rodar um instalador mais novo por cima: ele fecha o Wallet aberto e abre de novo.
  Leva junto as três DLLs do Visual C++, então não precisa instalar mais nada.
- **Onde baixar:** o workflow Desktop compila em Windows no GitHub a cada mudança no `app/` e
  anexa o `Wallet-Setup-<versão>.exe` à execução. Uma tag `v<versão>` (igual à do `pubspec.yaml`)
  também publica o instalador nos Releases do repositório.
- **Endereço do BFF:** enquanto não houver deploy, o app instalado fala com o BFF deste PC
  (`localhost:8080`), então core e BFF precisam estar rodando. Depois do deploy, a variável
  `WALLET_BFF_URL` do repositório no GitHub entra no build.
- **Sem assinatura de código:** o Windows mostra "O Windows protegeu o computador" na primeira
  execução do instalador (Mais informações → Executar assim mesmo). Assinar custa um
  certificado; fica para antes de abrir a outras pessoas.
- **Rodar aqui no PC** (`flutter run -d windows`) exige o Visual Studio (2022 ou 2026) com
  "Desenvolvimento para desktop com C++". Só o compilador dele é usado; ninguém escreve C++.
  Baixar e usar o instalador não exige nada disso.
- **Visual Studio 2026:** o plugin do Windows Hello (`local_auth_windows` 2.0.2, o mais novo)
  ainda usa `<experimental/coroutine>`, que o compilador do VS 2026 recusa (STL1011). O
  `windows/CMakeLists.txt` libera isso só para esse plugin; tirar quando ele for atualizado.
  Achado na primeira execução do workflow Desktop, em 2026-10-04.
- **Ainda não:** atualização automática como a do Discord, ícone na bandeja, abrir com o
  Windows, macOS (exige um Mac, como o iOS) e Linux.

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
- **Bancos:** o logo real que a Pluggy manda (`imageUrl` do conector, SVG no CDN dela, com CORS
  aberto) num círculo branco, desde 2026-10-03 a pedido do Rafael. Enquanto carrega, sem logo ou
  se falhar, as iniciais na cor da marca (`InstitutionColors`). Os SVGs da Pluggy pintam as
  formas por classes CSS num bloco `<style>`, que o `flutter_svg` ignora (o "nu" do Nubank sairia
  preto sobre um disco preto): o `InstitutionLogoLoader` passa essas regras para o `style` de
  cada elemento antes de desenhar.

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

## Investimentos (desde 2026-10-03)

- **Distribuição:** rosca fina por tipo (renda fixa, Tesouro, fundos, ações, previdência) com as
  pontas das fatias arredondadas, o total no furo e a legenda com a fatia e o valor de cada
  tipo. Clicar numa fatia (ou na linha dela na legenda) destaca a fatia e põe no furo o tipo, a
  porcentagem e o valor; clicar de novo, ou no furo, volta ao total (pedido do Rafael em
  2026-10-03, mesmo gesto do calendário).
  A rosca é o `DonutChart` (`core/widgets`), o mesmo dos gastos por categoria.
- **Evolução dos investimentos:** linha do total investido dia a dia, com os períodos 1M, 3M,
  6M (padrão), 1A e Tudo (até 2 anos), vindos de `GET /api/investments/history`. Acima da
  linha, quanto o total andou no período (verde subindo, vermelho caindo), calculado com
  `Money`. A variação inclui dinheiro novo aplicado: é a evolução do patrimônio investido, não
  a rentabilidade.
- A linha começa no **primeiro dia com dado**: o BFF manda zeros antes da primeira
  sincronização, e desenhá-los pareceria uma fortuna feita da noite para o dia. Com menos de
  dois dias, o cartão explica que o gráfico ganha um ponto a cada dia de atualização.
- O histórico vem das fotos diárias que a sincronização grava, então numa conta nova (ou na
  Pluggy de demonstração) só existe o dia de hoje. A Pluggy não manda histórico de
  investimentos.
- Trocar de período no meio de uma resposta descarta a resposta velha; uma sincronização
  atualiza o período na tela sem apagar a linha.
- Ordem: evolução em cima, distribuição embaixo, depois os tipos com as posições. A partir de
  900 px de conteúdo (PC, tablet deitado), duas colunas: os tipos à esquerda e os dois gráficos
  à direita (pedido do Rafael em 2026-10-03).
- Conferido em 2026-10-03 contra a demo com 400 dias de fotos inseridas no banco
  descartável: a variação de 1M, 1A e Tudo bateu centavo a centavo com as somas no banco.

## Ícone

A carteira da tela de login, branca sobre preto e inclinada 20°, em todos os ícones (web,
favicon, Android e iOS). Gerado por `tool/render_app_icon_test.dart` (fora de `test/`, para o
`flutter test` normal não rodar): desenha o glifo grande, reduz com média em luz linear (o
traço não some nos 16 px do favicon) e grava PNG RGB sem transparência, que o iOS exige. O
maskable do Android usa um desenho menor, para caber na zona segura. Para regerar:
`flutter test tool/render_app_icon_test.dart`.

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
  Pluggy. Em debug, o diálogo lembra os IDs da Pluggy de demonstração (`demo-nubank`,
  `demo-itau`, `demo-xp` e os básicos `demo-banco` e `demo-corretora`).
- **Produção:** widget `flutter_pluggy_connect` no celular (backlog).

Ver [[Fluxo de Conexão]].

## Limites conhecidos

- A biometria foi testada pela lógica (relógio controlado), não com uma digital cadastrada
  no emulador.
- O build de iOS não foi feito: exige macOS.

## Testes

63 testes com um BFF em memória (`test/support/fake_bff.dart`):

- Unitários: `Money`, sessão (restaurar, recusar, servidor fora), interceptor (duas chamadas
  com 401 geram **um** refresh), bloqueio por biometria, inatividade no navegador,
  conexões (acompanhamento e aviso às outras telas), patrimônio sem os dias zerados,
  histórico de investimentos (começo no primeiro dia com dado, perda, período abandonado
  descartado, atualização que falha mantendo a linha), estilos CSS dos logos passados para os
  elementos, o app de PC se identificando como `desktop`.
- Widget: login, visão geral (completa, com parte indisponível, sem conexões), shell nos três
  tamanhos e o "Mais", calendário (mês inteiro, dia, tocar de novo), extrato (agrupamento por
  dia, filtros, segunda página), investimentos (rosca, clique na fatia e na legenda, troca de
  período, menos de dois dias, tipos à esquerda na tela larga), rosca dos gastos (fina, clicável),
  saída voluntária.

Além disso, verificado à mão em 2026-10-02 no Chrome (build release) e no emulador
`trilha_pixel`, contra o core e o BFF reais com a Pluggy de demonstração.

Ver [[Visão Geral]].
