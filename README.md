# Wallet

[![Core](https://github.com/rafaeldossanto/Wallet/actions/workflows/core.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/core.yml)
[![BFF](https://github.com/rafaeldossanto/Wallet/actions/workflows/bff.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/bff.yml)
[![App](https://github.com/rafaeldossanto/Wallet/actions/workflows/app.yml/badge.svg)](https://github.com/rafaeldossanto/Wallet/actions/workflows/app.yml)

Carteira financeira para celular e PC que agrega contas, cartões e investimentos pelo
Open Finance. Nome provisório.

| Pasta | O que é | Porta |
|---|---|---|
| `Docs/` | Cofre Obsidian com a arquitetura, as decisões e o plano | — |
| `core/` | Core (Spring Boot, Maven): dados, regras e sincronização com a Pluggy | 8081 |
| `bff/` | BFF (Spring Boot, Maven): a única porta pública do app | 8080 |
| `app/` | App Flutter (Android, iOS e Web) | 5000 no navegador |

Comece por `Docs/Wallet.md`. As tarefas estão em `Docs/Plano/Plano de Implementação.md`.

## Ver tudo funcionando sem conta na Pluggy

Com o [banco local](#banco-local-uma-vez) criado e o `WALLET_DB_PASSWORD` definido, em três
terminais:

```bash
cd core && ./mvnw spring-boot:test-run
```

```bash
cd bff && ./mvnw spring-boot:run
```

```bash
cd app && flutter run -d chrome --web-port 5000
```

O `test-run` usa o mesmo Postgres local do `spring-boot:run` e não cria nenhum container. Sem
`PLUGGY_CLIENT_ID` definido, ele sobe também uma **Pluggy de demonstração** com dados sintéticos
gerados a partir da data de hoje. No app,
crie uma conta, vá em Conexões → Vincular conexão (no Android, o ícone de link no topo) e use os
Item IDs `demo-nubank`, `demo-itau`
e `demo-xp` (bancos com cara de reais: conta, poupança, cartões com faturas e parcelas,
investimentos) ou os básicos `demo-banco` e `demo-corretora`. Com
`PLUGGY_CLIENT_ID` e `PLUGGY_CLIENT_SECRET` definidos, o mesmo comando fala com a Pluggy de
verdade. O que for criado fica no banco local depois que o core para. Para começar do zero, exclua
a conta pelo app (Ajustes → Zona de perigo).

### Containers no Docker

Só os testes de integração (`./mvnw verify`) criam containers, e eles somem sozinhos:

| Container | O que é |
|---|---|
| `wallet-core-test-db-xxxx` | banco de um teste de integração |
| `testcontainers-ryuk-…` | faxineiro do Testcontainers: apaga os outros quando o processo termina |

O sufixo de 4 letras deixa duas execuções ao mesmo tempo sem disputar o nome. Se o Docker for
fechado no meio, o próprio Docker apaga os containers de teste quando abrir de novo. Nem o core,
nem o BFF, nem o `test-run` criam container.

## Core

Precisa do **JDK 25** no `JAVA_HOME` e do Docker (testes de integração).

### Banco local (uma vez)

```bash
psql -U postgres -h localhost -v wallet_password=SUA_SENHA -f core/db/setup-local.sql
```

Depois crie a variável de ambiente `WALLET_DB_PASSWORD` com a mesma senha.

### Chaves do JWT

Sem chave configurada, o core gera uma chave temporária a cada inicialização e avisa no log.
O BFF busca a chave pública no próprio core (JWKS) e pega a nova sozinho, então isso basta
para desenvolver. O único efeito é que toda sessão acaba quando o core reinicia. Com
`SPRING_PROFILES_ACTIVE=prod` não há chave temporária: sem as duas variáveis, o core não sobe.
Para ter chaves fixas:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out wallet-jwt-private.pem
openssl pkey -in wallet-jwt-private.pem -pubout -out wallet-jwt-public.pem
```

Guarde os arquivos fora do repositório e aponte `WALLET_JWT_PRIVATE_KEY` e
`WALLET_JWT_PUBLIC_KEY` para os caminhos. O BFF não precisa de nenhuma das duas.

### Chave da criptografia de dados

A descrição das transações é gravada criptografada (AES-256-GCM). Sem chave, o core usa uma
temporária e avisa no log: o que for gravado fica ilegível depois de reiniciar. Com
`SPRING_PROFILES_ACTIVE=prod` o core não sobe sem ela. Gere uma vez e guarde em `WALLET_DATA_KEY`:

```bash
openssl rand -base64 32
```

### Rodar

```bash
cd core
./mvnw spring-boot:run
```

`./mvnw spring-boot:test-run` sobe o mesmo core no mesmo banco, com a Pluggy de demonstração
quando `PLUGGY_CLIENT_ID` não está definido. Nenhum dos dois cria container.

### Testar

```bash
./mvnw test     # unitários (*Test), sem Docker
./mvnw verify   # unitários + integração (*IT) com Testcontainers
```

## BFF

Precisa do **JDK 25** no `JAVA_HOME`. Não tem banco nem segredo: só precisa saber onde está
o core.

| Variável | Padrão | Para quê |
|---|---|---|
| `WALLET_CORE_URL` | `http://localhost:8081` | Endereço do core |
| `WALLET_WEB_ALLOWED_ORIGINS` | `http://localhost:5000` | Origem do Flutter Web (CORS com cookie) |

### Rodar

Com o core já no ar:

```bash
cd bff
./mvnw spring-boot:run
```

O app web roda na porta 5000 (`flutter run -d chrome --web-port 5000`). O celular
manda `X-Wallet-Client: mobile`, o app de Windows `X-Wallet-Client: desktop` e o navegador
`X-Wallet-Client: web` nas rotas de `/api/auth`.

### Testar

```bash
./mvnw verify   # core simulado no WireMock, sem Docker
```

## App

Flutter 3.47 ou mais novo (o Material vem do pacote `material_ui`). Um projeto para
Android, iOS, navegador e Windows (app instalável); só fala com o BFF.

```bash
cd app
flutter test       # unitários e de tela, com o BFF simulado
flutter analyze
flutter run -d chrome --web-port 5000       # PC, no navegador
flutter run -d emulator-5554                # emulador Android (fala com o BFF em 10.0.2.2:8080)
flutter run -d windows                      # app de Windows (exige o Visual Studio com C++)
```

O endereço do BFF muda com `--dart-define=WALLET_BFF_URL=https://...`. A porta 5000 do
navegador é a origem que o BFF aceita no CORS (`WALLET_WEB_ALLOWED_ORIGINS`).

### App de Windows

O instalador `Wallet-Setup-<versão>.exe` é gerado pelo workflow **Desktop** do GitHub Actions a
cada mudança no `app/`: abra a última execução em Actions → Desktop e baixe o arquivo em
*Artifacts*. Uma tag `v<versão>` (a mesma do `pubspec.yaml`) também publica o instalador em
*Releases*.

- Instala só para o seu usuário, sem pedir administrador; atalho no Menu Iniciar.
- Atualiza sozinho, como o Discord: procura a versão nova nos Releases ao abrir e a cada 6 horas,
  baixa em segundo plano e confere o SHA-256. Na bandeja, instala sozinho; com a janela aberta,
  avisa e espera você reiniciar. Publicar uma versão é subir a `version` do `pubspec.yaml` e criar
  a tag igual (`git tag v0.1.1 && git push origin v0.1.1`).
- O X esconde o Wallet na bandeja (Ajustes → Computador muda isso), e lá também dá para ligar
  "Abrir com o Windows".
- O instalador não é assinado: na primeira vez o Windows avisa "O Windows protegeu o
  computador" (Mais informações → Executar assim mesmo).
- Até o deploy, o app instalado fala com o BFF deste PC (`localhost:8080`): core e BFF precisam
  estar rodando. Com a variável de repositório `WALLET_BFF_URL` definida no GitHub, o build
  aponta para ela.
- Para rodar com `flutter run -d windows` é preciso o Visual Studio Community (2022 ou 2026) com a carga
  "Desenvolvimento para desktop com C++" (só o compilador é usado).

### App no iPhone (sem a conta paga da Apple)

O workflow **iOS** gera, num Mac do GitHub, um `Wallet-<versão>.ipa` sem assinatura. Quem assina
é o AltStore (ou o Sideloadly) na hora de instalar, com um Apple ID comum e gratuito. Sem o Apple
Developer Program, a assinatura vale **7 dias**; o AltStore renova sozinho.

Uma vez, no PC (Windows):

1. Instale o **iTunes** e o **iCloud** baixados do site da Apple, não os da Microsoft Store.
2. Instale o **AltServer** (altstore.io). Com o iPhone no cabo, clique no ícone dele na bandeja →
   *Install AltStore* → escolha o iPhone. Ele pede um Apple ID: pode ser um secundário, criado só
   para isso.
3. No iPhone: Ajustes → Geral → VPN e Gerenciamento de Dispositivos → confie no seu Apple ID. E
   Ajustes → Privacidade e Segurança → **Modo de Desenvolvedor** → ligar (o iPhone reinicia).

No iPhone:

4. Abra o AltStore → *Sources* → **+** → cole
   `https://github.com/rafaeldossanto/Wallet/releases/latest/download/altstore.json` e instale o
   Wallet. As versões novas aparecem ali mesmo, para atualizar.
5. A renovação dos 7 dias acontece sozinha quando o iPhone e o PC (com o AltServer aberto) estão
   na mesma Wi-Fi. Se vencer, o Wallet não abre até você abrir o AltStore e tocar em *Refresh*.

Sem AltStore: o **Sideloadly** (sideloadly.io) instala o `.ipa` baixado dos Releases com o iPhone
no cabo, mas aí a renovação dos 7 dias é manual.

Para usar (até o deploy):

- Core e BFF rodando no PC, e o iPhone na mesma Wi-Fi.
- Na primeira vez que o BFF sobe, o Windows pergunta se o Java pode usar a rede: permita em redes
  privadas.
- Descubra o endereço do PC com `ipconfig` (Endereço IPv4, algo como `192.168.0.10`).
- Na tela de login do Wallet, **Servidor: … · Alterar** → digite o endereço → Salvar. O iPhone
  pergunta se o Wallet pode acessar a rede local: permita.

O Apple ID gratuito assina até 3 apps por vez. Quando houver deploy (HTTPS público), a variável
`WALLET_BFF_URL` do repositório entra no build e nada disso de endereço é preciso.

### App no tablet Android

O workflow **Android** gera um `Wallet-<versão>.apk` a cada mudança no `app/`: abra a última
execução em Actions → Android e baixe o arquivo em *Artifacts* (exige estar logado no GitHub).
Uma tag `v<versão>` também anexa o APK aos *Releases*, que qualquer um baixa.

Para instalar:

1. Baixe o `.apk` no próprio tablet e toque nele.
2. Na primeira vez, o Android pede para o navegador (ou o app de arquivos) poder instalar apps:
   Configurações → **Instalar apps desconhecidos** → permitir. Se o Play Protect avisar que não
   conhece o app, *Instalar mesmo assim*.
3. Uma versão nova instala por cima da antiga, sem perder os dados, porque todo APK sai assinado
   com a mesma chave. Um APK gerado sem os segredos de assinatura (o workflow avisa) usa outra
   chave, e o Android só o aceita depois de desinstalar o anterior.

Com o tablet no cabo e a depuração USB ligada, `adb install -r Wallet-<versão>.apk` também instala.

**Atualizações (desde a 0.1.4):** depois da primeira instalação, não precisa mais entrar no GitHub.
O Wallet procura a versão nova nos Releases ao abrir e a cada 6 horas (pelo `latest-android.json`
que o workflow publica junto do APK), baixa em segundo plano e confere o SHA-256. Aí o canto da
tela avisa "Nova versão disponível":

1. Toque em **Atualizar**. Na primeira vez, o Wallet explica que o Android precisa de uma
   permissão: **Abrir configurações** → ligue **Permitir desta fonte** → volte para o Wallet.
2. O Android pergunta se quer atualizar o app: **Atualizar**. Ele fecha o Wallet para trocar a
   versão; abra de novo, e o canto confirma "Wallet atualizado".

Nada instala sem você tocar em Atualizar e confirmar. Ajustes → Atualizações mostra a versão,
o que está acontecendo e "Procurar agora". Um APK de desenvolvimento (sem
`--dart-define=WALLET_VERSION`) nunca se atualiza. Se o Android recusar a versão nova porque ela
foi assinada com outra chave (um APK de debug instalado, ou um Release gerado sem os segredos), o
Wallet avisa: aí é desinstalar e instalar o APK dos Releases à mão.

Para usar (até o deploy):

- Core e BFF rodando no PC, e o tablet na mesma Wi-Fi.
- Na primeira vez que o BFF sobe, o Windows pergunta se o Java pode usar a rede: permita em redes
  privadas. A Wi-Fi precisa estar como rede **Privada** no Windows; se a pergunta já passou, é em
  Firewall do Windows → Permitir um aplicativo → Java, coluna Privada.
- Descubra o endereço do PC com `ipconfig` (Endereço IPv4, algo como `192.168.0.10`).
- Na tela de login do Wallet, **Servidor: … · Alterar** → digite o endereço → Salvar.

O app aceita HTTP (`res/xml/network_security_config.xml`) porque o BFF no PC não tem HTTPS; com o
deploy, volta a ser só HTTPS.

**A chave de assinatura** é uma keystore que fica fora do git, em `%USERPROFILE%\.wallet\android`:
`wallet-release.jks`, o `key.properties` com as senhas e o alias, e `wallet-release.jks.base64.txt`
(a keystore em texto, para o GitHub). Guarde uma cópia: perder a chave obriga a desinstalar o app
para trocar. O
workflow a recebe por quatro segredos do repositório (Settings → Secrets and variables → Actions):

| Segredo | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | O conteúdo de `wallet-release.jks.base64.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` do `key.properties` |
| `ANDROID_KEY_ALIAS` | `keyAlias` do `key.properties` (`wallet`) |
| `ANDROID_KEY_PASSWORD` | `keyPassword` do `key.properties` |

Gerar o APK aqui no PC, com a mesma chave (sai em `build/app/outputs/flutter-apk/`):

```powershell
cd app
$env:ANDROID_KEY_PROPERTIES = "$HOME\.wallet\android\key.properties"
flutter build apk --release --dart-define=WALLET_VERSION=0.1.5
```

#### Conectar banco pelo Pluggy Connect (Android)

No tablet, Conexões → **Conectar banco** abre o widget da Pluggy; escolhido o banco, a autorização
abre no app do banco e, confirmada, o Android volta ao Wallet, que vincula a conexão. Precisa da
Pluggy de verdade com o widget liberado (o trial do plano Dados, até ~2026-10-21, ou o plano pago);
a Pluggy de demonstração não serve aqui, use o Item ID. Para testar:

1. **Credenciais no PC:** crie `PLUGGY_CLIENT_ID` e `PLUGGY_CLIENT_SECRET` como variáveis de
   ambiente do Windows (Configurações → Sistema → Sobre → Configurações avançadas do sistema →
   Variáveis de Ambiente), com o Client ID e o Client Secret da aplicação do trial no painel da
   Pluggy. Feche e abra o terminal, e suba core e BFF como de costume.
2. **No painel da Pluggy (dashboard.pluggy.ai), na aplicação do trial:**
   - Os conectores dos bancos que você vai ligar precisam estar ativos, nas versões **Open
     Finance** (as que abrem o app do banco). Se o banco só aparece sem "Open Finance" no widget,
     o acesso ao Open Finance ainda não está liberado na aplicação: peça à Pluggy.
   - O redirect não se cadastra: o core manda `com.wallet.wallet://pluggy-connect` em cada token
     (`wallet.provider.oauth-redirect-uri`). Se o painel tiver uma lista de URIs de redirecionamento
     permitidas, inclua esse endereço. Se a Pluggy recusar, o log do core mostra
     `[PLUGGY] Request failed: 400 ...` e o app diz que o serviço está fora do ar.
   - Webhook ainda não: o BFF não tem endereço público.
3. **No tablet:** o APK novo (do workflow Android da `dev`), o app do banco instalado e logado, e
   o endereço do PC na tela de login.
4. Conexões → Conectar banco → escolha o banco → autorize no app dele. Se o navegador perguntar
   se pode abrir o Wallet, permita. De volta, espere o widget terminar de buscar os dados: a
   conexão aparece como "Atualizando" e vira "Ativa" quando a primeira sincronização termina.

O core continua em `WALLET_CONNECTION_MODE=MEU_PLUGGY` (o padrão), em que valem os dois caminhos:
os itens do widget só podem ser vinculados pelo dono, e os do Meu Pluggy continuam entrando pelo
Item ID. `PLUGGY_CONNECT` (produção) aceita só itens do widget. Detalhes em
`Docs/Arquitetura/Fluxo de Conexão.md`.

## Branches

Só duas, desde 2026-10-18:

| Branch | Para quê |
|---|---|
| `dev` | Onde o trabalho acontece. Cada push roda os testes e gera os instaladores de teste (em Actions → Artifacts) |
| `main` | O que vai para produção. Recebe a `dev` quando uma versão está pronta, e só ela gera versões publicadas |

Publicar uma versão: levar a `dev` para a `main`, subir a `version` do `pubspec.yaml` e criar a
tag igual na `main` (`git tag v0.1.5 && git push origin v0.1.5`). Uma tag fora da `main` é
recusada pelos workflows, então nada da `dev` vira versão por engano.

## CI

O GitHub Actions roda a suíte de cada parte a cada push na `main` ou na `dev` e em pull request,
só quando a pasta dela muda (`.github/workflows/`):

| Workflow | O que roda |
|---|---|
| Core | `./mvnw -B verify` com JDK 25: unitários e integração (Postgres no Docker do runner) |
| BFF | `./mvnw -B verify` com JDK 25 (core simulado no WireMock) |
| App | Flutter 3.47.6: traduções geradas batendo com o `.arb`, `flutter analyze` e `flutter test` |
| Desktop | Em Windows: compila o app, monta o instalador (Inno Setup) e anexa o `.exe` à execução; numa tag `v*`, publica nos Releases |
| Android | Em Linux, com JDK 21: compila o APK assinado e anexa o `.apk` e o `latest-android.json` à execução; numa tag `v*`, anexa os dois aos Releases |

Só o Android usa segredos: os quatro `ANDROID_*` da chave de assinatura ("App no tablet
Android"). Sem eles o APK sai com a chave de debug, e a execução avisa. Quando um teste falha, os
relatórios ficam 7 dias como artefato da execução.

## Segredos

Segredos (senha do banco, Pluggy, chaves de JWT e de criptografia) vêm só de variáveis
de ambiente. O `.gitignore` também bloqueia `application-local.yaml`, `.env` e `.pem`, e o de
`app/android` bloqueia keystores e `key.properties`. A chave de assinatura do Android fica em
`%USERPROFILE%\.wallet\android`, fora do repositório, e no GitHub só como segredo.
