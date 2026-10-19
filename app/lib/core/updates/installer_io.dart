import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';

import 'updater.dart';

/// The downloaded file is not the one the release published.
class InstallerMismatch implements Exception {
  const InstallerMismatch(this.version);

  final String version;

  @override
  String toString() => 'The installer of $version does not match its SHA-256';
}

/// Downloads installers into a folder of the app's own (`%LOCALAPPDATA%\Wallet\updates` on
/// Windows, the app's private storage on Android) and keeps one only when its SHA-256 matches the
/// release's. An installer already downloaded and intact is not downloaded again.
class DownloadedInstallers implements InstallerStore {
  DownloadedInstallers({Dio? dio, Directory? directory, String Function(String version)? fileName})
      : _dio = dio ?? Dio(BaseOptions(connectTimeout: const Duration(seconds: 15))),
        _directory = directory ?? _defaultDirectory(),
        _fileName = fileName ?? ((version) => 'Wallet-Setup-$version.exe');

  final Dio _dio;
  final Directory _directory;
  final String Function(String version) _fileName;

  static Directory _defaultDirectory() {
    final base = Platform.environment['LOCALAPPDATA'] ?? Directory.systemTemp.path;
    return Directory('$base${Platform.pathSeparator}Wallet${Platform.pathSeparator}updates');
  }

  @override
  Future<String> fetch(Release release) async {
    final target = File('${_directory.path}${Platform.pathSeparator}${_fileName(release.version)}');
    if (await target.exists() && await _sha256(target) == release.sha256) {
      return target.path;
    }
    await _directory.create(recursive: true);
    final partial = File('${target.path}.part');
    await _dio.download(release.installerUrl.toString(), partial.path);
    if (await _sha256(partial) != release.sha256) {
      await partial.delete();
      throw InstallerMismatch(release.version);
    }
    if (await target.exists()) {
      await target.delete();
    }
    await partial.rename(target.path);
    await _removeOlder(keep: target.path);
    return target.path;
  }

  /// Every installer kept, once the app runs the version they brought.
  Future<void> clear() async {
    if (await _directory.exists()) {
      await _removeOlder(keep: null);
    }
  }

  /// Installers of versions already installed, or of a download that failed halfway.
  Future<void> _removeOlder({required String? keep}) async {
    await for (final entry in _directory.list()) {
      if (entry is File && entry.path != keep) {
        try {
          await entry.delete();
        } on FileSystemException {
          // Still open somewhere; the next update tries again.
        }
      }
    }
  }

  static Future<String> _sha256(File file) async => (await sha256.bind(file.openRead()).first).toString();
}
