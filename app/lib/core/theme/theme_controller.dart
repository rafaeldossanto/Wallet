import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Where the chosen theme is kept between launches.
abstract interface class ThemePreference {
  Future<String?> read();

  Future<void> write(String value);
}

class SharedThemePreference implements ThemePreference {
  SharedThemePreference([SharedPreferencesAsync? preferences]) : _preferences = preferences ?? SharedPreferencesAsync();

  static const _key = 'wallet.theme';

  final SharedPreferencesAsync _preferences;

  @override
  Future<String?> read() => _preferences.getString(_key);

  @override
  Future<void> write(String value) => _preferences.setString(_key, value);
}

/// Kept only while the app runs: the default in tests.
class MemoryThemePreference implements ThemePreference {
  String? _value;

  @override
  Future<String?> read() async => _value;

  @override
  Future<void> write(String value) async => _value = value;
}

/// Light, dark or the device's choice. Dark until the user picks.
class ThemeController extends ChangeNotifier {
  ThemeController(this._preference, [this._mode = ThemeMode.dark]);

  final ThemePreference _preference;
  ThemeMode _mode;

  ThemeMode get mode => _mode;

  /// Read before the first frame, so the app never flashes the wrong theme.
  static Future<ThemeController> load(ThemePreference preference) async {
    final saved = await preference.read();
    final mode = ThemeMode.values.where((mode) => mode.name == saved).firstOrNull ?? ThemeMode.dark;
    return ThemeController(preference, mode);
  }

  Future<void> setMode(ThemeMode mode) async {
    if (mode == _mode) {
      return;
    }
    _mode = mode;
    notifyListeners();
    await _preference.write(mode.name);
  }

  /// The header's sun and moon: flips whatever is on screen now.
  Future<void> toggle(Brightness current) =>
      setMode(current == Brightness.dark ? ThemeMode.light : ThemeMode.dark);
}
