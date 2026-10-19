import '../../../core/api/api_exception.dart';
import '../../../core/state/loadable.dart';
import '../data/planning_api.dart';

/// Debits that repeat every month in the bank accounts, proposed as fixed expenses. Apart from the
/// plan: they do not depend on the month, only the planning screen shows them, and when they fail
/// the screen just shows none. A fixed expense saved rings [DataChanges], which reloads them, so
/// the one just added goes away.
class FixedExpenseSuggestionsController extends LoadController<List<FixedExpenseSuggestion>> {
  FixedExpenseSuggestionsController(this._api, {super.dataChanges});

  final PlanningApi _api;

  @override
  Future<List<FixedExpenseSuggestion>> fetch() => _api.fixedExpenseSuggestions();

  /// It is not suggested again. Null when it worked.
  Future<ApiException?> dismiss(String id) async {
    try {
      await _api.dismissFixedExpenseSuggestion(id);
    } on ApiException catch (error) {
      return error;
    }
    await refresh();
    return null;
  }
}
