import 'package:material_ui/material_ui.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/money/money.dart';
import '../../../core/theme/app_theme.dart';
import '../data/planning_api.dart';

/// One slice of the salary: a bill, the fixed expenses or what is left.
class PlanSlice {
  const PlanSlice(this.label, this.amount, this.color);

  final String label;
  final Money amount;
  final Color color;
}

/// The salary split in slices, in order: each bill, the fixed expenses, then what is left.
/// When the month does not close there is nothing left, and the bar splits what goes out.
List<PlanSlice> planSlices(MonthPlan plan, BuildContext context) {
  final l10n = context.l10n;
  final colors = context.walletColors;
  // The first chart color is the blue of the card the bar sits on.
  final billColors = colors.chart.sublist(2, 6);
  // A card goes by its bank ("Nubank" says more than "Cartão de crédito"), unless the bank has two.
  final banks = [for (final bill in plan.bills) bill.institutionName];
  String nameOf(PlanBill bill) {
    final bank = bill.institutionName;
    return bank != null && banks.where((other) => other == bank).length == 1 ? bank : bill.cardName;
  }

  return [
    for (var index = 0; index < plan.bills.length; index++)
      PlanSlice(nameOf(plan.bills[index]), plan.bills[index].total, billColors[index % billColors.length]),
    if (!plan.totals.fixedExpenses.isZero) PlanSlice(l10n.planningLegendFixed, plan.totals.fixedExpenses, colors.chart[6]),
    if (plan.totals.leftover > Money.zero) PlanSlice(l10n.planningLegendLeftover, plan.totals.leftover, colors.highlight),
  ];
}

/// A rounded bar of [slices], each as wide as its share of the whole.
class PlanBar extends StatelessWidget {
  const PlanBar({super.key, required this.slices, required this.track, this.height = 14});

  final List<PlanSlice> slices;
  final Color track;
  final double height;

  @override
  Widget build(BuildContext context) {
    final whole = slices.map((slice) => slice.amount).where((amount) => amount > Money.zero).sum();
    return ClipRRect(
      borderRadius: BorderRadius.circular(height / 2),
      child: SizedBox(
        height: height,
        child: whole.isZero
            ? ColoredBox(color: track)
            : Row(
                children: [
                  for (final slice in slices)
                    if (slice.amount > Money.zero)
                      Expanded(
                        // Chart geometry only: the shares never become text.
                        flex: (slice.amount.shareOf(whole) * 1000).round().clamp(1, 1000),
                        child: Container(
                          color: slice.color,
                          margin: const EdgeInsets.only(right: 2),
                        ),
                      ),
                ],
              ),
      ),
    );
  }
}

/// A colored dot, the slice's name and its amount, for each slice.
class PlanLegend extends StatelessWidget {
  const PlanLegend({super.key, required this.slices, this.textColor});

  final List<PlanSlice> slices;
  final Color? textColor;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.bodySmall?.copyWith(color: textColor);
    return Wrap(
      spacing: 16,
      runSpacing: 6,
      children: [
        for (final slice in slices)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 10, height: 10, decoration: BoxDecoration(color: slice.color, shape: BoxShape.circle)),
              const SizedBox(width: 6),
              Text('${slice.label} ${slice.amount.format()}', style: style),
            ],
          ),
      ],
    );
  }
}
