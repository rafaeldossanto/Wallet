import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';

enum ConnectionStatus {
  active,
  syncing,
  needsAttention,

  /// The Open Finance authorization ended; nothing is read until the user renews it.
  consentExpired,
  unknown;

  static ConnectionStatus parse(String? value) => switch (value) {
        'ACTIVE' => active,
        'SYNCING' => syncing,
        'NEEDS_ATTENTION' => needsAttention,
        'CONSENT_EXPIRED' => consentExpired,
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

  /// How many days before the end the card starts to warn.
  static const consentWarningDays = 7;

  /// The authorization ended: the server marked it so, or its date has passed (the server may not
  /// have looked yet). A connection without a date never ends (Meu Pluggy items carry none).
  bool consentEnded(DateTime now) =>
      status == ConnectionStatus.consentExpired || (consentExpiresAt != null && !consentExpiresAt!.isAfter(now));

  /// Calendar days until the authorization ends, only inside the last [consentWarningDays] and while
  /// it still holds; null otherwise. 0 = it ends today.
  int? consentDaysLeft(DateTime now) {
    final ends = consentExpiresAt?.toLocal();
    if (ends == null || consentEnded(now)) {
      return null;
    }
    final days = DateTime(ends.year, ends.month, ends.day).difference(DateTime(now.year, now.month, now.day)).inDays;
    return days <= consentWarningDays ? days : null;
  }
}

class ConnectionsApi {
  ConnectionsApi(this._api);

  /// What the core accepts as a Pluggy item id.
  static final itemIdPattern = RegExp(r'^[A-Za-z0-9-]{1,100}$');

  final ApiClient _api;

  Future<List<Connection>> list() async =>
      [for (final item in Json.listOf(await _api.get('/api/connections'))) Connection.fromJson(item)];

  /// The text of the purpose screen ("Antes de conectar") the user agrees to before any connection or
  /// renewal; the server requires it and records it.
  static const consentVersion = 'open-finance-v1';

  /// Opens the Pluggy Connect widget for 30 minutes, for one connection. With [connectionId] it is the
  /// widget in update mode, to renew that connection's authorization.
  Future<String> connectToken({String? connectionId}) async => Json.of(await _api.post(
          '/api/connections/connect-token',
          body: {'consentVersion': consentVersion, 'connectionId': ?connectionId}))
      .string('connectToken');

  Future<Connection> link(String itemId) async => Connection.fromJson(
      Json.of(await _api.post('/api/connections', body: {'providerItemId': itemId, 'consentVersion': consentVersion})));

  /// After Pluggy Connect renewed the authorization: the server reads the new date, records the new
  /// consent and brings the connection back.
  Future<Connection> renew(String connectionId) async => Connection.fromJson(Json.of(await _api.post(
      '/api/connections/${Uri.encodeComponent(connectionId)}/renew',
      body: {'consentVersion': consentVersion})));

  Future<void> unlink(String connectionId) => _api.delete('/api/connections/${Uri.encodeComponent(connectionId)}');

  Future<void> sync(String connectionId) => _api.post('/api/connections/${Uri.encodeComponent(connectionId)}/sync');
}
