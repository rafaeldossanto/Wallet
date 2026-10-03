import 'dart:async';
import 'dart:math' as math;

import 'package:dio/dio.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/core/api/json.dart';
import 'package:wallet/core/format/percent.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/money/money.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/core/state/loadable.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/features/investments/data/investments_api.dart';
import 'package:wallet/features/investments/presentation/investment_history_controller.dart';
import 'package:wallet/features/investments/presentation/investments_screen.dart';

import '../support/fake_bff.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;
  late DataChanges changes;

  Map<String, Object?> position(String name, String kind, String balance) => {
        'id': 'inv-$name',
        'kind': kind,
        'subtype': null,
        'name': name,
        'balance': balance,
        'amountInvested': null,
        'dueDate': null,
      };

  final portfolio = {
    'total': '40000.00',
    'byKind': [
      {'kind': 'FIXED_INCOME', 'total': '25000.00'},
      {'kind': 'TREASURY', 'total': '10000.00'},
      {'kind': 'EQUITY', 'total': '5000.00'},
    ],
    'positions': [
      position('CDB Banco Demo', 'FIXED_INCOME', '25000.00'),
      position('Tesouro Selic 2029', 'TREASURY', '10000.00'),
      position('BOVA11', 'EQUITY', '5000.00'),
    ],
  };

  Map<String, Object?> history(String period, List<(String, String)> points) => {
        'period': period,
        'from': points.first.$1,
        'to': points.last.$1,
        'points': [for (final (date, total) in points) {'date': date, 'total': total}],
      };

  // Six months: a day before the first sync, then up R$ 1.234,56 over R$ 38.580,00.
  final sixMonths = history('6M', [
    ('2026-09-30', '0.00'),
    ('2026-10-01', '38580.00'),
    ('2026-10-02', '39000.00'),
    ('2026-10-03', '39814.56'),
  ]);

  // One year: down R$ 1.000,00 over R$ 41.000,00.
  final oneYear = history('1A', [('2026-10-01', '41000.00'), ('2026-10-03', '40000.00')]);

  setUp(() {
    bff = FakeBff();
    changes = DataChanges();
    bff.json('GET', '/api/investments', portfolio);
    bff.on('GET', '/api/investments/history', (RequestOptions request) async =>
        FakeResponse(request.queryParameters['period'] == '1A' ? oneYear : sixMonths));
  });

  List<Map<String, dynamic>> historyQueries() =>
      [for (final request in bff.calls('GET', '/api/investments/history')) request.queryParameters];

  group('history', () {
    test('the line starts on the first day with data and the change runs from there', () {
      final parsed = InvestmentHistory.fromJson(Json(sixMonths));

      expect(parsed.points.map((point) => point.date), [DateTime(2026, 10, 1), DateTime(2026, 10, 2), DateTime(2026, 10, 3)]);
      expect(parsed.change, Money.parse('1234.56'));
      expect(formatPercent(parsed.changeShare!, signed: true), '+3,2%');
    });

    test('a loss reads negative', () {
      final parsed = InvestmentHistory.fromJson(Json(oneYear));

      expect(parsed.change, Money.parse('-1000.00'));
      expect(formatPercent(parsed.changeShare!, signed: true), '-2,4%');
    });

    test('no data at all leaves the line empty and nothing to compare', () {
      final parsed = InvestmentHistory.fromJson(Json(history('1M', [('2026-10-02', '0.00'), ('2026-10-03', '0.00')])));

      expect(parsed.points, isEmpty);
      expect(parsed.change, isNull);
    });
  });

  group('controller', () {
    InvestmentHistoryController controller() =>
        InvestmentHistoryController(InvestmentsApi(fakeClient(bff)), dataChanges: changes);

    List<InvestmentHistoryPoint> points(InvestmentHistoryController history) =>
        (history.state as Loaded<InvestmentHistory>).value.points;

    test('starts on six months and asks the BFF for the period picked', () async {
      final history = controller();

      await history.load();
      expect(history.period, InvestmentPeriod.sixMonths);
      expect(historyQueries(), [
        {'period': '6M'},
      ]);

      await history.setPeriod(InvestmentPeriod.oneYear);
      expect(historyQueries().last, {'period': '1A'});
      expect(points(history).map((point) => point.total), [Money.parse('41000.00'), Money.parse('40000.00')]);
      history.dispose();
    });

    test('a response for a period the user already left is dropped', () async {
      final slow = Completer<FakeResponse>();
      bff.on('GET', '/api/investments/history', (RequestOptions request) =>
          request.queryParameters['period'] == '6M' ? slow.future : Future.value(FakeResponse(oneYear)));
      final history = controller();

      final first = history.load();
      await history.setPeriod(InvestmentPeriod.oneYear);
      slow.complete(FakeResponse(sixMonths));
      await first;

      expect(history.period, InvestmentPeriod.oneYear);
      expect(points(history), hasLength(2), reason: 'still the year, not the late six months');
      history.dispose();
    });

    test('refreshes the period on screen when the data changes, and keeps the line if that fails', () async {
      final history = controller();
      await history.load();
      await history.setPeriod(InvestmentPeriod.oneYear);

      changes.changed();
      await pumpEventQueue();
      expect(historyQueries(), [
        {'period': '6M'},
        {'period': '1A'},
        {'period': '1A'},
      ]);

      bff.error('GET', '/api/investments/history', 503, 'core.unavailable');
      await history.refresh();
      expect(points(history), hasLength(2));
      history.dispose();
    });
  });

  group('screen', () {
    Future<void> openInvestments(WidgetTester tester, {Size size = const Size(360, 2000)}) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(MultiProvider(
        providers: [
          Provider.value(value: InvestmentsApi(fakeClient(bff))),
          ChangeNotifierProvider.value(value: changes),
        ],
        child: MaterialApp(
          theme: AppTheme.light(),
          locale: const Locale('pt', 'BR'),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
          home: const Builder(builder: buildInvestments),
        ),
      ));
      await tester.pumpAndSettle();
    }

    List<FlSpot> drawnSpots(WidgetTester tester) =>
        tester.widget<LineChart>(find.byType(LineChart)).data.lineBarsData.single.spots;

    testWidgets('the donut splits the portfolio by kind, with share and amount in the legend', (tester) async {
      await openInvestments(tester);

      expect(tester.widget<PieChart>(find.byType(PieChart)).data.sections, hasLength(3));
      expect(find.text('Total investido'), findsOneWidget);
      expect(find.text('R\$ 40.000,00'), findsOneWidget, reason: 'the total, in the hole');
      expect(find.text('62,5%'), findsOneWidget);
      expect(find.text('25,0%'), findsOneWidget);
      expect(find.text('12,5%'), findsOneWidget);
      expect(find.text('Renda fixa'), findsNWidgets(2), reason: 'in the legend and over its positions');
      expect(find.text('Ações e ETFs'), findsNWidgets(2));
      expect(find.text('Tesouro Selic 2029'), findsOneWidget, reason: 'the positions by kind stay');
      expect(tester.widget<PieChart>(find.byType(PieChart)).data.sections.map((section) => section.cornerRadius),
          everyElement(greaterThan(0)),
          reason: 'rounded slice ends');
      final evolution = tester.getTopLeft(find.text('Evolução dos investimentos')).dy;
      final allocation = tester.getTopLeft(find.text('Distribuição dos investimentos')).dy;
      final positions = tester.getTopLeft(find.text('Tesouro Selic 2029')).dy;
      expect(evolution, lessThan(allocation), reason: 'on a phone the evolution comes first, the allocation under it');
      expect(allocation, lessThan(positions), reason: 'and the positions by kind after both');
    });

    testWidgets('tapping a slice puts its share and amount in the hole; tapping it again goes back to the total',
        (tester) async {
      await openInvestments(tester);
      final pie = find.byType(PieChart);
      // Renda fixa is the first slice: 62.5% of the ring, clockwise from three o'clock. Its middle
      // is at 112.5 degrees, halfway across the ring (hole of 64, ring of 28).
      const angle = 112.5 * math.pi / 180;
      final fixedIncome = tester.getCenter(pie) + Offset(math.cos(angle), math.sin(angle)) * (64 + 14);

      await tester.tapAt(fixedIncome);
      await tester.pumpAndSettle();

      expect(find.text('Total investido'), findsNothing);
      expect(find.text('62,5%'), findsNWidgets(2), reason: 'in the hole and in the legend');
      expect(find.text('Renda fixa'), findsNWidgets(3), reason: 'the hole, the legend and over its positions');
      expect(find.text('Toque de novo na fatia para voltar ao total.'), findsOneWidget);
      final sections = tester.widget<PieChart>(pie).data.sections;
      expect(sections.first.radius, greaterThan(sections.last.radius), reason: 'the picked slice stands out');

      await tester.tapAt(fixedIncome);
      await tester.pumpAndSettle();

      expect(find.text('Total investido'), findsOneWidget);
      expect(find.text('62,5%'), findsOneWidget);
      expect(find.text('Toque numa fatia para ver a porcentagem e o valor dela.'), findsOneWidget);
    });

    testWidgets('the legend picks a kind too, and a tap in the hole goes back to the total', (tester) async {
      await openInvestments(tester);

      await tester.tap(find.text('Ações e ETFs').first);
      await tester.pumpAndSettle();

      expect(find.text('12,5%'), findsNWidgets(2));
      expect(find.text('Total investido'), findsNothing);

      await tester.tapAt(tester.getCenter(find.byType(PieChart)));
      await tester.pumpAndSettle();

      expect(find.text('Total investido'), findsOneWidget);
      expect(find.text('12,5%'), findsOneWidget);
    });

    testWidgets('the evolution starts on six months; picking 1A asks for it and redraws', (tester) async {
      await openInvestments(tester);

      final gain = find.text('+R\$ 1.234,56 (+3,2%) no período');
      expect(gain, findsOneWidget);
      expect(tester.widget<Text>(gain).style!.color, tester.element(gain).walletColors.inflow);
      expect(drawnSpots(tester), hasLength(3), reason: 'the day before the first sync is not drawn');
      expect(historyQueries(), [
        {'period': '6M'},
      ]);

      await tester.tap(find.text('1A'));
      await tester.pumpAndSettle();

      expect(historyQueries().last, {'period': '1A'});
      final loss = find.text('-R\$ 1.000,00 (-2,4%) no período');
      expect(loss, findsOneWidget);
      expect(gain, findsNothing);
      expect(tester.widget<Text>(loss).style!.color, Theme.of(tester.element(loss)).colorScheme.error);
      expect(drawnSpots(tester), hasLength(2));
    });

    testWidgets('with fewer than two days there is no line yet, just why', (tester) async {
      bff.json('GET', '/api/investments/history', history('6M', [('2026-10-02', '0.00'), ('2026-10-03', '40000.00')]));

      await openInvestments(tester);

      expect(find.text('O gráfico começa no primeiro dia de uso e ganha um ponto a cada dia em que o Wallet atualiza seus investimentos.'),
          findsOneWidget);
      expect(find.byType(LineChart), findsNothing);
    });

    testWidgets('on a wide screen the positions by kind sit left of the charts', (tester) async {
      await openInvestments(tester, size: const Size(1280, 1400));

      final allocation = tester.getTopLeft(find.text('Distribuição dos investimentos'));
      final evolution = tester.getTopLeft(find.text('Evolução dos investimentos'));
      final positions = tester.getTopLeft(find.text('CDB Banco Demo'));
      expect(evolution.dx, allocation.dx, reason: 'the charts share a column');
      expect(evolution.dy, lessThan(allocation.dy), reason: 'the evolution on top');
      expect(positions.dx, lessThan(evolution.dx), reason: 'the kinds on the left');
      expect(positions.dy, lessThan(allocation.dy), reason: 'beside the charts, not under them');
    });
  });
}
