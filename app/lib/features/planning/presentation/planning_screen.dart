import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/format/categories.dart';
import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/money/money.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/planning_api.dart';
import 'fixed_expense_suggestions_controller.dart';
import 'plan_bar.dart';
import 'planning_controller.dart';

/// The month's plan: what is left of the salary after the card bills and the fixed expenses.
/// The summary leads; the salary, each bill with its purchases and the fixed expenses follow.
class PlanningScreen extends StatelessWidget {
  const PlanningScreen({super.key});

  /// From here up, the salary and the fixed expenses sit beside the bills.
  static const twoColumnsWidth = 900.0;

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<PlanningController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navPlanning)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: MonthSelector(
              month: controller.month,
              label: Dates.month(controller.month),
              latest: controller.latestMonth,
              onChanged: controller.setMonth,
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.refresh,
              child: LoadableView(
                state: controller.state,
                onRetry: controller.load,
                builder: (context, plan) => LayoutBuilder(builder: (context, constraints) {
                  const gap = SizedBox(height: 16);
                  final wide = constraints.maxWidth >= twoColumnsWidth;
                  final salary = _SalaryCard(salary: plan.salary);
                  final fixed = _FixedExpensesCard(plan: plan);
                  final bills = _BillsColumn(bills: plan.bills);
                  return ListView(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                    children: [
                      ContentWidth(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (controller.refreshError != null) ...[
                              RefreshErrorBanner(error: controller.refreshError!),
                              gap,
                            ],
                            _SummaryCard(plan: plan),
                            gap,
                            if (wide)
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Expanded(child: Column(children: [salary, gap, fixed])),
                                  const SizedBox(width: 16),
                                  Expanded(child: bills),
                                ],
                              )
                            else ...[salary, gap, bills, gap, fixed],
                          ],
                        ),
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

/// The blue card on top: what is left, and the salary split between the bills, the fixed
/// expenses and the rest.
class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.plan});

  final MonthPlan plan;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors;
    final onFeature = colors.onFeature;
    final leftover = plan.totals.leftover;
    final slices = planSlices(plan, context);
    return Card(
      color: colors.feature,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.planningLeftover, style: theme.textTheme.titleMedium?.copyWith(color: onFeature)),
            const SizedBox(height: 8),
            MoneyText(leftover, style: theme.textTheme.displaySmall?.copyWith(color: onFeature)),
            const SizedBox(height: 4),
            Text(l10n.planningLeftoverFormula,
                style: theme.textTheme.bodySmall?.copyWith(color: onFeature.withValues(alpha: 0.8))),
            const SizedBox(height: 16),
            PlanBar(slices: slices, track: onFeature.withValues(alpha: 0.2)),
            const SizedBox(height: 12),
            PlanLegend(slices: slices, textColor: onFeature),
            if (plan.salary.source == SalarySource.none) ...[
              const SizedBox(height: 12),
              _Notice(text: l10n.planningNoSalaryHint, color: onFeature),
            ] else if (leftover.isNegative) ...[
              const SizedBox(height: 12),
              _Notice(text: l10n.planningShortfall(leftover.abs().format()), color: onFeature),
            ],
          ],
        ),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.text, required this.color});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 18, color: color),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: TextStyle(color: color))),
        ],
      );
}

class _SalaryCard extends StatelessWidget {
  const _SalaryCard({required this.salary});

  final PlanSalary salary;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final amount = salary.amount;
    final date = salary.date;
    final explanation = switch (salary.source) {
      SalarySource.received => l10n.planningSalaryReceived(Dates.short(date!)),
      SalarySource.previousMonth => l10n.planningSalaryFromPreviousMonth(Dates.short(date!)),
      SalarySource.expected => l10n.planningSalaryExpected,
      SalarySource.none => l10n.planningSalaryNone,
    };
    return SectionCard(
      title: l10n.planningSalary,
      trailing: TextButton.icon(
        onPressed: () => _SalaryDialog.show(context, salary),
        icon: const Icon(Icons.tune, size: 18),
        label: Text(l10n.planningSalaryAdjust),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              amount == null
                  ? Text('—', style: theme.textTheme.headlineSmall)
                  : MoneyText(amount, style: theme.textTheme.headlineSmall),
              if (salary.predicted) _Tag(l10n.planningSalaryPredicted, color: context.walletColors.warning),
            ],
          ),
          const SizedBox(height: 8),
          Text(explanation, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}

/// A small rounded label: a purchase's category, its installment, the "Previsto" badge.
class _Tag extends StatelessWidget {
  const _Tag(this.text, {this.color});

