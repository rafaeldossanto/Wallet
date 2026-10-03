import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:local_auth/local_auth.dart';
import 'package:wallet/core/security/app_lock.dart';
import 'package:wallet/core/security/idle_timeout.dart';
import 'package:wallet/core/session/session_api.dart';
import 'package:wallet/core/session/session_controller.dart';
import 'package:material_ui/material_ui.dart';

import '../support/fake_bff.dart';

class _FakeAuth extends LocalAuthentication {
  bool supported = true;
  bool answer = true;
  int prompts = 0;

  @override
  Future<bool> isDeviceSupported() async => supported;

  @override
  Future<bool> authenticate({
    required String localizedReason,
    Iterable<dynamic> authMessages = const [],
    bool biometricOnly = false,
    bool sensitiveTransaction = true,
    bool persistAcrossBackgrounding = false,
  }) async {
    prompts++;
    return answer;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeBff bff;
  late SessionController session;
  late DateTime now;

  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    bff = FakeBff()..signedIn();
    session = SessionController(api: SessionApi(fakeClient(bff)), store: MemorySessionStore('refresh-1'));
    await session.restore();
    now = DateTime(2026, 10, 2, 12);
  });

  group('app lock (phones)', () {
    Future<AppLock> startLock(_FakeAuth auth) async {
      final lock = AppLock(session: session, auth: auth, now: () => now);
      await lock.start();
      addTearDown(lock.dispose);
      return lock;
    }

    test('five minutes in the background ask for the biometrics on return', () async {
      final auth = _FakeAuth();
      final lock = await startLock(auth);

      lock.didChangeAppLifecycleState(AppLifecycleState.paused);
      now = now.add(const Duration(minutes: 5));
      lock.didChangeAppLifecycleState(AppLifecycleState.resumed);
      expect(lock.isLocked, isTrue);

      expect(await lock.unlock('Desbloqueie'), isTrue);
      expect(lock.isLocked, isFalse);
      expect(auth.prompts, 1);
    });

    test('a quick look at another app does not lock', () async {
      final lock = await startLock(_FakeAuth());

      lock.didChangeAppLifecycleState(AppLifecycleState.paused);
      now = now.add(const Duration(minutes: 4, seconds: 59));
      lock.didChangeAppLifecycleState(AppLifecycleState.resumed);

      expect(lock.isLocked, isFalse);
    });

    test('turned off, or on a device without a screen lock, it never locks', () async {
      final off = await startLock(_FakeAuth());
      await off.setEnabled(false);
      final unsupported = await startLock(_FakeAuth()..supported = false);

      for (final lock in [off, unsupported]) {
        lock.didChangeAppLifecycleState(AppLifecycleState.paused);
        now = now.add(const Duration(minutes: 10));
        lock.didChangeAppLifecycleState(AppLifecycleState.resumed);
        expect(lock.isLocked, isFalse);
      }
    });

    test('a failed unlock keeps it locked', () async {
      final lock = await startLock(_FakeAuth()..answer = false);
      lock.didChangeAppLifecycleState(AppLifecycleState.paused);
      now = now.add(const Duration(minutes: 6));
      lock.didChangeAppLifecycleState(AppLifecycleState.resumed);

      expect(await lock.unlock('Desbloqueie'), isFalse);
      expect(lock.isLocked, isTrue);
    });
  });

  group('idle timeout (browser)', () {
    testWidgets('thirty minutes without a click end the session', (tester) async {
      bff.json('POST', '/api/auth/logout', null, status: 204);
      await tester.pumpWidget(IdleTimeout(
        session: session,
        now: () => now,
        checkEvery: const Duration(seconds: 1),
        child: const SizedBox.expand(),
      ));

      now = now.add(const Duration(minutes: 29));
      await tester.pump(const Duration(seconds: 1));
      expect(session.isSignedIn, isTrue);

      await tester.tap(find.byType(SizedBox));
      now = now.add(const Duration(minutes: 29));
      await tester.pump(const Duration(seconds: 1));
      expect(session.isSignedIn, isTrue, reason: 'the click restarted the count');

      now = now.add(const Duration(minutes: 1));
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      expect(session.isSignedIn, isFalse);
      expect(session.lastSignOutReason, SignOutReason.idle);
    });
  });
}
