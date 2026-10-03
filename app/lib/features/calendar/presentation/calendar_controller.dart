import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/format/dates.dart';
import '../../../core/state/data_changes.dart';
import '../../../core/state/loadable.dart';
import '../data/calendar_api.dart';

/// The spending calendar: one month, and the day picked in it. Opening a month picks the latest
/// day that had spending (today's, when there was some), so the list beside it is never empty
/// for no reason.
class CalendarController extends ChangeNotifier {
  CalendarController(this._api, {this._dataChanges, DateTime? today})
      : _today = Dates.today(today),
        _month = Dates.firstOfMonth(today ?? DateTime.now()) {
    _dataChanges?.addListener(refresh);
  }

  final CalendarApi _api;
  final DataChanges? _dataChanges;
  final DateTime _today;

  DateTime _month;
  Loadable<CalendarMonth> _monthState = const Loading();
  DateTime? _selected;
  Loadable<DaySpending>? _dayState;
  int _monthGeneration = 0;
  int _dayGeneration = 0;
  bool _disposed = false;

  DateTime get today => _today;

  DateTime get month => _month;

  Loadable<CalendarMonth> get monthState => _monthState;

  DateTime? get selected => _selected;

  /// Null while no day is picked (a month without spending).
  Loadable<DaySpending>? get dayState => _dayState;

  bool get isCurrentMonth => _month.year == _today.year && _month.month == _today.month;

  Future<void> load() => _loadMonth(keepSelection: false);

  Future<void> refresh() => _loadMonth(keepSelection: true);

  void setMonth(DateTime month) {
    _month = Dates.firstOfMonth(month);
    _loadMonth(keepSelection: false);
  }

  /// Future days have nothing to show and cannot be picked.
  Future<void> select(DateTime day) async {
    final date = DateTime(day.year, day.month, day.day);
    if (date.isAfter(_today)) {
      return;
    }
    _selected = date;
    final generation = ++_dayGeneration;
    _dayState = const Loading();
    _notify();
    try {
      final spending = await _api.day(date);
      if (generation == _dayGeneration) {
        _dayState = Loaded(spending);
      }
    } on ApiException catch (error) {
      if (generation == _dayGeneration) {
        _dayState = Failed(error);
      }
    }
    _notify();
  }

  Future<void> _loadMonth({required bool keepSelection}) async {
    final generation = ++_monthGeneration;
    final month = _month;
    if (_monthState is! Loaded<CalendarMonth> || !keepSelection) {
      _monthState = const Loading();
      _notify();
    }
    try {
      final loaded = await _api.month(month);
      if (generation != _monthGeneration) {
        return;
      }
      _monthState = Loaded(loaded);
      final day = keepSelection && _selected != null ? _selected : _defaultDay(loaded);
      if (day == null) {
        _selected = null;
        _dayState = null;
        _notify();
      } else {
        await select(day);
      }
    } on ApiException catch (error) {
      if (generation == _monthGeneration) {
        _monthState = Failed(error);
        _notify();
      }
    }
  }

  DateTime? _defaultDay(CalendarMonth month) {
    final last = Dates.lastOfMonth(_month);
    return month.lastSpendingDayUpTo(last.isAfter(_today) ? _today : last);
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
