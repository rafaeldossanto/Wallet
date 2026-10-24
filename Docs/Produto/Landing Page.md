---
tags: [produto, landing]
atualizado: 2026-10-23
---

# Landing Page

Página de venda do Wallet, para anunciar o produto antes de abrir para outras pessoas. O botão principal é a **lista de espera**. Fica na pasta `site/`, na raiz do repositório: HTML, CSS e JS puros, sem build, sem framework e sem nada externo (nem fonte da web).

## O que a página tem

1. **Hero**: título, subtítulo, formulário da lista de espera e, ao lado (embaixo no celular), uma ilustração em HTML/CSS do painel do app, com valores fictícios e marcada como "Ilustração".
2. **Como funciona**: três passos (autoriza no app do banco, o Wallet junta e organiza, você vê tudo e planeja o mês).
3. **Recursos**: patrimônio, calendário de gastos, plano do mês, cartões e faturas, gastos fixos sugeridos e investimentos.
4. **Segurança e privacidade**: somente leitura, Open Finance por instituição autorizada pelo Banco Central, descrições cifradas, segundo fator no login e exclusão da conta conforme a LGPD. Não cita o provedor de Open Finance pelo nome.
5. **Onde usar**: Android, Windows e navegador.
6. **Perguntas frequentes**: cinco perguntas, em `<details>`.
7. **Chamada final**: o mesmo formulário de novo.
8. **Rodapé**: "© 2026 Wallet" e os links de Privacidade e Termos.

Fora do texto, de propósito: IA, preço, número de usuários e datas.

Visual do app: tema escuro por padrão (`#0B0B0C`, cartões cinza escuro), azul `#3D5AFE` nos botões e no cartão de destaque, verde-limão `#D4F34A` nos destaques pequenos, cantos de 24 px. Com `prefers-color-scheme: light`, fundo `#EEF0F4` e cartões brancos com borda fina. O logo é o ícone do app (cartões empilhados) redesenhado em SVG a partir de `app/tool/render_app_icon_test.dart`; o favicon é cópia de `app/web/favicon.png`.

## Arquivos

| Arquivo | Para quê |
|---|---|
| `site/index.html` | A página |
| `site/styles.css` | Cores, layout e tema claro |
| `site/app.js` | Envio do formulário da lista de espera |
| `site/config.js` | Endereço da API (ver abaixo) |
| `site/assets/` | `logo.svg` e `favicon.png` |
| `site/privacidade.html`, `site/termos.html` | Páginas provisórias |

## Abrir localmente

Da raiz do repositório:

```bash
python -m http.server 5181 --bind localhost --directory site
```

Depois, `http://localhost:5181`. Para o envio funcionar, o BFF precisa estar no ar (`:8080`).

## `config.js` e `WALLET_API_URL`

`site/config.js` define `window.WALLET_API_URL`, o endereço do BFF para onde o formulário envia. No desenvolvimento vale `http://localhost:8080`. O deploy troca esse valor; nenhum outro arquivo muda.

## Contrato do formulário

`POST {WALLET_API_URL}/api/waitlist`, `Content-Type: application/json`, corpo `{"email": "...", "consent": true}`, sem credenciais (nada de cookie). A caixa de consentimento começa desmarcada e, sem ela marcada, nada é enviado.

| Resposta | Mensagem na tela |
|---|---|
| 202 | "Quase lá! Enviamos um link para o seu e-mail. Confirme por lá para entrar na lista." (o campo é limpo) |
| 400 | "Confira o e-mail digitado." |
| 429 | "Muitas tentativas. Tente de novo mais tarde." |
| outro erro ou falha de rede | "Não foi possível enviar agora. Tente de novo em instantes." |

A mensagem aparece numa região `aria-live="polite"`. E-mail inválido é barrado no navegador, com a mesma mensagem do 400.

## CORS

O navegador faz a chamada de outra origem, então a origem da página precisa estar em `WALLET_WEB_ALLOWED_ORIGINS` do BFF. Para o comando acima: `http://localhost:5181`. Sem isso o navegador bloqueia a chamada e o formulário mostra a mensagem de erro genérico. Em produção, vale a origem pública da página.

## Privacidade e termos

`privacidade.html` e `termos.html` trazem o **rascunho** da Política de Privacidade e dos Termos de Uso, com o aviso "Versão preliminar, em revisão jurídica" no topo e os dados da empresa como marcadores entre colchetes (em destaque na página). O texto vem de `Docs/Legal/Política de Privacidade.md` e `Docs/Legal/Termos de Uso.md`; ao mudar um `.md`, a página precisa ser gerada de novo. Os textos legais de verdade dependem da revisão do advogado antes de abrir para outras pessoas.

## Ver também

- [[BFF]]: variáveis do BFF, incluindo `WALLET_WEB_ALLOWED_ORIGINS`.
