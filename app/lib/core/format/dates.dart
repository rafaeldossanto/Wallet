import 'package:intl/intl.dart';

import '../l10n/app_localizations.dart';

/// Dates as people in Brazil read them. Needs `initializeDateFormatting('pt_BR')` at startup.
abstract final class Dates {
  static const locale = 'pt_BR';

  static DateTime today([DateTime? now]) {
    final current = now ?? DateTime.now();
    return DateTime(current.year, current.month, current.day);
  }

  static DateTime firstOfMonth(DateTime day) => DateTime(day.year, day.month);

  static DateTime lastOfMonth(DateTime day) => DateTime(day.year, day.month + 1, 0);

  /// `2026-09-30`, for the BFF.
  static String iso(DateTime day) => DateFormat('yyyy-MM-dd').format(day);

  /// `2026-09`, for the BFF.
  static String isoMonth(DateTime month) => DateFormat('yyyy-MM').format(month);

  static DateTime parseMonth(String value) {
    final [year, month] = value.split('-').map(int.parse).toList();
    return DateTime(year, month);
  }

  /// `30/09/2026`.
  static String short(DateTime day) => DateFormat('dd/MM/yyyy', locale).format(day);

  /// `30 de set.`.
  static String dayMonth(DateTime day) => DateFormat("d 'de' MMM", locale).format(day);

  /// `Outubro de 2026`.
  static String month(DateTime month) => _capitalize(DateFormat.yMMMM(locale).format(month));

  /// `Outubro`.
  static String monthName(DateTime month) => _capitalize(DateFormat.MMMM(locale).format(month));

  /// `out.`, for chart axes.
  static String monthAbbreviation(DateTime month) => DateFormat.MMM(locale).format(month);

  /// `Hoje`, `Ontem` or `Sexta, 25 de setembro`.
  static String dayHeader(DateTime day, AppLocalizations l10n, {DateTime? now}) {
    final today = Dates.today(now);
    final difference = today.difference(DateTime(day.year, day.month, day.day)).inDays;
    if (difference == 0) {
      return l10n.dateToday;
    }
    if (difference == 1) {
      return l10n.dateYesterday;
    }
    final pattern = day.year == today.year ? "EEEE, d 'de' MMMM" : "EEEE, d 'de' MMMM 'de' y";
    return _capitalize(DateFormat(pattern, locale).format(day).replaceFirst('-feira', ''));
  }

  /// `há 5 min`, `há 3 h`, `há 2 dias`.
  static String timeAgo(DateTime moment, AppLocalizations l10n, {DateTime? now}) {
    final elapsed = (now ?? DateTime.now()).difference(moment);
    if (elapsed.inMinutes < 1) {
      return l10n.timeAgoNow;
    }
    if (elapsed.inHours < 1) {
      return l10n.timeAgoMinutes(elapsed.inMinutes);
    }
    if (elapsed.inDays < 1) {
      return l10n.timeAgoHours(elapsed.inHours);
    }
    return l10n.timeAgoDays(elapsed.inDays);
  }

  static String _capitalize(String text) => text.isEmpty ? text : text[0].toUpperCase() + text.substring(1);
}
