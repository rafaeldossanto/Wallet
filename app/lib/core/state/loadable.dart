import 'package:flutter/foundation.dart';

import '../api/api_exception.dart';
import 'data_changes.dart';

/// What a screen has to show: still loading, the data, or why it could not get it.
sealed class Loadable<T> {
  const Loadable();
}

final class Loading<T> extends Loadable<T> {
  const Loading();
}

final class Loaded<T> extends Loadable<T> {
  const Loaded(this.value);

  final T value;
}

final class Failed<T> extends Loadable<T> {
  const Failed(this.error);

  final ApiException error;
}

/// A screen's data from one call. After the first load, [refresh] keeps the old data on screen
/// while it fetches, and a failure there goes to [refreshError] instead of wiping the screen.
/// It also refreshes by itself when [DataChanges] says the user's data changed.
abstract class LoadController<T> extends ChangeNotifier {
  LoadController({this._dataChanges}) {
    _dataChanges?.addListener(refresh);
  }

  final DataChanges? _dataChanges;
  Loadable<T> _state = const Loading();
  ApiException? _refreshError;
  bool _refreshing = false;
  bool _disposed = false;

  Loadable<T> get state => _state;

  ApiException? get refreshError => _refreshError;

  bool get isRefreshing => _refreshing;

  @protected
  Future<T> fetch();

  Future<void> load() async {
    _state = const Loading();
    _refreshError = null;
    _notify();
    await _run();
  }

  Future<void> refresh() async {
    if (_state is! Loaded<T>) {
      return load();
    }
    _refreshing = true;
    _notify();
    await _run();
  }

  Future<void> _run() async {
    try {
      final value = await fetch();
      _state = Loaded(value);
      _refreshError = null;
    } on ApiException catch (error) {
      if (_state is Loaded<T>) {
        _refreshError = error;
      } else {
        _state = Failed(error);
      }
    } finally {
      _refreshing = false;
      _notify();
    }
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
