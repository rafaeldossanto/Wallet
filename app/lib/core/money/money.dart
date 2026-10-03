import 'package:decimal/decimal.dart';

/// An amount in reais. The BFF sends money as text (`"1234.56"`) and it stays a [Decimal] until
/// it is drawn: a `double` in between rounds cents.
class Money implements Comparable<Money> {
  const Money._(this.amount);

  static final zero = Money._(Decimal.zero);

  final Decimal amount;

  factory Money.parse(String value) => Money._(Decimal.parse(value));

  static Money? tryParse(String? value) => value == null ? null : Money.parse(value);

  Money operator +(Money other) => Money._(amount + other.amount);

  Money operator -(Money other) => Money._(amount - other.amount);

  bool operator >(Money other) => amount > other.amount;

  bool operator <(Money other) => amount < other.amount;

  bool get isNegative => amount < Decimal.zero;

  bool get isZero => amount == Decimal.zero;

  Money abs() => isNegative ? Money._(-amount) : this;

  /// Only for chart geometry. Never for arithmetic or for text on screen.
  double toChartValue() => amount.toDouble();

  /// Share of [total], for percentages and proportional bars. Zero when [total] is zero.
  double shareOf(Money total) => total.isZero ? 0 : (amount / total.amount).toDouble();

  /// `R$ 1.234,56`, `-R$ 12,50`; with [signed], gains read `+R$ 50,00`.
  String format({bool signed = false}) {
    final rounded = amount.round(scale: 2);
    final negative = rounded < Decimal.zero;
    final digits = (negative ? -rounded : rounded).toStringAsFixed(2);
    final [whole, cents] = digits.split('.');
    final grouped = StringBuffer();
    for (var index = 0; index < whole.length; index++) {
      if (index > 0 && (whole.length - index) % 3 == 0) {
        grouped.write('.');
      }
      grouped.write(whole[index]);
    }
    final sign = negative ? '-' : (signed && rounded > Decimal.zero ? '+' : '');
    return '${sign}R\$ $grouped,$cents';
  }

  /// `R$ 45 mil`, for chart axes where the full amount does not fit.
  String formatCompact() {
    final value = amount.toDouble().abs();
    final sign = isNegative ? '-' : '';
    if (value >= 1000000) {
      return '${sign}R\$ ${_oneDecimal(value / 1000000)} mi';
    }
    if (value >= 1000) {
      return '${sign}R\$ ${_oneDecimal(value / 1000)} mil';
    }
    return '${sign}R\$ ${value.round()}';
  }

  static String _oneDecimal(double value) {
    final text = value >= 100 ? value.round().toString() : value.toStringAsFixed(1);
    return text.endsWith('.0') ? text.substring(0, text.length - 2) : text.replaceAll('.', ',');
  }

  @override
  int compareTo(Money other) => amount.compareTo(other.amount);

  @override
  bool operator ==(Object other) => other is Money && other.amount == amount;

  @override
  int get hashCode => amount.hashCode;

  @override
  String toString() => format();
}

extension MoneySum on Iterable<Money> {
  Money sum() => fold(Money.zero, (total, value) => total + value);
}
