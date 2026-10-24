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

  static const _siteUrlOverride = String.fromEnvironment('WALLET_SITE_URL');

  /// Where the landing site lives, which has the Terms of Use and the Privacy Policy. `--dart-define=WALLET_SITE_URL=`
  /// overrides it; otherwise it follows the BFF: `https://app.example.com` is served next to `https://example.com`,
  /// and in development the site is the folder served on port 5181 (`python -m http.server 5181 --directory site`).
  static String get siteUrl {
    if (_siteUrlOverride.isNotEmpty) {
      return _siteUrlOverride;
    }
    final bff = Uri.tryParse(bffUrl);
    if (bff != null && bff.scheme == 'https' && bff.host.startsWith('app.')) {
      return bff.replace(host: bff.host.substring('app.'.length)).toString();
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:5181';
    }
    return 'http://localhost:5181';
  }

  static Uri get termsUrl => Uri.parse('$siteUrl/termos.html');

  static Uri get privacyUrl => Uri.parse('$siteUrl/privacidade.html');

  /// The installed Windows app, where the lock is Windows Hello.
  static bool get isWindows => !kIsWeb && defaultTargetPlatform == TargetPlatform.windows;

  /// "Conectar banco" opens the Pluggy Connect widget, and the bank's app comes back through the
  /// deep link in the AndroidManifest. Elsewhere (iOS for now, desktop, browser) a connection is
  /// linked by its Item ID.
  static bool get usesPluggyConnect => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// What the BFF calls this app in `X-Wallet-Client`. The browser gets the refresh token in a
  /// cookie; the phone and desktop apps get it in the body and keep it in the system's vault.
  static String get client {
    if (kIsWeb) {
      return 'web';
    }
    return switch (defaultTargetPlatform) {
      TargetPlatform.windows || TargetPlatform.macOS || TargetPlatform.linux => 'desktop',
      TargetPlatform.android || TargetPlatform.iOS || TargetPlatform.fuchsia => 'mobile',
    };
  }
}
