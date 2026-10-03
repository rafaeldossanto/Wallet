import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/categories.dart';
import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/money/money.dart';
import '../../../core/state/loadable.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/donut_chart.dart';
import '../../../core/widgets/loadable_view.dart';
import '../../../core/widgets/money_line_chart.dart';
import '../data/insights_api.dart';

class InsightsController extends LoadController<Insights> {
  InsightsController(this._api, {super.dataChanges, DateTime? today}) : _month = Dates.firstOfMonth(today ?? DateTime.now());

  final InsightsApi _api;
  DateTime _month;

  DateTime get month => _month;

  void setMonth(DateTime month) {
    _month = Dates.firstOfMonth(month);
    load();
  }

  @override
  Future<Insights> fetch() => _api.insights(_month);
}

class InsightsScreen extends StatelessWidget {
  const InsightsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<InsightsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navInsights)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: MonthSelector(
              month: controller.month,
              label: Dates.month(controller.month),
              latest: DateTime.now(),
              onChanged: controller.setMonth,
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.refresh,
              child: LoadableView(
                state: controller.state,
                onRetry: controller.load,
                builder: (context, insights) => LayoutBuilder(builder: (context, constraints) {
                  final wide = constraints.maxWidth >= 900;
                  final spending = _SpendingCard(spending: insights.spending);
                  final netWorth = _NetWorthCard(points: insights.netWorth);
                  return ListView(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    children: [
                      ContentWidth(
                        child: wide
                            ? Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [Expanded(child: spending), const SizedBox(width: 16), Expanded(child: netWorth)],
                              )
                            : Column(children: [spending, const SizedBox(height: 16), netWorth]),
                      ),
                    ],
                  );
                }),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Spending by category, in a donut that shows a category's share and amount when tapped.
class _SpendingCard extends StatelessWidget {
  const _SpendingCard({required this.spending});

  /// The donut shows the largest categories and joins the rest, so slices stay readable.
  static const maxSlices = 6;

  final Spending spending;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return SectionCard(
      title: l10n.insightsSpending,
      child: spending.categories.isEmpty
          ? Text(l10n.insightsNoSpending)
          : DonutChart(totalLabel: l10n.insightsTotal, total: spending.total, slices: _slices(spending.categories, l10n)),
    );
  }

  static List<DonutSlice> _slices(List<CategoryTotal> categories, AppLocalizations l10n) {
    if (categories.length <= maxSlices) {
      return [for (final category in categories) DonutSlice(Categories.label(category.category), category.total)];
    }
    return [
      for (final category in categories.take(maxSlices - 1)) DonutSlice(Categories.label(category.category), category.total),
      DonutSlice(l10n.insightsOtherCategories, categories.skip(maxSlices - 1).map((category) => category.total).sum()),
    ];
  }
}

class _NetWorthCard extends StatelessWidget {
  const _NetWorthCard({required this.points});

  final List<NetWorthPoint> points;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return SectionCard(
      title: l10n.insightsNetWorth,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (points.isNotEmpty) MoneyText(points.last.netWorth, style: theme.textTheme.headlineSmall),
          const SizedBox(height: 8),
          if (points.length < 2)
            Text(l10n.insightsNetWorthGrowing, style: theme.textTheme.bodyMedium)
          else
            MoneyLineChart(points: [for (final point in points) ChartPoint(point.date, point.netWorth)]),
        ],
      ),
    );
  }
}

/// Built by the router for the insights branch.
Widget buildInsights(BuildContext context) => ChangeNotifierProvider(
      create: (context) => InsightsController(context.read<InsightsApi>(), dataChanges: context.read())..load(),
      child: const InsightsScreen(),
    );
