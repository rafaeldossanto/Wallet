import 'package:dio/dio.dart';

import '../session/session_controller.dart';

/// Puts the access token on every call and, when the BFF answers 401, renews the session once
/// and repeats the call. Renewal is shared between calls ([SessionController.renew]).
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._session, this._dio);

  static const _retried = 'wallet.retried';

  final SessionController _session;
  final Dio _dio;

  /// Login, refresh and logout carry no Bearer: refresh happens precisely when it has expired.
  static bool isAuthRoute(String path) => path.startsWith('/api/auth/');

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = _session.accessToken;
    if (!isAuthRoute(options.path) && token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    if (err.response?.statusCode != 401 || isAuthRoute(options.path) || options.extra[_retried] == true) {
      handler.next(err);
      return;
    }
    final renewed = await _session.renewAfter(options.headers['Authorization'] as String?);
    if (!renewed) {
      handler.next(err);
      return;
    }
    options.extra[_retried] = true;
    try {
      handler.resolve(await _dio.fetch<dynamic>(options));
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }
}
