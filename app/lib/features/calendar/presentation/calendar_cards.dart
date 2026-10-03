import 'package:intl/intl.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/format/categories.dart';
import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/money/money.dart';
import '../../../core/state/loadable.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../data/calendar_api.dart';
import 'calendar_controller.dart';

/// The blue card: the month's days, shaded by how much was spent; tap one to see what it was.
class SpendingCalendarCard extends StatelessWidget {
  const SpendingCalendarCard({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<CalendarController>();
    final colors = context.walletColors;
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final onFeature = colors.onFeature;
    return Card(
      color: colors.feature,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(l10n.calendarTitle, style: theme.textTheme.titleMedium?.copyWith(color: onFeature)),
                ),
                _MonthStepper(controller: controller),
              ],
            ),
            const SizedBox(height: 16),
            switch (controller.monthState) {
              Loading<CalendarMonth>() => SizedBox(
                  height: 260,
                  child: Center(child: CircularProgressIndicator(color: onFeature)),
                ),
              Failed<CalendarMonth>(:final error) => SizedBox(
                  height: 260,
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(l10n.errorMessage(error), textAlign: TextAlign.center, style: TextStyle(color: onFeature)),
                        const SizedBox(height: 12),
                        OutlinedButton(
                          style: OutlinedButton.styleFrom(foregroundColor: onFeature, side: BorderSide(color: onFeature)),
                          onPressed: controller.load,
                          child: Text(l10n.actionRetry),
                        ),
                      ],
                    ),
                  ),
                ),
              Loaded<CalendarMonth>(:final value) => _MonthGrid(controller: controller, calendar: value),
            },
          ],
        ),
      ),
    );
  }
}

class _MonthStepper extends StatelessWidget {
  const _MonthStepper({required this.controller});

  final CalendarController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final onFeature = context.walletColors.onFeature;
    final month = controller.month;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: l10n.monthPrevious,
          color: onFeature,
          onPressed: () => controller.setMonth(DateTime(month.year, month.month - 1)),
          icon: const Icon(Icons.chevron_left),
        ),
        Text(Dates.month(month), style: Theme.of(context).textTheme.labelLarge?.copyWith(color: onFeature)),
        IconButton(
          tooltip: l10n.monthNext,
          color: onFeature,
          disabledColor: onFeature.withValues(alpha: 0.3),
          onPressed: controller.isCurrentMonth ? null : () => controller.setMonth(DateTime(month.year, month.month + 1)),
          icon: const Icon(Icons.chevron_right),
        ),
      ],
    );
  }
}

class _MonthGrid extends StatelessWidget {
  const _MonthGrid({required this.controller, required this.calendar});

  final CalendarController controller;
  final CalendarMonth calendar;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final onFeature = context.walletColors.onFeature;
    final month = controller.month;
    final first = DateTime(month.year, month.month);
    // Brazilian calendars start on Sunday: DateTime.weekday is 7 for Sunday.
    final leading = first.weekday % 7;
    final daysInMonth = Dates.lastOfMonth(month).day;
    final busiest = calendar.busiest;
    final weekdays = DateFormat.EEEEE(Dates.locale).dateSymbols.NARROWWEEKDAYS;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            for (final weekday in weekdays)
              Expanded(
                child: Text(weekday.toUpperCase(),
                    textAlign: TextAlign.center,
                    style: theme.textTheme.labelSmall?.copyWith(color: onFeature.withValues(alpha: 0.7))),
              ),
          ],
        ),
        const SizedBox(height: 8),
        GridView.count(
          crossAxisCount: 7,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 4,
          crossAxisSpacing: 4,
          children: [
            for (var blank = 0; blank < leading; blank++) const SizedBox.shrink(),
            for (var day = 1; day <= daysInMonth; day++)
              _DayCell(
                date: DateTime(month.year, month.month, day),
                spending: calendar.dayOf(DateTime(month.year, month.month, day)),
                busiest: busiest,
                controller: controller,
              ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: Text(
                l10n.calendarSummary(calendar.days.length),
                style: theme.textTheme.bodySmall?.copyWith(color: onFeature.withValues(alpha: 0.8)),
              ),
            ),
            MoneyText(calendar.total, style: theme.textTheme.titleMedium?.copyWith(color: onFeature)),
          ],
        ),
      ],
    );
  }
}

