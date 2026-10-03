import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/format/dates.dart';
import '../../../core/models/account.dart';
import '../../../core/models/transaction.dart';
import '../../../core/state/data_changes.dart';
import '../data/transactions_api.dart';

/// The statement: one month, filtered, loaded a page at a time as the user scrolls.
class TransactionsController extends ChangeNotifier {
  TransactionsController(this._api, {this._dataChanges, String? accountId, DateTime? today})
      : _filters = TransactionFilters(month: Dates.firstOfMonth(today ?? DateTime.now()), accountId: accountId) {
    _dataChanges?.addListener(start);
  }

  final TransactionsApi _api;
  final DataChanges? _dataChanges;

  TransactionFilters _filters;
  List<Transaction> _items = const [];
  List<Account> _accounts = const [];
  int _page = 0;
  bool _hasMore = false;
  bool _loading = false;
  bool _loadingMore = false;
  ApiException? _error;
  int _generation = 0;
  bool _disposed = false;

  TransactionFilters get filters => _filters;

  List<Transaction> get items => _items;

  List<Account> get accounts => _accounts;

  bool get isLoading => _loading;

  bool get isLoadingMore => _loadingMore;

  bool get hasMore => _hasMore;

  ApiException? get error => _error;

  Map<String, String> get accountNames => {for (final account in _accounts) account.id: account.name};

  /// The first load, and again when a connection changes: new accounts come with new data.
  Future<void> start() async {
    await Future.wait([_loadAccounts(), reload()]);
  }

  /// The account filter is a convenience: without the list, the statement still works.
  Future<void> _loadAccounts() async {
    try {
      _accounts = await _api.accounts();
      _notify();
    } on ApiException {
      _accounts = const [];
    }
  }

  void setMonth(DateTime month) => _apply(_filters.copyWith(month: Dates.firstOfMonth(month)));

  void setAccount(String? accountId) => _apply(_filters.copyWith(accountId: () => accountId));

  void setDirection(Direction? direction) => _apply(_filters.copyWith(direction: () => direction));

  void setQuery(String query) {
    if (query.trim() != _filters.query.trim()) {
      _apply(_filters.copyWith(query: query));
    }
  }

  void _apply(TransactionFilters filters) {
    _filters = filters;
    reload();
  }

  /// Back to page 1. A response for filters the user already left is dropped.
  Future<void> reload() async {
    final generation = ++_generation;
    _loading = true;
    _error = null;
    _notify();
    try {
      final page = await _api.page(_filters, page: 1);
      if (generation != _generation) {
        return;
      }
      _items = page.items;
      _page = page.page;
      _hasMore = page.hasMore;
    } on ApiException catch (error) {
      if (generation == _generation) {
        _items = const [];
        _hasMore = false;
        _error = error;
      }
    } finally {
      if (generation == _generation) {
        _loading = false;
        _notify();
      }
    }
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loading || _loadingMore) {
      return;
    }
    final generation = _generation;
    _loadingMore = true;
    _notify();
    try {
      final page = await _api.page(_filters, page: _page + 1);
      if (generation != _generation) {
        return;
      }
      _items = [..._items, ...page.items];
      _page = page.page;
      _hasMore = page.hasMore;
    } on ApiException catch (error) {
      if (generation == _generation) {
        _error = error;
      }
    } finally {
      _loadingMore = false;
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
    _dataChanges?.removeListener(start);
    super.dispose();
  }
}
