import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/in_memory_shared_preferences_async.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_async_platform_interface.dart';
import 'package:wallet/core/android/android.dart';
import 'package:wallet/core/android/package_installer.dart';
import 'package:wallet/core/updates/github_release_feed.dart';
import 'package:wallet/core/updates/installer_io.dart';
import 'package:wallet/core/updates/updater.dart';

const _manifestUrl = 'https://github.com/rafaeldossanto/Wallet/releases/latest/download/latest-android.json';
const _apkUrl = 'https://github.com/rafaeldossanto/Wallet/releases/download/v0.2.0/Wallet-0.2.0.apk';

/// GitHub's release download links, serving the Android workflow's manifest and APK as GitHub
/// serves release files: octet-stream.
class _Releases implements HttpClientAdapter {
  _Releases(this.apk, {String? published})
      : manifest = jsonEncode({'version': '0.2.0', 'apk': 'Wallet-0.2.0.apk', 'sha256': published ?? sha256.convert(apk).toString()});

  final List<int> apk;
  String? manifest;
  final requested = <Uri>[];

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requested.add(options.uri);
    const octetStream = {Headers.contentTypeHeader: ['application/octet-stream']};
    return switch ((options.uri.toString(), manifest)) {
      (_manifestUrl, final String manifest) => ResponseBody.fromString(manifest, 200, headers: octetStream),
      (_apkUrl, _) => ResponseBody.fromBytes(apk, 200, headers: octetStream),
      _ => ResponseBody.fromString('Not Found', 404),
    };
  }

  @override
  void close({bool force = false}) {}
}

/// Android's package installer, as the updater sees it.
class _SystemInstaller implements InstallerLauncher {
  final handed = <String>[];

  /// How the system answers the next tries, in order; null is the user saying no.
  final answers = <InstallProblem?>[];

  bool userAllows = true;
  int sentToSettings = 0;

  @override
  Future<void> launch(String installer, Relaunch relaunch) async {
    handed.add(installer);
    if (answers.isNotEmpty) {
      if (answers.removeAt(0) case final problem?) {
        throw InstallRefused(problem);
      }
    }
  }

