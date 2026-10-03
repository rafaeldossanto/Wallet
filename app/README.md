# Wallet — app

Flutter para Android, iOS e navegador. Só fala com o BFF. Como rodar e testar está no
README da raiz; a arquitetura está em `Docs/Arquitetura/App Flutter.md`.

```
lib/
  app.dart       composição: dependências, tema, rotas, bloqueio por plataforma
  core/          HTTP, sessão, dinheiro, datas, tema, rotas, textos (ARB), segurança
  features/      uma pasta por tela, cada uma com data/ (API e modelos) e presentation/
```

Os textos ficam em `lib/core/l10n/app_pt.arb`; `flutter gen-l10n` gera as classes.
