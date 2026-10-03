import 'package:fl_chart/fl_chart.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/format/percent.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../../../core/widgets/money_line_chart.dart';
import '../data/investments_api.dart';
import 'investment_history_controller.dart';

class InvestmentsController extends LoadController<Portfolio> {
  InvestmentsController(this._api, {super.dataChanges});

  final InvestmentsApi _api;

  @override
  Future<Portfolio> fetch() => _api.portfolio();
}

extension InvestmentKindLabel on InvestmentKind {
  String label(AppLocalizations l10n) => switch (this) {
        InvestmentKind.fixedIncome => l10n.investmentKindFixedIncome,
        InvestmentKind.treasury => l10n.investmentKindTreasury,
        InvestmentKind.fund => l10n.investmentKindFund,
        InvestmentKind.equity => l10n.investmentKindEquity,
        InvestmentKind.retirement => l10n.investmentKindRetirement,
        InvestmentKind.other => l10n.investmentKindOther,
      };
}

extension InvestmentPeriodLabel on InvestmentPeriod {
  String label(AppLocalizations l10n) => switch (this) {
        InvestmentPeriod.oneMonth => l10n.investmentPeriodOneMonth,
        InvestmentPeriod.threeMonths => l10n.investmentPeriodThreeMonths,
        InvestmentPeriod.sixMonths => l10n.investmentPeriodSixMonths,
        InvestmentPeriod.oneYear => l10n.investmentPeriodOneYear,
        InvestmentPeriod.all => l10n.investmentPeriodAll,
      };

  String hint(AppLocalizations l10n) => switch (this) {
        InvestmentPeriod.oneMonth => l10n.investmentPeriodOneMonthHint,
        InvestmentPeriod.threeMonths => l10n.investmentPeriodThreeMonthsHint,
        InvestmentPeriod.sixMonths => l10n.investmentPeriodSixMonthsHint,
        InvestmentPeriod.oneYear => l10n.investmentPeriodOneYearHint,
        InvestmentPeriod.all => l10n.investmentPeriodAllHint,
      };
}

class InvestmentsScreen extends StatelessWidget {
  const InvestmentsScreen({super.key});

  /// From this content width the allocation and the evolution sit side by side.
  static const _sideBySideWidth = 900.0;

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<InvestmentsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navInvestments)),
      body: RefreshIndicator(
        onRefresh: () => Future.wait([controller.refresh(), context.read<InvestmentHistoryController>().refresh()]),
        child: LoadableView(
          state: controller.state,
          onRetry: () {
            controller.load();
            context.read<InvestmentHistoryController>().load();
          },
          builder: (context, portfolio) => portfolio.positions.isEmpty
              ? ListView(children: [EmptyState(icon: Icons.savings_outlined, message: l10n.investmentsEmpty)])
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    ContentWidth(
                      child: LayoutBuilder(builder: (context, constraints) {
                        final allocation = _AllocationCard(portfolio: portfolio);
                        const evolution = _EvolutionCard();
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (controller.refreshError != null) ...[
                              RefreshErrorBanner(error: controller.refreshError!),
                              const SizedBox(height: 16),
                            ],
                            if (constraints.maxWidth >= _sideBySideWidth)
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [Expanded(child: allocation), const SizedBox(width: 16), const Expanded(child: evolution)],
                              )
                            else ...[
                              allocation,
                              const SizedBox(height: 16),
                              evolution,
                            ],
                            for (final kind in portfolio.byKind) ...[
                              const SizedBox(height: 16),
                              _KindCard(kind: kind, positions: portfolio.positionsOf(kind.kind)),
                            ],
                          ],
                        );
                      }),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

/// A donut of the portfolio by kind, the total in its hole.
class _AllocationCard extends StatelessWidget {
  const _AllocationCard({required this.portfolio});

  static const _holeRadius = 64.0;

