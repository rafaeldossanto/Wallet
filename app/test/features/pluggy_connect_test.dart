import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/features/connections/data/connections_api.dart';
import 'package:wallet/features/connections/data/pluggy_connect.dart';
import 'package:wallet/features/connections/presentation/pluggy_connect_screen.dart';

import '../support/fake_bff.dart';
import '../support/fake_pluggy_connect.dart';
import '../support/fixtures.dart';

/// The spinner turns until Connect says it is open, so the screen never settles by itself.
Future<void> pumpFrames(WidgetTester tester) async {
  for (var frame = 0; frame < 10; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(setUpLocale);

  group('the Connect protocol', () {
    test('the widget opens with the token, in Portuguese and in the app\'s theme', () {
      final url = PluggyConnect.url(connectToken: 'connect-token-1', dark: true);

      expect(url.host, 'connect.pluggy.ai');
      expect(url.queryParameters, {'connect_token': 'connect-token-1', 'lang': 'pt', 'theme': 'dark'});
      expect(PluggyConnect.url(connectToken: 't', dark: false, includeSandbox: true).queryParameters,
          containsPair('with_sandbox', 'true'));
    });

    test('Meu Pluggy and a bank app\'s own scheme leave the WebView; Pluggy\'s pages stay', () {
      expect(PluggyConnect.opensOutside(Uri.parse('https://meu.pluggy.ai/conexoes')), isTrue);
      expect(PluggyConnect.opensOutside(Uri.parse('itau://openfinance/consent?id=1')), isTrue);
      expect(PluggyConnect.opensOutside(Uri.parse('https://connect.pluggy.ai/?connect_token=t')), isFalse);
      expect(PluggyConnect.opensOutside(Uri.parse('about:blank')), isFalse);
    });

    test('reads the authorization, the links and each event once', () {
      final reader = ConnectMessageReader();
      String message(String type, String value) => jsonEncode({'type': type, 'message': value});

      final authorization = reader.read(message('OAUTH_OPEN', 'https://auth.itau.com.br/oauth?request=abc'));
      expect(authorization, isA<OpenAuthorization>());
      expect((authorization as OpenAuthorization).url.host, 'auth.itau.com.br');
      expect(reader.read(message('LINK_OPEN', 'https://pluggy.ai/privacidade')), isA<OpenLink>());

      const query = '?connect_token=t&events=OPEN,SELECTED_INSTITUTION,ITEM_RESPONSE&timestamp=1760800000001'
          '&item_id=item-1&item_status=UPDATING&execution_status=ACCOUNTS_IN_PROGRESS&connector_name=Ita%C3%BA';
      final event = reader.read(message('LOCATION', query)) as ConnectEvent;
      expect(event.name, 'ITEM_RESPONSE');
      expect(event.type, ConnectEventType.other);
      expect(event.itemId, 'item-1');
      expect(event.institutionName, 'Itaú');
      expect(event.itemAuthorized, isTrue);
      expect(reader.read(message('LOCATION', query)), isNull, reason: 'the same timestamp is the same event');

      final success = reader.read(message('LOCATION', '?events=OPEN,SUCCESS&timestamp=1760800000002&item_id=item-1'
          '&item_status=UPDATED&execution_status=SUCCESS')) as ConnectEvent;
      expect(success.type, ConnectEventType.succeeded);
      expect(success.itemId, 'item-1');
    });

    test('an item is worth linking only once the bank let the Wallet in', () {
      ConnectEvent item(String status, String execution) =>
          ConnectEvent(ConnectEventType.other, itemId: 'item-1', itemStatus: status, executionStatus: execution);

      expect(item('UPDATING', 'LOGIN_IN_PROGRESS').itemAuthorized, isFalse);
      expect(item('WAITING_USER_ACTION', 'WAITING_USER_ACTION').itemAuthorized, isFalse);
      expect(item('LOGIN_ERROR', 'USER_AUTHORIZATION_NOT_GRANTED').itemAuthorized, isFalse);
      expect(item('UPDATING', 'TRANSACTIONS_IN_PROGRESS').itemAuthorized, isTrue);
      expect(item('UPDATED', 'SUCCESS').itemAuthorized, isTrue);
      expect(const ConnectEvent(ConnectEventType.other, itemStatus: 'UPDATED').itemAuthorized, isFalse);
    });

    test('anything else the page posts is ignored', () {
      final reader = ConnectMessageReader();

      expect(reader.read('not json'), isNull);
      expect(reader.read(jsonEncode({'type': 'SOMETHING_NEW', 'message': 'x'})), isNull);
      expect(reader.read(jsonEncode({'type': 'OAUTH_OPEN', 'message': 'no scheme'})), isNull);
      expect(reader.read(jsonEncode({'type': 'LOCATION', 'message': '?events=OPEN&error=%E0%A4%A'})), isNull);
      expect(reader.read(jsonEncode({'type': 'LOCATION', 'message': '?connect_token=t'})), isNull);
    });
  });

  group('the Connect screen', () {
    late FakeBff bff;
    late FakeConnect connect;
    String? popped;
    var closed = false;

    setUp(() {
      bff = FakeBff();
      connect = FakeConnect();
      bff.json('POST', '/api/connections/connect-token', {'connectToken': 'connect-token-1'});
      popped = null;
      closed = false;
    });

    Future<void> openConnect(WidgetTester tester) async {
      await tester.pumpWidget(MultiProvider(
        providers: [
          Provider(create: (_) => ConnectionsApi(fakeClient(bff))),
          Provider.value(value: connect.host),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          locale: const Locale('pt', 'BR'),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () async {
                    popped = await PluggyConnectScreen.open(context);
                    closed = true;
                  },
                  child: const Text('Abrir'),
                ),
              ),
            ),
          ),
        ),
      ));
      await tester.tap(find.text('Abrir'));
      await pumpFrames(tester);
    }

    testWidgets('opens Connect with the token from the BFF and hands over the screen once it is up', (tester) async {
      await openConnect(tester);

      expect(connect.view!.url!.queryParameters, containsPair('connect_token', 'connect-token-1'));
      expect(connect.view!.url!.queryParameters, containsPair('theme', 'dark'));
      expect(find.text('Abrindo a Pluggy…'), findsOneWidget);
      expect(find.byType(AppBar), findsOneWidget);

      connect.view!.event('OPEN');
      await tester.pump();

      expect(find.text('Abrindo a Pluggy…'), findsNothing);
      expect(find.byType(AppBar), findsNothing, reason: 'Connect draws its own header');
      expect(find.byKey(const Key('connect-page')), findsOneWidget);
    });

    testWidgets('the bank\'s authorization goes to its app, and links go to the browser', (tester) async {
      await openConnect(tester);
      connect.view!.event('OPEN');

      connect.view!.send('OAUTH_OPEN', 'https://auth.itau.com.br/oauth?request=abc');
      connect.view!.send('LINK_OPEN', 'https://pluggy.ai/termos');
      await tester.pump();

      expect(connect.opened.map((open) => (open.url.host, open.preferApp)),
          [('auth.itau.com.br', true), ('pluggy.ai', false)]);
      expect(connect.view!.posted, isEmpty);
    });

    testWidgets('when nothing opens the bank, Connect is told instead of waiting forever', (tester) async {
      connect.opens = false;
      await openConnect(tester);

      connect.view!.send('OAUTH_OPEN', 'https://auth.itau.com.br/oauth?request=abc');
      await tester.pump();

      expect(connect.view!.posted.single, containsPair('source', 'connector-oauth-login'));
      expect(connect.view!.posted.single['error'], isA<String>());
    });

    testWidgets('once the bank\'s data is in, it comes back with the item to link', (tester) async {
      await openConnect(tester);
      connect.view!.event('OPEN');
      connect.view!.event('ITEM_RESPONSE', itemId: 'item-1', itemStatus: 'UPDATING', executionStatus: 'ACCOUNTS_IN_PROGRESS');

      connect.view!.event('SUCCESS', itemId: 'item-1', itemStatus: 'UPDATED', executionStatus: 'SUCCESS');
      await tester.pumpAndSettle();

      expect(closed, isTrue);
      expect(popped, 'item-1');
    });

    testWidgets('closed before the bank authorized, there is nothing to link', (tester) async {
      await openConnect(tester);
      connect.view!.event('OPEN');
      connect.view!.event('ITEM_RESPONSE', itemId: 'item-1', itemStatus: 'WAITING_USER_ACTION',
          executionStatus: 'WAITING_USER_ACTION');

      connect.view!.event('CLOSE', itemId: 'item-1', itemStatus: 'WAITING_USER_ACTION');
      await tester.pumpAndSettle();

      expect(closed, isTrue);
      expect(popped, isNull);
    });

    testWidgets('leaving with back while the bank sends the data still links the item', (tester) async {
      await openConnect(tester);
      connect.view!.event('OPEN');
      connect.view!.event('ITEM_RESPONSE', itemId: 'item-1', itemStatus: 'UPDATING', executionStatus: 'TRANSACTIONS_IN_PROGRESS');
      await tester.pump();

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(closed, isTrue);
      expect(popped, 'item-1');
    });

    testWidgets('without a token it explains why and tries again', (tester) async {
      bff.error('POST', '/api/connections/connect-token', 503, 'provider.not_configured');
      await openConnect(tester);

      expect(find.text('O servidor ainda não tem as credenciais da Pluggy.'), findsOneWidget);
      expect(connect.view, isNull);

      bff.json('POST', '/api/connections/connect-token', {'connectToken': 'connect-token-2'});
      await tester.tap(find.text('Tentar de novo'));
      await pumpFrames(tester);

      expect(connect.view!.url!.queryParameters, containsPair('connect_token', 'connect-token-2'));
    });

    testWidgets('a page that does not load says so, and the X closes', (tester) async {
      await openConnect(tester);

      connect.view!.listener.onFailed();
      await tester.pump();
      expect(find.text('Não foi possível abrir a Pluggy. Confira sua internet e tente de novo.'), findsOneWidget);

      await tester.tap(find.byType(CloseButton));
      await tester.pumpAndSettle();
      expect(closed, isTrue);
      expect(popped, isNull);
    });
  });

  group('the connections screen', () {
    late FakeBff bff;
    late FakeConnect connect;

    setUp(() {
      bff = FakeBff()..signedIn();
      connect = FakeConnect();
      bff.json('GET', '/api/home', Fixtures.home());
      bff.json('GET', '/api/connections', <Object>[]);
      bff.json('POST', '/api/connections/connect-token', {'connectToken': 'connect-token-1'});
    });

    tearDown(() => debugDefaultTargetPlatformOverride = null);

    Future<void> openConnections(WidgetTester tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, pluggyConnect: connect.host)));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Conexões'));
      await tester.pumpAndSettle();
    }

    testWidgets('on Android "Conectar banco" goes through Pluggy Connect and links what it created', (tester) async {
      await openConnections(tester);
      expect(find.textContaining('Toque em Conectar banco'), findsOneWidget);

      await tester.tap(find.widgetWithText(FloatingActionButton, 'Conectar banco'));
      await pumpFrames(tester);
      bff.json('POST', '/api/connections', Fixtures.connection(status: 'SYNCING', lastSyncedAt: null), status: 201);
      bff.json('GET', '/api/connections', [Fixtures.connection(status: 'SYNCING', lastSyncedAt: null)]);
      connect.view!.event('OPEN');
      connect.view!.event('SUCCESS', itemId: 'item-1', itemStatus: 'UPDATED', executionStatus: 'SUCCESS');
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));

      expect(bff.calls('POST', '/api/connections').single.data, {'providerItemId': 'item-1'});
      expect(find.text('Banco conectado. Os dados chegam em instantes.'), findsOneWidget);
      expect(find.text('Banco Demo'), findsOneWidget);
      expect(find.text('Atualizando'), findsOneWidget);

      // The list follows the first sync, as after linking by Item ID.
      bff.json('GET', '/api/connections', [Fixtures.connection()]);
      await tester.pump(const Duration(seconds: 3));
      await tester.pump();
      expect(find.text('Ativa'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('on Android the Item ID is still one tap away', (tester) async {
      await openConnections(tester);

      await tester.tap(find.byTooltip('Vincular pelo Item ID'));
      await tester.pumpAndSettle();

      expect(find.widgetWithText(TextField, 'Item ID'), findsOneWidget);
    });

    testWidgets('a bank already in the Wallet is reported, and nothing else changes', (tester) async {
      bff.error('POST', '/api/connections', 409, 'connection.already_linked');
      await openConnections(tester);

      await tester.tap(find.widgetWithText(FloatingActionButton, 'Conectar banco'));
      await pumpFrames(tester);
      connect.view!.event('SUCCESS', itemId: 'item-1', itemStatus: 'UPDATED', executionStatus: 'SUCCESS');
      await tester.pumpAndSettle();

      expect(find.text('Essa conexão já está no Wallet.'), findsOneWidget);
    });

    testWidgets('elsewhere a connection is linked by its Item ID', (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      await openConnections(tester);

      expect(find.widgetWithText(FloatingActionButton, 'Vincular conexão'), findsOneWidget);
      expect(find.byTooltip('Vincular pelo Item ID'), findsNothing);
      expect(find.textContaining('Meu Pluggy'), findsOneWidget);
      debugDefaultTargetPlatformOverride = null;
    });
  });
}
