import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/core/state/loadable.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/features/calendar/data/calendar_api.dart';
import 'package:wallet/features/calendar/presentation/calendar_cards.dart';
import 'package:wallet/features/calendar/presentation/calendar_controller.dart';

import '../support/fake_bff.dart';

void main() {
  setUpAll(setUpLocale);

  final today = DateTime(2026, 10, 3);
  late FakeBff bff;

  Map<String, Object?> month(String month, List<Map<String, Object?>> days, String total) =>
      {'month': month, 'total': total, 'days': days};

  Map<String, Object?> day(String date, String total, int count) => {'date': date, 'total': total, 'count': count};

  Map<String, Object?> item(String description, String amount, {String? bank = 'Banco Demo', String category = 'Groceries'}) => {
        'id': 'tx-$description',
        'accountId': 'acc-1',
        'accountName': 'Conta Corrente',
        'institutionName': bank,
        'institutionImageUrl': null,
        'description': description,
        'amount': amount,
        'status': 'POSTED',
        'category': category,
        'installmentNumber': null,
        'installmentTotal': null,
      };

  setUp(() {
    bff = FakeBff();
    bff.json('GET', '/api/calendar', month('2026-10', [day('2026-10-01', '37.15', 2), day('2026-10-02', '169.61', 1)], '206.76'));
    bff.json('GET', '/api/calendar/2026-10-02', {
      'date': '2026-10-02',
      'items': [item('SUPERMERCADO BOM PRECO', '169.61')],
    });
    bff.json('GET', '/api/calendar/2026-10-01', {
      'date': '2026-10-01',
      'items': [item('PADARIA PAO QUENTE', '23.09', category: 'Eating out'), item('UBER *TRIP', '14.06', bank: 'Cartão Bank')],
    });
  });

  CalendarController controller() => CalendarController(CalendarApi(fakeClient(bff)), dataChanges: DataChanges(), today: today);

  test('opening the month picks the latest day with spending and loads it', () async {
    final calendar = controller();

    await calendar.load();

    expect(calendar.selected, DateTime(2026, 10, 2));
    final spending = (calendar.dayState! as Loaded<DaySpending>).value;
    expect(spending.items.single.institutionName, 'Banco Demo');
    expect(bff.calls('GET', '/api/calendar').single.queryParameters, {'month': '2026-10'});
    calendar.dispose();
  });

  test('a month without spending picks no day', () async {
    bff.json('GET', '/api/calendar', month('2026-09', [], '0.00'));
    final calendar = controller();

    await calendar.load();
    calendar.setMonth(DateTime(2026, 9));
    await pumpEventQueue();

    expect(calendar.selected, isNull);
    expect(calendar.dayState, isNull);
    calendar.dispose();
  });

  testWidgets('tapping a day shows what was spent and at which bank; future days do nothing', (tester) async {
    final calendar = controller();
    tester.view.physicalSize = const Size(500, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.light(),
      locale: const Locale('pt', 'BR'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
      home: Scaffold(
        body: ChangeNotifierProvider.value(
          value: calendar..load(),
          child: const SingleChildScrollView(
            child: Column(children: [SpendingCalendarCard(), DaySpendingCard()]),
          ),
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('2 dias com gastos'), findsOneWidget);
    expect(find.text('SUPERMERCADO BOM PRECO'), findsOneWidget);
    expect(find.text('Banco Demo · Conta Corrente · Mercado'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('1 de out.: R\$ 37,15 em gastos'));
    await tester.pumpAndSettle();
    expect(find.text('PADARIA PAO QUENTE'), findsOneWidget);
    expect(find.text('Cartão Bank · Conta Corrente · Mercado'), findsOneWidget);
    expect(find.text('-R\$ 37,15'), findsOneWidget, reason: 'the day total, next to the title');

    await tester.tap(find.bySemanticsLabel('20 de out.: nenhum gasto'));
    await tester.pumpAndSettle();
    expect(calendar.selected, DateTime(2026, 10, 1));
    calendar.dispose();
  });
}
