import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Opening the app when the user signs in to Windows.
abstract interface class StartupLaunch {
  bool get isEnabled;

  void setEnabled(bool enabled);
}

/// Kept only while the app runs: the default in tests.
class MemoryStartupLaunch implements StartupLaunch {
  @override
  bool isEnabled = false;

  @override
  void setEnabled(bool enabled) => isEnabled = enabled;
}

/// The Windows app's own settings: closing to the tray and opening with Windows.
class DesktopSettings extends ChangeNotifier {
  DesktopSettings._(this._preferences, this._startup, this._closeToTray);

  static const _closeToTrayKey = 'wallet.desktop.close_to_tray';

  /// Read before the window opens. Closing to the tray is on until the user turns it off, as in
  /// Discord.
  static Future<DesktopSettings> load(StartupLaunch startup, [SharedPreferencesAsync? preferences]) async {
    final store = preferences ?? SharedPreferencesAsync();
    return DesktopSettings._(store, startup, await store.getBool(_closeToTrayKey) ?? true);
  }

  final SharedPreferencesAsync _preferences;
  final StartupLaunch _startup;
  bool _closeToTray;

  /// The window's X hides it in the tray; the app quits from the tray's menu.
  bool get closeToTray => _closeToTray;

  bool get launchAtStartup => _startup.isEnabled;

  Future<void> setCloseToTray(bool enabled) async {
    _closeToTray = enabled;
    notifyListeners();
    await _preferences.setBool(_closeToTrayKey, enabled);
  }

  void setLaunchAtStartup(bool enabled) {
    _startup.setEnabled(enabled);
    notifyListeners();
  }
}
