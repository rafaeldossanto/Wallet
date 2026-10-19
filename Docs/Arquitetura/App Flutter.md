---
tags: [arquitetura, flutter, app]
atualizado: 2026-10-18
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
| `window_manager`, `screen_retriever` | Janela do Windows: tamanho, centro, esconder na bandeja |
| `tray_manager` | Ícone e menu na bandeja do Windows |
| `win32_registry` | "Abrir com o Windows" |
| `crypto` | SHA-256 do instalador (ou do APK) baixado pelo atualizador |
| `path_provider` | A pasta privada do app no Android, onde o atualizador guarda o APK |
| `shared_preferences` | A escolha de tema (`SharedPreferencesAsync`, a API atual) |
| `webview_flutter` | O widget Pluggy Connect no Android ("Conectar banco") |
| `url_launcher` | Abrir a autorização do Open Finance no app do banco (ou no navegador) |

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
    desktop/      só o Windows: janela, bandeja, "Abrir com o Windows", Inno Setup
    android/      só o Android: o atualizador e o canal para o instalador do sistema
    updates/      o atualizador dos dois (Updater, manifestos, download com SHA-256, avisos)
    router/       rotas e shell adaptativo
    state/        Loadable, LoadController, DataChanges
    models/       Account, Transaction, CreditCard, Bill
    money/        Money (Decimal) e formatação R$
    format/       datas, porcentagem, categorias da Pluggy em pt-BR
    theme/        temas claro e escuro, ThemeController, cores com significado
    l10n/         app_pt.arb e as classes geradas
    widgets/      LoadableView, SectionCard, StatTile, MoneyText, MoneyLineChart, ThemeToggle...
  features/
    auth/  overview/  calendar/  transactions/  cards/  investments/  planning/  insights/  connections/  settings/
```

Cada feature tem `data/` (chamadas ao BFF e modelos) e `presentation/` (telas e
controllers).

## Navegação

| Largura | Navegação |
|---|---|
| < 600 px (celular) | Barra inferior: Início, Extrato, Cartões, Investir e **Mais** (Planejamento, Gastos, Conexões, Ajustes) |
| ≥ 600 px | Trilho flutuante de botões redondos (`WalletRail`): a marca, os destinos com dica ao passar o mouse, a troca de tema e as iniciais do usuário (vão para Ajustes) |

Rotas: `/home`, `/transactions?accountId=`, `/cards`, `/cards/:id?name=` (faturas),
`/investments`, `/planning`, `/insights`, `/connections`, `/settings`, além de `/splash`, `/login` e
`/register`. Ninguém vê dado antes de a sessão ser confirmada; um link aberto deslogado volta
depois do login (`?from=`), menos quando a pessoa saiu de propósito.

## Com quem o app fala

Só com o [[BFF]]: `http://localhost:8080` no navegador, no Windows e no iOS,
`http://10.0.2.2:8080` no emulador Android. Muda com `--dart-define=WALLET_BFF_URL=`, e no
celular e no tablet pelo endereço digitado na tela de login. O Android aceita HTTP em todos os
builds até o deploy com HTTPS (ver "App no tablet Android"). Toda chamada leva `X-Wallet-Client: mobile`, `desktop` ou
`web`.

## App de Windows (desde 2026-10-04)

Pedido do Rafael: além do navegador, um app instalável como o Steam ou o Discord. É o mesmo
projeto Flutter e o mesmo código Dart; o Flutter gera o executável nativo a partir de
`app/windows/` (a casca em C++ que ele cria, como o Kotlin do Android e o Swift do iOS).

- **Sessão:** como no celular. O refresh token vai no corpo (`X-Wallet-Client: desktop`) e fica
  no cofre do Windows (`flutter_secure_storage`, cifrado com a conta do usuário).
- **Bloqueio:** depois de 5 minutos minimizado, pede o Windows Hello (PIN, rosto ou digital),
  como a digital no celular. Dá para desligar em Ajustes.
