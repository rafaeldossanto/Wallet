import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/api/api_exception.dart';
import 'package:wallet/core/l10n/app_localizations_pt.dart';
import 'package:wallet/core/l10n/l10n.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

/// "Esqueci minha senha" on the sign-in screen, "Trocar senha" and the new recovery codes in
/// Settings, and the sentence for a busy server.
void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() => bff = FakeBff());

  Map<String, Object?> me({bool twoFactorEnabled = false}) => {
        'id': '6f1c2a9e-0000-4000-8000-000000000099',
        'email': 'rafael@example.com',
        'displayName': 'Rafael Santos',
        'createdAt': '2026-09-01T12:00:00Z',
        'twoFactorEnabled': twoFactorEnabled,
      };

  Future<void> openApp(WidgetTester tester, {String? storedRefreshToken}) async {
    tester.view.physicalSize = const Size(400, 860);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, storedRefreshToken: storedRefreshToken)));
    await tester.pumpAndSettle();
  }

  Future<void> openSettings(WidgetTester tester, {bool twoFactorEnabled = false}) async {
    bff.signedIn();
    bff.json('GET', '/api/me', me(twoFactorEnabled: twoFactorEnabled));
    bff.json('GET', '/api/home', Fixtures.home());
    await openApp(tester, storedRefreshToken: 'refresh-1');
    await tester.tap(find.text('Mais'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustes'));
    await tester.pumpAndSettle();
  }

  /// Settings is a long list: scroll the tile into view before tapping it.
  Future<void> tapTile(WidgetTester tester, String title) async {
    await tester.scrollUntilVisible(find.text(title), 200, scrollable: find.byType(Scrollable).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text(title));
    await tester.pumpAndSettle();
  }

  group('forgot password', () {
    testWidgets('the sign-in has the link, and the screen sends the e-mail and says the same thing for any e-mail',
        (tester) async {
      bff.json('POST', '/api/auth/password/forgot', null, status: 202);
      await openApp(tester);

      await tester.tap(find.text('Esqueci minha senha'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), ' rafael@example.com ');
      await tester.tap(find.widgetWithText(FilledButton, 'Enviar o link'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/password/forgot').single.data, {'email': 'rafael@example.com'});
      expect(find.text('Se houver uma conta com esse e-mail, enviamos um link para criar uma senha nova.'),
          findsOneWidget);
      await tester.tap(find.text('Voltar para entrar'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(FilledButton, 'Entrar'), findsOneWidget);
    });

    testWidgets('an address without @ never leaves the app', (tester) async {
      await openApp(tester);
      await tester.tap(find.text('Esqueci minha senha'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael');
      await tester.tap(find.widgetWithText(FilledButton, 'Enviar o link'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/password/forgot'), isEmpty);
      expect(find.text('Digite um e-mail válido.'), findsOneWidget);
    });

    testWidgets('a busy server is told as busy', (tester) async {
      bff.error('POST', '/api/auth/password/forgot', 503, 'service.busy');
      await openApp(tester);
      await tester.tap(find.text('Esqueci minha senha'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
      await tester.tap(find.widgetWithText(FilledButton, 'Enviar o link'));
      await tester.pumpAndSettle();

      expect(find.text('O Wallet está com muito movimento agora. Tente de novo em alguns segundos.'), findsOneWidget);
    });
  });

  group('change password', () {
    Future<void> fill(WidgetTester tester, {String current = 'certa1234', String next = 'senha nova 5678', String? repeat}) async {
      await tester.enterText(find.widgetWithText(TextField, 'Senha atual'), current);
      await tester.enterText(find.widgetWithText(TextField, 'Nova senha'), next);
      await tester.enterText(find.widgetWithText(TextField, 'Repita a nova senha'), repeat ?? next);
      await tester.tap(find.widgetWithText(FilledButton, 'Trocar senha'));
      await tester.pumpAndSettle();
    }

    testWidgets('asks for the current and the new password, then signs out and says why', (tester) async {
      bff.json('PUT', '/api/me/password', null, status: 204);
      await openSettings(tester);

      await tapTile(tester, 'Trocar senha');
      await tester.pumpAndSettle();
      await fill(tester);

      expect(bff.calls('PUT', '/api/me/password').single.data,
          {'currentPassword': 'certa1234', 'newPassword': 'senha nova 5678'});
      // The server ended every session: no logout call, straight to the sign-in with the reason.
      expect(bff.calls('POST', '/api/auth/logout'), isEmpty);
      expect(find.widgetWithText(FilledButton, 'Entrar'), findsOneWidget);
      expect(find.text('Senha trocada. Entre de novo com a senha nova.'), findsOneWidget);
    });

    testWidgets('a wrong current password says so on the field and keeps the session', (tester) async {
      bff.error('PUT', '/api/me/password', 403, 'auth.wrong_password');
      await openSettings(tester);

      await tapTile(tester, 'Trocar senha');
      await tester.pumpAndSettle();
      await fill(tester, current: 'errada');

      expect(find.textContaining('Senha incorreta'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Entrar'), findsNothing);
    });

    testWidgets('different repeats, short passwords and the same password never leave the app', (tester) async {
      await openSettings(tester);
      await tapTile(tester, 'Trocar senha');
      await tester.pumpAndSettle();

      await fill(tester, repeat: 'outra senha 1234');
      expect(find.text('As senhas não são iguais.'), findsOneWidget);
      await fill(tester, next: 'curta');
      expect(find.text('Pelo menos 8 caracteres.'), findsOneWidget);
      await fill(tester, current: 'certa1234', next: 'certa1234');
      expect(find.text('A nova senha precisa ser diferente da atual.'), findsOneWidget);

      expect(bff.calls('PUT', '/api/me/password'), isEmpty);
    });
  });

  group('new recovery codes', () {
    testWidgets('only with the second factor on', (tester) async {
      await openSettings(tester);

      await tester.ensureVisible(find.text('Verificação em duas etapas'));
      expect(find.text('Gerar códigos novos'), findsNothing);
    });

    testWidgets('the password and a code bring ten new ones, shown once', (tester) async {
      bff.json('POST', '/api/me/two-factor/recovery-codes', {
        'recoveryCodes': ['ABCD-EFGH-JKMN-PQRS', 'TVWX-YZ01-2345-6789'],
      });
      await openSettings(tester, twoFactorEnabled: true);

      await tapTile(tester, 'Gerar códigos novos');
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.enterText(find.widgetWithText(TextField, 'Código'), ' 123456 ');
      await tester.tap(find.widgetWithText(FilledButton, 'Gerar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/me/two-factor/recovery-codes').single.data,
          {'password': 'certa1234', 'code': '123456'});
      expect(find.text('Guarde os códigos de recuperação'), findsOneWidget);
      expect(find.textContaining('ABCD-EFGH-JKMN-PQRS'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Pronto'));
      await tester.pumpAndSettle();
      expect(find.text('Guarde os códigos de recuperação'), findsNothing);
    });

    testWidgets('a wrong code is told and the dialog stays', (tester) async {
      bff.error('POST', '/api/me/two-factor/recovery-codes', 403, 'auth.invalid_two_factor_code');
      await openSettings(tester, twoFactorEnabled: true);

      await tapTile(tester, 'Gerar códigos novos');
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.enterText(find.widgetWithText(TextField, 'Código'), '000000');
      await tester.tap(find.widgetWithText(FilledButton, 'Gerar'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Código incorreto'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Gerar'), findsOneWidget);
    });
  });

  test('a busy server, at the BFF or at the sign-in, gets one sentence', () {
    final l10n = AppLocalizationsPt();
    const sentence = 'O Wallet está com muito movimento agora. Tente de novo em alguns segundos.';

    expect(l10n.errorMessage(const ApiException('service.busy', status: 503)), sentence);
    expect(l10n.errorMessage(const ApiException('auth.busy', status: 429)), sentence);
  });
}
