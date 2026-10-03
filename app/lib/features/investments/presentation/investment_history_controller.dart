import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/state/data_changes.dart';
import '../../../core/state/loadable.dart';
import '../data/investments_api.dart';

/// The evolution chart: the invested total over the period the user picked, six months at first.
class InvestmentHistoryController extends ChangeNotifier {
  InvestmentHistoryController(this._api, {this._dataChanges}) {
    _dataChanges?.addListener(refresh);
  }

  final InvestmentsApi _api;
  final DataChanges? _dataChanges;

  InvestmentPeriod _period = InvestmentPeriod.sixMonths;
  Loadable<InvestmentHistory> _state = const Loading();
  int _generation = 0;
  bool _disposed = false;

  InvestmentPeriod get period => _period;

  Loadable<InvestmentHistory> get state => _state;

  Future<void> load() async {
    _state = const Loading();
    _notify();
    await _fetch();
  }

  /// After a sync: same period, fresh numbers. The line on screen stays while it loads, and
  /// stays if the refresh fails.
  Future<void> refresh() => _state is Loaded<InvestmentHistory> ? _fetch() : load();

  Future<void> setPeriod(InvestmentPeriod period) async {
    if (period == _period) {
      return;
    }
    _period = period;
    await load();
  }

  /// A response for a period the user already left is dropped.
  Future<void> _fetch() async {
    final generation = ++_generation;
    try {
      final history = await _api.history(_period);
      if (generation == _generation) {
        _state = Loaded(history);
      }
    } on ApiException catch (error) {
      if (generation == _generation && _state is! Loaded<InvestmentHistory>) {
        _state = Failed(error);
      }
    }
    _notify();
  }

  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _dataChanges?.removeListener(refresh);
    super.dispose();
  }
}
