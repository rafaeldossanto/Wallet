import '../../../core/api/api_exception.dart';
import '../../../core/format/dates.dart';
import '../../../core/money/money.dart';
import '../../../core/state/data_changes.dart';
import '../../../core/state/loadable.dart';
import '../data/planning_api.dart';

/// A month's plan, and the changes the user makes to it. A change rings [DataChanges], so this
/// screen and the overview's "Sobra do mês" both fetch the new numbers.
class PlanningController extends LoadController<MonthPlan> {
  PlanningController(this._api, {super.dataChanges, DateTime? today})
      : _changes = dataChanges,
        _today = Dates.today(today),
        _month = Dates.firstOfMonth(today ?? DateTime.now());

  final PlanningApi _api;
  final DataChanges? _changes;
  final DateTime _today;
  DateTime _month;

  DateTime get month => _month;

  DateTime get today => _today;

  /// The plan looks one month ahead at most: past that there is no bill to show yet.
  DateTime get latestMonth => DateTime(_today.year, _today.month + 1);

  void setMonth(DateTime month) {
    _month = Dates.firstOfMonth(month);
    load();
  }

  @override
  Future<MonthPlan> fetch() => _api.month(_month);

  /// Creates the expense, or changes it when [id] is given. Null when it worked.
  Future<ApiException?> saveFixedExpense(FixedExpenseDraft draft, {String? id}) =>
      _change(() => id == null ? _api.createFixedExpense(draft) : _api.updateFixedExpense(id, draft));

  Future<ApiException?> deleteFixedExpense(String id) => _change(() => _api.deleteFixedExpense(id));

  Future<ApiException?> setExpectedSalary(Money amount) => _change(() => _api.setExpectedSalary(amount));

  /// Back to predicting from the previous month's salary.
  Future<ApiException?> clearExpectedSalary() => _change(_api.clearExpectedSalary);

  Future<ApiException?> _change(Future<Object?> Function() request) async {
    try {
      await request();
    } on ApiException catch (error) {
      return error;
    }
    final changes = _changes;
    if (changes == null) {
      await refresh();
    } else {
      changes.changed();
    }
    return null;
  }
}
