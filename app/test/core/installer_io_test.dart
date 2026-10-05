import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/desktop/installer_io.dart';
import 'package:wallet/core/desktop/updater.dart';

/// Serves [bytes] for every request and counts the downloads.
class _Download implements HttpClientAdapter {
  _Download(this.bytes);

  List<int> bytes;
  int requests = 0;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests++;
    return ResponseBody.fromBytes(bytes, 200);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory directory;
  late _Download server;
  late DownloadedInstallers installers;

  final installer = [for (var byte = 0; byte < 4096; byte++) byte % 251];
  Release release(String version, List<int> published) => Release(
        version: version,
        installerUrl: Uri.parse('https://github.test/Wallet-Setup-$version.exe'),
        sha256: sha256.convert(published).toString(),
      );

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('wallet-updates');
    server = _Download(installer);
    installers = DownloadedInstallers(dio: Dio()..httpClientAdapter = server, directory: directory);
  });

  tearDown(() => directory.delete(recursive: true));

  List<String> files() => [for (final file in directory.listSync()) file.uri.pathSegments.last]..sort();

  test('a download that matches its SHA-256 is kept, and the older installers go', () async {
    File('${directory.path}${Platform.pathSeparator}Wallet-Setup-0.1.0.exe').writeAsBytesSync([1, 2, 3]);

    final path = await installers.fetch(release('0.2.0', installer));

    expect(File(path).readAsBytesSync(), installer);
    expect(files(), ['Wallet-Setup-0.2.0.exe']);
  });

  test('a download that does not match is thrown away and never offered', () async {
    server.bytes = [...installer.take(4000), 0, 0];

    await expectLater(installers.fetch(release('0.2.0', installer)), throwsA(isA<InstallerMismatch>()));
    expect(files(), isEmpty);
  });

  test('an intact installer already downloaded is not downloaded again', () async {
    await installers.fetch(release('0.2.0', installer));
    await installers.fetch(release('0.2.0', installer));

    expect(server.requests, 1);
  });
}
