import 'package:fl_chart/fl_chart.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/core/api/json.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/core/widgets/donut_chart.dart';
import 'package:wallet/features/insights/data/insights_api.dart';
import 'package:wallet/features/insights/presentation/insights_screen.dart';

import '../support/fake_bff.dart';

void main() {
  setUpAll(setUpLocale);

  Map<String, Object?> point(String date, String netWorth) =>
      {'date': date, 'netWorth': netWorth, 'cash': netWorth, 'investments': '0.00', 'creditCardDebt': '0.00'};

  test('the net worth line starts on the first day with data, not on six months of zeros', () {
    final insights = Insights.fromJson(Json({
      'spending': {'month': '2026-10', 'total': '0.00', 'categories': <Object>[]},
      'netWorth': {
        'points': [point('2026-09-29', '0.00'), point('2026-09-30', '0.00'), point('2026-10-01', '80000.00'), point('2026-10-02', '81101.04')],
      },
    }));

    expect(insights.netWorth.map((point) => point.date), [DateTime(2026, 10, 1), DateTime(2026, 10, 2)]);
  });

  test('no data at all leaves the line empty', () {
    final insights = Insights.fromJson(Json({
      'spending': {'month': '2026-10', 'total': '0.00', 'categories': <Object>[]},
      'netWorth': {
        'points': [point('2026-10-01', '0.00')],
      },
    }));

    expect(insights.netWorth, isEmpty);
  });

  test('a bill whose day has passed says it was due, not that it is due', () {
    final l10n = lookupAppLocalizations(const Locale('pt'));
    final today = DateTime(2026, 10, 2);

    expect(l10n.billDue(DateTime(2026, 9, 10), now: today), 'Venceu em 10/09/2026');
    expect(l10n.billDue(DateTime(2026, 10, 2), now: today), 'Vence em 02/10/2026');
    expect(l10n.billDue(DateTime(2026, 10, 10), now: today), 'Vence em 10/10/2026');
  });

  testWidgets('the spending donut is thin, rounded and puts a category\'s share in the hole when tapped', (tester) async {
    final bff = FakeBff()
      ..json('GET', '/api/insights', {
        'spending': {
          'month': '2026-10',
          'total': '1000.00',
          'categories': [
            {'category': 'Education', 'total': '600.00'},
            {'category': 'Services', 'total': '400.00'},
          ],
        },
        'netWorth': {'points': <Object>[]},
      });
    tester.view.physicalSize = const Size(360, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MultiProvider(
      providers: [
        Provider.value(value: InsightsApi(fakeClient(bff))),
        ChangeNotifierProvider.value(value: DataChanges()),
      ],
      child: MaterialApp(
        theme: AppTheme.light(),
        locale: const Locale('pt', 'BR'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
        home: const Builder(builder: buildInsights),
      ),
    ));
    await tester.pumpAndSettle();

    final sections = tester.widget<PieChart>(find.byType(PieChart)).data.sections;
    expect(sections.map((section) => section.radius), everyElement(DonutChart.ringWidth));
    expect(sections.map((section) => section.cornerRadius), everyElement(greaterThan(0)));
    expect(find.text('60,0%'), findsOneWidget, reason: 'only in the legend');

    await tester.tap(find.text('Educação'));
    await tester.pumpAndSettle();

    expect(find.text('60,0%'), findsNWidgets(2), reason: 'in the hole too');
    expect(find.text('Total'), findsNothing);

    await tester.tap(find.text('Educação').last);
    await tester.pumpAndSettle();

    expect(find.text('Total'), findsOneWidget);
    expect(find.text('60,0%'), findsOneWidget);
  });
}
