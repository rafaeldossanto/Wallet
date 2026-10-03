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

/// A donut of the portfolio by kind, the total in its hole. Tapping a slice, or its line in the
/// legend, puts that kind's share and amount in the hole; tapping it again goes back to the total.
class _AllocationCard extends StatefulWidget {
  const _AllocationCard({required this.portfolio});

  final Portfolio portfolio;

  @override
  State<_AllocationCard> createState() => _AllocationCardState();
}

class _AllocationCardState extends State<_AllocationCard> {
  static const _holeRadius = 64.0;
  static const _ringWidth = 28.0;

  /// The picked slice stands out of the ring by this much more.
  static const _pickedRingWidth = 34.0;

  /// Rounds the slice ends; the chart shrinks it on a slice too thin to take it.
  static const _sliceCornerRadius = 10.0;

  InvestmentKind? _picked;

  /// Null (a tap in the hole or off the ring) always goes back to the total.
  void _toggle(InvestmentKind? kind) => setState(() => _picked = kind == _picked ? null : kind);

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors.chart;
    final portfolio = widget.portfolio;
    final kinds = portfolio.byKind.where((kind) => !kind.total.isZero && !kind.total.isNegative).toList();
    // A kind gone after a refresh is no longer picked.
    final picked = kinds.where((kind) => kind.kind == _picked).firstOrNull;
    final hint = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    int touchedIndex(PieTouchResponse? response) => response?.touchedSection?.touchedSectionIndex ?? -1;

    return SectionCard(
      title: l10n.investmentsAllocation,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: (_holeRadius + _pickedRingWidth) * 2,
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (kinds.isNotEmpty)
                  PieChart(PieChartData(
                    centerSpaceRadius: _holeRadius,
                    sectionsSpace: kinds.length > 1 ? 4 : 0,
                    pieTouchData: PieTouchData(
                      touchCallback: (event, response) {
                        if (event is FlTapUpEvent) {
                          final index = touchedIndex(response);
                          _toggle(index >= 0 && index < kinds.length ? kinds[index].kind : null);
                        }
                      },
                      mouseCursorResolver: (event, response) =>
                          touchedIndex(response) >= 0 ? SystemMouseCursors.click : MouseCursor.defer,
                    ),
                    sections: [
                      for (var index = 0; index < kinds.length; index++)
                        PieChartSectionData(
                          value: kinds[index].total.toChartValue(),
                          color: picked == null || kinds[index] == picked
                              ? colors[index % colors.length]
                              : colors[index % colors.length].withValues(alpha: 0.35),
                          radius: kinds[index] == picked ? _pickedRingWidth : _ringWidth,
                          cornerRadius: _sliceCornerRadius,
                          showTitle: false,
                        ),
                    ],
                  )),
                // A large amount shrinks to fit the hole instead of spilling over the ring. Taps
                // go through it to the chart, so a tap in the hole goes back to the total.
                IgnorePointer(
                  child: SizedBox(
                    width: _holeRadius * 2 - 16,
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: picked == null
                            ? [
                                Text(l10n.investmentsTotal, style: theme.textTheme.labelMedium),
                                MoneyText(portfolio.total, style: theme.textTheme.titleMedium),
                              ]
                            : [
                                Text(picked.kind.label(l10n), style: theme.textTheme.labelMedium),
                                Text(formatPercent(picked.total.shareOf(portfolio.total)),
                                    style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w600)),
                                MoneyText(picked.total, style: theme.textTheme.titleSmall),
                              ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (kinds.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              picked == null ? l10n.investmentsAllocationHintPick : l10n.investmentsAllocationHintUnpick,
              style: hint,
              textAlign: TextAlign.center,
            ),
          ],
          const SizedBox(height: 12),
          for (var index = 0; index < kinds.length; index++)
            Semantics(
              selected: kinds[index] == picked,
              child: InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: () => _toggle(kinds[index].kind),
                child: Ink(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  decoration: BoxDecoration(
                    color: kinds[index] == picked ? theme.colorScheme.onSurface.withValues(alpha: 0.06) : null,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(color: colors[index % colors.length], shape: BoxShape.circle),
                      ),
                      const SizedBox(width: 8),
                      Expanded(child: Text(kinds[index].kind.label(l10n))),
                      Text(formatPercent(kinds[index].total.shareOf(portfolio.total)), style: hint),
                      const SizedBox(width: 12),
                      MoneyText(kinds[index].total),
                    ],
                  ),
                ),
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
