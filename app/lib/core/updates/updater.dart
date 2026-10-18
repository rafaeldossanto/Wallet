import 'dart:async';

import 'package:flutter/foundation.dart';

/// The version this build carries: `--dart-define=WALLET_VERSION=0.1.0`, which the Desktop and
/// Android workflows pass from the pubspec. Empty in a development build, which never updates itself.
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

/// A published version and its installer: the Windows setup or the Android APK.
class Release {
  const Release({required this.version, required this.installerUrl, required this.sha256});

  final String version;
  final Uri installerUrl;

  /// Hex SHA-256 of the installer, as GitHub publishes it for every release file.
  final String sha256;
}

abstract interface class ReleaseFeed {
  /// The newest published release with an installer for this platform, or null when there is none
  /// yet.
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

/// Why the system did not install a downloaded update (Android).
enum InstallProblem {
  /// Android installs nothing from an app until the user allows it, once, in its settings
  /// ("Instalar apps desconhecidos" → Wallet → "Permitir desta fonte").
  notAllowed,

  /// The APK is signed with another key than the installed app, so it cannot go over it.
  conflict,

  /// Anything else: no space, a broken file, the installer failing.
  failed,
}

class InstallRefused implements Exception {
  const InstallRefused(this.problem);

  final InstallProblem problem;

  @override
  String toString() => 'The system did not install the update: ${problem.name}';
}

abstract interface class InstallerLauncher {
  /// Windows: starts the installer on its own and returns; the caller then quits so it can replace
  /// the app. Android: hands the APK to the system's installer, which asks the user to confirm,
  /// and returns if they say no; when they say yes Android replaces the app, ending this process.
  /// Throws [InstallRefused] when the system will not install it.
  Future<void> launch(String installer, Relaunch relaunch);

  /// Opens the setting where the user lets Wallet install apps and says, once they are back,
  /// whether they did. Only Android asks; elsewhere it is always allowed.
  Future<bool> allowInstalls();
}

/// How a downloaded update gets installed, which the buttons and the notice tell.
enum InstallFlow {
  /// Windows: the app closes, the installer replaces it and opens it again.
  restart,

  /// Android: the system's installer asks to confirm, then replaces the app.
  confirm,
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
  const UpdateReady(this.version, this.installer, {this.problem});

  final String version;
  final String installer;

  /// Why the last try to install it did not go through, or null.
  final InstallProblem? problem;
}

/// The check or the download failed; the app goes on, and the next check tries again.
final class UpdateFailed extends UpdateState {
  const UpdateFailed();
}

/// Keeps the installed app up to date, the way Discord does: it looks for a newer release when it
/// starts and every few hours (sooner after a failure), downloads it in the background and checks
/// it. On Windows, while the window is in the tray the update installs itself and the app comes
/// back to the tray; with the window open it waits for the user to restart, or installs when they
/// quit. On Android it never installs on its own: the user taps "Atualizar" and confirms in the
/// system's installer.
class Updater extends ChangeNotifier {
  Updater({
    required this._feed,
    required this._store,
    required this._launcher,
    required this._quit,
    required this._isAway,
    this.currentVersion = AppVersion.current,
    this.flow = InstallFlow.restart,
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
  final InstallFlow flow;
  final Duration interval;
  final Duration retryAfterFailure;

  UpdateState _state;
  String? _updatedTo;
  Timer? _timer;
  int _failures = 0;
  bool _disposed = false;

  UpdateState get state => _state;

  /// The version an update just installed (the Windows installer reopens the app with
  /// `--updated`; Android compares with the version it last ran), until the user closes its notice.
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

  /// The "Reiniciar" button on Windows, which installs now and opens the new version; "Atualizar"
  /// on Android, which hands it to the system's installer.
  Future<void> restartToUpdate() async {
    if (_state case UpdateReady(:final version, :final installer, :final problem)) {
      if (problem != null) {
        _set(UpdateReady(version, installer));
      }
      try {
        await _install(installer, Relaunch.open);
      } on InstallRefused catch (refused) {
        debugPrint('[UPDATE] $refused');
        if (_state case UpdateReady(version: final current) when current == version) {
          _set(UpdateReady(version, installer, problem: refused.problem));
        }
      }
    }
  }

  /// Android, the first time: opens the setting that lets Wallet install apps and, once the user
  /// allowed it and is back, goes on to the installer's confirmation.
  Future<void> allowInstalls() async {
    if (_state case UpdateReady(problem: InstallProblem.notAllowed)) {
      if (await _launcher.allowInstalls()) {
        await restartToUpdate();
      }
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