- **Janela:** abre centralizada em 1280×800 (no máximo 90% da tela) e não fica menor que
  400×640, pelo `window_manager`, em Dart (`lib/core/desktop/desktop_start_io.dart`). Abrir de novo
  pelo Menu Iniciar traz para a frente a janela que já está aberta (ou escondida na bandeja), em
  vez de abrir outra (`windows/runner/main.cpp`).
- **Visual de vidro, como no macOS (desde 2026-10-05, pedido do Rafael):** janela sem a moldura do
  Windows, cantos arredondados (12 px) e, à esquerda, os três botões do Mac (vermelho fecha,
  amarelo minimiza, verde maximiza; os símbolos aparecem com o mouse em cima). A barra de cima
  arrasta a janela, duplo clique maximiza, e as bordas redimensionam. Tudo isso é desenhado em
  Flutter (`lib/core/desktop/window_frame.dart`). Maximizada, os cantos ficam retos.
  - **Fundo:** Ajustes → Computador → "Fundo da janela": **Translúcido** (padrão) deixa ver,
    desfocado, o que está atrás; **Sólido** é o fundo normal. No translúcido, as telas não pintam
    fundo e os cartões ficam 78% opacos, então o texto continua nítido sobre o desfoque.
  - **Parte nativa** (`windows/runner/window_style.cpp`, chamada pelo canal `wallet/window_style`):
    no Windows 11 22H2 o desfoque é o acrílico do próprio sistema; no Windows 10 é o desfoque do
    `SetWindowCompositionAttribute`. O Windows 11 arredonda a janela sozinho; no Windows 10 o
    recorte arredondado é refeito a cada `WM_SIZE`, sem atraso ao redimensionar.
- **Bandeja (desde 2026-10-05):** ícone perto do relógio (`tray_manager`); clique abre a janela,
  o botão direito mostra "Abrir o Wallet" e "Sair". O X da janela esconde o Wallet na bandeja,
  como no Discord; dá para desligar em Ajustes → Computador ("Ao fechar, continuar na bandeja").
- **Abrir com o Windows:** opção em Ajustes → Computador, desligada até a pessoa ligar. Grava
  `HKEY_CURRENT_USER\...\Run\Wallet` com `--hidden`, então o Wallet já começa na bandeja. O
  desinstalador apaga a entrada.
- **Atualização automática (desde 2026-10-05):** como a do Discord. O app procura a versão nova nos
  Releases do GitHub ao abrir e a cada 6 horas, baixa o instalador em segundo plano para
  `%LOCALAPPDATA%\Wallet\updates` e só o aceita se o SHA-256 bater.
  - **Onde ele lê (desde a 0.1.2):** o `latest.json` que o workflow publica em cada Release
    (`{version, installer, sha256}`), pelo link `releases/latest/download/latest.json`. Esse link
    não conta no limite da API do GitHub (60 consultas por hora por conexão, sem login), que
    estourou em 2026-10-05 de tanto acompanhar builds. A API ficou só de reserva, para Releases sem
    manifesto.
  - **Quando falha** (sem internet, GitHub recusando), tenta de novo em 30 min, depois 1 h, 2 h...
    até voltar às 6 h quando der certo.
  - Escondido na bandeja: instala sozinho (Inno Setup em `/VERYSILENT`) e volta para a bandeja.
  - Com a janela aberta: um aviso no canto, como o do Claude Desktop, diz "Nova versão disponível"
    com Reiniciar ou Depois. O botão já instala e reabre; ninguém precisa entrar no GitHub.
    "Depois" instala quando a pessoa sair pelo menu da bandeja, ou na próxima vez em que estiver
    na bandeja.
  - Depois de atualizar, o instalador reabre o app com `--updated`, e o mesmo canto avisa "Wallet
    atualizado" com a versão nova (também quando a atualização aconteceu na bandeja).
  - Ajustes → Computador mostra a versão e "Procurar agora". Um build de desenvolvimento (sem
    `--dart-define=WALLET_VERSION`) nunca se atualiza.
  - O instalador recebe `/RELAUNCH=open|tray|none` e decide se reabre o app. Sem assinatura de
    código não há aviso do SmartScreen na atualização, porque o arquivo não vem do navegador.
  - Desde 2026-10-18 o atualizador fica em `lib/core/updates/`, dividido com o Android; o que é
    só do Windows (Inno Setup, janela, bandeja) continua em `lib/core/desktop/`.
