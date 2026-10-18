import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/format/dates.dart';
import '../../../core/money/money.dart';

/// Where the month's salary came from.
enum SalarySource {
  /// Landed in the account this month.
  received,

  /// Not landed yet: the amount the user set.
  expected,

  /// Not landed yet: the last salary received.
  previousMonth,

  /// Not landed and nothing to go by.
  none;

  static SalarySource parse(String value) => switch (value) {
        'RECEIVED' => received,
        'EXPECTED' => expected,
        'PREVIOUS_MONTH' => previousMonth,
        _ => none,
      };
}

class PlanSalary {
  const PlanSalary({required this.source, required this.predicted, this.amount, this.date, this.expectedAmount});

  factory PlanSalary.fromJson(Json json) => PlanSalary(
        amount: json.moneyOrNull('amount'),
        date: json.dateOrNull('date'),
        predicted: json.raw['predicted'] as bool,
        source: SalarySource.parse(json.string('source')),
        expectedAmount: json.moneyOrNull('expectedAmount'),
      );

  /// Null when there is nothing to go by ([SalarySource.none]).
  final Money? amount;

  /// When it landed or, for a prediction, when it should.
  final DateTime? date;
  final bool predicted;
  final SalarySource source;

  /// The salary the user set, if any; also there when the real one already landed.
  final Money? expectedAmount;
}

class PlanBillItem {
  const PlanBillItem({
    required this.id,
    required this.date,
    required this.description,
    required this.amount,
    required this.isRefund,
    this.category,
    this.installmentNumber,
    this.installmentTotal,
  });

  factory PlanBillItem.fromJson(Json json) => PlanBillItem(
        id: json.string('transactionId'),
        date: json.date('date'),
        description: json.stringOrNull('description') ?? '',
        amount: json.money('amount'),
        isRefund: json.string('direction') == 'INFLOW',
        category: json.stringOrNull('category'),
        installmentNumber: json.intOrNull('installmentNumber'),
        installmentTotal: json.intOrNull('installmentTotal'),
      );

  final String id;
  final DateTime date;
  final String description;
  final Money amount;

  /// A refund lowers the bill instead of adding to it.
  final bool isRefund;
  final String? category;
  final int? installmentNumber;
  final int? installmentTotal;

  /// `2/3`, when the bank sent the installment.
  String? get installment =>
      installmentNumber == null || installmentTotal == null ? null : '$installmentNumber/$installmentTotal';
}

class PlanBill {
  const PlanBill({
    required this.accountId,
    required this.cardName,
    required this.dueDate,
    required this.total,
    required this.open,
    required this.items,
    required this.otherCharges,
    this.numberLastDigits,
    this.closingDate,
    this.institutionName,
    this.institutionImageUrl,
  });

  factory PlanBill.fromJson(Json json) => PlanBill(
        accountId: json.string('accountId'),
        institutionName: json.stringOrNull('institutionName'),
        institutionImageUrl: json.stringOrNull('institutionImageUrl'),
        cardName: json.stringOrNull('cardName') ?? '',
        numberLastDigits: json.stringOrNull('numberLastDigits'),
        dueDate: json.date('dueDate'),
        closingDate: json.dateOrNull('closingDate'),
        total: json.money('total'),
        open: json.raw['open'] as bool,
        items: [for (final item in json.list('items')) PlanBillItem.fromJson(item)],
        otherCharges: json.money('otherCharges'),
      );

  final String accountId;
  final String? institutionName;
  final String? institutionImageUrl;
  final String cardName;
  final String? numberLastDigits;
  final DateTime dueDate;
  final DateTime? closingDate;

  /// The bank's bill amount: it always matches what will be charged.
  final Money total;

  /// Not closed yet: the purchases so far, and it may still grow.
  final bool open;

  /// Purchases largest first, then refunds.
  final List<PlanBillItem> items;

  /// What the bill charges beyond the listed purchases; negative when credits lower it.
  final Money otherCharges;

  String get label => numberLastDigits == null ? cardName : '$cardName •••• $numberLastDigits';
}

class FixedExpense {
  const FixedExpense({required this.id, required this.name, required this.amount, required this.dayOfMonth, this.date, this.category});

  factory FixedExpense.fromJson(Json json) => FixedExpense(
        id: json.string('id'),
        name: json.string('name'),
        amount: json.money('amount'),
        dayOfMonth: json.integer('dayOfMonth'),
        date: json.dateOrNull('date'),
        category: json.stringOrNull('category'),
      );

  final String id;
  final String name;
  final Money amount;
  final int dayOfMonth;

  /// The day it falls on in the plan's month (the 31st of a 30-day month is the 30th).
  final DateTime? date;
  final String? category;
}

/// What the user types to create or change a fixed expense.
class FixedExpenseDraft {
  const FixedExpenseDraft({required this.name, required this.amount, required this.dayOfMonth, this.category});

  final String name;
  final Money amount;
  final int dayOfMonth;
  final String? category;

  Map<String, Object?> toJson() => {
        'name': name,
        'amount': amount.toApi(),
        'dayOfMonth': dayOfMonth,
        'category': category,
      };
}

class PlanTotals {
  const PlanTotals({required this.salary, required this.bills, required this.fixedExpenses, required this.leftover});

  factory PlanTotals.fromJson(Json json) => PlanTotals(
        salary: json.money('salary'),
        bills: json.money('bills'),
        fixedExpenses: json.money('fixedExpenses'),
        leftover: json.money('leftover'),
      );

  final Money salary;
  final Money bills;
  final Money fixedExpenses;

  /// Salary minus bills minus fixed expenses, from the server; negative when the month does not close.
  final Money leftover;
}

/// One month's plan: the net salary, minus each card's bill, minus the fixed expenses.
class MonthPlan {
  const MonthPlan({required this.month, required this.salary, required this.bills, required this.fixedExpenses, required this.totals});

  factory MonthPlan.fromJson(Json json) => MonthPlan(
        month: Dates.parseMonth(json.string('month')),
        salary: PlanSalary.fromJson(json.object('salary')),
        bills: [for (final bill in json.list('bills')) PlanBill.fromJson(bill)],
        fixedExpenses: [for (final expense in json.list('fixedExpenses')) FixedExpense.fromJson(expense)],
        totals: PlanTotals.fromJson(json.object('totals')),
      );

  final DateTime month;
  final PlanSalary salary;

  /// Earliest due first.
  final List<PlanBill> bills;

  /// By day of the month.
  final List<FixedExpense> fixedExpenses;
  final PlanTotals totals;
}

class PlanningApi {
  PlanningApi(this._api);

  final ApiClient _api;

  Future<MonthPlan> month(DateTime month) async =>
      MonthPlan.fromJson(Json.of(await _api.get('/api/planning', query: {'month': Dates.isoMonth(month)})));

  Future<FixedExpense> createFixedExpense(FixedExpenseDraft draft) async =>
      FixedExpense.fromJson(Json.of(await _api.post('/api/planning/fixed-expenses', body: draft.toJson())));

  Future<FixedExpense> updateFixedExpense(String id, FixedExpenseDraft draft) async => FixedExpense.fromJson(
      Json.of(await _api.put('/api/planning/fixed-expenses/${Uri.encodeComponent(id)}', body: draft.toJson())));

  Future<void> deleteFixedExpense(String id) => _api.delete('/api/planning/fixed-expenses/${Uri.encodeComponent(id)}');

  Future<void> setExpectedSalary(Money amount) => _api.put('/api/planning/expected-salary', body: {'amount': amount.toApi()});

  Future<void> clearExpectedSalary() => _api.delete('/api/planning/expected-salary');
}