  final Portfolio portfolio;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors.chart;
    final kinds = portfolio.byKind.where((kind) => !kind.total.isZero && !kind.total.isNegative).toList();
    return SectionCard(
      title: l10n.investmentsAllocation,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: 200,
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (kinds.isNotEmpty)
                  PieChart(PieChartData(
                    centerSpaceRadius: _holeRadius,
                    sectionsSpace: kinds.length > 1 ? 2 : 0,
                    sections: [
                      for (var index = 0; index < kinds.length; index++)
                        PieChartSectionData(
                          value: kinds[index].total.toChartValue(),
                          color: colors[index % colors.length],
                          radius: 28,
                          showTitle: false,
                        ),
                    ],
                  )),
                // A large total shrinks to fit the hole instead of spilling over the ring.
                SizedBox(
                  width: _holeRadius * 2 - 16,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(l10n.investmentsTotal, style: theme.textTheme.labelMedium),
                        MoneyText(portfolio.total, style: theme.textTheme.titleMedium),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          for (var index = 0; index < kinds.length; index++)
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
                  Expanded(child: Text(kinds[index].kind.label(l10n))),
                  Text(formatPercent(kinds[index].total.shareOf(portfolio.total)),
                      style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                  const SizedBox(width: 12),
                  MoneyText(kinds[index].total),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// The invested total over the period picked, and how much it moved.
class _EvolutionCard extends StatelessWidget {
  const _EvolutionCard();

  static const _chartHeight = 220.0;

  /// The change line above the chart and the gap under it.
  static const _changeHeight = 36.0;

  @override
  Widget build(BuildContext context) {
    final history = context.watch<InvestmentHistoryController>();
    final l10n = context.l10n;
    return SectionCard(
      title: l10n.investmentsEvolution,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SegmentedButton<InvestmentPeriod>(
            showSelectedIcon: false,
            style: SegmentedButton.styleFrom(
              visualDensity: VisualDensity.compact,
              padding: const EdgeInsets.symmetric(horizontal: 4),
            ),
            segments: [
              for (final period in InvestmentPeriod.values)
                ButtonSegment(value: period, label: Text(period.label(l10n)), tooltip: period.hint(l10n)),
            ],
            selected: {history.period},
            onSelectionChanged: (selected) => history.setPeriod(selected.single),
          ),
          const SizedBox(height: 16),
          // The spinner and the error take the chart's room, so the card does not jump around.
          ConstrainedBox(
            constraints: BoxConstraints(
              minHeight: history.state is Loaded<InvestmentHistory> ? 0 : _chartHeight + _changeHeight,
            ),
            child: LoadableView(
              state: history.state,
              onRetry: history.load,
              builder: (context, value) => _Evolution(history: value),
            ),
          ),
        ],
      ),
    );
  }
}

class _Evolution extends StatelessWidget {
  const _Evolution({required this.history});

  final InvestmentHistory history;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final change = history.change;
    if (change == null) {
      return Text(l10n.investmentsEvolutionGrowing, style: theme.textTheme.bodyMedium);
    }
    final color = change.isNegative
        ? theme.colorScheme.error
        : change.isZero
            ? theme.colorScheme.onSurfaceVariant
            : context.walletColors.inflow;
    final icon = change.isNegative
        ? Icons.trending_down
        : change.isZero
            ? Icons.trending_flat
            : Icons.trending_up;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                l10n.investmentsChange(change.format(signed: true), formatPercent(history.changeShare!, signed: true)),
                style: theme.textTheme.titleSmall?.copyWith(color: color),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        MoneyLineChart(
          height: _EvolutionCard._chartHeight,
          points: [for (final point in history.points) ChartPoint(point.date, point.total)],
        ),
      ],
    );
  }
}

class _KindCard extends StatelessWidget {
  const _KindCard({required this.kind, required this.positions});

  final KindTotal kind;
  final List<Position> positions;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return SectionCard(
      title: kind.kind.label(l10n),
      trailing: MoneyText(kind.total, style: theme.textTheme.titleMedium),
      flush: true,
      child: Column(
        children: [
          for (final position in positions)
            ListTile(
              title: Text(position.name),
              subtitle: Text([
                if (position.subtype != null) _subtypeLabel(position.subtype!),
                if (position.dueDate != null) l10n.investmentDue(Dates.short(position.dueDate!)),
              ].join(' · ')),
              trailing: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  MoneyText(position.balance, style: theme.textTheme.bodyLarge),
                  if (position.gain != null)
                    Text(
                      [
                        position.gain!.format(signed: true),
                        if (position.gainShare != null) formatPercent(position.gainShare!, signed: true),
                      ].join(' · '),
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: position.gain!.isNegative ? theme.colorScheme.error : context.walletColors.inflow,
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// Pluggy's subtypes that people recognise by name; the rest are left out of the line.
  static String _subtypeLabel(String subtype) => switch (subtype) {
        'CDB' || 'LCI' || 'LCA' || 'CRI' || 'CRA' || 'LC' || 'LF' => subtype,
        'TREASURY' => 'Tesouro Direto',
        'DEBENTURES' => 'Debêntures',
        'MULTIMARKET_FUND' => 'Multimercado',
        'FIXED_INCOME_FUND' => 'Fundo de renda fixa',
        'STOCK_FUND' => 'Fundo de ações',
        'ETF' => 'ETF',
        'STOCK' => 'Ações',
        'REAL_ESTATE_FUND' => 'FII',
        'RETIREMENT' => 'Previdência',
        _ => subtype,
      };
}

/// Built by the router for the investments branch. The chart loads alongside the portfolio, not
/// after it.
Widget buildInvestments(BuildContext context) => MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (context) => InvestmentsController(context.read<InvestmentsApi>(), dataChanges: context.read())..load(),
        ),
        ChangeNotifierProvider(
          lazy: false,
          create: (context) =>
              InvestmentHistoryController(context.read<InvestmentsApi>(), dataChanges: context.read())..load(),
        ),
      ],
      child: const InvestmentsScreen(),
    );