- **Ícone:** os cartões empilhados (ver "Ícone" abaixo), num `app_icon.ico` com 8 tamanhos e cantos
  arredondados, também usado na bandeja.
- **Instalador:** Inno Setup (`windows/installer/wallet.iss`). Instala só para o usuário, em
  `%LOCALAPPDATA%\Programs\Wallet`, sem pedir administrador, como o Discord; atalho no Menu
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
- **Ainda não:** assinatura de código, macOS (exige um Mac, como o iOS) e Linux.
- **Não testado aqui:** janela, bandeja, Windows Hello e a atualização de verdade só rodam no
  Windows instalado; este PC não tem Visual Studio. O que é lógica (quando atualizar, a
  conferência do SHA-256, os Ajustes) tem teste; o resto é conferido pelo Rafael no app instalado.

## App no iPhone (desde 2026-10-18)

Pedido do Rafael, que tem um iPhone 11 e não quer pagar o Apple Developer Program por enquanto.

- O workflow **iOS** compila num Mac do GitHub um `.ipa` **sem assinatura**. Quem assina é o
  AltStore (ou o Sideloadly) na instalação, com um Apple ID gratuito; a assinatura vale 7 dias e o
  AltStore renova sozinho quando o iPhone e o PC com o AltServer estão na mesma Wi-Fi.
- Cada versão publica junto um `altstore.json` (fonte do AltStore), lido pelo link
  `releases/latest/download/altstore.json`: o Wallet aparece no AltStore para instalar e
  atualizar pelo celular. As permissões listadas ali precisam bater com o `Info.plist`, senão o
  AltStore recusa; o workflow tira os textos do próprio `Info.plist`.
- **Servidor:** no celular, `localhost` é o próprio celular. A tela de login mostra "Servidor: … ·
  Alterar", onde se digita o endereço do PC na Wi-Fi (`192.168.0.10` vira
  `http://192.168.0.10:8080`). Fica salvo no aparelho e vale já na próxima requisição
  (`lib/core/config/server_address.dart`). No navegador não aparece.
- `NSLocalNetworkUsageDescription` no `Info.plist`: sem ele o iOS 14+ bloqueia o acesso ao PC na
  rede local. O HTTP para a rede local já era liberado (`NSAllowsLocalNetworking`).
- Passo a passo para instalar no README ("App no iPhone").

## App no tablet Android (desde 2026-10-18)

Pedido do Rafael, para o tablet Android dele. Sem Play Store: um APK instalado à mão.

- O workflow **Android** compila em Linux (JDK 21) um `Wallet-<versão>.apk` e o anexa à execução;
  numa tag `v<versão>`, espera o Release que o Desktop cria e anexa o APK a ele.
- **Assinatura:** o Android só instala uma versão nova por cima se as duas tiverem a mesma chave.
  O modelo do Flutter assinava o release com a chave de debug, que muda de máquina para máquina.
  Agora há uma keystore própria (RSA 4096, alias `wallet`), gerada em 2026-10-18 e guardada fora do
  git em `%USERPROFILE%\.wallet\android` com o `key.properties`. O `android/app/build.gradle.kts`
  lê a chave de `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e
  `ANDROID_KEY_PASSWORD`, ou de um `key.properties` apontado por `ANDROID_KEY_PROPERTIES`; sem
  nenhum dos dois, cai na chave de debug. No CI, os quatro segredos `ANDROID_*` do repositório
  (a keystore vai em base64); faltando, o APK sai com a de debug e a execução avisa.
- **HTTP:** o tablet fala com o BFF no PC pela Wi-Fi, em `http://192.168.x.x:8080`. O
  `res/xml/network_security_config.xml` libera HTTP para qualquer endereço, porque o da rede local
  não se sabe na hora do build e o arquivo não aceita faixa de IP. Antes só o build de debug
  (emulador) aceitava HTTP. Com o deploy em HTTPS, volta a ser só HTTPS.
