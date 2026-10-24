import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/in_memory_shared_preferences_async.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_async_platform_interface.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/config/server_address.dart';

import '../support/fake_bff.dart';

void main() {
  setUpAll(setUpLocale);
  setUp(() => SharedPreferencesAsyncPlatform.instance = InMemorySharedPreferencesAsync.empty());

  test('what a person types becomes an address the app can call', () {
    expect(ServerAddress.normalize('192.168.0.10'), 'http://192.168.0.10:8080', reason: 'the BFF port by default');
    expect(ServerAddress.normalize(' 192.168.0.10:9090 '), 'http://192.168.0.10:9090');
    expect(ServerAddress.normalize('http://meu-pc.local:8080/'), 'http://meu-pc.local:8080');
    expect(ServerAddress.normalize('https://wallet.example.com'), 'https://wallet.example.com',
        reason: 'a full address keeps its own port');
    expect(ServerAddress.normalize(''), isNull);
    expect(ServerAddress.normalize('ftp://192.168.0.10'), isNull);
    expect(ServerAddress.normalize('http://'), isNull);
  });

  test('the address is remembered, and "Usar o padrão" goes back to the build\'s', () async {
    final server = await ServerAddress.load();
    expect(server.isCustom, isFalse);

    expect(await server.set('192.168.0.10'), isTrue);
    expect((await ServerAddress.load()).current, 'http://192.168.0.10:8080');

    expect(await server.set('not an address ://'), isFalse);
    expect(server.current, 'http://192.168.0.10:8080', reason: 'a bad address changes nothing');

    await server.reset();
    expect(await SharedPreferencesAsync().getString('wallet.server_url'), isNull);
    expect(server.isCustom, isFalse);
  });

  testWidgets('the login screen points the phone at the PC, and the next request goes there', (tester) async {
    // Tall enough for the whole sign-in column, "Esqueci minha senha" included.
    tester.view.physicalSize = const Size(800, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final bff = FakeBff();
    final server = await ServerAddress.load();
    final dependencies = AppDependencies.create(
      api: fakeClient(bff),
      store: MemorySessionStore(),
      withAppLock: false,
      server: server,
    );
    await tester.pumpWidget(WalletApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    expect(find.textContaining('Servidor: '), findsOneWidget);

    await tester.tap(find.text('Alterar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'nope ://');
    await tester.tap(find.text('Salvar'));
    await tester.pumpAndSettle();
    expect(find.text('Digite um endereço como 192.168.0.10:8080.'), findsOneWidget);

    await tester.enterText(find.byType(TextField).last, '192.168.0.10');
    await tester.tap(find.text('Salvar'));
    await tester.pumpAndSettle();

    expect(find.text('Servidor: 192.168.0.10:8080'), findsOneWidget);
    expect(dependencies.api.dio.options.baseUrl, 'http://192.168.0.10:8080');
  });
}
