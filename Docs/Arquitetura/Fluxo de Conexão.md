---
tags: [arquitetura, open-finance, fluxo]
atualizado: 2026-10-21
---

# Fluxo de Conexão

Como uma conta de banco passa a aparecer no Wallet. São dois caminhos, e os dois terminam no
mesmo `POST /api/connections` com o `itemId`; só muda de onde o ID vem.

| Caminho | Onde | De onde vem o item |
|---|---|---|
| **Item ID** | Conexões → "Vincular conexão" (iOS, PC, navegador); no Android, o ícone de link no topo | Meu Pluggy ou a Pluggy de demonstração |
| **Pluggy Connect** | Conexões → "Conectar banco", no **Android** (desde 2026-10-18) | O widget da Pluggy, criado para o usuário |

`wallet.provider.connection-mode` no core decide quem pode vincular o quê (ver "Dono do item").

## Item ID: Meu Pluggy

```mermaid
sequenceDiagram
    actor R as Rafael
    participant M as Meu Pluggy
    participant D as Dashboard Pluggy
    participant A as App Wallet
    participant F as BFF
    participant C as Core
    participant P as API Pluggy
    R->>M: Conecta o banco
    R->>D: Vincula o item à aplicação (conector MeuPluggy)
    D-->>R: itemId
    R->>A: Conexões > Vincular conexão (cola o itemId)
    A->>F: POST /api/connections
    F->>C: POST /internal/connections
    C->>P: GET /items/{id}
    P-->>C: Item encontrado
    C-->>F: Conexão criada
    F-->>A: Conexão criada (e limpa o cache)
    C->>P: Primeira sincronização (contas, transações, faturas, investimentos)
```

Detalhes e limites em [[Meu Pluggy no Desenvolvimento]].

## Pluggy Connect no Android

Pedido do Rafael em 2026-10-18: tocar em conectar e cair direto no app do banco, onde ele já está
logado.

```mermaid
sequenceDiagram
    actor U as Usuário
    participant A as App Wallet
    participant F as BFF
    participant C as Core
    participant P as Pluggy
    participant K as App do banco
    U->>A: Conexões > Conectar banco
    A->>F: POST /api/connections/connect-token
    F->>C: POST /internal/connections/connect-token
    C->>P: POST /connect_token (clientUserId, oauthRedirectUri)
    P-->>C: Token de 30 min
    C-->>A: connectToken (pelo BFF)
    A->>P: WebView com connect.pluggy.ai
    U->>P: Escolhe o banco
    P-->>A: OAUTH_OPEN com a URL de autorização do banco
    A->>K: Abre a URL fora da WebView (o Android entrega ao app do banco)
    U->>K: Confirma o compartilhamento
    K->>P: Autorizado
    P-->>A: Deep link com.wallet.wallet://pluggy-connect (o app volta)
    P-->>A: SUCCESS com o itemId, depois de coletar os dados
    A->>F: POST /api/connections
    F->>C: POST /internal/connections
    C->>P: Primeira sincronização
```

- **Token:** só o core cria (precisa do Client Secret). Vai com `clientUserId` = id do usuário no
  Wallet e `oauthRedirectUri` = `wallet.provider.oauth-redirect-uri`. A Pluggy aceita HTTPS ou
  deep link, nunca localhost, e recusa na criação do token. Vale 30 min, para uma conexão; cada
  toque em "Conectar banco" pede um novo. A chamada sai no registro de auditoria com o motivo
  `CONNECT` ([[Core]]).
- **WebView:** o app fala com o widget pelo mesmo protocolo dos SDKs móveis da Pluggy
  (`react-native-pluggy-connect` 1.6 e `flutter_pluggy_connect` 3.0): um canal
  `window.ReactNativeWebView.postMessage` por onde o Connect manda `OAUTH_OPEN` (a URL do banco),
  `LINK_OPEN` (termos, privacidade) e `LOCATION` (a query da página, com cada evento em `events`,
  `item_id`, `item_status`). Código em `app/lib/features/connections/data/pluggy_connect.dart`.
- **Por que não o pacote `flutter_pluggy_connect`:** a 3.0.1 (de 2024) fecha a própria tela ao
  carregar se algum deep link já chegou ao app, então a segunda conexão depois de uma volta do
  banco fecharia sozinha; e ela usa o Material antigo do framework, não o `material_ui`.
- **Abrir o banco:** a URL de autorização vai para o `url_launcher`, primeiro só para apps que não
  são navegador (o app do banco, pelos app links que os bancos do Open Finance publicam), e, se
  nenhum app a reivindicar, para o navegador. Se nada abrir, o Connect é avisado e mostra o erro.
