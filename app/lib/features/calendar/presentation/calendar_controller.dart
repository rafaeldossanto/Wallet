import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/format/dates.dart';
import '../../../core/state/data_changes.dart';
import '../../../core/state/loadable.dart';
import '../data/calendar_api.dart';

/// The spending calendar: one month, and the spending listed beside it. With no day picked the
/// list covers the whole month; tapping a day narrows it to that day, and tapping the same day
/// again goes back to the month.
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
  Loadable<List<SpendingItem>> _listState = const Loading();
  int _page = 0;
  bool _hasMore = false;
  bool _loadingMore = false;
  ApiException? _moreError;
  int _monthGeneration = 0;
  int _listGeneration = 0;
  bool _disposed = false;

  DateTime get today => _today;

  DateTime get month => _month;

  Loadable<CalendarMonth> get monthState => _monthState;

  /// Null: the whole month.
  DateTime? get selected => _selected;

  Loadable<List<SpendingItem>> get listState => _listState;

  bool get hasMore => _hasMore;

  bool get isLoadingMore => _loadingMore;

  ApiException? get moreError => _moreError;

  bool get isCurrentMonth => _month.year == _today.year && _month.month == _today.month;

  Future<void> load() async {
    _selected = null;
    await Future.wait([_loadMonth(), _loadList()]);
  }

  /// After a sync: same month, same pick, fresh numbers.
  Future<void> refresh() => Future.wait([_loadMonth(), _loadList()]);

  void setMonth(DateTime month) {
    _month = Dates.firstOfMonth(month);
    load();
  }

  /// Picks a day, or goes back to the whole month when the picked day is tapped again. Future
  /// days have nothing to show and cannot be picked.
  Future<void> toggle(DateTime day) async {
    final date = DateTime(day.year, day.month, day.day);
    if (date.isAfter(_today)) {
      return;
    }
    _selected = date == _selected ? null : date;
    await _loadList();
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loadingMore || _listState is! Loaded<List<SpendingItem>>) {
      return;
    }
    final generation = _listGeneration;
    _loadingMore = true;
    _moreError = null;
    _notify();
    try {
      final next = await _fetch(_page + 1);
      if (generation == _listGeneration) {
        final current = (_listState as Loaded<List<SpendingItem>>).value;
        _listState = Loaded([...current, ...next.items]);
        _page = next.page;
        _hasMore = next.hasMore;
      }
    } on ApiException catch (error) {
      if (generation == _listGeneration) {
        _moreError = error;
      }
    } finally {
      _loadingMore = false;
      _notify();
    }
  }

  Future<void> _loadMonth() async {
    final generation = ++_monthGeneration;
    if (_monthState is! Loaded<CalendarMonth>) {
      _monthState = const Loading();
      _notify();
    }
    try {
      final loaded = await _api.month(_month);
      if (generation == _monthGeneration) {
        _monthState = Loaded(loaded);
      }
    } on ApiException catch (error) {
      if (generation == _monthGeneration) {
        _monthState = Failed(error);
      }
    }
    _notify();
  }

  /// Back to the first page of the current period. A response for a period the user already
  /// left is dropped.
  Future<void> _loadList() async {
    final generation = ++_listGeneration;
    _listState = const Loading();
    _moreError = null;
    _notify();
    try {
      final first = await _fetch(1);
      if (generation == _listGeneration) {
        _listState = Loaded(first.items);
        _page = first.page;
        _hasMore = first.hasMore;
      }
    } on ApiException catch (error) {
      if (generation == _listGeneration) {
        _listState = Failed(error);
        _hasMore = false;
      }
    }
    _notify();
  }

  Future<SpendingPage> _fetch(int page) {
    final day = _selected;
    return day == null
        ? _api.spending(_month, Dates.lastOfMonth(_month), page: page)
        : _api.spending(day, day, page: page);
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
