import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../updates/github_release_feed.dart';
import '../updates/installer_io.dart';
import '../updates/updater.dart';
import 'android.dart';
import 'package_installer.dart';

/// The Android app's updater: it reads the Android workflow's `latest-android.json`, downloads the
/// APK into the app's private storage, checks its SHA-256 and leaves installing to the user, who
/// taps "Atualizar" and confirms in the system's installer. Null on Windows and on the iPhone.
Future<Updater?> startAndroidUpdater() async {
  if (!Platform.isAndroid) {
    return null;
  }
  final Directory folder;
  try {
    folder = await getApplicationSupportDirectory();
  } on Exception catch (error) {
    // Without its folder the app still opens, only without updates.
    debugPrint('[UPDATE] $error');
    return null;
  }
  final store = DownloadedInstallers(
    directory: Directory('${folder.path}${Platform.pathSeparator}updates'),
    fileName: (version) => 'Wallet-$version.apk',
  );
  var justUpdated = false;
  try {
    justUpdated = await rememberVersion(SharedPreferencesAsync(), AppVersion.current);
    if (justUpdated) {
      // The APK that brought this version has done its job.
      await store.clear();
    }
  } on Exception catch (error) {
    debugPrint('[UPDATE] $error');
  }
  final updater = Updater(
    feed: ManifestReleaseFeed.android(),
    store: store,
    launcher: const AndroidPackageInstaller(),
    // Android itself replaces the app once the user confirms: nothing to close here, and nothing
    // installs while nobody is looking.
    quit: () async {},
    isAway: () async => false,
    flow: InstallFlow.confirm,
    justUpdated: justUpdated,
  );
  updater.start();
  return updater;
}
