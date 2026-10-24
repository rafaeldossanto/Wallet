import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/api/api_exception.dart';
import 'package:wallet/core/l10n/app_localizations_pt.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/features/connections/data/connections_api.dart';

import '../support/consent.dart';
import '../support/fake_bff.dart';
import '../support/fake_pluggy_connect.dart';
import '../support/fixtures.dart';
import 'pluggy_connect_test.dart' show pumpFrames;

/// The purpose screen before connecting (LGPD L3) and the end of an authorization (L5): what the
/// user is told, the box that gates "Continuar", the warning before it ends, the renewal.
void main() {
  setUpAll(setUpLocale);

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

  String endsIn(Duration duration) => DateTime.now().add(duration).toUtc().toIso8601String();

  group('before connecting', () {
    testWidgets('says what is read, why, for how long and how to revoke, and waits for the box', (tester) async {
      await openConnections(tester);

      await tester.tap(find.widgetWithText(FloatingActionButton, 'Conectar banco'));
      await tester.pumpAndSettle();

      expect(find.text('Antes de conectar'), findsOneWidget);
      expect(find.text('Mostrar seus saldos, extratos, cartões, faturas e investimentos no Wallet.'), findsOneWidget);
      expect(find.textContaining('Contas e saldos'), findsOneWidget);
      expect(find.textContaining('Lançamentos'), findsOneWidget);
      expect(find.textContaining('Cartões de crédito e faturas'), findsOneWidget);
      expect(find.textContaining('Investimentos'), findsWidgets);
      expect(find.text('O Wallet só lê: não movimenta dinheiro.'), findsOneWidget);
      expect(find.text('Até você desconectar aqui ou no app do banco, ou até a autorização vencer.'), findsOneWidget);
      expect(find.textContaining('Desvincular'), findsOneWidget);
      final box = tester.widget<CheckboxListTile>(find.byType(CheckboxListTile));
      expect(box.value, isFalse, reason: 'unchecked: the user ticks it, the app never does');
      expect(find.text('Autorizo o Wallet a ler esses dados para essa finalidade.'), findsOneWidget);
    });

    testWidgets('"Continuar" stays off until the box is ticked, and then opens Pluggy Connect with the version', (tester) async {
      await openConnections(tester);
      await tester.tap(find.widgetWithText(FloatingActionButton, 'Conectar banco'));
      await tester.pumpAndSettle();

      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Continuar')).onPressed, isNull);
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'), warnIfMissed: false);
      await tester.pump();
      expect(bff.calls('POST', '/api/connections/connect-token'), isEmpty);

      await tester.tap(find.byType(CheckboxListTile));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
      await pumpFrames(tester);

      expect(bff.calls('POST', '/api/connections/connect-token').single.data, {'consentVersion': 'open-finance-v1'});
      expect(connect.view!.url!.queryParameters, containsPair('connect_token', 'connect-token-1'));
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('leaving the screen connects nothing', (tester) async {
      await openConnections(tester);
      await tester.tap(find.widgetWithText(FloatingActionButton, 'Conectar banco'));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(CloseButton));
      await tester.pumpAndSettle();

      expect(find.text('Antes de conectar'), findsNothing);
      expect(bff.calls('POST', '/api/connections/connect-token'), isEmpty);
      expect(connect.view, isNull);
    });

    testWidgets('the Item ID goes through the same screen, and the version travels with the link', (tester) async {
      await openConnections(tester);
      bff.json('POST', '/api/connections', Fixtures.connection(), status: 201);

      await tester.tap(find.byTooltip('Vincular pelo Item ID'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(TextField, 'Item ID'), findsNothing, reason: 'the purpose screen comes first');
      await agreeToConsent(tester);
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Item ID'), 'item-9');
      await tester.tap(find.widgetWithText(FilledButton, 'Vincular'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/connections').single.data,
          {'providerItemId': 'item-9', 'consentVersion': 'open-finance-v1'});
    });
  });

  group('the end of an authorization', () {
    testWidgets('an expired one says so, offers to renew on Android and does not offer to update', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(status: 'CONSENT_EXPIRED'), 'consentExpiresAt': endsIn(const Duration(days: -2))},
      ]);
      await openConnections(tester);

      expect(find.text('A autorização deste banco venceu. Renove para voltar a atualizar.'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Renovar'), findsOneWidget);
      expect(find.text('Atualizar'), findsNothing);
      expect(find.text('Autorização vencida'), findsOneWidget);
    });

    testWidgets('a date in the past counts even if the server has not marked the connection yet', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(), 'consentExpiresAt': endsIn(const Duration(days: -1))},
      ]);
      await openConnections(tester);

      expect(find.text('A autorização deste banco venceu. Renove para voltar a atualizar.'), findsOneWidget);
    });

    testWidgets('elsewhere than Android it points to the phone', (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(status: 'CONSENT_EXPIRED'), 'consentExpiresAt': endsIn(const Duration(days: -2))},
      ]);
      await openConnections(tester);

      expect(find.text('A autorização deste banco venceu. Renove para voltar a atualizar.'), findsOneWidget);
      expect(find.text('Renove pelo app no celular.'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Renovar'), findsNothing);
      debugDefaultTargetPlatformOverride = null;
    });

    testWidgets('seven days or fewer before it ends the card warns; eight does not', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(), 'consentExpiresAt': endsIn(const Duration(days: 5))},
      ]);
      await openConnections(tester);
      expect(find.text('A autorização vence em 5 dias.'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Renovar'), findsOneWidget);
      expect(find.text('Atualizar'), findsOneWidget, reason: 'still works until it ends');

      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(), 'consentExpiresAt': endsIn(const Duration(days: 8))},
      ]);
      await tester.drag(find.byType(ListView).first, const Offset(0, 300));
      await tester.pumpAndSettle();
      expect(find.textContaining('A autorização vence'), findsNothing);
    });

    testWidgets('renewing on Android: purpose screen, Connect in update mode, then the renewal is recorded', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(status: 'CONSENT_EXPIRED'), 'consentExpiresAt': endsIn(const Duration(days: -2))},
      ]);
      bff.json('POST', '/api/connections/${Fixtures.connectionId}/renew', Fixtures.connection());
      await openConnections(tester);

      await tester.tap(find.widgetWithText(FilledButton, 'Renovar'));
      await agreeToConsent(tester);
      await pumpFrames(tester);
      expect(bff.calls('POST', '/api/connections/connect-token').single.data,
          {'consentVersion': 'open-finance-v1', 'connectionId': Fixtures.connectionId});

      bff.json('GET', '/api/connections', [Fixtures.connection()]);
      connect.view!.event('OPEN');
      connect.view!.event('SUCCESS', itemId: 'item-1', itemStatus: 'UPDATED', executionStatus: 'SUCCESS');
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));

      expect(bff.calls('POST', '/api/connections/${Fixtures.connectionId}/renew').single.data,
          {'consentVersion': 'open-finance-v1'});
      expect(bff.calls('POST', '/api/connections'), isEmpty, reason: 'it is the same connection: nothing new is linked');
      expect(find.text('Autorização renovada.'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('closing Connect without finishing renews nothing', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(status: 'CONSENT_EXPIRED'), 'consentExpiresAt': endsIn(const Duration(days: -2))},
      ]);
      await openConnections(tester);

      await tester.tap(find.widgetWithText(FilledButton, 'Renovar'));
      await agreeToConsent(tester);
      await pumpFrames(tester);
      connect.view!.event('OPEN');
      connect.view!.event('CLOSE');
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/connections/${Fixtures.connectionId}/renew'), isEmpty);
    });

    testWidgets('a renewal the provider does not show yet is told', (tester) async {
      bff.json('GET', '/api/connections', [
        {...Fixtures.connection(status: 'CONSENT_EXPIRED'), 'consentExpiresAt': endsIn(const Duration(days: -2))},
      ]);
      bff.error('POST', '/api/connections/${Fixtures.connectionId}/renew', 409, 'connection.consent_not_renewed');
      await openConnections(tester);

      await tester.tap(find.widgetWithText(FilledButton, 'Renovar'));
      await agreeToConsent(tester);
      await pumpFrames(tester);
      connect.view!.event('SUCCESS', itemId: 'item-1', itemStatus: 'UPDATED', executionStatus: 'SUCCESS');
      await tester.pumpAndSettle();

      expect(find.text('A renovação ainda não apareceu no banco. Tente de novo em instantes.'), findsOneWidget);
    });

    testWidgets('a manual update the server refused for an ended authorization says why', (tester) async {
      bff.json('GET', '/api/connections', [Fixtures.connection()]);
      bff.error('POST', '/api/connections/${Fixtures.connectionId}/sync', 409, 'connection.consent_expired');
      await openConnections(tester);

      await tester.tap(find.text('Atualizar'));
      await tester.pumpAndSettle();

      expect(find.text('A autorização deste banco venceu. Renove para voltar a atualizar.'), findsWidgets);
    });
  });

  group('the notice, as a rule', () {
    final now = DateTime(2026, 10, 1, 12);
    Connection connection({String status = 'ACTIVE', DateTime? ends}) =>
        Connection(id: 'c', status: ConnectionStatus.parse(status), consentExpiresAt: ends);

    test('ended when marked or when the date has passed', () {
      expect(connection(status: 'CONSENT_EXPIRED').consentEnded(now), isTrue);
      expect(connection(ends: now.subtract(const Duration(minutes: 1))).consentEnded(now), isTrue);
      expect(connection(ends: now.add(const Duration(minutes: 1))).consentEnded(now), isFalse);
      expect(connection().consentEnded(now), isFalse, reason: 'Meu Pluggy items carry no date');
    });

    test('days left only inside the last seven, by calendar day', () {
      expect(connection(ends: DateTime(2026, 10, 8, 9)).consentDaysLeft(now), 7);
      expect(connection(ends: DateTime(2026, 10, 9, 9)).consentDaysLeft(now), isNull);
      expect(connection(ends: DateTime(2026, 10, 1, 23)).consentDaysLeft(now), 0);
      expect(connection(ends: DateTime(2026, 10, 1, 11)).consentDaysLeft(now), isNull, reason: 'already over');
      expect(connection().consentDaysLeft(now), isNull);
    });

    test('the sentences, and the code the server uses for a refused update', () {
      final l10n = AppLocalizationsPt();

      expect(l10n.connectionConsentExpiresIn(0), 'A autorização vence hoje.');
      expect(l10n.connectionConsentExpiresIn(1), 'A autorização vence em 1 dia.');
      expect(l10n.connectionConsentExpiresIn(7), 'A autorização vence em 7 dias.');
      expect(l10n.errorMessage(const ApiException('connection.consent_expired', status: 409)),
          'A autorização deste banco venceu. Renove para voltar a atualizar.');
    });
  });
}
