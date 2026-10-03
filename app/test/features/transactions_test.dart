import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/features/transactions/data/transactions_api.dart';
import 'package:wallet/features/transactions/presentation/transactions_controller.dart';
import 'package:wallet/features/transactions/presentation/transactions_screen.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() {
    bff = FakeBff();
    bff.json('GET', '/api/accounts', [Fixtures.account()]);
  });

  Future<TransactionsController> openStatement(WidgetTester tester) async {
    final controller = TransactionsController(TransactionsApi(fakeClient(bff)),
        dataChanges: DataChanges(), today: DateTime(2026, 10, 2));
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.dark(),
      locale: const Locale('pt', 'BR'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
      home: ChangeNotifierProvider.value(value: controller..start(), child: const TransactionsScreen()),
    ));
    await tester.pumpAndSettle();
    return controller;
  }

  testWidgets('groups by day, signs inflows and shows installments and pending', (tester) async {
    bff.json('GET', '/api/transactions', Fixtures.page([
      Fixtures.transaction(id: 'a', description: 'SALARIO', amount: '7350.00', direction: 'INFLOW', bookedOn: '2026-09-30'),
      Fixtures.transaction(id: 'b', description: 'MERCADO', amount: '45.90', bookedOn: '2026-09-30', status: 'PENDING'),
      Fixtures.transaction(
          id: 'c', description: 'NOTEBOOK', amount: '389.90', bookedOn: '2026-09-18', installmentNumber: 3, installmentTotal: 10),
    ]));

    await openStatement(tester);

    expect(find.text('Quarta, 30 de setembro'), findsOneWidget);
    expect(find.text('Sexta, 18 de setembro'), findsOneWidget);
    expect(find.text('+R\$ 7.350,00'), findsOneWidget);
    expect(find.text('-R\$ 45,90'), findsOneWidget);
    expect(find.text('Pendente'), findsOneWidget);
    expect(find.text('Parcela 3/10'), findsOneWidget);
    final request = bff.calls('GET', '/api/transactions').single;
    expect(request.queryParameters, {'from': '2026-10-01', 'to': '2026-10-31', 'page': 1, 'pageSize': 50});
  });

  testWidgets('filters go to the BFF and the month moves back', (tester) async {
    bff.json('GET', '/api/transactions', Fixtures.page([]));
    await openStatement(tester);

    await tester.tap(find.text('Saídas'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Mês anterior'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Buscar pela descrição'), 'mercado');
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    final last = bff.calls('GET', '/api/transactions').last.queryParameters;
    expect(last['direction'], 'OUTFLOW');
    expect(last['from'], '2026-09-01');
    expect(last['to'], '2026-09-30');
    expect(last['q'], 'mercado');
    expect(find.text('Nenhuma movimentação com esses filtros.'), findsOneWidget);
  });

  /// Found in the browser: a statement opened before linking a bank never offered its accounts.
  test('a data change reloads the account filter too, not only the movements', () async {
    final changes = DataChanges();
    bff.json('GET', '/api/accounts', <Object>[]);
    bff.json('GET', '/api/transactions', Fixtures.page([]));
    final controller = TransactionsController(TransactionsApi(fakeClient(bff)), dataChanges: changes);
    await controller.start();
    expect(controller.accounts, isEmpty);

    bff.json('GET', '/api/accounts', [Fixtures.account()]);
    changes.changed();
    await pumpEventQueue();

    expect(controller.accounts.single.label, 'Conta Corrente •••• 4567');
    controller.dispose();
  });

  testWidgets('scrolling to the end brings the next page', (tester) async {
    bff.on('GET', '/api/transactions', (request) async {
      final page = request.queryParameters['page'] as int;
      final items = [
        for (var index = 0; index < 50; index++)
          Fixtures.transaction(id: 'p$page-$index', description: 'COMPRA $page-$index', amount: '10.00', bookedOn: '2026-10-01'),
      ];
      return FakeResponse(Fixtures.page(items, page: page, totalPages: 2));
    });
    final controller = await openStatement(tester);

    await tester.fling(find.byType(ListView), const Offset(0, -20000), 5000);
    await tester.pumpAndSettle();

    expect(controller.items, hasLength(100));
    expect(controller.hasMore, isFalse);
    expect(bff.calls('GET', '/api/transactions').map((request) => request.queryParameters['page']), [1, 2]);
  });
}
