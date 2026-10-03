import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/models/credit_card.dart';
import '../../../core/router/adaptive_shell.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../../../core/widgets/theme_toggle.dart';
import '../../calendar/data/calendar_api.dart';
import '../../calendar/presentation/calendar_cards.dart';
import '../../calendar/presentation/calendar_controller.dart';
import '../../transactions/presentation/transaction_tile.dart';
import '../data/home_api.dart';
import 'overview_controller.dart';

/// The dashboard: a greeting, the net worth and the month on one side, the spending calendar
/// and the picked day's spending on the other; one column on phones.
class OverviewScreen extends StatelessWidget {
  const OverviewScreen({super.key});

  /// From here up, the dashboard splits in two columns.
  static const twoColumnsWidth = 1000.0;

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OverviewController>();
    final l10n = context.l10n;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: () => Future.wait([controller.refresh(), context.read<CalendarController>().refresh()]),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth >= twoColumnsWidth;
              return ListView(
                padding: EdgeInsets.fromLTRB(wide ? 8 : 16, 16, wide ? 24 : 16, 24),
                children: [
                  ContentWidth(
                    maxWidth: 1320,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const _Header(),
                        const SizedBox(height: 20),
                        switch (controller.state) {
                          Loading<HomeData>() => const Padding(
                              padding: EdgeInsets.all(48),
                              child: Center(child: CircularProgressIndicator()),
                            ),
                          Failed<HomeData>(:final error) => ErrorView(error: error, onRetry: controller.load),
                          Loaded<HomeData>(:final value) => value.hasNoConnections
                              ? EmptyState(
                                  icon: Icons.account_balance_outlined,
                                  message: l10n.overviewEmpty,
                                  action: FilledButton(
                                      onPressed: () => context.go('/connections'), child: Text(l10n.overviewConnectFirst)),
                                )
                              : _Dashboard(data: value, wide: wide, medium: constraints.maxWidth >= 720),
                        },
                      ],
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

/// `Olá, Rafael!` and today's date; refresh, and on phones the theme switch (the rail has it
/// on larger screens).
class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final controller = context.watch<OverviewController>();
    final name = context.select<SessionController, String?>((session) => session.user?.firstName);
    final phone = MediaQuery.sizeOf(context).width < AdaptiveShell.mediumWidth;
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name == null ? l10n.navOverview : l10n.overviewGreeting(name), style: theme.textTheme.headlineMedium),
              const SizedBox(height: 4),
              Text(Dates.fullDate(DateTime.now()),
                  style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ],
          ),
        ),
        IconButton(
          tooltip: l10n.actionRefresh,
          onPressed: controller.isRefreshing ? null : controller.refresh,
          icon: const Icon(Icons.refresh),
        ),
        if (phone) const ThemeToggle(),
      ],
    );
  }
}

class _Dashboard extends StatelessWidget {
  const _Dashboard({required this.data, required this.wide, required this.medium});

  final HomeData data;
  final bool wide;

  /// Room for the calendar and the day side by side, but not for two full columns.
  final bool medium;

  @override
  Widget build(BuildContext context) {
    final banner = context.read<OverviewController>().refreshError;
    const gap = SizedBox(height: 16);
    final netWorth = _NetWorthCard(data: data);
    final month = _MonthCard(data: data);
    const calendar = SpendingCalendarCard();
    const day = SpendingListCard();
    final accounts = _AccountsCard(data: data);
    final cards = _CardsCard(data: data);
    final recent = _RecentTransactionsCard(data: data);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (banner != null) ...[RefreshErrorBanner(error: banner), gap],
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 3, child: Column(children: [netWorth, gap, month, gap, accounts, gap, recent])),
              const SizedBox(width: 16),
              Expanded(flex: 2, child: Column(children: [calendar, gap, day, gap, cards])),
            ],
          )
        else if (medium) ...[
          netWorth,
          gap,
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [const Expanded(child: calendar), const SizedBox(width: 16), const Expanded(child: day)],
          ),
          gap,
          month,
          gap,
          cards,
          gap,
          accounts,
          gap,
          recent,
        ]
        else ...[netWorth, gap, calendar, gap, day, gap, month, gap, cards, gap, accounts, gap, recent],
      ],
    );
  }
}

class _NetWorthCard extends StatelessWidget {
  const _NetWorthCard({required this.data});

  final HomeData data;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors;
    final overview = data.overview;
    return SectionCard(
      title: l10n.overviewNetWorth,
      trailing: overview?.lastSyncedAt == null
          ? null
          : Text(l10n.updatedAgo(Dates.timeAgo(overview!.lastSyncedAt!, l10n)),
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
      child: overview == null
          ? const UnavailableNotice()
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                MoneyText(overview.netWorth, style: theme.textTheme.displaySmall),
                const SizedBox(height: 20),
                StatTileRow(children: [
                  StatTile(
                    icon: Icons.account_balance_wallet_outlined,
                    color: colors.feature,
                    label: l10n.overviewCash,
                    value: MoneyText(overview.cashBalance),
                  ),
                  StatTile(
                    icon: Icons.trending_up,
                    color: colors.highlight,
                    label: l10n.overviewInvestments,
                    value: MoneyText(overview.investmentsTotal),
                  ),
                  StatTile(
                    icon: Icons.credit_card,
                    color: colors.chart[2],
                    label: l10n.overviewCardDebt,
                    value: MoneyText(overview.creditCardDebt),
                  ),
                ]),
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
    final colors = context.walletColors;
    final overview = data.overview;
    return SectionCard(
      title: overview == null ? l10n.overviewMonth : Dates.monthName(overview.month),
      child: overview == null
          ? const UnavailableNotice()
          : StatTileRow(children: [
              StatTile(
                icon: Icons.south_west,
                color: colors.inflow,
                label: l10n.overviewIncome,
                value: MoneyText(overview.monthIncome, inflow: true),
              ),
              StatTile(
                icon: Icons.north_east,
                color: colors.chart[4],
                label: l10n.overviewExpenses,
                value: MoneyText(overview.monthExpenses, inflow: false),
              ),
              StatTile(
                icon: Icons.balance,
                color: colors.chart[3],
                label: l10n.overviewMonthBalance,
                value: MoneyText(overview.monthBalance),
              ),
            ]),
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
Widget buildOverview(BuildContext context) => MultiProvider(
      providers: [
        ChangeNotifierProvider(
            create: (context) => OverviewController(context.read<HomeApi>(), dataChanges: context.read())..load()),
        ChangeNotifierProvider(
            create: (context) => CalendarController(context.read<CalendarApi>(), dataChanges: context.read())..load()),
      ],
      child: const OverviewScreen(),
    );

