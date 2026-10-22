import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/api/json.dart';
import 'package:wallet/core/router/adaptive_shell.dart';
import 'package:wallet/core/session/session_api.dart';
import 'package:wallet/core/widgets/qr_code_view.dart';
import 'package:wallet/core/widgets/wallet_logo.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

/// The account from the outside: sign-up waiting for the e-mailed link, sign-in with the second
/// factor, and the profile and second factor in Settings.
void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() => bff = FakeBff());

  const tokens = {
    'accessToken': 'access-1',
    'tokenType': 'Bearer',
    'expiresIn': 900,
    'refreshToken': 'refresh-1',
    'refreshTokenExpiresAt': '2026-11-01T12:00:00Z',
  };

  Map<String, Object?> me({String? pendingEmail, bool twoFactorEnabled = false}) => {
        'id': '6f1c2a9e-0000-4000-8000-000000000099',
        'email': 'rafael@example.com',
        'displayName': 'Rafael Santos',
        'createdAt': '2026-09-01T12:00:00Z',
        'pendingEmail': pendingEmail,
        'twoFactorEnabled': twoFactorEnabled,
      };

  Future<void> openApp(WidgetTester tester, {Size size = const Size(400, 860), String? storedRefreshToken}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, storedRefreshToken: storedRefreshToken)));
    await tester.pumpAndSettle();
  }

  Future<void> openSettings(WidgetTester tester) async {
    bff.signedIn();
    bff.json('GET', '/api/home', Fixtures.home());
    await openApp(tester, storedRefreshToken: 'refresh-1');
    await tester.tap(find.text('Mais'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustes'));
    await tester.pumpAndSettle();
  }

  Future<void> signIn(WidgetTester tester) async {
    await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();
  }

  group('sign-up', () {
    testWidgets('waits for the e-mailed link instead of signing in', (tester) async {
      bff.json('POST', '/api/auth/register', null, status: 202);
      await openApp(tester);
      await tester.tap(find.text('Ainda não tenho conta'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Nome'), 'Rafael');
      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), ' rafael@example.com ');
      await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Criar conta'));
      await tester.pumpAndSettle();

      expect(find.text('Confira seu e-mail'), findsOneWidget);
      expect(find.textContaining('Enviamos um link para rafael@example.com'), findsOneWidget);
      expect(bff.calls('POST', '/api/auth/register').single.data,
          {'displayName': 'Rafael', 'email': 'rafael@example.com', 'password': 'certa1234'});
      expect(bff.calls('POST', '/api/auth/login'), isEmpty);
    });

    testWidgets('the link can be sent again, and back to sign in', (tester) async {
      bff.json('POST', '/api/auth/register', null, status: 202);
      bff.json('POST', '/api/auth/email/resend', null, status: 202);
      await openApp(tester);
      await tester.tap(find.text('Ainda não tenho conta'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextFormField, 'Nome'), 'Rafael');
      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
      await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Criar conta'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Reenviar o link'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/email/resend').single.data, {'email': 'rafael@example.com'});
      expect(find.textContaining('um link novo chega em instantes'), findsOneWidget);
      await tester.tap(find.text('Voltar para entrar'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(FilledButton, 'Entrar'), findsOneWidget);
    });
  });

  group('sign-in', () {
    testWidgets('shows the stacked-cards logo, not the old wallet', (tester) async {
      await openApp(tester);

      expect(find.byType(WalletLogo), findsOneWidget);
      expect(find.byIcon(Icons.account_balance_wallet_outlined), findsNothing);
    });

    testWidgets('a wrong password also tells a new account to open the link first', (tester) async {
      bff.error('POST', '/api/auth/login', 401, 'auth.invalid_credentials');
      await openApp(tester);

      await signIn(tester);

      expect(find.textContaining('abra antes o link que enviamos'), findsOneWidget);
    });

    testWidgets('with the second factor on, the same form asks for the code and sends it', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
      var attempts = 0;
      bff.on('POST', '/api/auth/login', (request) async => ++attempts == 1
          ? const FakeResponse({'code': 'auth.two_factor_required', 'message': 'code'}, status: 401)
          : const FakeResponse(tokens));
      await openApp(tester);

      await signIn(tester);
      expect(find.widgetWithText(TextFormField, 'Código'), findsOneWidget);
      expect(find.textContaining('app autenticador'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextFormField, 'Código'), ' 123456 ');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/login').last.data,
          {'email': 'rafael@example.com', 'password': 'certa1234', 'code': '123456'});
      expect(find.text('Olá, Rafael!'), findsOneWidget);
    });

    testWidgets('a wrong code says so and empties the field', (tester) async {
      var attempts = 0;
      bff.on('POST', '/api/auth/login', (request) async => ++attempts == 1
          ? const FakeResponse({'code': 'auth.two_factor_required', 'message': 'code'}, status: 401)
          : const FakeResponse({'code': 'auth.invalid_two_factor_code', 'message': 'no'}, status: 401));
      await openApp(tester);
      await signIn(tester);

      await tester.enterText(find.widgetWithText(TextFormField, 'Código'), '000000');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Código incorreto'), findsOneWidget);
      expect(tester.widget<TextFormField>(find.widgetWithText(TextFormField, 'Código')).controller!.text, isEmpty);
    });

    testWidgets('another confirmation link can be asked for from the sign-in', (tester) async {
      bff.json('POST', '/api/auth/email/resend', null, status: 202);
      await openApp(tester);

      await tester.tap(find.text('Não recebeu o e-mail de confirmação?'));
      await tester.pumpAndSettle();
      await tester.enterText(find.descendant(of: find.byType(AlertDialog), matching: find.byType(TextField)), 'rafael@example.com');
      await tester.tap(find.widgetWithText(FilledButton, 'Reenviar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/email/resend').single.data, {'email': 'rafael@example.com'});
      expect(find.textContaining('um link novo chega em instantes'), findsOneWidget);
    });
  });

  testWidgets('the rail shows the stacked-cards logo', (tester) async {
    bff.signedIn();
    bff.json('GET', '/api/home', Fixtures.home());
    await openApp(tester, size: const Size(1200, 860), storedRefreshToken: 'refresh-1');

    expect(find.descendant(of: find.byType(WalletRail), matching: find.byType(WalletLogo)), findsOneWidget);
    expect(find.descendant(of: find.byType(WalletRail), matching: find.text('W')), findsNothing);
  });

  group('settings', () {
    testWidgets('renames the account', (tester) async {
      bff.json('PUT', '/api/me', {...me(), 'displayName': 'Rafael dos Santos'});
      await openSettings(tester);

      await tester.tap(find.text('Rafael Santos'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Nome'), 'Rafael dos Santos');
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(bff.calls('PUT', '/api/me').single.data, {'displayName': 'Rafael dos Santos'});
      expect(find.text('Rafael dos Santos'), findsOneWidget);
    });

    testWidgets('changing the e-mail takes the password and then waits for the new address', (tester) async {
      bff.json('POST', '/api/me/email', null, status: 202);
      await openSettings(tester);
      bff.json('GET', '/api/me', me(pendingEmail: 'novo@example.com'));

      await tester.tap(find.text('rafael@example.com'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Novo e-mail'), 'novo@example.com');
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/me/email').single.data, {'newEmail': 'novo@example.com', 'password': 'certa1234'});
      expect(find.textContaining('Link enviado para novo@example.com'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Pronto'));
      await tester.pumpAndSettle();
      expect(find.text('Aguardando a confirmação de novo@example.com'), findsOneWidget);
    });

    testWidgets('a wrong password on the e-mail change says so on the field', (tester) async {
      bff.error('POST', '/api/me/email', 403, 'auth.wrong_password');
      await openSettings(tester);

      await tester.tap(find.text('rafael@example.com'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Novo e-mail'), 'novo@example.com');
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'errada');
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Senha incorreta'), findsOneWidget);
    });

    testWidgets('turning the second factor on: password, QR code and code, then the recovery codes', (tester) async {
      bff.json('POST', '/api/me/two-factor/setup', {
        'secret': 'JBSWY3DPEHPK3PXP',
        'otpauthUri': 'otpauth://totp/Wallet:rafael%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Wallet',
      });
      bff.json('POST', '/api/me/two-factor/enable', {
        'recoveryCodes': ['ABCD-EFGH-JKMN-PQRS', 'TVWX-YZ01-2345-6789'],
      });
      await openSettings(tester);
      bff.json('GET', '/api/me', me(twoFactorEnabled: true));

      await tester.ensureVisible(find.text('Verificação em duas etapas'));
      await tester.tap(find.text('Verificação em duas etapas'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/me/two-factor/setup').single.data, {'password': 'certa1234'});
      expect(find.byType(QrCodeView), findsOneWidget);
      expect(find.text('JBSW Y3DP EHPK 3PXP'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, 'Código'), '123456');
      await tester.tap(find.widgetWithText(FilledButton, 'Ligar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/me/two-factor/enable').single.data, {'code': '123456'});
      expect(find.text('Guarde os códigos de recuperação'), findsOneWidget);
      expect(find.textContaining('ABCD-EFGH-JKMN-PQRS'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Pronto'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Ligada'), findsOneWidget);
    });

    testWidgets('a code that does not match keeps the QR code up', (tester) async {
      bff.json('POST', '/api/me/two-factor/setup', {'secret': 'JBSWY3DPEHPK3PXP', 'otpauthUri': 'otpauth://totp/x'});
      bff.error('POST', '/api/me/two-factor/enable', 422, 'two_factor.invalid_code');
      await openSettings(tester);

      await tester.ensureVisible(find.text('Verificação em duas etapas'));
      await tester.tap(find.text('Verificação em duas etapas'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Código'), '000000');
      await tester.tap(find.widgetWithText(FilledButton, 'Ligar'));
      await tester.pumpAndSettle();

      expect(find.textContaining('horário do celular'), findsOneWidget);
      expect(find.byType(QrCodeView), findsOneWidget);
    });

    testWidgets('turning it off takes the password and a code', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/me', me(twoFactorEnabled: true));
      bff.json('GET', '/api/home', Fixtures.home());
      bff.json('DELETE', '/api/me/two-factor', null, status: 204);
      tester.view.physicalSize = const Size(400, 860);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mais'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ajustes'));
      await tester.pumpAndSettle();
      bff.json('GET', '/api/me', me());

      await tester.ensureVisible(find.text('Verificação em duas etapas'));
      await tester.tap(find.text('Verificação em duas etapas'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Sua senha'), 'certa1234');
      await tester.enterText(find.widgetWithText(TextField, 'Código'), 'abcd-efgh-jkmn-pqrs');
      await tester.tap(find.widgetWithText(FilledButton, 'Desligar'));
      await tester.pumpAndSettle();

      expect(bff.calls('DELETE', '/api/me/two-factor').single.data,
          {'password': 'certa1234', 'code': 'abcd-efgh-jkmn-pqrs'});
      expect(find.textContaining('Desligada'), findsOneWidget);
    });
  });

  test('a profile from an older server has the second factor off', () {
    final profile = UserProfile.fromJson(Json({
      'id': 'x',
      'email': 'rafael@example.com',
      'displayName': 'Rafael',
    }));

    expect(profile.twoFactorEnabled, isFalse);
    expect(profile.pendingEmail, isNull);
  });

  test('the secret shows in groups of four, easier to type', () {
    expect(const TwoFactorSetup(secret: 'JBSWY3DPEHPK3PXPA', otpauthUri: '').groupedSecret, 'JBSW Y3DP EHPK 3PXP A');
  });
}
