import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_config.dart';

/// Where the app finds the BFF. A build points at one ([AppConfig.bffUrl]); a phone that talks to
/// the BFF running on the user's PC gets the PC's address typed once on the login screen, and it
/// is remembered. Not in the browser, which always uses the build's.
class ServerAddress extends ChangeNotifier {
  ServerAddress._(this._preferences, this._saved);

  static const _key = 'wallet.server_url';

  /// The BFF's port when the address is typed without one.
  static const defaultPort = 8080;

  static Future<ServerAddress> load([SharedPreferencesAsync? preferences]) async {
    final store = preferences ?? SharedPreferencesAsync();
    return ServerAddress._(store, await store.getString(_key));
  }

  final SharedPreferencesAsync _preferences;
  String? _saved;

  String get current => _saved ?? AppConfig.bffUrl;

  bool get isCustom => _saved != null;

  /// `192.168.0.10`, `192.168.0.10:8080` or `http://...` → `http://192.168.0.10:8080`. Null when
  /// it is not an address.
  static String? normalize(String typed) {
    var text = typed.trim();
    if (text.isEmpty) {
      return null;
    }
    if (!text.contains('://')) {
      text = 'http://$text';
    }
    final uri = Uri.tryParse(text);
    if (uri == null || uri.host.isEmpty || !{'http', 'https'}.contains(uri.scheme) || uri.hasQuery) {
      return null;
    }
    final port = uri.hasPort ? uri.port : (typed.contains('://') ? null : defaultPort);
    return Uri(scheme: uri.scheme, host: uri.host, port: port).toString();
  }

  /// False, and nothing saved, when [typed] is not an address.
  Future<bool> set(String typed) async {
    final address = normalize(typed);
    if (address == null) {
      return false;
    }
    _saved = address;
    notifyListeners();
    await _preferences.setString(_key, address);
    return true;
  }

  /// Back to the build's address.
  Future<void> reset() async {
    _saved = null;
    notifyListeners();
    await _preferences.remove(_key);
  }
}
