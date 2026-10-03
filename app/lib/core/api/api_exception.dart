import 'package:dio/dio.dart';

/// An error the app can explain. [code] is the BFF's or the core's stable code (`auth.locked`,
/// `sync.too_soon`...) or one of the app's own network codes; screens translate it and never
/// show [serverMessage], which is English and meant for logs.
class ApiException implements Exception {
  const ApiException(this.code, {this.status, this.serverMessage});

  static const networkUnreachable = 'network.unreachable';
  static const networkTimeout = 'network.timeout';
  static const unexpected = 'internal.error';

  final String code;
  final int? status;
  final String? serverMessage;

  bool get isUnauthorized => status == 401;

  factory ApiException.fromDio(DioException error) {
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        return const ApiException(networkTimeout);
      case DioExceptionType.connectionError:
        return const ApiException(networkUnreachable);
      case DioExceptionType.badResponse:
        final response = error.response;
        final data = response?.data;
        if (data is Map && data['code'] is String) {
          return ApiException(data['code'] as String,
              status: response?.statusCode, serverMessage: data['message'] as String?);
        }
        return ApiException(unexpected, status: response?.statusCode);
      case DioExceptionType.badCertificate:
      case DioExceptionType.cancel:
      case DioExceptionType.unknown:
        return const ApiException(unexpected);
    }
  }

  @override
  String toString() => 'ApiException($code, status: $status)';
}
