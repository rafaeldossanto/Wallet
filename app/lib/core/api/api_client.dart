import 'dart:convert';

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

  /// A file, as the bytes the BFF sent and its headers (the name comes in `Content-Disposition`).
  /// [receiveTimeout] replaces the usual 20 s: such a file is built from everything the user has.
  Future<Response<List<int>>> download(String path, {Duration? receiveTimeout}) async {
    try {
      return await dio.get<List<int>>(path,
          options: Options(responseType: ResponseType.bytes, receiveTimeout: receiveTimeout));
    } on DioException catch (error) {
      throw ApiException.fromDio(_withJsonBody(error));
    }
  }

  /// A refusal to a download comes as bytes too: read it as the JSON it is, so its code is found.
  static DioException _withJsonBody(DioException error) {
    final response = error.response;
    final data = response?.data;
    if (response != null && data is List<int>) {
      try {
        response.data = jsonDecode(utf8.decode(data));
      } on FormatException {
        response.data = null;
      }
    }
    return error;
  }

  Future<dynamic> post(String path, {Object? body}) => _send(() => dio.post<dynamic>(path, data: body));

  Future<dynamic> put(String path, {Object? body}) => _send(() => dio.put<dynamic>(path, data: body));

  /// [receiveTimeout] replaces the usual 20 s for a call the server is known to take long on.
  Future<void> delete(String path, {Object? body, Duration? receiveTimeout}) => _send(() => dio.delete<dynamic>(path,
      data: body, options: receiveTimeout == null ? null : Options(receiveTimeout: receiveTimeout)));

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
