import '../api/json.dart';
import '../money/money.dart';

class Bill {
  const Bill({required this.id, required this.dueDate, required this.totalAmount, this.closingDate, this.minimumPayment});

  factory Bill.fromJson(Json json) => Bill(
        id: json.string('id'),
        dueDate: json.date('dueDate'),
        closingDate: json.dateOrNull('closingDate'),
        totalAmount: json.money('totalAmount'),
        minimumPayment: json.moneyOrNull('minimumPayment'),
      );

  final String id;
  final DateTime dueDate;
  final DateTime? closingDate;
  final Money totalAmount;
  final Money? minimumPayment;
}

class CreditCard {
  const CreditCard({
    required this.accountId,
    required this.connectionId,
    required this.name,
    required this.currentBalance,
    this.numberLastDigits,
    this.creditLimit,
    this.availableCredit,
    this.currentBill,
  });

  factory CreditCard.fromJson(Json json) => CreditCard(
        accountId: json.string('accountId'),
        connectionId: json.string('connectionId'),
        name: json.string('name'),
        numberLastDigits: json.stringOrNull('numberLastDigits'),
        creditLimit: json.moneyOrNull('creditLimit'),
        availableCredit: json.moneyOrNull('availableCredit'),
        currentBalance: json.money('currentBalance'),
        currentBill: json.objectOrNull('currentBill') == null ? null : Bill.fromJson(json.object('currentBill')),
      );

  final String accountId;
  final String connectionId;
  final String name;
  final String? numberLastDigits;
  final Money? creditLimit;
  final Money? availableCredit;
  final Money currentBalance;
  final Bill? currentBill;

  String get label => numberLastDigits == null ? name : '$name •••• $numberLastDigits';

  /// How much of the limit is taken, from 0 to 1. Null when the bank did not send a limit.
  double? get usedShare {
    final limit = creditLimit;
    final available = availableCredit;
    if (limit == null || available == null || limit.isZero) {
      return null;
    }
    return (limit - available).shareOf(limit).clamp(0, 1);
  }
}
