import '../api/json.dart';
import '../money/money.dart';

enum Direction {
  inflow,
  outflow;

  static Direction parse(String value) => value == 'INFLOW' ? inflow : outflow;

  String get apiValue => this == inflow ? 'INFLOW' : 'OUTFLOW';
}

/// The amount is always positive; [direction] says which way the money went.
class Transaction {
  const Transaction({
    required this.id,
    required this.accountId,
    required this.bookedOn,
    required this.description,
    required this.amount,
    required this.direction,
    required this.pending,
    this.category,
    this.installmentNumber,
    this.installmentTotal,
  });

  factory Transaction.fromJson(Json json) => Transaction(
        id: json.string('id'),
        accountId: json.string('accountId'),
        bookedOn: json.date('bookedOn'),
        description: json.stringOrNull('description') ?? '',
        amount: json.money('amount'),
        direction: Direction.parse(json.string('direction')),
        pending: json.stringOrNull('status') == 'PENDING',
        category: json.stringOrNull('category'),
        installmentNumber: json.intOrNull('installmentNumber'),
        installmentTotal: json.intOrNull('installmentTotal'),
      );

  final String id;
  final String accountId;
  final DateTime bookedOn;
  final String description;
  final Money amount;
  final Direction direction;
  final bool pending;
  final String? category;
  final int? installmentNumber;
  final int? installmentTotal;

  bool get isInflow => direction == Direction.inflow;

  /// `3/10`, when the bank sent the installment.
  String? get installment =>
      installmentNumber == null || installmentTotal == null ? null : '$installmentNumber/$installmentTotal';
}

class TransactionPage {
  const TransactionPage({required this.items, required this.page, required this.totalPages, required this.total});

  factory TransactionPage.fromJson(Json json) => TransactionPage(
        items: [for (final item in json.list('items')) Transaction.fromJson(item)],
        page: json.integer('page'),
        totalPages: json.integer('totalPages'),
        total: json.integer('total'),
      );

  final List<Transaction> items;
  final int page;
  final int totalPages;
  final int total;

  bool get hasMore => page < totalPages;
}
