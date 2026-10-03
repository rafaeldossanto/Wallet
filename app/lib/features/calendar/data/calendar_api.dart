import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/format/dates.dart';
import '../../../core/money/money.dart';

class CalendarDay {
  const CalendarDay({required this.date, required this.total, required this.count});

  factory CalendarDay.fromJson(Json json) =>
      CalendarDay(date: json.date('date'), total: json.money('total'), count: json.integer('count'));

  final DateTime date;
  final Money total;
  final int count;
}

/// One month of spending, day by day. Days without spending are absent.
class CalendarMonth {
  CalendarMonth({required this.month, required this.total, required List<CalendarDay> days})
      : days = {for (final day in days) day.date: day};

  factory CalendarMonth.fromJson(Json json) => CalendarMonth(
        month: Dates.parseMonth(json.string('month')),
        total: json.money('total'),
        days: [for (final day in json.list('days')) CalendarDay.fromJson(day)],
      );

  final DateTime month;
  final Money total;
  final Map<DateTime, CalendarDay> days;

  CalendarDay? dayOf(DateTime date) => days[DateTime(date.year, date.month, date.day)];

  /// The heaviest day, so each day's shade is relative to the month's worst.
  Money get busiest => days.values.map((day) => day.total).fold(Money.zero, (max, total) => total > max ? total : max);

  /// The latest day with spending up to [limit], or null when nothing was spent by then.
  DateTime? lastSpendingDayUpTo(DateTime limit) {
    final candidates = days.keys.where((date) => !date.isAfter(limit)).toList()..sort();
    return candidates.isEmpty ? null : candidates.last;
  }
}

class DaySpendingItem {
  const DaySpendingItem({
    required this.id,
    required this.description,
    required this.amount,
    required this.pending,
    this.accountName,
    this.institutionName,
    this.institutionImageUrl,
    this.category,
    this.installmentNumber,
    this.installmentTotal,
  });

  factory DaySpendingItem.fromJson(Json json) => DaySpendingItem(
        id: json.string('id'),
        description: json.stringOrNull('description') ?? '',
        amount: json.money('amount'),
        pending: json.stringOrNull('status') == 'PENDING',
        accountName: json.stringOrNull('accountName'),
        institutionName: json.stringOrNull('institutionName'),
        institutionImageUrl: json.stringOrNull('institutionImageUrl'),
        category: json.stringOrNull('category'),
        installmentNumber: json.intOrNull('installmentNumber'),
        installmentTotal: json.intOrNull('installmentTotal'),
      );

  final String id;
  final String description;
  final Money amount;
  final bool pending;
  final String? accountName;
  final String? institutionName;
  final String? institutionImageUrl;
  final String? category;
  final int? installmentNumber;
  final int? installmentTotal;

  String? get installment =>
      installmentNumber == null || installmentTotal == null ? null : '$installmentNumber/$installmentTotal';
}

class DaySpending {
  const DaySpending({required this.date, required this.items});

  factory DaySpending.fromJson(Json json) => DaySpending(
        date: json.date('date'),
        items: [for (final item in json.list('items')) DaySpendingItem.fromJson(item)],
      );

  final DateTime date;
  final List<DaySpendingItem> items;
}

class CalendarApi {
  CalendarApi(this._api);

  final ApiClient _api;

  Future<CalendarMonth> month(DateTime month) async =>
      CalendarMonth.fromJson(Json.of(await _api.get('/api/calendar', query: {'month': Dates.isoMonth(month)})));

  Future<DaySpending> day(DateTime day) async => DaySpending.fromJson(Json.of(await _api.get('/api/calendar/${Dates.iso(day)}')));
}
