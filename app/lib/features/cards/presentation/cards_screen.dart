import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/models/credit_card.dart';
import '../../../core/state/loadable.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/cards_api.dart';

class CardsController extends LoadController<List<CreditCard>> {
  CardsController(this._api, {super.dataChanges});

  final CardsApi _api;

  @override
  Future<List<CreditCard>> fetch() => _api.cards();
}

class CardsScreen extends StatelessWidget {
  const CardsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<CardsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navCards)),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: LoadableView(
          state: controller.state,
          onRetry: controller.load,
          builder: (context, cards) => cards.isEmpty
              ? ListView(children: [EmptyState(icon: Icons.credit_card_off_outlined, message: l10n.cardsEmpty)])
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
                          for (final card in cards) ...[_CardDetails(card: card), const SizedBox(height: 16)],
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

class _CardDetails extends StatelessWidget {
  const _CardDetails({required this.card});

  final CreditCard card;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final bill = card.currentBill;
    final used = card.usedShare;
    return SectionCard(
      title: card.label,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (bill == null)
            Text(l10n.cardNoBill)
          else
            Wrap(
              spacing: 32,
              runSpacing: 12,
              children: [
                LabeledValue(
                    label: bill.dueDate.isBefore(Dates.today()) ? l10n.cardLastBill : l10n.cardCurrentBill,
                    value: MoneyText(bill.totalAmount)),
                LabeledValue(label: l10n.cardDueDate, value: Text(Dates.short(bill.dueDate))),
                if (bill.closingDate != null)
                  LabeledValue(label: l10n.cardClosingDate, value: Text(Dates.short(bill.closingDate!))),
                if (bill.minimumPayment != null)
                  LabeledValue(label: l10n.cardMinimumPayment, value: MoneyText(bill.minimumPayment!)),
              ],
            ),
          const SizedBox(height: 16),
          if (used != null) ...[
            LinearProgressIndicator(value: used, minHeight: 8, borderRadius: BorderRadius.circular(4)),
            const SizedBox(height: 8),
            Text(l10n.cardAvailable(card.availableCredit!.format(), card.creditLimit!.format()),
                style: theme.textTheme.bodyMedium),
          ],
          Text(l10n.cardCurrentBalance(card.currentBalance.format()),
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              TextButton.icon(
                onPressed: () => context.go(Uri(
                  path: '/cards/${card.accountId}',
                  queryParameters: {'name': card.label},
                ).toString()),
                icon: const Icon(Icons.history),
                label: Text(l10n.cardSeeBills),
              ),
              TextButton.icon(
                onPressed: () => context.go(Uri(path: '/transactions', queryParameters: {'accountId': card.accountId}).toString()),
                icon: const Icon(Icons.receipt_long_outlined),
                label: Text(l10n.cardSeePurchases),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class BillsController extends LoadController<List<Bill>> {
  BillsController(this._api, this.accountId);

  final CardsApi _api;
  final String accountId;

  @override
  Future<List<Bill>> fetch() => _api.bills(accountId);
}

class BillsScreen extends StatelessWidget {
  const BillsScreen({super.key, this.cardName});

  final String? cardName;

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<BillsController>();
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final today = Dates.today();
    return Scaffold(
      appBar: AppBar(title: Text(cardName ?? l10n.cardBills)),
      body: LoadableView(
        state: controller.state,
        onRetry: controller.load,
        builder: (context, bills) => bills.isEmpty
            ? EmptyState(icon: Icons.receipt_outlined, message: l10n.cardBillsEmpty)
            : ListView(
                padding: const EdgeInsets.symmetric(vertical: 8),
                children: [
                  for (final bill in bills)
                    ContentWidth(
                      maxWidth: 900,
                      child: ListTile(
                        title: Text(l10n.billDue(bill.dueDate)),
                        subtitle: Text([
                          if (bill.closingDate != null) l10n.cardBillClosed(Dates.short(bill.closingDate!)),
                          if (bill.minimumPayment != null) l10n.cardBillMinimum(bill.minimumPayment!.format()),
                        ].join(' · ')),
                        trailing: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            MoneyText(bill.totalAmount, style: theme.textTheme.titleSmall),
                            if (!bill.dueDate.isBefore(today))
                              Text(l10n.cardBillOpen,
                                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.primary)),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
      ),
    );
  }
}

/// Built by the router for the cards branch.
Widget buildCards(BuildContext context) => ChangeNotifierProvider(
      create: (context) => CardsController(context.read<CardsApi>(), dataChanges: context.read())..load(),
      child: const CardsScreen(),
    );

Widget buildBills(BuildContext context, {required String accountId, String? cardName}) => ChangeNotifierProvider(
      create: (context) => BillsController(context.read<CardsApi>(), accountId)..load(),
      child: BillsScreen(cardName: cardName),
    );
