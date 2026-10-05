import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/desktop/github_release_feed.dart';
import 'package:wallet/core/desktop/updater.dart';

import '../support/fake_bff.dart';

class _FakeFeed implements ReleaseFeed {
  Release? release;
  Object? failure;
  int calls = 0;

  @override
  Future<Release?> latest() async {
    calls++;
    if (failure case final failure?) {
      throw failure;
    }
    return release;
  }
}

class _FakeStore implements InstallerStore {
  final fetched = <String>[];

  @override
  Future<String> fetch(Release release) async {
    fetched.add(release.version);
    return r'C:\updates\Wallet-Setup-' '${release.version}.exe';
  }
}

class _FakeLauncher implements InstallerLauncher {
  final launches = <(String, Relaunch)>[];

  @override
  Future<void> launch(String installer, Relaunch relaunch) async => launches.add((installer, relaunch));
}

Release _release(String version) =>
    Release(version: version, installerUrl: Uri.parse('https://example.test/$version.exe'), sha256: 'ab' * 32);

void main() {
  group('versions', () {
    test('compare part by part as numbers, with or without the tag\'s v', () {
      expect(isNewer('0.10.0', '0.9.9'), isTrue);
      expect(isNewer('v0.2.0', '0.1.9'), isTrue);
      expect(isNewer('1.0.0', '0.99.99'), isTrue);
      expect(isNewer('0.1.0', '0.1.0'), isFalse);
      expect(isNewer('0.1.0', '0.2.0'), isFalse);
      expect(isNewer('nightly', '0.1.0'), isFalse, reason: 'what is not a version is never offered');
    });
  });

  group('updater', () {
    late _FakeFeed feed;
    late _FakeStore store;
    late _FakeLauncher launcher;
    late bool away;
    late int quits;

    Updater updater({String version = '0.1.0'}) => Updater(
          feed: feed,
          store: store,
          launcher: launcher,
          quit: () async => quits++,
          isAway: () async => away,
          currentVersion: version,
        );

    setUp(() {
      feed = _FakeFeed();
      store = _FakeStore();
      launcher = _FakeLauncher();
      away = false;
      quits = 0;
    });

    test('a development build never looks for updates', () async {
      final subject = updater(version: '');

      subject.start();
      await subject.check();

      expect(subject.state, isA<UpdatesDisabled>());
      expect(feed.calls, 0);
    });

    test('with the window open, a newer version is downloaded and waits for the restart', () async {
      feed.release = _release('0.2.0');
      final subject = updater();
      final seen = <Type>[];
      subject.addListener(() => seen.add(subject.state.runtimeType));

      await subject.check();

      expect(seen, [CheckingForUpdate, DownloadingUpdate, UpdateReady]);
      expect((subject.state as UpdateReady).version, '0.2.0');
      expect(launcher.launches, isEmpty, reason: 'nothing happens behind the user\'s back');

      await subject.restartToUpdate();

      expect(launcher.launches, [(r'C:\updates\Wallet-Setup-0.2.0.exe', Relaunch.open)]);
      expect(quits, 1);
    });

    test('in the tray, the update installs itself and the app comes back to the tray', () async {
      feed.release = _release('0.2.0');
      away = true;
      final subject = updater();

      await subject.check();

      expect(launcher.launches, [(r'C:\updates\Wallet-Setup-0.2.0.exe', Relaunch.tray)]);
      expect(quits, 1);
    });

    test('an update left for later installs at the next check with the window in the tray', () async {
      feed.release = _release('0.2.0');
      final subject = updater();
      await subject.check();
      expect(launcher.launches, isEmpty);

      away = true;
      await subject.check();

      expect(launcher.launches.single.$2, Relaunch.tray);
      expect(store.fetched, ['0.2.0'], reason: 'downloaded once');
    });

    test('quitting from the tray installs a downloaded update and keeps the app closed', () async {
      final subject = updater();
      expect(await subject.installOnQuit(), isFalse, reason: 'nothing downloaded yet');

      feed.release = _release('0.2.0');
      await subject.check();

      expect(await subject.installOnQuit(), isTrue);
      expect(launcher.launches.single.$2, Relaunch.none);
      expect(quits, 0, reason: 'the caller is already quitting');
    });

    test('the same or an older version is nothing to do', () async {
      final subject = updater(version: '0.2.0');

      feed.release = _release('0.2.0');
      await subject.check();
      expect(subject.state, isA<UpToDate>());

      feed.release = _release('0.1.9');
      await subject.check();
      expect(subject.state, isA<UpToDate>());

      feed.release = null;
      await subject.check();
      expect(subject.state, isA<UpToDate>(), reason: 'no release published yet');
      expect(store.fetched, isEmpty);
    });

    test('a failed check leaves the app as it is and the next one tries again', () async {
      final subject = updater();
      feed.failure = DioException(requestOptions: RequestOptions(), type: DioExceptionType.connectionError);

      await subject.check();
      expect(subject.state, isA<UpdateFailed>());

      feed
        ..failure = null
        ..release = _release('0.2.0');
      await subject.check();
      expect(subject.state, isA<UpdateReady>());
    });
  });

  group('GitHub releases', () {
    late FakeBff github;
    late GitHubReleaseFeed releases;

    Map<String, Object?> asset(String name, {String? digest}) => {
          'name': name,
          'browser_download_url': 'https://github.com/rafaeldossanto/Wallet/releases/download/v0.2.0/$name',
          'digest': digest,
        };

    setUp(() {
      github = FakeBff();
      releases = GitHubReleaseFeed(dio: Dio(BaseOptions(baseUrl: 'https://api.github.com'))..httpClientAdapter = github);
    });

    test('the latest release offers its installer with the SHA-256 GitHub publishes', () async {
      github.json('GET', '/repos/rafaeldossanto/Wallet/releases/latest', {
        'tag_name': 'v0.2.0',
        'assets': [
          asset('notes.txt', digest: 'sha256:${'00' * 32}'),
          asset('Wallet-Setup-0.2.0.exe', digest: 'sha256:${'AB' * 32}'),
        ],
      });

      final release = await releases.latest();

      expect(release!.version, '0.2.0');
      expect(release.installerUrl.path, endsWith('/v0.2.0/Wallet-Setup-0.2.0.exe'));
      expect(release.sha256, 'ab' * 32);
    });

    test('a release without this version\'s installer or without its SHA-256 is not offered', () async {
      github.json('GET', '/repos/rafaeldossanto/Wallet/releases/latest', {
        'tag_name': 'v0.2.0',
        'assets': [
          asset('Wallet-Setup-0.1.9.exe', digest: 'sha256:${'00' * 32}'),
          asset('Wallet-Setup-0.2.0.exe'),
        ],
      });

      expect(await releases.latest(), isNull);
    });

    test('no release yet is not an error', () async {
      github.error('GET', '/repos/rafaeldossanto/Wallet/releases/latest', 404, 'Not Found');

      expect(await releases.latest(), isNull);
    });
  });
}
