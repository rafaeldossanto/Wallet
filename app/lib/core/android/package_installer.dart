import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../updates/updater.dart';

/// Hands a downloaded APK to Android's package installer through a session, the current API (the
/// old ACTION_INSTALL_PACKAGE intent is deprecated). The native side is
/// `android/app/src/main/kotlin/com/wallet/wallet/UpdateInstaller.kt`; it copies the APK from the
/// app's private storage into the session, so no file is shared with other apps.
class AndroidPackageInstaller implements InstallerLauncher {
  const AndroidPackageInstaller([this._channel = channel]);

  static const channel = MethodChannel('wallet/package_installer');

  final MethodChannel _channel;

  /// Android shows its confirmation ("Atualizar este app?"). This returns when the user says no;
  /// when they say yes, Android replaces the app and this process ends.
  @override
  Future<void> launch(String installer, Relaunch relaunch) async {
    try {
      await _channel.invokeMethod<String>('install', {'path': installer});
    } on PlatformException catch (error) {
      debugPrint('[UPDATE] ${error.code}: ${error.message}');
      throw InstallRefused(switch (error.code) {
        'not-allowed' => InstallProblem.notAllowed,
        'conflict' => InstallProblem.conflict,
        _ => InstallProblem.failed,
      });
    } on MissingPluginException {
      throw const InstallRefused(InstallProblem.failed);
    }
  }

  /// Opens Configurações → Instalar apps desconhecidos → Wallet, and answers when the user is back.
  @override
  Future<bool> allowInstalls() async {
    try {
      return await _channel.invokeMethod<bool>('allowInstalls') ?? false;
    } on PlatformException catch (error) {
      debugPrint('[UPDATE] ${error.code}: ${error.message}');
      return false;
    }
  }
}
