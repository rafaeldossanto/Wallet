import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/features/connections/data/connections_api.dart';
import 'package:wallet/features/connections/presentation/connections_controller.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  late FakeBff bff;
  late DataChanges changes;
  late int rings;

  setUp(() {
    bff = FakeBff();
    changes = DataChanges();
    rings = 0;
    changes.addListener(() => rings++);
  });

  test('while a sync runs it keeps checking, and tells the other screens when it lands', () {
    fakeAsync((async) {
      var status = 'SYNCING';
      String? synced = '2026-10-02T09:00:00Z';
      bff.on('GET', '/api/connections', (_) async =>
          FakeResponse([Fixtures.connection(status: status, lastSyncedAt: synced)]));
      final controller = ConnectionsController(ConnectionsApi(fakeClient(bff)), dataChanges: changes, now: () => DateTime(2026, 10, 2, 12));

      controller.load();
      async.flushMicrotasks();
      async.elapse(const Duration(seconds: 3));
      expect(bff.calls('GET', '/api/connections'), hasLength(2));
      expect(rings, 0);

      status = 'ACTIVE';
      synced = '2026-10-02T12:00:00Z';
      async.elapse(const Duration(seconds: 3));
      expect(rings, 1);

      async.elapse(const Duration(seconds: 30));
      expect(bff.calls('GET', '/api/connections'), hasLength(3), reason: 'nothing syncing: no more polling');
      controller.dispose();
    });
  });

  test('linking rings the other screens and reports the core\'s error codes', () async {
    bff.json('GET', '/api/connections', <Object>[]);
    bff.error('POST', '/api/connections', 404, 'connection.item_not_found');
    final controller = ConnectionsController(ConnectionsApi(fakeClient(bff)), dataChanges: changes);
    await controller.load();

    final error = await controller.link('nao-existe');

    expect(error!.code, 'connection.item_not_found');
    expect(rings, 0);

    bff.json('POST', '/api/connections', Fixtures.connection(), status: 201);
    expect(await controller.link(' demo-banco '), isNull);
    expect(bff.calls('POST', '/api/connections').last.data, {'providerItemId': 'demo-banco'});
    expect(rings, 1);
    controller.dispose();
  });

  test('a sync asked too soon comes back as sync.too_soon', () async {
    bff.json('GET', '/api/connections', [Fixtures.connection()]);
    bff.error('POST', '/api/connections/${Fixtures.connectionId}/sync', 429, 'sync.too_soon');
    final controller = ConnectionsController(ConnectionsApi(fakeClient(bff)), dataChanges: changes);
    await controller.load();

    final error = await controller.sync(Fixtures.connectionId);

    expect(error!.code, 'sync.too_soon');
    controller.dispose();
  });
}
