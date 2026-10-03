import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'api_exception.dart';
import 'platform_adapter.dart';

/// HTTP to the BFF. Every failure comes out as an [ApiException].
class ApiClient {
  ApiClient(this.dio);

  static const clientHeader = 'X-Wallet-Client';

  final Dio dio;

  factory ApiClient.create({String? baseUrl, HttpClientAdapter? adapter}) {
    final dio = Dio(BaseOptions(
      baseUrl: baseUrl ?? AppConfig.bffUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
      headers: {clientHeader: AppConfig.client},
    ));
    dio.httpClientAdapter = adapter ?? platformHttpAdapter();
    return ApiClient(dio);
  }

  Future<dynamic> get(String path, {Map<String, Object?> query = const {}}) =>
      _send(() => dio.get<dynamic>(path, queryParameters: _withoutNulls(query)));

  Future<dynamic> post(String path, {Object? body}) => _send(() => dio.post<dynamic>(path, data: body));

  Future<void> delete(String path) => _send(() => dio.delete<dynamic>(path));

  Future<dynamic> _send(Future<Response<dynamic>> Function() request) async {
    try {
      return (await request()).data;
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  /// Absent filters are left out, not sent as empty values the BFF would have to reject.
  static Map<String, Object> _withoutNulls(Map<String, Object?> query) => {
        for (final entry in query.entries)
          if (entry.value != null && entry.value != '') entry.key: entry.value!,
      };
}
