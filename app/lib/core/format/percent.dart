/// `12,5%`; with [signed], gains read `+3,2%`.
String formatPercent(double share, {bool signed = false}) {
  final value = share * 100;
  final text = '${value.abs().toStringAsFixed(1).replaceAll('.', ',')}%';
  if (value < 0) {
    return '-$text';
  }
  return signed && value > 0 ? '+$text' : text;
}
