import 'package:flutter/widgets.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

import '../session/session_controller.dart';

/// Phones only: after a few minutes in the background the app asks for the fingerprint, face or
/// device PIN before showing any balance again.
class AppLock extends ChangeNotifier with WidgetsBindingObserver {
  AppLock({
    required this._session,
    LocalAuthentication? auth,
    this._storage = const FlutterSecureStorage(),
    this.lockAfter = const Duration(minutes: 5),
    DateTime Function()? now,
  })  : _auth = auth ?? LocalAuthentication(),
        _now = now ?? DateTime.now;

  static const _enabledKey = 'wallet.biometric_lock';

  final SessionController _session;
  final LocalAuthentication _auth;
  final FlutterSecureStorage _storage;
  final Duration lockAfter;
  final DateTime Function() _now;

  bool _available = false;
  bool _enabled = true;
  bool _locked = false;
  DateTime? _backgroundSince;

  /// The device has a fingerprint, face or screen lock to ask for.
  bool get isAvailable => _available;

  bool get isEnabled => _enabled;

  bool get isLocked => _locked;

  Future<void> start() async {
    try {
      _available = await _auth.isDeviceSupported();
    } on LocalAuthException {
      _available = false;
    }
    _enabled = await _storage.read(key: _enabledKey) != 'off';
    WidgetsBinding.instance.addObserver(this);
    notifyListeners();
  }

  Future<void> setEnabled(bool enabled) async {
    _enabled = enabled;
    await _storage.write(key: _enabledKey, value: enabled ? 'on' : 'off');
    notifyListeners();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        _backgroundSince ??= _now();
      case AppLifecycleState.resumed:
        final since = _backgroundSince;
        _backgroundSince = null;
        if (since != null && _shouldLock && _now().difference(since) >= lockAfter) {
          _locked = true;
          notifyListeners();
        }
      case AppLifecycleState.inactive:
      case AppLifecycleState.detached:
        break;
    }
  }

  bool get _shouldLock => _enabled && _available && _session.isSignedIn;

  /// True when unlocked. The system dialog says [reason].
  Future<bool> unlock(String reason) async {
    try {
      final unlocked = await _auth.authenticate(localizedReason: reason, persistAcrossBackgrounding: true);
      if (unlocked) {
        _locked = false;
        notifyListeners();
      }
      return unlocked;
    } on LocalAuthException {
      return false;
    }
  }

  /// Signing out also unlocks: the login screen shows nothing private.
  Future<void> signOut() async {
    _locked = false;
    notifyListeners();
    await _session.signOut();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