  final String text;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tint = color;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: tint == null ? theme.colorScheme.surfaceContainerHighest : tint.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(text,
          style: theme.textTheme.labelSmall?.copyWith(color: tint ?? theme.colorScheme.onSurfaceVariant)),
    );
  }
}

class _BillsColumn extends StatelessWidget {
  const _BillsColumn({required this.bills});

  final List<PlanBill> bills;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (bills.isEmpty) {
      return SectionCard(title: l10n.planningBills, child: Text(l10n.planningNoBills));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var index = 0; index < bills.length; index++) ...[
          if (index > 0) const SizedBox(height: 16),
          _BillCard(key: ValueKey('${bills[index].accountId}-${bills[index].dueDate}'), bill: bills[index]),
        ],
      ],
    );
  }
}

/// One card's bill: the bank, the due date and the total, then its purchases, the largest
/// first, and what the bank charged beyond them.
class _BillCard extends StatefulWidget {
  const _BillCard({super.key, required this.bill});

  /// Purchases shown before "mais N compras".
  static const collapsedItems = 4;

  final PlanBill bill;

  @override
  State<_BillCard> createState() => _BillCardState();
}

class _BillCardState extends State<_BillCard> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final bill = widget.bill;
    final hidden = bill.items.length - _BillCard.collapsedItems;
    final shown = _expanded || hidden <= 0 ? bill.items : bill.items.take(_BillCard.collapsedItems);
    final today = context.read<PlanningController>().today;
    final subtitle = [?bill.institutionName, l10n.billDue(bill.dueDate, now: today)].join(' · ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                InstitutionAvatar(name: bill.institutionName ?? bill.cardName, imageUrl: bill.institutionImageUrl, radius: 18),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(bill.label, style: theme.textTheme.titleSmall),
                      Text(subtitle,
                          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                MoneyText(bill.total, style: theme.textTheme.titleMedium),
              ],
            ),
            if (bill.open) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  _Tag(l10n.planningBillOpen, color: theme.colorScheme.primary),
                  const SizedBox(width: 8),
                  if (bill.closingDate != null)
                    Expanded(
                      child: Text(l10n.planningBillOpenHelp(Dates.short(bill.closingDate!)),
                          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ),
                ],
              ),
            ],
            const SizedBox(height: 8),
            if (bill.items.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(l10n.planningBillNoItems, style: theme.textTheme.bodySmall),
              ),
            for (final item in shown) _BillItemRow(item: item),
            if (hidden > 0)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: () => setState(() => _expanded = !_expanded),
                  child: Text(_expanded ? l10n.planningBillLess : l10n.planningBillMore(hidden)),
                ),
              ),
            if (!bill.otherCharges.isZero) _OtherChargesRow(amount: bill.otherCharges),
          ],
        ),
      ),
    );
  }
}

class _BillItemRow extends StatelessWidget {
  const _BillItemRow({required this.item});