/// A day: brighter the more was spent, lime when picked, ringed when it is today.
class _DayCell extends StatelessWidget {
  const _DayCell({required this.date, required this.spending, required this.busiest, required this.controller});

  final DateTime date;
  final CalendarDay? spending;
  final Money busiest;
  final CalendarController controller;

  @override
  Widget build(BuildContext context) {
    final colors = context.walletColors;
    final onFeature = colors.onFeature;
    final selected = controller.selected == date;
    final future = date.isAfter(controller.today);
    final today = date == controller.today;
    final share = spending == null ? 0.0 : spending!.total.shareOf(busiest);
    final background = selected
        ? colors.highlight
        : spending == null
            ? Colors.transparent
            : onFeature.withValues(alpha: 0.14 + 0.46 * share);
    final foreground = selected ? colors.onHighlight : onFeature.withValues(alpha: future ? 0.3 : (spending == null ? 0.65 : 1));
    final label = spending == null
        ? context.l10n.calendarDayNoSpending(Dates.dayMonth(date))
        : context.l10n.calendarDaySpending(Dates.dayMonth(date), spending!.total.format());
    return Semantics(
      button: !future,
      selected: selected,
      label: label,
      excludeSemantics: true,
      child: Tooltip(
        message: future ? '' : label,
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: future ? null : () => controller.select(date),
          child: Container(
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: background,
              shape: BoxShape.circle,
              border: today && !selected ? Border.all(color: onFeature, width: 1.5) : null,
            ),
            child: Text(
              '${date.day}',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: foreground,
                    fontWeight: spending == null ? FontWeight.w400 : FontWeight.w700,
                  ),
            ),
          ),
        ),
      ),
    );
  }
}

/// What was spent on the picked day, each line with its bank and account.
class DaySpendingCard extends StatelessWidget {
  const DaySpendingCard({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<CalendarController>();
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final selected = controller.selected;
    final calendar = switch (controller.monthState) {
      Loaded<CalendarMonth>(:final value) => value,
      _ => null,
    };
    final dayTotal = selected == null ? null : calendar?.dayOf(selected)?.total;
    return SectionCard(
      title: selected == null ? l10n.calendarNoDay : l10n.calendarDayTitle(Dates.dayHeader(selected, l10n)),
      trailing: dayTotal == null ? null : MoneyText(dayTotal, inflow: false, style: theme.textTheme.titleMedium),
      flush: true,
      child: switch (controller.dayState) {
        null => Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(calendar == null ? '' : l10n.calendarMonthEmpty),
          ),
        Loading<DaySpending>() => const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          ),
        Failed<DaySpending>(:final error) => Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(child: Text(l10n.errorMessage(error))),
                TextButton(onPressed: () => controller.select(selected!), child: Text(l10n.actionRetry)),
              ],
            ),
          ),
        Loaded<DaySpending>(:final value) => value.items.isEmpty
            ? Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(l10n.calendarDayEmpty))
            : Column(children: [for (final item in value.items) _DayItemTile(item: item)]),
      },
    );
  }
}

class _DayItemTile extends StatelessWidget {
  const _DayItemTile({required this.item});

  final DaySpendingItem item;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final where = [?item.institutionName, ?item.accountName].join(' · ');
    final details = [if (where.isNotEmpty) where, Categories.label(item.category)].join(' · ');
    final installment = item.installment;
    return ListTile(
      leading: InstitutionAvatar(name: item.institutionName ?? item.accountName, imageUrl: item.institutionImageUrl),
      title: Text(item.description, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(details, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          MoneyText(item.amount, inflow: false, style: theme.textTheme.bodyLarge),
          if (item.pending || installment != null)
            Text(
              [if (installment != null) l10n.transactionInstallment(installment), if (item.pending) l10n.transactionPending]
                  .join(' · '),
              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
        ],
      ),
    );
  }
}
