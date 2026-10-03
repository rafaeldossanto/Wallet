import 'dart:async';

import '../../../core/api/api_exception.dart';
import '../../../core/state/data_changes.dart';
import '../../../core/state/loadable.dart';
import '../data/connections_api.dart';

/// The connections and their status. Syncs run in the core's background, so the list is checked
/// again every few seconds while a connection is syncing, and for a while after the user links
/// or syncs (the core may not have started yet). When a sync lands, the other screens are told.
class ConnectionsController extends LoadController<List<Connection>> {
  ConnectionsController(
    this._api, {
    DataChanges? dataChanges,
    this.pollEvery = const Duration(seconds: 3),
    this.watchAfterAction = const Duration(seconds: 30),
    DateTime Function()? now,
  })  : _changes = dataChanges,
        _now = now ?? DateTime.now;

  final ConnectionsApi _api;

  /// Only rung, never listened to: this screen is where the change is seen first.
  final DataChanges? _changes;
  final Duration pollEvery;
  final Duration watchAfterAction;
  final DateTime Function() _now;

  Timer? _poll;
  DateTime? _watchUntil;
  Map<String, DateTime?> _lastSynced = const {};
  bool _closed = false;

  @override
  Future<List<Connection>> fetch() async {
    final connections = await _api.list();
    final lastSynced = {for (final connection in connections) connection.id: connection.lastSyncedAt};
    final landed = lastSynced.entries
        .any((entry) => _lastSynced.containsKey(entry.key) && _lastSynced[entry.key] != entry.value);
    if (landed) {
      _changes?.changed();
    }
    _lastSynced = lastSynced;
    _schedulePoll(connections.any((connection) => connection.isSyncing));
    return connections;
  }

  void _schedulePoll(bool anySyncing) {
    _poll?.cancel();
    final watching = _watchUntil != null && _now().isBefore(_watchUntil!);
    if (!_closed && (anySyncing || watching)) {
      _poll = Timer(pollEvery, refresh);
    }
  }

  /// Null when it worked; the error to show otherwise.
  Future<ApiException?> link(String itemId) => _act(() async {
        await _api.link(itemId.trim());
        _changes?.changed();
      });

  Future<ApiException?> unlink(String connectionId) => _act(() async {
        await _api.unlink(connectionId);
        _changes?.changed();
      });

  Future<ApiException?> sync(String connectionId) => _act(() => _api.sync(connectionId));

  Future<ApiException?> _act(Future<void> Function() action) async {
    try {
      await action();
      _watchUntil = _now().add(watchAfterAction);
      await refresh();
      return null;
    } on ApiException catch (error) {
      return error;
    }
  }

  @override
  void dispose() {
    _closed = true;
    _poll?.cancel();
    super.dispose();
  }
}