- **A volta:** o banco devolve para a Pluggy, que redireciona para
  `com.wallet.wallet://pluggy-connect`. O `AndroidManifest.xml` tem o intent filter desse
  endereço; a activity é `singleTask`, então a volta cai na mesma tela do Connect (e limpa o que o
  banco deixou por cima), e o deep linking do próprio Flutter está desligado, para o go_router
  não tratar o link como rota. O Connect continua sozinho: ele consulta o item até a coleta
  terminar.
- **Quando vincular:** no `SUCCESS` (dados coletados). Se a pessoa sair antes (voltar ou fechar)
  com o banco já autorizado e os dados chegando, o item também é vinculado; a primeira
  sincronização pode ainda achar a Pluggy atualizando, e os dados vêm com o "Atualizar" (15 min
  depois dessa tentativa, ver [[Sincronização]]) ou na sincronização agendada. Saindo antes de autorizar, nada é vinculado.
- **Depois:** a tela de Conexões acompanha a primeira sincronização como no vínculo pelo Item ID
  (consulta a cada 3 s).
- **Pluggy de demonstração:** responde ao `/connect_token` (com a mesma regra do redirect), mas o
  widget de verdade não aceita o token dela. Com a demo, use o Item ID.
- **Sandbox:** em build de debug o widget mostra também os bancos de teste da Pluggy.

## Dono do item

| Item | `MEU_PLUGGY` (dev) | `PLUGGY_CONNECT` (produção) |
|---|---|---|
| Do Meu Pluggy (sem `clientUserId`) | Vincula | Recusa (parece inexistente) |
| Do widget, deste usuário | Vincula | Vincula |
| Do widget, de outro usuário | Recusa (parece inexistente) | Recusa (parece inexistente) |
| Já vinculado por outro usuário | Recusa (parece inexistente) | Recusa (parece inexistente) |
| Já vinculado por este usuário | 409 `connection.already_linked` | 409 `connection.already_linked` |

"Parece inexistente" é a mesma resposta de um item que a Pluggy não conhece (404
`connection.item_not_found`), depois da mesma consulta à Pluggy. Até 2026-10-21 o "já vinculado"
era conferido antes do dono e respondia 409 para qualquer pessoa, o que dizia que alguém usava o
Wallet com aquele item (achado A17). Agora só o dono ouve o 409.

O `MEU_PLUGGY` aceita item sem dono de **qualquer** usuário: é como o Item ID do Meu Pluggy é
vinculado no desenvolvimento. Num servidor, quem descobrisse um itemId do operador leria os dados
dele (achado A6). Por isso, com `SPRING_PROFILES_ACTIVE=prod` o core **não sobe** se
`WALLET_CONNECTION_MODE` não for `PLUGGY_CONNECT`, e a mensagem diz a variável. Fora do `prod` o
padrão continua `MEU_PLUGGY`.

## Limites

Cada vínculo faz o core ler até um ano do banco na Pluggy, e cada token do widget é uma chamada à
Pluggy, tudo na apiKey de todos os usuários (achados A7, A8 e A9).

| Limite | Onde | Padrão | Resposta |
|---|---|---|---|
| Conexões por usuário | Core (`wallet.provider.max-connections-per-user`) | 10 | 422 `connection.limit_reached` |
| Vínculos (`POST /api/connections`), contando tentativas que falham | BFF (`wallet.rate-limit.link-limit` / `link-window`) | 10 por hora, por usuário | 429 `rate_limit.connections` |
| Tokens do widget (`POST /api/connections/connect-token`) | BFF (`wallet.rate-limit.connect-token-limit` / `connect-token-window`) | 10 por hora, por usuário | 429 `rate_limit.connections` |

- O teto vale também com pedidos em paralelo: os vínculos de um usuário passam um de cada vez,
  por um advisory lock do Postgres por usuário (`UserLinkLock`), e só então contam as conexões.
- Os limites do BFF somam ao geral (300 por minuto) e ficam na memória: reiniciar o BFF zera as
  contas. A janela é fixa a partir do primeiro pedido; o `Retry-After` diz a janela inteira.

Desvincular revoga o item na Pluggy (e o consentimento do Open Finance com ele) em produção e,
no modo dev, quando o item foi criado pelo widget para aquele usuário. Item do Meu Pluggy só sai do
Wallet. Se a Pluggy não responder ao conferir o dono, o Wallet desvincula sem revogar.

## Pontos de atenção

- A **chave de API** da Pluggy fica só no core. O app recebe só o connect token.
- A Pluggy **não assina** os webhooks. Proteção: header secreto no cadastro do
  webhook e lista de IPs. O BFF é a porta pública e só encaminha para o core. Ver
  [[Sincronização]]. O token ainda não leva `webhookUrl`: falta o deploy.
- Reconectar um banco cria um item novo, com IDs novos de transação.
- iOS fica com o Item ID enquanto o iPhone está pausado; para o Connect lá falta o URL type do
  deep link no `Info.plist`.

Regras que o fluxo cumpre: [[Consentimento]]. Onde cada peça roda: [[Visão Geral]].