- O endereço do PC se digita na tela de login, como no iPhone. O firewall do Windows precisa
  deixar o Java receber conexões na rede privada.
- **Atualização pelo próprio app (desde 2026-10-18, a partir da 0.1.4):** pedido do Rafael, para
  as versões novas chegarem ao tablet com um toque, como no Windows, sem entrar no GitHub. É o
  mesmo `Updater` do Windows (ao abrir e a cada 6 horas, mais cedo depois de falha), com outras
  peças (`lib/core/android/`):
  - **Onde lê:** o `latest-android.json` (`{version, apk, sha256}`) que o workflow Android publica
    junto do APK, pelo link `releases/latest/download/latest-android.json`. Nunca a API do GitHub
    (60 consultas por hora sem login). O `latest.json` do Windows continua só do Desktop. Numa
    tag, o APK sobe antes do manifesto, para ninguém ler um manifesto sem o APK.
  - **Download:** em segundo plano, para a pasta privada do app (`path_provider`, `files/updates`),
    e só fica se o SHA-256 bater. O APK de uma versão já instalada é apagado na primeira vez que
    ela abre.
  - **Instalação, nunca sozinha:** o aviso do canto diz "Nova versão disponível" com **Atualizar**,
    que entrega o APK ao instalador do Android pela API de sessão do `PackageInstaller`
    (`android/app/src/main/kotlin/com/wallet/wallet/UpdateInstaller.kt`, canal
    `wallet/package_installer`); o `ACTION_INSTALL_PACKAGE` está depreciado. O APK é copiado para a
    sessão, então nenhum arquivo é compartilhado com outro app (sem `FileProvider`). No Android 12+
    a sessão exige a confirmação do usuário (`USER_ACTION_REQUIRED`), mesmo podendo dispensá-la.
  - **Permissão:** o manifesto declara `REQUEST_INSTALL_PACKAGES`. Na primeira vez o Android não
    deixa o Wallet instalar apps: o aviso explica e **Abrir configurações** leva direto a Instalar
    apps desconhecidos → Wallet ("Permitir desta fonte"); de volta ao app, a confirmação do
    Android aparece sozinha.
  - **Depois:** o Android fecha o Wallet para trocar a versão e não o reabre. Na abertura seguinte o
    app compara com a versão que rodou da última vez (guardada nas preferências) e avisa "Wallet
    atualizado".
  - **Recusas:** se o usuário cancela, a atualização continua esperando. Se o APK tem outra
    assinatura (um APK de debug instalado, ou um Release gerado sem os segredos), o Android recusa
    e o aviso diz para desinstalar e instalar o dos Releases. Outra falha oferece "Tentar de novo".
  - Ajustes → Atualizações mostra a versão, o estado e "Procurar agora". Um APK sem
    `--dart-define=WALLET_VERSION` nunca se atualiza.
  - Verificado no emulador `trilha_pixel` (Android 15), com um app de teste chamando o canal: sem a
    permissão vem a recusa; "Abrir configurações" cai direto em Instalar apps desconhecidos →
    Wallet e volta com a resposta, sem reiniciar o app; a confirmação do Android aparece;
    Cancelar devolve a atualização à espera; Atualizar instala a versão nova por cima, e o Android
    fecha o app sem reabrir. O Wallet passa a constar como instalador dele mesmo.
