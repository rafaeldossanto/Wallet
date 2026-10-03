import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/format/dates.dart';
import '../../../core/money/money.dart';

class CategoryTotal {
  const CategoryTotal(this.category, this.total);

  final String category;
  final Money total;
}

class Spending {
  const Spending({required this.month, required this.total, required this.categories});

  factory Spending.fromJson(Json json) => Spending(
        month: Dates.parseMonth(json.string('month')),
        total: json.money('total'),
        categories: [
          for (final category in json.list('categories')) CategoryTotal(category.string('category'), category.money('total')),
        ],
      );

  final DateTime month;
  final Money total;

  /// Largest first, as the core sends them.
  final List<CategoryTotal> categories;
}

class NetWorthPoint {
  const NetWorthPoint({required this.date, required this.netWorth, required this.cash, required this.investments, required this.creditCardDebt});

  factory NetWorthPoint.fromJson(Json json) => NetWorthPoint(
        date: json.date('date'),
        netWorth: json.money('netWorth'),
        cash: json.money('cash'),
        investments: json.money('investments'),
        creditCardDebt: json.money('creditCardDebt'),
      );

  final DateTime date;
  final Money netWorth;
  final Money cash;
  final Money investments;
  final Money creditCardDebt;

  bool get isEmpty => netWorth.isZero && cash.isZero && investments.isZero && creditCardDebt.isZero;
}

class Insights {
  const Insights({required this.spending, required this.netWorth});

  factory Insights.fromJson(Json json) => Insights(
        spending: Spending.fromJson(json.object('spending')),
        netWorth: _fromFirstData([for (final point in json.object('netWorth').list('points')) NetWorthPoint.fromJson(point)]),
      );

  final Spending spending;

  /// Oldest first, one point per day, starting on the first day with any data.
  final List<NetWorthPoint> netWorth;

  /// The core sends every day of the period and fills the days before the first snapshot with
  /// zeros; drawn as they come, they read as a fortune made overnight.
  static List<NetWorthPoint> _fromFirstData(List<NetWorthPoint> points) {
    final first = points.indexWhere((point) => !point.isEmpty);
    return first < 0 ? const [] : points.sublist(first);
  }
}

class InsightsApi {
  InsightsApi(this._api);

  final ApiClient _api;

  Future<Insights> insights(DateTime month) async =>
      Insights.fromJson(Json.of(await _api.get('/api/insights', query: {'month': Dates.isoMonth(month)})));
}
