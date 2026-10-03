import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/format/percent.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/investments_api.dart';

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

class InvestmentsScreen extends StatelessWidget {
  const InvestmentsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<InvestmentsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navInvestments)),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: LoadableView(
          state: controller.state,
          onRetry: controller.load,
          builder: (context, portfolio) => portfolio.positions.isEmpty
              ? ListView(children: [EmptyState(icon: Icons.savings_outlined, message: l10n.investmentsEmpty)])
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    ContentWidth(
                      maxWidth: 900,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (controller.refreshError != null) ...[
                            RefreshErrorBanner(error: controller.refreshError!),
                            const SizedBox(height: 16),
                          ],
                          _AllocationCard(portfolio: portfolio),
                          for (final kind in portfolio.byKind) ...[
                            const SizedBox(height: 16),
                            _KindCard(kind: kind, positions: portfolio.positionsOf(kind.kind)),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _AllocationCard extends StatelessWidget {
  const _AllocationCard({required this.portfolio});

  final Portfolio portfolio;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors.chart;
    final kinds = portfolio.byKind.where((kind) => !kind.total.isZero && !kind.total.isNegative).toList();
    return SectionCard(
      title: l10n.investmentsTotal,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          MoneyText(portfolio.total, style: theme.textTheme.displaySmall),
          const SizedBox(height: 16),
          if (kinds.isNotEmpty)
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: SizedBox(
                height: 12,
                child: Row(
                  children: [
                    for (var index = 0; index < kinds.length; index++)
                      Expanded(
                        flex: (kinds[index].total.shareOf(portfolio.total) * 1000).round().clamp(1, 1000),
                        child: ColoredBox(color: colors[index % colors.length], child: const SizedBox.expand()),
                      ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 12),
          for (var index = 0; index < kinds.length; index++)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
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


/// Built by the router for the investments branch.
Widget buildInvestments(BuildContext context) => ChangeNotifierProvider(
      create: (context) => InvestmentsController(context.read<InvestmentsApi>(), dataChanges: context.read())..load(),
      child: const InvestmentsScreen(),
    );