- Passo a passo para instalar e os segredos no README ("App no tablet Android").

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
- Arranjo: duas colunas a partir de 1000 px de conteúdo (patrimônio, mês, sobra do mês, contas e
  últimas movimentações à esquerda; calendário, lista e cartões à direita); entre 720 e 1000 px,
  calendário e lista lado a lado; uma coluna no celular.

## Planejamento (desde 2026-10-18)

Quanto sobra do salário no mês, pedido do Rafael depois de fazer a conta à mão com o holerite e
as faturas. Aba **Planejamento** (no trilho e no "Mais" do celular), com o seletor de mês (até o
mês que vem; depois disso não há fatura para mostrar). As regras estão no [[Core#Planejamento do mês (desde 2026-10-18)]];
o app não faz conta com dinheiro, só lê os totais.

- **Resumo** (cartão azul, como o que abre a visão geral): a sobra em destaque e uma barra
  empilhada com o salário dividido entre cada fatura, os gastos fixos e a sobra (verde-limão),
  com a legenda. O cartão aparece pelo nome do banco ("Nubank"), ou pelo do cartão quando o banco
  tem dois. Mês no vermelho: a barra divide o que sai e o cartão diz quanto falta.
- **Salário líquido:** o valor, o selo **Previsto** quando ainda não caiu, e de onde veio (caiu em
  tal dia, previsto pelo mês anterior, valor que você definiu). **Ajustar** abre o salário esperado
  (digitado como se escreve aqui: `3.400,00`), e **Usar o automático** volta à previsão.
- **Uma fatura por cartão:** logo e nome do banco, vencimento, total do banco, as compras da maior
  para a menor com o dia, a categoria, a parcela ("Parcela 2/3") e "Estorno" em verde; quatro de
  cada vez e **mais N compras** para ver o resto. "Outros lançamentos" mostra o que o banco cobra
  além das compras listadas. Fatura que ainda não fechou leva o selo **Aberta**, com o dia em que
  deve fechar.
- **Gastos fixos:** a lista com o dia do mês e a categoria, o total, **Adicionar** e, tocando num,
  editar ou excluir (com confirmação). A categoria é opcional e sai de uma lista curta de
  categorias da Pluggy (aluguel, moradia, faculdade, contas da casa, plano de saúde...).
- **Sugestões do seu extrato** (desde 2026-10-18), no fim do cartão de gastos fixos: os débitos que
  se repetem todo mês nas contas e ainda não são gastos fixos ([[Core#Gastos fixos sugeridos pelo extrato (desde 2026-10-18)]]),
  cada um com "R$ 690,00 · todo dia 9 · em 4 meses". Um toque (ou o **+**) abre o formulário de
  gasto fixo já preenchido com nome, valor, dia e categoria, que dá para mudar antes de salvar; o
  **×** dispensa para sempre ("Sugestão dispensada. Ela não volta a aparecer."). Três de cada vez
  e **mais N sugestões**. Carregando, sem nenhuma ou com erro, a parte simplesmente não aparece.
  Um `FixedExpenseSuggestionsController` próprio, só na tela de planejamento (a visão geral não
  busca sugestões); salvar um gasto fixo toca o `DataChanges`, que recarrega as sugestões e tira
  a que acabou de virar gasto fixo. Dispensar não mexe no plano.
- **Visão geral:** o cartão **Sobra do mês** com a sobra do mês corrente, a mesma barra, a linha
  "Salário · faturas · fixos" e "Ver planejamento".
- Mudar um gasto fixo ou o salário toca o `DataChanges`, e a tela e o cartão da visão geral
  buscam os números novos (o BFF já descartou o cache do planejamento).
- A partir de 900 px de conteúdo, salário e gastos fixos à esquerda e as faturas à direita.

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

**Cartões empilhados** (escolha do Rafael em 2026-10-12, entre seis opções): três cartões em leque
sobre o preto do app, verde-limão atrás, azul no meio e branco com chip na frente, que são os
bancos e cartões dele juntos num lugar só. Antes era a carteira da tela de login, branca e
inclinada.

Gerado por `tool/render_app_icon_test.dart` (fora de `test/`, para o `flutter test` normal não
rodar), que desenha em Canvas e reduz com média em luz linear, cor por cor:
- iOS, Android e ícones web: quadrado cheio, que o sistema arredonda; PNG RGB sem transparência,
  como o iOS exige.
- Windows (`.ico` do executável e da bandeja) e favicon: bloco com cantos arredondados e
  transparente fora deles, como um app de Mac.
- De 16 a 32 px os cartões ficam 18% maiores, para os três continuarem separados; o maskable
  usa 90%, para caber no círculo de 40%. Para regerar:
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

## Excluir conta (desde 2026-10-19)

Ajustes → **Zona de perigo** (cartão com a borda e o título na cor de erro) → **Excluir conta**
abre um diálogo (`DeleteAccountDialog`) que diz o que vai embora, inclusive que as conexões são
revogadas na Pluggy, e pede duas coisas: marcar "Entendo que não dá para desfazer" e digitar a
senha de novo. O botão **Excluir de vez** só se habilita com a caixa marcada.

- **Contrato:** `DELETE /api/me` com `{"password"}` (`SessionApi.deleteAccount`). Ver [[BFF]] e
  [[Modelo de Dados#Exclusão]].
- **Senha vazia** (ou só espaços) é barrada no app e nem é enviada; o core responderia
  `validation.failed`.
- **Pode demorar:** o core revoga cada banco na Pluggy antes de responder, e o BFF espera até
  2 min (`wallet.core.slow-read-timeout`). Essa chamada tem `receiveTimeout` de 2 min 30 s, para o
  app ouvir a resposta do BFF em vez de desistir antes. Enquanto isso o diálogo mostra uma barra de
  progresso e o aviso, e não aceita segundo toque, Cancelar nem o gesto de voltar.
- **Respostas:**
  - 204: `SessionController.deleteAccount` apaga o refresh token do `flutter_secure_storage` e o
    access token da memória, e o login abre com "Sua conta foi excluída".
  - 403 `auth.wrong_password`: o erro aparece no campo, que é limpo, e a sessão continua.
    O texto avisa que cada erro conta para o bloqueio.
  - 423 `auth.locked`: "Tente de novo às 17:15", pelo `lockedUntil` do erro (a data entra quando
    o bloqueio acaba em outro dia). Sem o campo, a frase dos 15 minutos.
  - 5xx (como 503 `bff.core_unavailable`): a frase do erro mais "Nada foi apagado; você pode
    tentar de novo". Excluir duas vezes dá 204, então tentar de novo é seguro.
  - Sem rede ou timeout: as frases de sempre do `errorMessage`.
- O `errorMessage` (`l10n.dart`) ganhou `auth.wrong_password` e o horário no `auth.locked`, que
  vale também para o login.

## Dados que mudam por baixo

A sincronização roda no fundo do core. A tela de Conexões consulta a lista a cada 3 s
enquanto alguma conexão sincroniza (e por 30 s depois de vincular ou pedir atualização) e,
quando o `lastSyncedAt` muda, avisa as outras telas (`DataChanges`), que se atualizam
sozinhas. O BFF usa a mesma consulta para descartar o cache das telas daquele usuário.

## Conectar banco

- **Android (desde 2026-10-18, pedido do Rafael):** Conexões → **Conectar banco** abre o widget
  Pluggy Connect em tela cheia (uma WebView, `pluggy_connect_screen.dart`). A pessoa escolhe o
  banco, a autorização do Open Finance abre no **app do banco** (onde já está logada), e o banco
  devolve para o Wallet pelo deep link `com.wallet.wallet://pluggy-connect`. Quando os dados
  chegam, o app vincula o item e a lista acompanha a primeira sincronização. O tema do widget
  segue o do app. Detalhes, protocolo e por que não o pacote `flutter_pluggy_connect` em
  [[Fluxo de Conexão]].
- **Item ID (Meu Pluggy e a demo):** "Vincular conexão" pede o Item ID copiado do painel da
  Pluggy; no Android fica no ícone de link no topo de Conexões ("Vincular pelo Item ID"), e é o
  único caminho no iOS (pausado), no PC e no navegador. Em debug, o diálogo lembra os IDs da
  Pluggy de demonstração (`demo-nubank`, `demo-itau`, `demo-xp` e os básicos `demo-banco` e
  `demo-corretora`).

Ver [[Fluxo de Conexão]].

## Limites conhecidos

- A biometria foi testada pela lógica (relógio controlado), não com uma digital cadastrada
  no emulador.
- O build de iOS não foi feito: exige macOS.

## Testes

157 testes com um BFF em memória (`test/support/fake_bff.dart`):

- Unitários: `Money` (inclusive o valor digitado num formulário, `1.234,56`), sessão (restaurar, recusar, servidor fora), interceptor (duas chamadas
  com 401 geram **um** refresh), bloqueio por biometria, inatividade no navegador,
  conexões (acompanhamento e aviso às outras telas), patrimônio sem os dias zerados,
  histórico de investimentos (começo no primeiro dia com dado, perda, período abandonado
  descartado, atualização que falha mantendo a linha), estilos CSS dos logos passados para os
  elementos, o app de PC se identificando como `desktop`, o atualizador (quando baixa, quando
  instala e como reabre; manifesto `latest.json` com a API de reserva; novas tentativas mais cedo
  depois de falha; SHA-256 que não bate), o do Android (`latest-android.json` sem a API,
  download conferido e entregue ao instalador só no toque, a permissão da primeira vez, recusa
  por outra chave, o canal com o lado nativo, a versão lembrada para o aviso de atualizado), o
  protocolo do Pluggy Connect (URL, mensagens, evento repetido, quando um item já vale vincular).
- Widget: login, visão geral (completa, com parte indisponível, sem conexões), shell nos três
  tamanhos e o "Mais", calendário (mês inteiro, dia, tocar de novo), extrato (agrupamento por
  dia, filtros, segunda página), investimentos (rosca, clique na fatia e na legenda, troca de
  período, menos de dois dias, tipos à esquerda na tela larga), rosca dos gastos (fina, clicável),
  Ajustes → Computador, Ajustes → Atualizações no Android (sem os ajustes da janela), os avisos
  de versão nova e de atualizado (Reiniciar no Windows, Atualizar e a permissão no Android), a
  moldura da janela (os três
  botões, arrastar, redimensionar, cantos retos maximizada, vidro que segue o tema),
  saída voluntária, o Pluggy Connect com uma página falsa no lugar da WebView
  (`test/support/fake_pluggy_connect.dart`: token, abrir o banco e os links, sucesso, fechar antes
  e depois de autorizar, falhas) e "Conectar banco" vinculando o item, o planejamento (sobra,
  barra e legenda, salário recebido, previsto, definido à mão ou ausente, mês no vermelho, fatura
  aberta, outros lançamentos, "mais N compras", Ajustar e Usar o automático, gasto fixo criado,
  editado, excluído com confirmação e recusado pelo servidor, mês anterior e limite do próximo,
  duas colunas na tela larga, sugestões do extrato: lista, formulário preenchido, valores mudados
  antes de salvar, dispensa e dispensa recusada, "mais N sugestões", nenhuma ou com erro), o
  cartão "Sobra do mês" da visão geral e o Planejamento no "Mais" e no trilho.

Além disso, verificado à mão em 2026-10-02 no Chrome (build release) e no emulador
`trilha_pixel`, contra o core e o BFF reais com a Pluggy de demonstração.

Ver [[Visão Geral]].
