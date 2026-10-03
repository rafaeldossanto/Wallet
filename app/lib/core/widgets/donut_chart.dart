import 'package:fl_chart/fl_chart.dart';
import 'package:material_ui/material_ui.dart';

import '../format/percent.dart';
import '../l10n/l10n.dart';
import '../money/money.dart';
import '../theme/app_theme.dart';
import 'common.dart';

class DonutSlice {
  const DonutSlice(this.label, this.total);

  final String label;

  /// Positive: the ring has no room for a slice of nothing.
  final Money total;
}

/// A thin donut with its legend, the total in the hole. Tapping a slice, or its line in the
/// legend, puts that slice's share and amount in the hole; tapping it again, or the hole, goes
/// back to the total.
class DonutChart extends StatefulWidget {
  const DonutChart({super.key, required this.slices, required this.total, required this.totalLabel});

  static const holeRadius = 70.0;
  static const ringWidth = 18.0;

  /// The picked slice stands out of the ring by this much more.
  static const pickedRingWidth = 22.0;

  final List<DonutSlice> slices;
  final Money total;
  final String totalLabel;

  @override
  State<DonutChart> createState() => _DonutChartState();
}

class _DonutChartState extends State<DonutChart> {
  /// Rounds the slice ends into a pill; the chart shrinks it on a slice too thin to take it.
  static const _sliceCornerRadius = DonutChart.ringWidth / 2;

  /// Kept by label, so a refresh that reorders the slices keeps the same one picked.
  String? _picked;

  /// Null (a tap in the hole or off the ring) always goes back to the total.
  void _toggle(String? label) => setState(() => _picked = label == _picked ? null : label);

  static int _touchedIndex(PieTouchResponse? response) => response?.touchedSection?.touchedSectionIndex ?? -1;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = context.walletColors.chart;
    final slices = widget.slices;
    // A slice gone after a refresh is no longer picked.
    final pickedIndex = slices.indexWhere((slice) => slice.label == _picked);
    final picked = pickedIndex < 0 ? null : slices[pickedIndex];
    final hint = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    Color colorOf(int index) => colors[index % colors.length];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: (DonutChart.holeRadius + DonutChart.pickedRingWidth) * 2,
          child: Stack(
            alignment: Alignment.center,
            children: [
              if (slices.isNotEmpty)
                PieChart(PieChartData(
                  centerSpaceRadius: DonutChart.holeRadius,
                  sectionsSpace: slices.length > 1 ? 4 : 0,
                  pieTouchData: PieTouchData(
                    touchCallback: (event, response) {
                      if (event is FlTapUpEvent) {
                        final index = _touchedIndex(response);
                        _toggle(index >= 0 && index < slices.length ? slices[index].label : null);
                      }
                    },
                    mouseCursorResolver: (event, response) =>
                        _touchedIndex(response) >= 0 ? SystemMouseCursors.click : MouseCursor.defer,
                  ),
                  sections: [
                    for (var index = 0; index < slices.length; index++)
                      PieChartSectionData(
                        value: slices[index].total.toChartValue(),
                        color: picked == null || index == pickedIndex ? colorOf(index) : colorOf(index).withValues(alpha: 0.35),
                        radius: index == pickedIndex ? DonutChart.pickedRingWidth : DonutChart.ringWidth,
                        cornerRadius: _sliceCornerRadius,
                        showTitle: false,
                      ),
                  ],
                )),
              // A large amount shrinks to fit the hole instead of spilling over the ring. Taps go
              // through it to the chart, so a tap in the hole goes back to the total.
              IgnorePointer(
                child: SizedBox(
                  width: DonutChart.holeRadius * 2 - 20,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: picked == null
                          ? [
                              Text(widget.totalLabel, style: theme.textTheme.labelMedium),
                              MoneyText(widget.total, style: theme.textTheme.titleMedium),
                            ]
                          : [
                              Text(picked.label, style: theme.textTheme.labelMedium),
                              Text(formatPercent(picked.total.shareOf(widget.total)),
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
        if (slices.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(picked == null ? l10n.donutHintPick : l10n.donutHintUnpick, style: hint, textAlign: TextAlign.center),
        ],
        const SizedBox(height: 12),
        for (var index = 0; index < slices.length; index++)
          Semantics(
            selected: index == pickedIndex,
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () => _toggle(slices[index].label),
              child: Ink(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                decoration: BoxDecoration(
                  color: index == pickedIndex ? theme.colorScheme.onSurface.withValues(alpha: 0.06) : null,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(color: colorOf(index), shape: BoxShape.circle),
                    ),
                    const SizedBox(width: 8),
                    Expanded(child: Text(slices[index].label)),
                    Text(formatPercent(slices[index].total.shareOf(widget.total)), style: hint),
                    const SizedBox(width: 12),
                    MoneyText(slices[index].total),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}
