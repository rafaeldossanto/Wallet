import 'package:dio/dio.dart';
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

  Map<String, Object?> item(String description, String amount, String bookedOn, {String bank = 'Nubank', String category = 'Groceries'}) => {
        'id': 'tx-$description',
        'accountId': 'acc-1',
        'bookedOn': bookedOn,
        'accountName': 'Conta',
        'institutionName': bank,
        'institutionImageUrl': null,
        'description': description,
        'amount': amount,
        'status': 'POSTED',
        'category': category,
        'installmentNumber': null,
        'installmentTotal': null,
      };

  Map<String, Object?> page(List<Map<String, Object?>> items, {int page = 1, int totalPages = 1}) =>
      {'from': '', 'to': '', 'page': page, 'totalPages': totalPages, 'total': items.length, 'items': items};

  final market = item('SUPERMERCADO BOM PRECO', '169.61', '2026-10-02', bank: 'Itaú');
  final bakery = item('PADARIA PAO QUENTE', '23.09', '2026-10-01', category: 'Eating out');

  setUp(() {
    bff = FakeBff();
    bff.json('GET', '/api/calendar', month('2026-10', [day('2026-10-01', '23.09', 1), day('2026-10-02', '169.61', 1)], '192.70'));
    bff.on('GET', '/api/calendar/spending', (RequestOptions request) async {
      final from = request.queryParameters['from'];
      final to = request.queryParameters['to'];
      if (from == '2026-10-01' && to == '2026-10-31') {
        return FakeResponse(page([market, bakery]));
      }
      if (from == '2026-10-02' && to == '2026-10-02') {
        return FakeResponse(page([market]));
      }
      return FakeResponse(page([bakery]));
    });
  });

  CalendarController controller() => CalendarController(CalendarApi(fakeClient(bff)), dataChanges: DataChanges(), today: today);

  List<SpendingItem> listed(CalendarController calendar) => (calendar.listState as Loaded<List<SpendingItem>>).value;

  test('with no day picked the list covers the whole month', () async {
    final calendar = controller();

    await calendar.load();

    expect(calendar.selected, isNull);
    expect(listed(calendar).map((item) => item.description), ['SUPERMERCADO BOM PRECO', 'PADARIA PAO QUENTE']);
    expect(bff.calls('GET', '/api/calendar/spending').single.queryParameters,
        {'from': '2026-10-01', 'to': '2026-10-31', 'page': 1});
    calendar.dispose();
  });

  test('tapping a day narrows to it, tapping it again goes back to the month', () async {
    final calendar = controller();
    await calendar.load();

    await calendar.toggle(DateTime(2026, 10, 2));
    expect(calendar.selected, DateTime(2026, 10, 2));
    expect(listed(calendar).single.institutionName, 'Itaú');

    await calendar.toggle(DateTime(2026, 10, 2));
    expect(calendar.selected, isNull);
    expect(listed(calendar), hasLength(2));

    await calendar.toggle(DateTime(2026, 10, 20));
    expect(calendar.selected, isNull, reason: 'a future day cannot be picked');
    calendar.dispose();
  });

  test('a long month comes a page at a time', () async {
    bff.on('GET', '/api/calendar/spending', (RequestOptions request) async {
      final number = request.queryParameters['page'] as int;
      return FakeResponse(page([item('COMPRA $number', '10.00', '2026-10-01')], page: number, totalPages: 2));
    });
    final calendar = controller();
    await calendar.load();
    expect(calendar.hasMore, isTrue);

    await calendar.loadMore();

    expect(listed(calendar).map((item) => item.description), ['COMPRA 1', 'COMPRA 2']);
    expect(calendar.hasMore, isFalse);
    calendar.dispose();
  });

  testWidgets('the cards: month total and banks first, a day on tap, the month again on a second tap', (tester) async {
    final calendar = controller();
    tester.view.physicalSize = const Size(500, 1500);
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
            child: Column(children: [SpendingCalendarCard(), SpendingListCard()]),
          ),
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Gastos · Outubro de 2026'), findsOneWidget);
    expect(find.text('-R\$ 192,70'), findsOneWidget, reason: 'the month total');
    expect(find.text('2 de out. · Itaú · Conta · Mercado'), findsOneWidget);
    expect(find.text('Toque num dia para ver só os gastos dele.'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('2 de out.: R\$ 169,61 em gastos'));
    await tester.pumpAndSettle();
    expect(find.text('Gastos · Ontem'), findsOneWidget);
    expect(find.text('Itaú · Conta · Mercado'), findsOneWidget);
    expect(find.text('PADARIA PAO QUENTE'), findsNothing);

    await tester.tap(find.bySemanticsLabel('2 de out.: R\$ 169,61 em gastos'));
    await tester.pumpAndSettle();
    expect(find.text('Gastos · Outubro de 2026'), findsOneWidget);
    expect(find.text('PADARIA PAO QUENTE'), findsOneWidget);
    calendar.dispose();
  });
}
