import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';

enum ConnectionStatus {
  active,
  syncing,
  needsAttention,
  unknown;

  static ConnectionStatus parse(String? value) => switch (value) {
        'ACTIVE' => active,
        'SYNCING' => syncing,
        'NEEDS_ATTENTION' => needsAttention,
        _ => unknown,
      };
}

class Connection {
  const Connection({
    required this.id,
    required this.status,
    this.institutionName,
    this.institutionImageUrl,
    this.lastSyncedAt,
    this.consentExpiresAt,
  });

  factory Connection.fromJson(Json json) => Connection(
        id: json.string('id'),
        institutionName: json.stringOrNull('institutionName'),
        institutionImageUrl: json.stringOrNull('institutionImageUrl'),
        status: ConnectionStatus.parse(json.stringOrNull('status')),
        lastSyncedAt: json.instantOrNull('lastSyncedAt'),
        consentExpiresAt: json.instantOrNull('consentExpiresAt'),
      );

  final String id;
  final String? institutionName;
  final String? institutionImageUrl;
  final ConnectionStatus status;
  final DateTime? lastSyncedAt;
  final DateTime? consentExpiresAt;

  bool get isSyncing => status == ConnectionStatus.syncing;
}

class ConnectionsApi {
  ConnectionsApi(this._api);

  /// What the core accepts as a Pluggy item id.
  static final itemIdPattern = RegExp(r'^[A-Za-z0-9-]{1,100}$');

  final ApiClient _api;

  Future<List<Connection>> list() async =>
      [for (final item in Json.listOf(await _api.get('/api/connections'))) Connection.fromJson(item)];

  Future<Connection> link(String itemId) async =>
      Connection.fromJson(Json.of(await _api.post('/api/connections', body: {'providerItemId': itemId})));

  Future<void> unlink(String connectionId) => _api.delete('/api/connections/${Uri.encodeComponent(connectionId)}');

  Future<void> sync(String connectionId) => _api.post('/api/connections/${Uri.encodeComponent(connectionId)}/sync');
}
