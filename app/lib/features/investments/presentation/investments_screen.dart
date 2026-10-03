import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/format/percent.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/donut_chart.dart';
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

  /// From this content width the positions by kind and the charts sit side by side.
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
                        // The evolution on top and the allocation under it; the positions by kind
                        // follow them on a phone and sit to their left on a wide screen.
                        final charts = [const _EvolutionCard(), _AllocationCard(portfolio: portfolio)];
                        final kinds = [
                          for (final kind in portfolio.byKind) _KindCard(kind: kind, positions: portfolio.positionsOf(kind.kind)),
                        ];
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          spacing: 16,
                          children: [
                            if (controller.refreshError != null) RefreshErrorBanner(error: controller.refreshError!),
                            if (constraints.maxWidth >= _sideBySideWidth)
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Expanded(
                                    flex: 2,
                                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, spacing: 16, children: kinds),
                                  ),
                                  const SizedBox(width: 16),
                                  Expanded(
                                    flex: 3,
                                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, spacing: 16, children: charts),
                                  ),
                                ],
                              )
                            else ...[
                              ...charts,
                              ...kinds,
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

/// The portfolio by kind, in a donut that shows a kind's share and amount when tapped.
class _AllocationCard extends StatelessWidget {
  const _AllocationCard({required this.portfolio});

  final Portfolio portfolio;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return SectionCard(
      title: l10n.investmentsAllocation,
      child: DonutChart(
        totalLabel: l10n.investmentsTotal,
        total: portfolio.total,
        slices: [
          for (final kind in portfolio.byKind)
            if (!kind.total.isZero && !kind.total.isNegative) DonutSlice(kind.kind.label(l10n), kind.total),
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
