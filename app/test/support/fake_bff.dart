import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/api/api_client.dart';
import 'package:wallet/core/session/session_store.dart';

class FakeResponse {
  const FakeResponse(this.body, {this.status = 200});

  final Object? body;
  final int status;
}

typedef FakeHandler = Future<FakeResponse> Function(RequestOptions request);

/// The BFF, in memory: routes answer canned JSON and every request is kept for assertions.
class FakeBff implements HttpClientAdapter {
  final _routes = <String, FakeHandler>{};
  final requests = <RequestOptions>[];

  void on(String method, String path, FakeHandler handler) => _routes['$method $path'] = handler;

  void json(String method, String path, Object? body, {int status = 200}) =>
      on(method, path, (_) async => FakeResponse(body, status: status));

  void error(String method, String path, int status, String code) =>
      json(method, path, {'code': code, 'message': code}, status: status);

  List<RequestOptions> calls(String method, String path) =>
      [for (final request in requests) if (request.method == method && request.path == path) request];

  /// A signed-in user, as the app sees it right after opening.
  void signedIn({String accessToken = 'access-1'}) {
    json('POST', '/api/auth/refresh', {
      'accessToken': accessToken,
      'tokenType': 'Bearer',
      'expiresIn': 900,
      'refreshToken': 'refresh-2',
      'refreshTokenExpiresAt': '2026-11-01T12:00:00Z',
    });
    json('GET', '/api/me', {
      'id': '6f1c2a9e-0000-4000-8000-000000000099',
      'email': 'rafael@example.com',
      'displayName': 'Rafael Santos',
      'createdAt': '2026-09-01T12:00:00Z',
    });
  }

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    final handler = _routes['${options.method} ${options.path}'];
    final response = handler == null
        ? const FakeResponse({'code': 'route.not_found', 'message': 'not found'}, status: 404)
        : await handler(options);
    return ResponseBody.fromString(
      response.body == null ? '' : jsonEncode(response.body),
      response.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class MemorySessionStore implements SessionStore {
  MemorySessionStore([this.token]);

  String? token;

  @override
  Future<String?> readRefreshToken() async => token;

  @override
  Future<void> saveRefreshToken(String token) async => this.token = token;

  @override
  Future<void> clear() async => token = null;
}

ApiClient fakeClient(FakeBff bff) => ApiClient.create(baseUrl: 'http://bff.test', adapter: bff);

AppDependencies fakeDependencies(FakeBff bff, {String? storedRefreshToken = 'refresh-1'}) =>
    AppDependencies.create(api: fakeClient(bff), store: MemorySessionStore(storedRefreshToken), withAppLock: false);

Future<void> setUpLocale() async {
  Intl.defaultLocale = 'pt_BR';
  await initializeDateFormatting('pt_BR');
}
