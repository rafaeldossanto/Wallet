import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/money/money.dart';

enum InvestmentKind {
  fixedIncome,
  treasury,
  fund,
  equity,
  retirement,
  other;

  static InvestmentKind parse(String value) => switch (value) {
        'FIXED_INCOME' => fixedIncome,
        'TREASURY' => treasury,
        'FUND' => fund,
        'EQUITY' => equity,
        'RETIREMENT' => retirement,
        _ => other,
      };
}

class Position {
  const Position({
    required this.id,
    required this.kind,
    required this.name,
    required this.balance,
    this.subtype,
    this.amountInvested,
    this.dueDate,
  });

  factory Position.fromJson(Json json) => Position(
        id: json.string('id'),
        kind: InvestmentKind.parse(json.string('kind')),
        subtype: json.stringOrNull('subtype'),
        name: json.stringOrNull('name') ?? '',
        balance: json.money('balance'),
        amountInvested: json.moneyOrNull('amountInvested'),
        dueDate: json.dateOrNull('dueDate'),
      );

  final String id;
  final InvestmentKind kind;
  final String? subtype;
  final String name;
  final Money balance;
  final Money? amountInvested;
  final DateTime? dueDate;

  /// What it earned (or lost) over what went in. Null when the bank did not say what went in.
  Money? get gain => amountInvested == null ? null : balance - amountInvested!;

  double? get gainShare => amountInvested == null || amountInvested!.isZero ? null : gain!.shareOf(amountInvested!);
}

class KindTotal {
  const KindTotal(this.kind, this.total);

  final InvestmentKind kind;
  final Money total;
}

class Portfolio {
  const Portfolio({required this.total, required this.byKind, required this.positions});

  factory Portfolio.fromJson(Json json) => Portfolio(
        total: json.money('total'),
        byKind: [
          for (final kind in json.list('byKind')) KindTotal(InvestmentKind.parse(kind.string('kind')), kind.money('total')),
        ],
        positions: [for (final position in json.list('positions')) Position.fromJson(position)],
      );

  final Money total;
  final List<KindTotal> byKind;
  final List<Position> positions;

  List<Position> positionsOf(InvestmentKind kind) =>
      positions.where((position) => position.kind == kind).toList()..sort((first, second) => second.balance.compareTo(first.balance));
}

/// The periods of the evolution chart, with the codes the BFF takes.
enum InvestmentPeriod {
  oneMonth('1M'),
  threeMonths('3M'),
  sixMonths('6M'),
  oneYear('1A'),
  all('TUDO');

  const InvestmentPeriod(this.code);

  final String code;
}

class InvestmentHistoryPoint {
  const InvestmentHistoryPoint(this.date, this.total);

  factory InvestmentHistoryPoint.fromJson(Json json) => InvestmentHistoryPoint(json.date('date'), json.money('total'));

  final DateTime date;
  final Money total;
}

/// The invested total day by day over a period.
class InvestmentHistory {
  const InvestmentHistory(this.points);

  factory InvestmentHistory.fromJson(Json json) =>
      InvestmentHistory(_fromFirstData([for (final point in json.list('points')) InvestmentHistoryPoint.fromJson(point)]));

  /// Oldest first, one point per day, starting on the first day with any data.
  final List<InvestmentHistoryPoint> points;

  /// What the invested total moved from the first day to the last, new money included. Null
  /// with fewer than two points.
  Money? get change => points.length < 2 ? null : points.last.total - points.first.total;

  /// [change] over the first day's total.
  double? get changeShare => change?.shareOf(points.first.total);

  /// The BFF sends every day of the period and fills the days before the first sync with zeros;
  /// drawn as they come, they read as a fortune made overnight.
  static List<InvestmentHistoryPoint> _fromFirstData(List<InvestmentHistoryPoint> points) {
    final first = points.indexWhere((point) => !point.total.isZero);
    return first < 0 ? const [] : points.sublist(first);
  }
}

class InvestmentsApi {
  InvestmentsApi(this._api);

  final ApiClient _api;

  Future<Portfolio> portfolio() async => Portfolio.fromJson(Json.of(await _api.get('/api/investments')));

  Future<InvestmentHistory> history(InvestmentPeriod period) async => InvestmentHistory.fromJson(
      Json.of(await _api.get('/api/investments/history', query: {'period': period.code})));
}
