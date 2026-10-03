import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/models/transaction.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/transactions_api.dart';
import 'transaction_tile.dart';
import 'transactions_controller.dart';

class TransactionsScreen extends StatefulWidget {
  const TransactionsScreen({super.key});

  @override
  State<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends State<TransactionsScreen> {
  static const _searchDelay = Duration(milliseconds: 400);

  final _scroll = ScrollController();
  final _search = TextEditingController();
  Timer? _searchTimer;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchTimer?.cancel();
    _scroll.dispose();
    _search.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scroll.position.extentAfter < 600) {
      context.read<TransactionsController>().loadMore();
    }
  }

  void _onSearchChanged(String value) {
    _searchTimer?.cancel();
    _searchTimer = Timer(_searchDelay, () => context.read<TransactionsController>().setQuery(value));
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<TransactionsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navTransactions)),
      body: Column(
        children: [
          ContentWidth(maxWidth: 900, child: _Filters(controller: controller, search: _search, onSearchChanged: _onSearchChanged)),
          const Divider(height: 1),
          Expanded(child: _buildList(context, controller)),
        ],
      ),
    );
  }

  Widget _buildList(BuildContext context, TransactionsController controller) {
    final l10n = context.l10n;
    final error = controller.error;
    if (controller.isLoading && controller.items.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && controller.items.isEmpty) {
      return ErrorView(error: error, onRetry: controller.reload);
    }
    if (controller.items.isEmpty) {
      return EmptyState(icon: Icons.receipt_long_outlined, message: l10n.transactionsEmpty);
    }
    final entries = _group(controller.items);
    final names = controller.accountNames;
    return RefreshIndicator(
      onRefresh: controller.reload,
      child: ListView.builder(
        controller: _scroll,
        itemCount: entries.length + 1,
        itemBuilder: (context, index) {
          if (index == entries.length) {
            return _Footer(controller: controller);
          }
          final entry = entries[index];
          return ContentWidth(
            maxWidth: 900,
            child: switch (entry) {
              _DayHeader(:final day) => Padding(
                  padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
                  child: Text(Dates.dayHeader(day, l10n), style: Theme.of(context).textTheme.labelLarge),
                ),
              _Row(:final transaction) => TransactionTile(transaction: transaction, accountName: names[transaction.accountId]),
            },
          );
        },
      ),
    );
  }

  /// The BFF sends the newest first; a header goes in front of each new day.
  static List<_Entry> _group(List<Transaction> items) {
    final entries = <_Entry>[];
    DateTime? current;
    for (final transaction in items) {
      if (current == null || transaction.bookedOn != current) {
        current = transaction.bookedOn;
        entries.add(_DayHeader(current));
      }
      entries.add(_Row(transaction));
    }
    return entries;
  }
}

sealed class _Entry {
  const _Entry();
}

final class _DayHeader extends _Entry {
  const _DayHeader(this.day);

  final DateTime day;
}

final class _Row extends _Entry {
  const _Row(this.transaction);

  final Transaction transaction;
}

class _Footer extends StatelessWidget {
  const _Footer({required this.controller});

  final TransactionsController controller;

  @override
  Widget build(BuildContext context) {
    final error = controller.error;
    if (controller.isLoadingMore) {
      return const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()));
    }
    if (error != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Center(
          child: TextButton.icon(
            onPressed: controller.loadMore,
            icon: const Icon(Icons.refresh),
            label: Text(context.l10n.errorMessage(error)),
          ),
        ),
      );
    }
    return const SizedBox(height: 32);
  }
}

class _Filters extends StatelessWidget {
  const _Filters({required this.controller, required this.search, required this.onSearchChanged});

  final TransactionsController controller;
  final TextEditingController search;
  final ValueChanged<String> onSearchChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final filters = controller.filters;
    final accountIds = {for (final account in controller.accounts) account.id};
    final selectedAccount = accountIds.contains(filters.accountId) ? filters.accountId : null;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: MonthSelector(
              month: filters.month,
              label: Dates.month(filters.month),
              latest: DateTime.now(),
              onChanged: controller.setMonth,
            ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              SegmentedButton<Direction?>(
                showSelectedIcon: false,
                segments: [
                  ButtonSegment(value: null, label: Text(l10n.filterAll)),
                  ButtonSegment(value: Direction.inflow, label: Text(l10n.filterInflow)),
                  ButtonSegment(value: Direction.outflow, label: Text(l10n.filterOutflow)),
                ],
                selected: {filters.direction},
                onSelectionChanged: (selection) => controller.setDirection(selection.first),
              ),
              if (controller.accounts.isNotEmpty)
                DropdownMenu<String?>(
                  key: ValueKey(selectedAccount),
                  initialSelection: selectedAccount,
                  label: Text(l10n.filterAccount),
                  width: 260,
                  onSelected: controller.setAccount,
                  dropdownMenuEntries: [
                    DropdownMenuEntry(value: null, label: l10n.filterAllAccounts),
                    for (final account in controller.accounts) DropdownMenuEntry(value: account.id, label: account.label),
                  ],
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: search,
            onChanged: onSearchChanged,
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.search),
              hintText: l10n.transactionsSearch,
              isDense: true,
            ),
          ),
        ],
      ),
    );
  }
}

/// Built by the router for the statement branch. [accountId] comes from the card screen's
/// "Ver compras".
Widget buildTransactions(BuildContext context, {String? accountId}) => ChangeNotifierProvider(
      key: ValueKey(accountId),
      create: (context) =>
          TransactionsController(context.read<TransactionsApi>(), dataChanges: context.read(), accountId: accountId)..start(),
      child: const TransactionsScreen(),
    );
