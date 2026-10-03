import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() => bff = FakeBff());

  Future<void> openApp(WidgetTester tester, {Size size = const Size(400, 860), String? storedRefreshToken = 'refresh-1'}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, storedRefreshToken: storedRefreshToken)));
    await tester.pumpAndSettle();
  }

  group('login', () {
    testWidgets('wrong password shows the core\'s reason in Portuguese', (tester) async {
      bff.error('POST', '/api/auth/login', 401, 'auth.invalid_credentials');
      await openApp(tester, storedRefreshToken: null);

      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
      await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'errada123');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('E-mail ou senha incorretos.'), findsOneWidget);
      expect(bff.calls('POST', '/api/auth/login').single.data, {'email': 'rafael@example.com', 'password': 'errada123'});
    });

    testWidgets('a good login lands on the overview', (tester) async {
      bff.signedIn();
      bff.json('POST', '/api/auth/login', {
        'accessToken': 'access-1',
        'tokenType': 'Bearer',
        'expiresIn': 900,
        'refreshToken': 'refresh-1',
        'refreshTokenExpiresAt': '2026-11-01T12:00:00Z',
      });
      bff.json('GET', '/api/home', Fixtures.home());
      await openApp(tester, storedRefreshToken: null);

      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
      await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('Olá, Rafael'), findsOneWidget);
    });
  });

  testWidgets('signing out on purpose and back in starts at the overview, not where the user left', (tester) async {
    bff.signedIn();
    bff.json('GET', '/api/home', Fixtures.home());
    bff.json('POST', '/api/auth/logout', null, status: 204);
    bff.json('POST', '/api/auth/login', {
      'accessToken': 'access-9',
      'tokenType': 'Bearer',
      'expiresIn': 900,
      'refreshToken': 'refresh-9',
      'refreshTokenExpiresAt': '2026-11-01T12:00:00Z',
    });
    await openApp(tester);

    await tester.tap(find.text('Mais'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sair'));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(FilledButton, 'Entrar'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();

    expect(find.text('Olá, Rafael'), findsOneWidget);
  });

  group('overview', () {
    testWidgets('shows net worth, accounts, cards and recent movements from one call', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
      await openApp(tester);

      expect(find.text('R\$ 23.456,78'), findsOneWidget);
      expect(find.text('Banco Demo'), findsOneWidget);
      expect(find.text('Conta Corrente •••• 4567'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('SUPERMERCADO BOM PRECO'), 300);
      expect(find.text('+R\$ 7.350,00'), findsWidgets);
      expect(find.text('-R\$ 210,45'), findsOneWidget);
      expect(bff.calls('GET', '/api/home'), hasLength(1));
    });

    testWidgets('a part that failed on the server only blanks its own block', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home(unavailable: ['creditCards']));
      await openApp(tester);

      expect(find.text('R\$ 23.456,78'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('Esta parte não carregou agora. Puxe para atualizar.'), 300);
      expect(find.text('Esta parte não carregou agora. Puxe para atualizar.'), findsOneWidget);
    });

    testWidgets('a broker with only investments stays out of the accounts block', (tester) async {
      bff.signedIn();
      final home = Fixtures.home();
      (home['institutions'] as List).add({
        'connectionId': '6f1c2a9e-0000-4000-8000-0000000000ff',
        'institutionName': 'Corretora Demo',
        'institutionImageUrl': null,
        'status': 'ACTIVE',
        'lastSyncedAt': '2026-10-02T09:00:00Z',
        'accounts': <Object>[],
      });
      bff.json('GET', '/api/home', home);
      await openApp(tester);

      expect(find.text('Banco Demo'), findsOneWidget);
      expect(find.text('Corretora Demo'), findsNothing);
    });

    testWidgets('with nothing linked it points to the connections screen', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', {
        'overview': Fixtures.home()['overview'],
        'institutions': <Object>[],
        'creditCards': <Object>[],
        'recentTransactions': <Object>[],
        'unavailable': <Object>[],
      });
      bff.json('GET', '/api/connections', <Object>[]);
      await openApp(tester);

      await tester.tap(find.text('Conectar banco'));
      await tester.pumpAndSettle();

      expect(find.widgetWithText(FloatingActionButton, 'Vincular conexão'), findsOneWidget);
    });
  });

  group('adaptive shell', () {
    testWidgets('bottom bar on a phone, rail on a tablet, fixed menu on a PC', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());

      await openApp(tester, size: const Size(400, 860));
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('Mais'), findsOneWidget);

      tester.view.physicalSize = const Size(700, 900);
      await tester.pumpAndSettle();
      expect(find.byType(NavigationRail), findsOneWidget);

      tester.view.physicalSize = const Size(1280, 900);
      await tester.pumpAndSettle();
      expect(find.byType(NavigationDrawer), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
    });

    testWidgets('"Mais" on a phone opens the other destinations', (tester) async {
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
      bff.json('GET', '/api/connections', [Fixtures.connection()]);
      await openApp(tester);

      await tester.tap(find.text('Mais'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Conexões'));
      await tester.pumpAndSettle();

      expect(find.text('Banco Demo'), findsOneWidget);
      expect(find.text('Ativa'), findsOneWidget);
    });
  });
}
