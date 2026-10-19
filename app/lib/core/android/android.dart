import 'package:shared_preferences/shared_preferences.dart';

import '../updates/updater.dart';

// What only the Android app has: `startAndroidUpdater()` starts the updater that brings new
// versions from the Releases to the tablet; anywhere else (Windows, the iPhone, the browser) it
// returns null.
export 'android_start_stub.dart' if (dart.library.io) 'android_start_io.dart' show startAndroidUpdater;

/// The version the app ran last time, in the app's preferences.
const lastVersionKey = 'wallet.updates.last_version';

/// Android replaces the app and leaves opening it again to the user, so nothing tells the new
/// version it was just installed: the app remembers the version it last ran. True on the first
/// start of a version newer than that one.
Future<bool> rememberVersion(SharedPreferencesAsync preferences, String current) async {
  if (current.isEmpty) {
    return false;
  }
  final last = await preferences.getString(lastVersionKey);
  if (last != current) {
    await preferences.setString(lastVersionKey, current);
  }
  return last != null && isNewer(current, last);
}