  @override
  Future<bool> allowInstalls() async {
    sentToSettings++;
    return userAllows;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the tablet', () {
    final apk = [for (var byte = 0; byte < 8192; byte++) byte % 241];
    late Directory directory;
    late _Releases github;
    late _SystemInstaller system;

    Updater tablet() {
      final dio = Dio()..httpClientAdapter = github;
      return Updater(
        feed: ManifestReleaseFeed.android(dio: dio),
        store: DownloadedInstallers(dio: dio, directory: directory, fileName: (version) => 'Wallet-$version.apk'),
        launcher: system,
        quit: () async {},
        isAway: () async => false,
        flow: InstallFlow.confirm,
        currentVersion: '0.1.5',
      );
    }

    List<String> files() => [for (final file in directory.listSync()) file.uri.pathSegments.last];

    setUp(() async {
      directory = await Directory.systemTemp.createTemp('wallet-apks');
      github = _Releases(apk);
      system = _SystemInstaller();
    });

    tearDown(() => directory.delete(recursive: true));

    test('a new version is downloaded and checked in the background, and goes to Android only on "Atualizar"', () async {
      final updater = tablet();

      await updater.check();

      final ready = updater.state as UpdateReady;
      expect(ready.version, '0.2.0');
      expect(File(ready.installer).readAsBytesSync(), apk);
      expect(files(), ['Wallet-0.2.0.apk']);
      expect(system.handed, isEmpty, reason: 'nothing installs on its own on Android');
      expect({for (final url in github.requested) url.host}, {'github.com'}, reason: 'never api.github.com');

      await updater.restartToUpdate();

      expect(system.handed, [ready.installer]);
    });

    test('an APK that does not match its SHA-256 is thrown away and never handed to Android', () async {
      github = _Releases(apk, published: sha256.convert([1, 2, 3]).toString());
      final updater = tablet();

      await updater.check();

      expect(updater.state, isA<UpdateFailed>());
      expect(files(), isEmpty);
      await updater.restartToUpdate();
      expect(system.handed, isEmpty);
    });

    test('a release without the Android manifest yet is nothing to do', () async {
      github.manifest = null;
      final updater = tablet();

      await updater.check();

      expect(updater.state, isA<UpToDate>());
      expect(github.requested, [Uri.parse(_manifestUrl)]);
    });

    test('the first time, the user is sent to the setting and the confirmation follows once they allow it', () async {
      final updater = tablet();
      await updater.check();
      system.answers.add(InstallProblem.notAllowed);

      await updater.restartToUpdate();
      expect((updater.state as UpdateReady).problem, InstallProblem.notAllowed);

      system.userAllows = false;
      await updater.allowInstalls();
      expect(system.handed, hasLength(1), reason: 'still not allowed: nothing to confirm');
      expect((updater.state as UpdateReady).problem, InstallProblem.notAllowed);

      system.userAllows = true;
      await updater.allowInstalls();
      expect(system.sentToSettings, 2);
      expect(system.handed, hasLength(2));
      expect((updater.state as UpdateReady).problem, isNull);
    });

    test('saying no to Android keeps the update waiting; an APK signed with another key says so', () async {
      final updater = tablet();
      await updater.check();

      system.answers.add(null);
      await updater.restartToUpdate();
      expect(updater.state, isA<UpdateReady>().having((ready) => ready.problem, 'problem', isNull));

      system.answers.add(InstallProblem.conflict);
      await updater.restartToUpdate();
      expect((updater.state as UpdateReady).problem, InstallProblem.conflict);

      await updater.restartToUpdate();
      expect((updater.state as UpdateReady).problem, isNull, reason: 'trying again starts clean');
      expect(system.handed, hasLength(3));
    });
  });

  group('the manifest', () {
    test('the desktop one names no APK, so the tablet is not offered a Windows installer', () async {
      final github = _Releases(const [1, 2, 3])
        ..manifest = jsonEncode({'version': '0.2.0', 'installer': 'Wallet-Setup-0.2.0.exe', 'sha256': 'ab' * 32});

      expect(await ManifestReleaseFeed.android(dio: Dio()..httpClientAdapter = github).latest(), isNull);
    });

    test('the desktop app keeps reading latest.json', () async {
      final github = _Releases(const [1, 2, 3]);

      expect(await ManifestReleaseFeed(dio: Dio()..httpClientAdapter = github).latest(), isNull);
      expect(github.requested.single.path, endsWith('/latest/download/latest.json'));
    });
  });

  group('the version it last ran', () {
    late SharedPreferencesAsync preferences;

    setUp(() {
      SharedPreferencesAsyncPlatform.instance = InMemorySharedPreferencesAsync.empty();
      preferences = SharedPreferencesAsync();
    });

    test('says "just updated" once, on the first start of a newer version', () async {
      expect(await rememberVersion(preferences, '0.1.5'), isFalse, reason: 'the first install is no update');
      expect(await rememberVersion(preferences, '0.1.5'), isFalse);

      expect(await rememberVersion(preferences, '0.2.0'), isTrue);
      expect(await rememberVersion(preferences, '0.2.0'), isFalse);
      expect(await preferences.getString(lastVersionKey), '0.2.0');
    });

    test('a development build remembers nothing', () async {
      expect(await rememberVersion(preferences, ''), isFalse);
      expect(await preferences.getString(lastVersionKey), isNull);
    });
  });

  group('the channel to Android', () {
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final calls = <MethodCall>[];

    void answer(Future<Object?> Function(MethodCall call) handler) =>
        messenger.setMockMethodCallHandler(AndroidPackageInstaller.channel, (call) {
          calls.add(call);
          return handler(call);
        });

    setUp(calls.clear);
    tearDown(() => messenger.setMockMethodCallHandler(AndroidPackageInstaller.channel, null));

    test('hands over the APK\'s path and returns when the user says no', () async {
      answer((_) async => 'cancelled');

      await const AndroidPackageInstaller().launch('/data/user/0/com.wallet.wallet/files/updates/Wallet-0.2.0.apk', Relaunch.open);

      expect(calls.single.method, 'install');
      expect(calls.single.arguments, {'path': '/data/user/0/com.wallet.wallet/files/updates/Wallet-0.2.0.apk'});
    });

    test('Android\'s refusals become the reason the app shows', () async {
      Future<InstallProblem?> problemOf(String code) async {
        answer((_) async => throw PlatformException(code: code));
        try {
          await const AndroidPackageInstaller().launch('Wallet-0.2.0.apk', Relaunch.open);
          return null;
        } on InstallRefused catch (refused) {
          return refused.problem;
        }
      }

      expect(await problemOf('not-allowed'), InstallProblem.notAllowed);
      expect(await problemOf('conflict'), InstallProblem.conflict);
      expect(await problemOf('failed'), InstallProblem.failed);
    });

    test('without the native side, the update is refused instead of crashing', () async {
      await expectLater(
        const AndroidPackageInstaller().launch('Wallet-0.2.0.apk', Relaunch.open),
        throwsA(isA<InstallRefused>().having((refused) => refused.problem, 'problem', InstallProblem.failed)),
      );
    });

    test('asks for the setting and says whether the user turned it on', () async {
      answer((_) async => true);
      expect(await const AndroidPackageInstaller().allowInstalls(), isTrue);

      answer((_) async => false);
      expect(await const AndroidPackageInstaller().allowInstalls(), isFalse);
      expect([for (final call in calls) call.method], ['allowInstalls', 'allowInstalls']);
    });
  });
}
