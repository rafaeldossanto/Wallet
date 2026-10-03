import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/models/credit_card.dart';
import '../../../core/router/adaptive_shell.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../../transactions/presentation/transaction_tile.dart';
import '../data/home_api.dart';
import 'overview_controller.dart';

class OverviewScreen extends StatelessWidget {
  const OverviewScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OverviewController>();
    final l10n = context.l10n;
    final name = context.select<SessionController, String?>((session) => session.user?.firstName);
    return Scaffold(
      appBar: AppBar(
        title: Text(name == null ? l10n.navOverview : l10n.overviewGreeting(name)),
        actions: [
          IconButton(
            tooltip: l10n.actionRefresh,
            onPressed: controller.isRefreshing ? null : controller.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: LoadableView(
          state: controller.state,
          onRetry: controller.load,
          builder: (context, data) => data.hasNoConnections
              ? ListView(children: [
                  EmptyState(
                    icon: Icons.account_balance_outlined,
                    message: l10n.overviewEmpty,
                    action: FilledButton(onPressed: () => context.go('/connections'), child: Text(l10n.overviewConnectFirst)),
                  ),
                ])
              : _OverviewContent(data: data),
        ),
      ),
    );
  }
}

class _OverviewContent extends StatelessWidget {
  const _OverviewContent({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final controller = context.read<OverviewController>();
    final banner = controller.refreshError;
    return LayoutBuilder(builder: (context, constraints) {
      final wide = constraints.maxWidth >= AdaptiveShell.expandedWidth - 200;
      final netWorth = _NetWorthCard(data: data);
      final month = _MonthCard(data: data);
      final accounts = _AccountsCard(data: data);
      final cards = _CardsCard(data: data);
      final recent = _RecentTransactionsCard(data: data);
      const gap = SizedBox(height: 16);
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ContentWidth(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (banner != null) ...[RefreshErrorBanner(error: banner), gap],
                if (wide)
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(flex: 3, child: Column(children: [netWorth, gap, month, gap, accounts])),
                      const SizedBox(width: 16),
                      Expanded(flex: 2, child: Column(children: [cards, gap, recent])),
                    ],
                  )
                else ...[netWorth, gap, month, gap, accounts, gap, cards, gap, recent],
              ],
            ),
          ),
        ],
      );
    });
  }
}

class _NetWorthCard extends StatelessWidget {
  const _NetWorthCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final overview = data.overview;
    return SectionCard(
      title: l10n.overviewNetWorth,
      child: overview == null
          ? const UnavailableNotice()
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                MoneyText(overview.netWorth, style: theme.textTheme.displaySmall),
                if (overview.lastSyncedAt != null)
                  Text(l10n.updatedAgo(Dates.timeAgo(overview.lastSyncedAt!, l10n)),
                      style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 32,
                  runSpacing: 12,
                  children: [
                    LabeledValue(label: l10n.overviewCash, value: MoneyText(overview.cashBalance)),
                    LabeledValue(label: l10n.overviewInvestments, value: MoneyText(overview.investmentsTotal)),
                    LabeledValue(label: l10n.overviewCardDebt, value: MoneyText(overview.creditCardDebt)),
                  ],
                ),
              ],
            ),
    );
  }
}

class _MonthCard extends StatelessWidget {
  const _MonthCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final overview = data.overview;
    return SectionCard(
      title: overview == null ? l10n.overviewMonth : Dates.monthName(overview.month),
      child: overview == null
          ? const UnavailableNotice()
          : Wrap(
              spacing: 32,
              runSpacing: 12,
              children: [
                LabeledValue(label: l10n.overviewIncome, value: MoneyText(overview.monthIncome, inflow: true)),
                LabeledValue(label: l10n.overviewExpenses, value: MoneyText(overview.monthExpenses, inflow: false)),
                LabeledValue(label: l10n.overviewMonthBalance, value: MoneyText(overview.monthBalance)),
              ],
            ),
    );
  }
}

