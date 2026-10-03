import 'package:flutter/foundation.dart';

/// Where the BFF is. `--dart-define=WALLET_BFF_URL=https://...` overrides it; the defaults are the
/// development addresses. The Android emulator reaches the computer's localhost at 10.0.2.2.
abstract final class AppConfig {
  static const _bffUrlOverride = String.fromEnvironment('WALLET_BFF_URL');

  static String get bffUrl {
    if (_bffUrlOverride.isNotEmpty) {
      return _bffUrlOverride;
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }

  /// What the BFF calls this app in `X-Wallet-Client`: it decides where the refresh token goes.
  static String get client => kIsWeb ? 'web' : 'mobile';
}