  final PlanBillItem item;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final installment = item.installment;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.description, maxLines: 1, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodyMedium),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(Dates.dayMonth(item.date),
                        style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    _Tag(Categories.label(item.category)),
                    if (installment != null) _Tag(l10n.transactionInstallment(installment), color: theme.colorScheme.primary),
                    if (item.isRefund) _Tag(l10n.planningBillRefund, color: context.walletColors.inflow),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          MoneyText(item.amount, inflow: item.isRefund ? true : null, style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _OtherChargesRow extends StatelessWidget {
  const _OtherChargesRow({required this.amount});

  final Money amount;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(top: 4, bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.planningBillOtherCharges, style: theme.textTheme.bodyMedium),
                Text(amount.isNegative ? l10n.planningBillOtherCreditsHelp : l10n.planningBillOtherChargesHelp,
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          MoneyText(amount.abs(), inflow: amount.isNegative ? true : null, style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _FixedExpensesCard extends StatelessWidget {
  const _FixedExpensesCard({required this.plan});

  final MonthPlan plan;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final expenses = plan.fixedExpenses;
    return SectionCard(
      title: l10n.planningFixed,
      flush: true,
      trailing: TextButton.icon(
        onPressed: () => _FixedExpenseDialog.show(context),
        icon: const Icon(Icons.add, size: 18),
        label: Text(l10n.planningFixedAdd),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (expenses.isEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(l10n.planningFixedEmpty, style: theme.textTheme.bodyMedium),
            )
          else ...[
            for (final expense in expenses)
              ListTile(
                leading: CircleAvatar(
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  foregroundColor: theme.colorScheme.onSurfaceVariant,
                  child: const Icon(Icons.event_repeat, size: 20),
                ),
                title: Text(expense.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text([
                  l10n.planningFixedDay(expense.dayOfMonth),
                  if (expense.category != null) Categories.label(expense.category),
                ].join(' · ')),
                trailing: MoneyText(expense.amount, style: theme.textTheme.bodyLarge),
                onTap: () => _FixedExpenseDialog.show(context, expense: expense),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              child: Row(
                children: [
                  Expanded(child: Text(l10n.planningFixedTotal, style: theme.textTheme.titleSmall)),
                  MoneyText(plan.totals.fixedExpenses, style: theme.textTheme.titleSmall),
                ],
              ),
            ),
          ],
          const _SuggestionsSection(),
        ],
      ),
    );
  }
}

/// Debits that repeat every month in the bank accounts and are not fixed expenses yet. A tap
/// opens the form filled in; the cross dismisses one for good. Nothing shows while they load,
/// when there are none, or when they fail: the fixed expenses are what this card is about.
class _SuggestionsSection extends StatefulWidget {
  const _SuggestionsSection();

  /// Suggestions shown before "mais N sugestões".
  static const collapsedItems = 3;

  @override
  State<_SuggestionsSection> createState() => _SuggestionsSectionState();
}

class _SuggestionsSectionState extends State<_SuggestionsSection> {
  bool _expanded = false;

  Future<void> _dismiss(FixedExpenseSuggestion suggestion) async {
    final l10n = context.l10n;
    final error = await context.read<FixedExpenseSuggestionsController>().dismiss(suggestion.id);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error == null ? l10n.planningSuggestionDismissed : l10n.errorMessage(error))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<FixedExpenseSuggestionsController>().state;
    if (state is! Loaded<List<FixedExpenseSuggestion>> || state.value.isEmpty) {
      return const SizedBox.shrink();
    }
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final suggestions = state.value;
    final hidden = suggestions.length - _SuggestionsSection.collapsedItems;
    final shown = _expanded || hidden <= 0 ? suggestions : suggestions.take(_SuggestionsSection.collapsedItems);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Divider(height: 16),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
          child: Row(
            children: [
              Icon(Icons.auto_awesome, size: 18, color: theme.colorScheme.primary),
              const SizedBox(width: 8),
              Expanded(child: Text(l10n.planningSuggestions, style: theme.textTheme.titleSmall)),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
          child: Text(l10n.planningSuggestionsHelp,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ),
        for (final suggestion in shown)
          ListTile(
            key: ValueKey(suggestion.id),
            title: Text(suggestion.name, maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text(l10n.planningSuggestionDetails(suggestion.amount.format(), suggestion.dayOfMonth, suggestion.months)),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: l10n.planningSuggestionDismiss,
                  icon: const Icon(Icons.close),
                  onPressed: () => _dismiss(suggestion),
                ),
                IconButton(
                  tooltip: l10n.planningSuggestionAdd,
                  icon: Icon(Icons.add_circle_outline, color: theme.colorScheme.primary),
                  onPressed: () => _FixedExpenseDialog.show(context, suggestion: suggestion),
                ),
              ],
            ),
            onTap: () => _FixedExpenseDialog.show(context, suggestion: suggestion),
          ),
        if (hidden > 0)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: () => setState(() => _expanded = !_expanded),
                child: Text(_expanded ? l10n.planningBillLess : l10n.planningSuggestionsMore(hidden)),
              ),
            ),
          ),
      ],
    );
  }
}

/// "Ajustar": the salary to use while the month's has not landed, or back to the automatic one.
class _SalaryDialog extends StatefulWidget {
  const _SalaryDialog({required this.salary});

  final PlanSalary salary;

