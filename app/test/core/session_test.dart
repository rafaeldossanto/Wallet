import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/api/api_client.dart';
import 'package:wallet/core/api/api_exception.dart';
import 'package:wallet/core/api/auth_interceptor.dart';
import 'package:wallet/core/session/session_api.dart';
import 'package:wallet/core/session/session_controller.dart';

import '../support/fake_bff.dart';

void main() {
  late FakeBff bff;
  late ApiClient api;
  late MemorySessionStore store;
  late SessionController session;

  setUp(() {
    bff = FakeBff();
    api = fakeClient(bff);
    store = MemorySessionStore('refresh-1');
    session = SessionController(api: SessionApi(api), store: store);
    api.dio.interceptors.add(AuthInterceptor(session, api.dio));
  });

  Map<String, Object> tokens(String access, String refresh) => {
        'accessToken': access,
        'tokenType': 'Bearer',
        'expiresIn': 900,
        'refreshToken': refresh,
        'refreshTokenExpiresAt': '2026-11-01T12:00:00Z',
      };

  group('restore', () {
    test('trades the stored refresh token for a session and keeps the new one', () async {
      bff.signedIn();

      await session.restore();

      expect(session.status, SessionStatus.signedIn);
      expect(session.user!.firstName, 'Rafael');
      expect(store.token, 'refresh-2');
      expect(bff.calls('POST', '/api/auth/refresh').single.data, {'refreshToken': 'refresh-1'});
      expect(bff.calls('POST', '/api/auth/refresh').single.headers[ApiClient.clientHeader], 'mobile');
    });

    test('without a stored token there is nothing to ask', () async {
      store.token = null;

      await session.restore();

      expect(session.status, SessionStatus.signedOut);
      expect(bff.requests, isEmpty);
    });

    test('a refused token is forgotten', () async {
      bff.error('POST', '/api/auth/refresh', 401, 'auth.invalid_refresh_token');

      await session.restore();

      expect(session.status, SessionStatus.signedOut);
      expect(store.token, isNull);
    });

    test('an unreachable server keeps the stored token for the next try', () async {
      bff.error('POST', '/api/auth/refresh', 503, 'bff.core_unavailable');

      await session.restore();

      expect(session.status, SessionStatus.unreachable);
      expect(store.token, 'refresh-1');
    });
  });

  group('interceptor', () {
    test('sends the Bearer on data routes and never on auth routes', () async {
      bff.signedIn();
      bff.json('GET', '/api/cards', <Object>[]);
      await session.restore();

      await api.get('/api/cards');

      expect(bff.calls('GET', '/api/cards').single.headers['Authorization'], 'Bearer access-1');
      expect(bff.calls('POST', '/api/auth/refresh').single.headers['Authorization'], isNull);
    });

    test('two calls failing together renew the session once and both go through', () async {
      bff.signedIn();
      await session.restore();
      final refreshes = <Completer<FakeResponse>>[];
      bff.on('POST', '/api/auth/refresh', (_) {
        final completer = Completer<FakeResponse>();
        refreshes.add(completer);
        return completer.future;
      });
      bff.on('GET', '/api/cards', (request) async => request.headers['Authorization'] == 'Bearer access-2'
          ? const FakeResponse(<Object>[])
          : const FakeResponse({'code': 'auth.unauthenticated', 'message': 'expired'}, status: 401));

      final first = api.get('/api/cards');
      final second = api.get('/api/cards');
      await pumpEventQueue();
      expect(refreshes, hasLength(1));
      refreshes.single.complete(FakeResponse(tokens('access-2', 'refresh-3')));

      expect(await first, isEmpty);
      expect(await second, isEmpty);
      expect(bff.calls('POST', '/api/auth/refresh'), hasLength(2), reason: 'one at startup, one for both calls');
      expect(store.token, 'refresh-3');
    });

    test('a renewal the core refuses ends the session', () async {
      bff.signedIn();
      await session.restore();
      bff.error('POST', '/api/auth/refresh', 401, 'auth.refresh_reused');
      bff.error('GET', '/api/cards', 401, 'auth.unauthenticated');

      await expectLater(api.get('/api/cards'), throwsA(isA<ApiException>().having((e) => e.status, 'status', 401)));

      expect(session.status, SessionStatus.signedOut);
      expect(session.lastSignOutReason, SignOutReason.expired);
      expect(store.token, isNull);
    });

    test('a call that fails again after renewing is not retried a second time', () async {
      bff.signedIn();
      await session.restore();
      bff.error('GET', '/api/cards', 401, 'auth.unauthenticated');

      await expectLater(api.get('/api/cards'), throwsA(isA<ApiException>()));

      expect(bff.calls('GET', '/api/cards'), hasLength(2));
    });
  });

  test('signing out revokes the refresh token and forgets it', () async {
    bff.signedIn();
    bff.json('POST', '/api/auth/logout', null, status: 204);
    await session.restore();

    await session.signOut();

    expect(bff.calls('POST', '/api/auth/logout').single.data, {'refreshToken': 'refresh-2'});
    expect(session.status, SessionStatus.signedOut);
    expect(session.accessToken, isNull);
    expect(store.token, isNull);
  });
}
