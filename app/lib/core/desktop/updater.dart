import 'dart:async';

import 'package:flutter/foundation.dart';

/// The version this build carries: `--dart-define=WALLET_VERSION=0.1.0`, which the Desktop workflow
/// passes from the pubspec. Empty in a development build, which never updates itself.
abstract final class AppVersion {
  static const current = String.fromEnvironment('WALLET_VERSION');
}

/// `0.10.2` is newer than `0.9.7`: the parts compare as numbers. Null when [text] is not
/// `major.minor.patch`.
List<int>? parseVersion(String text) {
  final match = RegExp(r'^v?(\d+)\.(\d+)\.(\d+)$').firstMatch(text.trim());
  return match == null ? null : [for (var group = 1; group <= 3; group++) int.parse(match.group(group)!)];
}

bool isNewer(String candidate, String than) {
  final next = parseVersion(candidate);
  final current = parseVersion(than);
  if (next == null || current == null) {
    return false;
  }
  for (var part = 0; part < 3; part++) {
    if (next[part] != current[part]) {
      return next[part] > current[part];
    }
  }
  return false;
}

/// A published version and its Windows installer.
class Release {
  const Release({required this.version, required this.installerUrl, required this.sha256});

  final String version;
  final Uri installerUrl;

  /// Hex SHA-256 of the installer, as GitHub publishes it for every release file.
  final String sha256;
}

abstract interface class ReleaseFeed {
  /// The newest published release with a Windows installer, or null when there is none yet.
  Future<Release?> latest();
}

/// A downloaded installer, checked against the release's SHA-256.
abstract interface class InstallerStore {
  Future<String> fetch(Release release);
}

/// What the app does after the installer replaces it.
enum Relaunch {
  /// Open again, as the user asked to restart.
  open,

  /// Open again in the tray: the update happened while nobody was looking.
  tray,

  /// Stay closed: the user was quitting.
  none,
}

abstract interface class InstallerLauncher {
  /// Starts the installer on its own and returns; the caller then quits so it can replace the app.
  Future<void> launch(String installer, Relaunch relaunch);
}

sealed class UpdateState {
  const UpdateState();
}

/// A development build: no version to compare.
final class UpdatesDisabled extends UpdateState {
  const UpdatesDisabled();
}

final class UpToDate extends UpdateState {
  const UpToDate();
}

final class CheckingForUpdate extends UpdateState {
  const CheckingForUpdate();
}

final class DownloadingUpdate extends UpdateState {
  const DownloadingUpdate(this.version);

  final String version;
}

final class UpdateReady extends UpdateState {
  const UpdateReady(this.version, this.installer);

  final String version;
  final String installer;
}

/// The check or the download failed; the app goes on, and the next check tries again.
final class UpdateFailed extends UpdateState {
  const UpdateFailed();
}

/// Keeps the Windows app up to date, the way Discord does: it looks for a newer release when it
/// starts and every few hours (sooner after a failure), downloads it in the background and checks it. While the window is
/// in the tray the update installs itself and the app comes back to the tray; with the window open
/// it waits for the user to restart, or installs when they quit.
class Updater extends ChangeNotifier {
  Updater({
    required this._feed,
    required this._store,
    required this._launcher,
    required this._quit,
    required this._isAway,
    this.currentVersion = AppVersion.current,
    this.interval = const Duration(hours: 6),
    this.retryAfterFailure = const Duration(minutes: 30),
    bool justUpdated = false,
  })  : _state = currentVersion.isEmpty ? const UpdatesDisabled() : const UpToDate(),
        _updatedTo = justUpdated && currentVersion.isNotEmpty ? currentVersion : null;

  final ReleaseFeed _feed;
  final InstallerStore _store;
  final InstallerLauncher _launcher;

  /// Closes the app for good, so the installer can replace it.
  final Future<void> Function() _quit;

  /// True while the window is hidden in the tray.
  final Future<bool> Function() _isAway;

  final String currentVersion;
  final Duration interval;
  final Duration retryAfterFailure;

  UpdateState _state;
  String? _updatedTo;
  Timer? _timer;
  int _failures = 0;
  bool _disposed = false;

  UpdateState get state => _state;

  /// The version an update just installed (the installer reopens the app with `--updated`), until
  /// the user closes its notice.
  String? get updatedTo => _updatedTo;

  void dismissUpdated() {
    _updatedTo = null;
    if (!_disposed) {
      notifyListeners();
    }
  }

  bool get isEnabled => _state is! UpdatesDisabled;

  void start() {
    if (!isEnabled) {
      return;
    }
    unawaited(_checkAndSchedule());
  }

  /// After a failed check (no internet, GitHub refusing) the next one comes sooner: 30 minutes,
  /// then an hour, two hours..., back to [interval] once a check works.
  Duration get nextCheckIn {
    if (_failures == 0) {
      return interval;
    }
    final backoff = retryAfterFailure * (1 << (_failures - 1).clamp(0, 16));
    return backoff < interval ? backoff : interval;
  }

  Future<void> _checkAndSchedule() async {
    await check();
    if (_disposed) {
      return;
    }
    _timer?.cancel();
    _timer = Timer(nextCheckIn, () => unawaited(_checkAndSchedule()));
  }

  Future<void> check() async {
    if (!isEnabled || _state is CheckingForUpdate || _state is DownloadingUpdate) {
      return;
    }
    if (_state case UpdateReady(:final installer) when await _isAway()) {
      return _install(installer, Relaunch.tray);
    }
    _set(const CheckingForUpdate());
    try {
      final release = await _feed.latest();
      if (release == null || !isNewer(release.version, currentVersion)) {
        _failures = 0;
        _set(const UpToDate());
        return;
      }
      _set(DownloadingUpdate(release.version));
      final installer = await _store.fetch(release);
      _failures = 0;
      _set(UpdateReady(release.version, installer));
      if (await _isAway()) {
        await _install(installer, Relaunch.tray);
      }
    } on Exception catch (error) {
      debugPrint('[UPDATE] $error');
      _failures++;
      _set(const UpdateFailed());
    }
  }

  /// The "restart" button: installs now and opens the new version.
  Future<void> restartToUpdate() async {
    if (_state case UpdateReady(:final installer)) {
      await _install(installer, Relaunch.open);
    }
  }

  /// Quitting from the tray with an update downloaded: install it on the way out.
  Future<bool> installOnQuit() async {
    if (_state case UpdateReady(:final installer)) {
      await _launcher.launch(installer, Relaunch.none);
      return true;
    }
    return false;
  }

  Future<void> _install(String installer, Relaunch relaunch) async {
    await _launcher.launch(installer, relaunch);
    await _quit();
  }

  void _set(UpdateState state) {
    _state = state;
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    super.dispose();
  }
}
