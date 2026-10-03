import 'package:fl_chart/fl_chart.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/categories.dart';
import '../../../core/format/dates.dart';
import '../../../core/format/percent.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/money/money.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
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

class _SpendingCard extends StatelessWidget {
  const _SpendingCard({required this.spending});

  /// The donut shows the largest categories and joins the rest, so slices stay readable.
  static const maxSlices = 6;

  final Spending spending;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors.chart;
    final slices = _slices(spending.categories, l10n);
    return SectionCard(
      title: l10n.insightsSpending,
      child: spending.categories.isEmpty
          ? Text(l10n.insightsNoSpending)
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  height: 200,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      PieChart(PieChartData(
                        centerSpaceRadius: 64,
                        sectionsSpace: 2,
                        sections: [
                          for (var index = 0; index < slices.length; index++)
                            PieChartSectionData(
                              value: slices[index].total.toChartValue(),
                              color: colors[index % colors.length],
                              radius: 28,
                              showTitle: false,
                            ),
                        ],
                      )),
                      Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(l10n.insightsTotal, style: theme.textTheme.labelMedium),
                          MoneyText(spending.total, style: theme.textTheme.titleMedium),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                for (var index = 0; index < slices.length; index++)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Row(
                      children: [
                        Container(
                          width: 10,
                          height: 10,
                          decoration: BoxDecoration(color: colors[index % colors.length], shape: BoxShape.circle),
                        ),
                        const SizedBox(width: 8),
                        Expanded(child: Text(slices[index].label)),
                        Text(formatPercent(slices[index].total.shareOf(spending.total)),
                            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                        const SizedBox(width: 12),
                        MoneyText(slices[index].total),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }

  static List<_Slice> _slices(List<CategoryTotal> categories, AppLocalizations l10n) {
    if (categories.length <= maxSlices) {
      return [for (final category in categories) _Slice(Categories.label(category.category), category.total)];
    }
    return [
      for (final category in categories.take(maxSlices - 1)) _Slice(Categories.label(category.category), category.total),
      _Slice(l10n.insightsOtherCategories, categories.skip(maxSlices - 1).map((category) => category.total).sum()),
    ];
  }
}

class _Slice {
  const _Slice(this.label, this.total);

  final String label;
  final Money total;
}

class _NetWorthCard extends StatelessWidget {
  const _NetWorthCard({required this.points});

  final List<NetWorthPoint> points;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
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
            SizedBox(
              height: 220,
              child: LineChart(LineChartData(
                gridData: FlGridData(
                  drawVerticalLine: false,
                  getDrawingHorizontalLine: (_) => FlLine(color: theme.colorScheme.outlineVariant, strokeWidth: 0.5),
                ),
                borderData: FlBorderData(show: false),
                titlesData: FlTitlesData(
                  topTitles: const AxisTitles(),
                  rightTitles: const AxisTitles(),
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 72,
                      minIncluded: false,
                      maxIncluded: false,
                      getTitlesWidget: (value, meta) => SideTitleWidget(
                        meta: meta,
                        child: Text(Money.parse(value.toStringAsFixed(2)).formatCompact(), style: theme.textTheme.labelSmall),
                      ),
                    ),
                  ),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 28,
                      minIncluded: false,
                      maxIncluded: false,
                      interval: _dayInterval(points),
                      getTitlesWidget: (value, meta) => SideTitleWidget(
                        meta: meta,
                        child: Text(Dates.dayMonth(_dateAt(value)), style: theme.textTheme.labelSmall),
                      ),
                    ),
                  ),
                ),
                lineTouchData: LineTouchData(
                  touchTooltipData: LineTouchTooltipData(
                    getTooltipColor: (_) => theme.colorScheme.surfaceContainerHighest,
                    getTooltipItems: (spots) => [
                      for (final spot in spots)
                        LineTooltipItem(
                          '${Dates.short(_dateAt(spot.x))}\n${Money.parse(spot.y.toStringAsFixed(2)).format()}',
                          theme.textTheme.bodySmall!,
                        ),
                    ],
                  ),
                ),
                lineBarsData: [
                  LineChartBarData(
                    spots: [for (final point in points) FlSpot(_dayOf(point.date), point.netWorth.toChartValue())],
                    isCurved: true,
                    preventCurveOverShooting: true,
                    color: color,
                    barWidth: 2.5,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(show: true, color: color.withValues(alpha: 0.12)),
                  ),
                ],
              )),
            ),
        ],
      ),
    );
  }

  /// The x axis counts days since the epoch, so gaps between snapshots keep their width.
  static double _dayOf(DateTime date) => DateTime.utc(date.year, date.month, date.day).millisecondsSinceEpoch / Duration.millisecondsPerDay;

  static DateTime _dateAt(double day) {
    final utc = DateTime.fromMillisecondsSinceEpoch((day * Duration.millisecondsPerDay).round(), isUtc: true);
    return DateTime(utc.year, utc.month, utc.day);
  }

  /// About four labels along the axis, whatever the span.
  static double _dayInterval(List<NetWorthPoint> points) {
    final span = _dayOf(points.last.date) - _dayOf(points.first.date);
    return span <= 4 ? 1 : (span / 4).ceilToDouble();
  }
}

/// Built by the router for the insights branch.
Widget buildInsights(BuildContext context) => ChangeNotifierProvider(
      create: (context) => InsightsController(context.read<InsightsApi>(), dataChanges: context.read())..load(),
      child: const InsightsScreen(),
    );
