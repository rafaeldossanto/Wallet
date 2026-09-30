---
tags: [arquitetura, open-finance, fluxo]
atualizado: 2026-09-29
---

# Fluxo de Conexão

Como uma conta de banco passa a aparecer no Wallet. São dois fluxos, escolhidos por
`wallet.provider.connection-mode` no core.

## Desenvolvimento: Meu Pluggy

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

## Produção: widget Pluggy Connect

```mermaid
sequenceDiagram
    actor U as Usuário
    participant A as App Wallet
    participant F as BFF
    participant C as Core
    participant P as Pluggy
    participant K as App do banco
    U->>A: Toca em "Conectar Itaú"
    A->>F: Pede token de conexão
    F->>C: Pede token de conexão
    C->>P: Cria connect token (chave de API)
    P-->>C: Token temporário
    C-->>F: Token temporário
    F-->>A: Token temporário
    A->>P: Abre o widget Pluggy Connect
    U->>P: Escolhe banco e dados
    P->>K: Cria o consentimento e redireciona
    U->>K: Faz login e confirma
    K-->>P: Consentimento autorizado
    P-->>A: onSuccess com o itemId
    A->>F: POST /api/connections
    F->>C: POST /internal/connections
    P-->>F: Webhook: item atualizado
    F->>C: Encaminha o evento
    C->>P: Busca contas, cartões e investimentos
```

Nos dois fluxos a última etapa é a mesma: `POST /api/connections` com o `itemId`. Só
muda de onde o ID vem. Regras que o fluxo cumpre: [[Consentimento]]. Onde cada peça
roda: [[Visão Geral]].

## Pontos de atenção

- A **chave de API** da Pluggy fica só no core. O app recebe só o connect token, que
  vale 30 minutos.
- No `connect_token`, mandar `clientUserId` com o id do usuário do Wallet. Em
  produção, o core confere que o item recebido pertence a esse usuário antes de
  vincular.
- O `flutter_pluggy_connect` abre o OAuth do banco no navegador do sistema por
  padrão; com `forceOauthInBrowser: false` o fluxo fica numa WebView dentro do app.
- A Pluggy **não assina** os webhooks. Proteção: header secreto no cadastro do
  webhook e lista de IPs. O BFF é a porta pública e só encaminha para o core. Ver
  [[Sincronização]].
- Reconectar um banco cria um item novo, com IDs novos de transação.