  static Future<void> show(BuildContext context, PlanSalary salary) async {
    final controller = context.read<PlanningController>();
    final message = await showDialog<String>(
      context: context,
      builder: (dialogContext) => ChangeNotifierProvider.value(value: controller, child: _SalaryDialog(salary: salary)),
    );
    if (message != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  State<_SalaryDialog> createState() => _SalaryDialogState();
}

class _SalaryDialogState extends State<_SalaryDialog> {
  late final _amount = TextEditingController(text: (widget.salary.expectedAmount ?? widget.salary.amount)?.toInput() ?? '');
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final l10n = context.l10n;
    final amount = Money.tryParseInput(_amount.text);
    if (amount == null || amount.isZero) {
      setState(() => _error = l10n.planningAmountInvalid);
      return;
    }
    await _run(() => context.read<PlanningController>().setExpectedSalary(amount), l10n.planningSalarySaved);
  }

  Future<void> _useAutomatic() async =>
      _run(() => context.read<PlanningController>().clearExpectedSalary(), context.l10n.planningSalaryCleared);

  Future<void> _run(Future<ApiException?> Function() action, String success) async {
    final l10n = context.l10n;
    setState(() {
      _busy = true;
      _error = null;
    });
    final error = await action();
    if (!mounted) {
      return;
    }
    if (error == null) {
      Navigator.of(context).pop(success);
    } else {
      setState(() {
        _busy = false;
        _error = l10n.errorMessage(error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.planningSalaryDialogTitle),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.planningSalaryDialogHelp),
            const SizedBox(height: 16),
            TextField(
              controller: _amount,
              autofocus: true,
              enabled: !_busy,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,R$ ]'))],
              decoration: InputDecoration(labelText: l10n.planningSalaryField, prefixText: 'R\$ ', errorText: _error),
              onSubmitted: (_) => _save(),
            ),
          ],
        ),
      ),
      actions: [
        if (widget.salary.expectedAmount != null)
          TextButton(onPressed: _busy ? null : _useAutomatic, child: Text(l10n.planningSalaryUseAutomatic)),
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(
          onPressed: _busy ? null : _save,
          child: _busy
              ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(l10n.actionSave),
        ),
      ],
    );
  }
}

/// Adds a fixed expense or, given one, changes or deletes it. Given a suggestion, it adds one
/// filled in with the suggestion's values.
class _FixedExpenseDialog extends StatefulWidget {
  const _FixedExpenseDialog({this.expense, this.suggestion});

  /// What people here pay outside the card, as the provider names them; the app shows them in
  /// Portuguese like any transaction's category.
  static const categories = [
    'Rent',
    'Housing',
    'Education',
    'University',
    'School',
    'Utilities',
    'Electricity',
    'Water',
    'Telecommunications',
    'Health insurance',
    'Healthcare',
    'Gyms and fitness centers',
    'Insurance',
    'Loans and financing',
    'Taxes',
    'Transportation',
    'Services',
    'Donations',
    'Other',
  ];

  final FixedExpense? expense;
  final FixedExpenseSuggestion? suggestion;

