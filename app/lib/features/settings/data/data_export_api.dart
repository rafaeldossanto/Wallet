import 'dart:typed_data';

import '../../../core/api/api_client.dart';
import '../../../core/format/dates.dart';

/// The user's data as the BFF built it: a JSON file, and the name it suggests for it.
class DataExport {
  const DataExport({required this.fileName, required this.bytes});

  final String fileName;
  final Uint8List bytes;
}

/// "Baixar meus dados" (LGPD, access and portability): `GET /api/me/export`.
class DataExportApi {
  DataExportApi(this._api);

  final ApiClient _api;

  /// The BFF caps this at 3 a day per user (`rate_limit.exceeded`).
  Future<DataExport> download({DateTime? now}) async {
    final response = await _api.download('/api/me/export', receiveTimeout: const Duration(minutes: 1));
    return DataExport(
      fileName: fileNameFrom(response.headers.value('content-disposition'), now: now),
      bytes: Uint8List.fromList(response.data ?? const []),
    );
  }

  /// The name in `Content-Disposition: attachment; filename="wallet-meus-dados-2026-10-23.json"`.
  /// A browser on another address does not see that header (CORS), and a name that is not plain
  /// letters, digits, dots and dashes is not trusted as a file name: both fall back to the same
  /// name made from today's date.
  static String fileNameFrom(String? contentDisposition, {DateTime? now}) {
    final named = RegExp(r'filename="([A-Za-z0-9][A-Za-z0-9._-]*)"').firstMatch(contentDisposition ?? '')?.group(1);
    return named ?? 'wallet-meus-dados-${Dates.iso(now ?? DateTime.now())}.json';
  }
}
