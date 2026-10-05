import 'package:dio/dio.dart';

import '../api/json.dart';
import 'updater.dart';

/// The repository's releases on GitHub, where the Desktop workflow publishes each version.
///
/// Its own Dio, never the BFF's: the session token must not leave for another host.
class GitHubReleaseFeed implements ReleaseFeed {
  GitHubReleaseFeed({Dio? dio, this.repository = 'rafaeldossanto/Wallet'})
      : _dio = dio ??
            Dio(BaseOptions(
              baseUrl: 'https://api.github.com',
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 20),
              headers: {'Accept': 'application/vnd.github+json', 'User-Agent': 'Wallet-desktop'},
            ));

  final Dio _dio;
  final String repository;

  /// GitHub's "latest" leaves drafts and pre-releases out. A release without this version's
  /// installer, or without its SHA-256, is not offered: nothing unchecked gets run.
  @override
  Future<Release?> latest() async {
    final Response<Object?> response;
    try {
      response = await _dio.get('/repos/$repository/releases/latest');
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) {
        return null;
      }
      rethrow;
    }
    final release = Json.of(response.data);
    final version = release.string('tag_name').replaceFirst(RegExp('^v'), '');
    final installerName = 'Wallet-Setup-$version.exe';
    for (final asset in release.list('assets')) {
      final digest = asset.stringOrNull('digest');
      if (asset.string('name') == installerName && digest != null && digest.startsWith('sha256:')) {
        return Release(
          version: version,
          installerUrl: Uri.parse(asset.string('browser_download_url')),
          sha256: digest.substring('sha256:'.length).toLowerCase(),
        );
      }
    }
    return null;
  }
}