class _AccountsCard extends StatelessWidget {
  const _AccountsCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final failed = data.isUnavailable(HomeData.accountsPart);
    // A broker with only investments has no place here; one that needs attention always does.
    final shown = [
      for (final institution in data.institutions)
        if (institution.accounts.isNotEmpty || institution.needsAttention) institution,
    ];
    return SectionCard(
      title: l10n.overviewAccounts,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (failed) const Padding(padding: EdgeInsets.only(bottom: 8), child: UnavailableNotice()),
          if (shown.isEmpty && !failed) Text(l10n.overviewNoBankAccounts, style: theme.textTheme.bodySmall),
          for (final institution in shown) ...[
            Row(
              children: [
                InstitutionAvatar(name: institution.name, imageUrl: institution.imageUrl, radius: 14),
                const SizedBox(width: 12),
                Expanded(child: Text(institution.name ?? l10n.institutionUnknown, style: theme.textTheme.titleSmall)),
                if (institution.needsAttention)
                  Tooltip(
                    message: l10n.connectionNeedsAttention,
                    child: Icon(Icons.warning_amber, color: context.walletColors.warning, size: 20),
                  ),
              ],
            ),
            for (final account in institution.accounts)
              ListTile(
                dense: true,
                contentPadding: const EdgeInsets.only(left: 40),
                title: Text(account.label),
                trailing: MoneyText(account.balance, style: theme.textTheme.bodyLarge),
              ),
            const SizedBox(height: 8),
          ],
        ],
      ),
    );
  }
}

class _CardsCard extends StatelessWidget {
  const _CardsCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final failed = data.isUnavailable(HomeData.creditCardsPart);
    return SectionCard(
      title: l10n.navCards,
      trailing: TextButton(onPressed: () => context.go('/cards'), child: Text(l10n.actionSeeAll)),
      child: failed
          ? const UnavailableNotice()
          : data.creditCards.isEmpty
              ? Text(l10n.cardsEmpty)
              : Column(
                  children: [
                    for (final card in data.creditCards) _CardSummary(card: card),
                  ],
                ),
    );
  }
}

class _CardSummary extends StatelessWidget {
  const _CardSummary({required this.card});

  final CreditCard card;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final bill = card.currentBill;
    final used = card.usedShare;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: Text(card.label, style: theme.textTheme.titleSmall)),
              if (bill != null) MoneyText(bill.totalAmount, style: theme.textTheme.titleSmall),
            ],
          ),
          if (bill != null) Text(l10n.billDue(bill.dueDate), style: theme.textTheme.bodySmall),
          if (used != null) ...[
            const SizedBox(height: 8),
            LinearProgressIndicator(value: used, borderRadius: BorderRadius.circular(4)),
            const SizedBox(height: 4),
            Text(l10n.cardAvailable(card.availableCredit!.format(), card.creditLimit!.format()),
                style: theme.textTheme.bodySmall),
          ],
        ],
      ),
    );
  }
}

class _RecentTransactionsCard extends StatelessWidget {
  const _RecentTransactionsCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final names = data.accountNames;
    return SectionCard(
      title: l10n.overviewRecent,
      flush: true,
      trailing: TextButton(onPressed: () => context.go('/transactions'), child: Text(l10n.overviewSeeStatement)),
      child: data.isUnavailable(HomeData.recentTransactionsPart)
          ? const Padding(padding: EdgeInsets.symmetric(horizontal: 16), child: UnavailableNotice())
          : data.recentTransactions.isEmpty
              ? Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(l10n.overviewNoRecent))
              : Column(
                  children: [
                    for (final transaction in data.recentTransactions)
                      TransactionTile(transaction: transaction, accountName: names[transaction.accountId], showDate: true),
                  ],
                ),
    );
  }
}

/// Built by the router for the home branch.
Widget buildOverview(BuildContext context) => ChangeNotifierProvider(
      create: (context) => OverviewController(context.read<HomeApi>(), dataChanges: context.read())..load(),
      child: const OverviewScreen(),
    );

