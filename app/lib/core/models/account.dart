import '../api/json.dart';
import '../money/money.dart';

enum AccountKind {
  checking,
  savings,
  creditCard,
  other;

  static AccountKind parse(String value) => switch (value) {
        'CHECKING' => checking,
        'SAVINGS' => savings,
        'CREDIT_CARD' => creditCard,
        _ => other,
      };
}

class Account {
  const Account({
    required this.id,
    required this.connectionId,
    required this.kind,
    required this.name,
    required this.balance,
    this.numberLastDigits,
    this.creditLimit,
    this.availableCredit,
  });

  factory Account.fromJson(Json json) => Account(
        id: json.string('id'),
        connectionId: json.string('connectionId'),
        kind: AccountKind.parse(json.string('kind')),
        name: json.string('name'),
        numberLastDigits: json.stringOrNull('numberLastDigits'),
        balance: json.money('balance'),
        creditLimit: json.moneyOrNull('creditLimit'),
        availableCredit: json.moneyOrNull('availableCredit'),
      );

  final String id;
  final String connectionId;
  final AccountKind kind;
  final String name;
  final String? numberLastDigits;
  final Money balance;
  final Money? creditLimit;
  final Money? availableCredit;

  /// `Conta Corrente •••• 4567`.
  String get label => numberLastDigits == null ? name : '$name •••• $numberLastDigits';
}
