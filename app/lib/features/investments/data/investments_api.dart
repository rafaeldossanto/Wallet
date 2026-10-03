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

class InvestmentsApi {
  InvestmentsApi(this._api);

  final ApiClient _api;

  Future<Portfolio> portfolio() async => Portfolio.fromJson(Json.of(await _api.get('/api/investments')));
}