  static Future<void> show(BuildContext context, {FixedExpense? expense, FixedExpenseSuggestion? suggestion}) async {
    final controller = context.read<PlanningController>();
    final message = await showDialog<String>(
      context: context,
      builder: (dialogContext) => ChangeNotifierProvider.value(
          value: controller, child: _FixedExpenseDialog(expense: expense, suggestion: suggestion)),
    );
    if (message != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  State<_FixedExpenseDialog> createState() => _FixedExpenseDialogState();
}

class _FixedExpenseDialogState extends State<_FixedExpenseDialog> {
  late final FixedExpenseDraft? _start = widget.suggestion?.toDraft();
  late final _name = TextEditingController(text: widget.expense?.name ?? _start?.name ?? '');
  late final _amount = TextEditingController(text: (widget.expense?.amount ?? _start?.amount)?.toInput() ?? '');
  late final _day = TextEditingController(text: (widget.expense?.dayOfMonth ?? _start?.dayOfMonth)?.toString() ?? '');
  late String? _category = widget.expense?.category ?? _start?.category;
  String? _nameError;
  String? _amountError;
  String? _dayError;
  String? _error;
  bool _busy = false;
  bool _confirmingDelete = false;

  @override
  void dispose() {
    _name.dispose();
    _amount.dispose();
    _day.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final l10n = context.l10n;
    final name = _name.text.trim();
    final amount = Money.tryParseInput(_amount.text);
    final day = int.tryParse(_day.text.trim());
    setState(() {
      _nameError = name.isEmpty ? l10n.validationRequired : null;
      _amountError = amount == null || amount.isZero ? l10n.planningAmountInvalid : null;
      _dayError = day == null || day < 1 || day > 31 ? l10n.planningFixedDayInvalid : null;
    });
    if (_nameError != null || _amountError != null || _dayError != null) {
      return;
    }
    final draft = FixedExpenseDraft(name: name, amount: amount!, dayOfMonth: day!, category: _category);
    await _run(() => context.read<PlanningController>().saveFixedExpense(draft, id: widget.expense?.id),
        l10n.planningFixedSaved);
  }

  Future<void> _delete() async =>
      _run(() => context.read<PlanningController>().deleteFixedExpense(widget.expense!.id), context.l10n.planningFixedDeleted);

  Future<void> _run(Future<ApiException?> Function() action, String success) async {
    final l10n = context.l10n;
    setState(() {
      _busy = true;
      _error = null;
    });
    final error = await action();
    if (!mounted) {
      return;
    }
    if (error == null) {
      Navigator.of(context).pop(success);
    } else {
      setState(() {
        _busy = false;
        _confirmingDelete = false;
        _error = l10n.errorMessage(error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final expense = widget.expense;
    return AlertDialog(
      title: Text(expense == null ? l10n.planningFixedNew : l10n.planningFixedEdit),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.planningFixedHelp, style: theme.textTheme.bodySmall),
              const SizedBox(height: 16),
              TextField(
                controller: _name,
                autofocus: expense == null,
                enabled: !_busy,
                maxLength: 80,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(labelText: l10n.fieldName, errorText: _nameError, counterText: ''),
              ),
              const SizedBox(height: 12),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 3,
                    child: TextField(
                      controller: _amount,
                      enabled: !_busy,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,R$ ]'))],
                      decoration: InputDecoration(
                          labelText: l10n.planningFixedAmount, prefixText: 'R\$ ', errorText: _amountError),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: _day,
                      enabled: !_busy,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(2)],
                      decoration: InputDecoration(labelText: l10n.planningFixedDayField, errorText: _dayError),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String?>(
                initialValue: _category,
                isExpanded: true,
                decoration: InputDecoration(labelText: l10n.planningFixedCategory),
                items: [
                  DropdownMenuItem(value: null, child: Text(Categories.label(null))),
                  for (final category in {..._FixedExpenseDialog.categories, ?expense?.category, ?_start?.category})
                    DropdownMenuItem(value: category, child: Text(Categories.label(category))),
                ],
                onChanged: _busy ? null : (value) => setState(() => _category = value),
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
              ],
              if (_confirmingDelete) ...[
                const SizedBox(height: 16),
                Material(
                  color: theme.colorScheme.errorContainer,
                  borderRadius: BorderRadius.circular(12),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(l10n.planningFixedDeleteConfirm(expense!.name),
                            style: TextStyle(color: theme.colorScheme.onErrorContainer)),
                        const SizedBox(height: 8),
                        OverflowBar(
                          alignment: MainAxisAlignment.end,
                          spacing: 8,
                          children: [
                            TextButton(
                                onPressed: _busy ? null : () => setState(() => _confirmingDelete = false),
                                child: Text(l10n.actionCancel)),
                            FilledButton(
                              style: FilledButton.styleFrom(
                                  backgroundColor: theme.colorScheme.error, foregroundColor: theme.colorScheme.onError),
                              onPressed: _busy ? null : _delete,
                              child: Text(l10n.planningFixedDelete),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        if (expense != null && !_confirmingDelete)
          TextButton(
            onPressed: _busy ? null : () => setState(() => _confirmingDelete = true),
            style: TextButton.styleFrom(foregroundColor: theme.colorScheme.error),
            child: Text(l10n.planningFixedDelete),
          ),
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(
          onPressed: _busy || _confirmingDelete ? null : _save,
          child: _busy
              ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(l10n.actionSave),
        ),
      ],
    );
  }
}

/// Built by the router for the planning branch.
Widget buildPlanning(BuildContext context) => MultiProvider(
      providers: [
        ChangeNotifierProvider(
            create: (context) => PlanningController(context.read<PlanningApi>(), dataChanges: context.read())..load()),
        ChangeNotifierProvider(
            create: (context) =>
                FixedExpenseSuggestionsController(context.read<PlanningApi>(), dataChanges: context.read())..load()),
      ],
      child: const PlanningScreen(),
    );
