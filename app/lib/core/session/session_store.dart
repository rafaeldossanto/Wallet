import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Where the refresh token lives between app launches. The access token never comes here: it
/// lives only in memory.
abstract interface class SessionStore {
  Future<String?> readRefreshToken();

  Future<void> saveRefreshToken(String token);

  Future<void> clear();
}

/// Phones: Keychain on iOS, Keystore-backed storage on Android.
class SecureSessionStore implements SessionStore {
  SecureSessionStore([this._storage = const FlutterSecureStorage()]);

  static const _key = 'wallet.refresh_token';

  final FlutterSecureStorage _storage;

  @override
  Future<String?> readRefreshToken() => _storage.read(key: _key);

  @override
  Future<void> saveRefreshToken(String token) => _storage.write(key: _key, value: token);

  @override
  Future<void> clear() => _storage.delete(key: _key);
}

/// The browser: the BFF keeps the refresh token in an HttpOnly cookie the page cannot read, so
/// there is nothing to store here. Keeping it out of `localStorage` is the point.
class CookieSessionStore implements SessionStore {
  const CookieSessionStore();

  @override
  Future<String?> readRefreshToken() async => null;

  @override
  Future<void> saveRefreshToken(String token) async {}

  @override
  Future<void> clear() async {}
}
