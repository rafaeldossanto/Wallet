import 'package:fl_chart/fl_chart.dart';
import 'package:material_ui/material_ui.dart';

import '../format/dates.dart';
import '../money/money.dart';

class ChartPoint {
  const ChartPoint(this.date, this.value);

  final DateTime date;
  final Money value;
}

/// An amount over time: net worth, invested total. Needs at least two points.
class MoneyLineChart extends StatelessWidget {
  const MoneyLineChart({super.key, required this.points, this.height = 220, this.color});

  /// Past this many days the axis shows months instead of days.
  static const _monthsAxisDays = 92;

  final List<ChartPoint> points;
  final double height;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final line = color ?? theme.colorScheme.primary;
    final span = _span(points);
    final fewDays = span <= 4;
    return SizedBox(
      height: height,
      child: LineChart(LineChartData(
        gridData: FlGridData(
          drawVerticalLine: false,
          getDrawingHorizontalLine: (_) => FlLine(color: theme.colorScheme.outlineVariant, strokeWidth: 0.5),
        ),
        borderData: FlBorderData(show: false),
        titlesData: FlTitlesData(
          topTitles: const AxisTitles(),
          rightTitles: const AxisTitles(),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 72,
              minIncluded: false,
              maxIncluded: false,
              getTitlesWidget: (value, meta) => SideTitleWidget(
                meta: meta,
                child: Text(Money.parse(value.toStringAsFixed(2)).formatCompact(), style: theme.textTheme.labelSmall),
              ),
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 28,
              // Over a few days each day gets its label, ends included; over longer spans the
              // ends would collide with the nearest regular label.
              minIncluded: fewDays,
              maxIncluded: fewDays,
              interval: fewDays ? 1 : (span / 4).ceilToDouble(),
              getTitlesWidget: (value, meta) => SideTitleWidget(
                meta: meta,
                child: Text(
                  span > _monthsAxisDays ? Dates.monthShortYear(_dateAt(value)) : Dates.dayMonth(_dateAt(value)),
                  style: theme.textTheme.labelSmall,
                ),
              ),
            ),
          ),
        ),
        lineTouchData: LineTouchData(
          touchTooltipData: LineTouchTooltipData(
            getTooltipColor: (_) => theme.colorScheme.surfaceContainerHighest,
            getTooltipItems: (spots) => [
              for (final spot in spots)
                LineTooltipItem(
                  '${Dates.short(_dateAt(spot.x))}\n${Money.parse(spot.y.toStringAsFixed(2)).format()}',
                  theme.textTheme.bodySmall!,
                ),
            ],
          ),
        ),
        lineBarsData: [
          LineChartBarData(
            spots: [for (final point in points) FlSpot(_dayOf(point.date), point.value.toChartValue())],
            isCurved: true,
            preventCurveOverShooting: true,
            color: line,
            barWidth: 2.5,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(show: true, color: line.withValues(alpha: 0.12)),
          ),
        ],
      )),
    );
  }

  /// The x axis counts days since the epoch, so gaps between points keep their width.
  static double _dayOf(DateTime date) => DateTime.utc(date.year, date.month, date.day).millisecondsSinceEpoch / Duration.millisecondsPerDay;

  static DateTime _dateAt(double day) {
    final utc = DateTime.fromMillisecondsSinceEpoch((day * Duration.millisecondsPerDay).round(), isUtc: true);
    return DateTime(utc.year, utc.month, utc.day);
  }

  static double _span(List<ChartPoint> points) =>
      points.length < 2 ? 0 : _dayOf(points.last.date) - _dayOf(points.first.date);
}
