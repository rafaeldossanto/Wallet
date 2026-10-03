import 'package:flutter/foundation.dart';

import '../api/api_exception.dart';
import 'session_api.dart';
import 'session_store.dart';

enum SessionStatus {
  /// Opening the app: checking whether a session is still valid.
  restoring,

  /// The check could not reach the BFF; a stored session may still be fine.
  unreachable,
  signedOut,
  signedIn,
}

enum SignOutReason { requested, expired, idle }

/// The signed-in user and the access token, in memory only.
class SessionController extends ChangeNotifier {
  SessionController({required this._api, required this._store});

  final SessionApi _api;
  final SessionStore _store;

  SessionStatus _status = SessionStatus.restoring;
  String? _accessToken;
  UserProfile? _user;
  SignOutReason? _lastSignOutReason;
  Future<bool>? _renewing;

  SessionStatus get status => _status;

  bool get isSignedIn => _status == SessionStatus.signedIn;

  String? get accessToken => _accessToken;

  UserProfile? get user => _user;

  /// Why the last session ended, so the login screen can say it (expired, idle).
  SignOutReason? get lastSignOutReason => _lastSignOutReason;

  /// Opening the app: trades the stored refresh token (or the web cookie) for a new session.
  Future<void> restore() async {
    _status = SessionStatus.restoring;
    notifyListeners();
    final stored = await _store.readRefreshToken();
    if (!kIsWeb && stored == null) {
      _setStatus(SessionStatus.signedOut);
      return;
    }
    try {
      await _start(await _api.refresh(stored));
    } on ApiException catch (error) {
      if (error.isUnauthorized) {
        await _store.clear();
        _setStatus(SessionStatus.signedOut);
      } else {
        // Offline, rate limited or the BFF is down: the stored session may still be good.
        _setStatus(SessionStatus.unreachable);
      }
    }
  }

  Future<void> signIn(String email, String password) async {
    await _start(await _api.login(email.trim(), password));
  }

  Future<void> register({required String displayName, required String email, required String password}) async {
    await _api.register(displayName: displayName.trim(), email: email.trim(), password: password);
    await signIn(email, password);
  }

  /// After a 401: renews the session unless another call already did it since [sentAuthorization]
  /// went out, in which case the caller only needs to repeat its request.
  Future<bool> renewAfter(String? sentAuthorization) {
    if (_accessToken != null && sentAuthorization != 'Bearer $_accessToken') {
      return Future.value(true);
    }
    return renew();
  }

  /// One renewal at a time. Two screens failing together must share it: the core rotates the
  /// refresh token on every use and reads a second use of the old one as theft.
  Future<bool> renew() => _renewing ??= _renew().whenComplete(() => _renewing = null);

  Future<bool> _renew() async {
    try {
      final tokens = await _api.refresh(await _store.readRefreshToken());
      _accessToken = tokens.accessToken;
      if (tokens.refreshToken != null) {
        await _store.saveRefreshToken(tokens.refreshToken!);
      }
      return true;
    } on ApiException catch (error) {
      if (error.isUnauthorized) {
        await _end(SignOutReason.expired);
      }
      return false;
    }
  }

  Future<void> signOut({SignOutReason reason = SignOutReason.requested}) async {
    try {
      await _api.logout(await _store.readRefreshToken());
    } on ApiException {
      // The session ends on this device anyway; the core expires the token on its own.
    }
    await _end(reason);
  }

  Future<void> _start(SessionTokens tokens) async {
    _accessToken = tokens.accessToken;
    if (tokens.refreshToken != null) {
      await _store.saveRefreshToken(tokens.refreshToken!);
    }
    try {
      _user = await _api.me();
    } on ApiException {
      _user = null;
    }
    _lastSignOutReason = null;
    _setStatus(SessionStatus.signedIn);
  }

  Future<void> _end(SignOutReason reason) async {
    _accessToken = null;
    _user = null;
    await _store.clear();
    _lastSignOutReason = reason;
    _setStatus(SessionStatus.signedOut);
  }

  void _setStatus(SessionStatus status) {
    _status = status;
    notifyListeners();
  }
}
