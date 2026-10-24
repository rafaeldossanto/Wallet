import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/files/saved_file.dart';
import 'package:wallet/features/settings/data/data_export_api.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;
  late List<(String, Uint8List)> saved;

  setUp(() {
    bff = FakeBff();
    bff.signedIn();
    bff.json('GET', '/api/home', Fixtures.home());
    saved = [];
  });

  const file = {
    'schemaVersion': 1,
    'banking': {'transactions': []},
  };

  Future<SavedFile> saveInMemory(String name, Uint8List bytes) async {
    saved.add((name, bytes));
    return SavedFile(name: name, folder: r'C:\Users\rafael\Downloads');
  }

  Future<void> openSettings(WidgetTester tester, {FileSaver? fileSaver}) async {
    tester.view.physicalSize = const Size(400, 860);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, fileSaver: fileSaver ?? saveInMemory)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mais'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustes'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();
  }

  testWidgets('Seus dados offers the download and says what is in the file', (tester) async {
    await openSettings(tester);

    expect(find.text('Seus dados'), findsOneWidget);
    expect(find.text('Baixar meus dados'), findsOneWidget);
    expect(find.textContaining('tudo o que o Wallet guarda sobre você'), findsOneWidget);
    expect(bff.calls('GET', '/api/me/export'), isEmpty);
  });

  testWidgets('tapping it saves the file the BFF sent under the name it suggested, and says where', (tester) async {
    bff.on(
        'GET',
        '/api/me/export',
        (_) async => const FakeResponse(file,
            headers: {'content-disposition': 'attachment; filename="wallet-meus-dados-2026-10-23.json"'}));
    await openSettings(tester);

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(bff.calls('GET', '/api/me/export'), hasLength(1));
    expect(saved, hasLength(1));
    expect(saved.single.$1, 'wallet-meus-dados-2026-10-23.json');
    expect(String.fromCharCodes(saved.single.$2), '{"schemaVersion":1,"banking":{"transactions":[]}}');
    expect(find.text(r'Pronto: wallet-meus-dados-2026-10-23.json, salvo em C:\Users\rafael\Downloads'), findsOneWidget);
  });

  testWidgets('where the browser decides the folder, the message is only the file name', (tester) async {
    bff.json('GET', '/api/me/export', file);
    await openSettings(tester, fileSaver: (name, bytes) async => SavedFile(name: name));

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Pronto: wallet-meus-dados-'), findsOneWidget);
    expect(find.textContaining('salvo em'), findsNothing);
  });

  testWidgets('while the file is prepared it says so and a second tap asks for nothing more', (tester) async {
    final answer = Completer<FakeResponse>();
    bff.on('GET', '/api/me/export', (_) => answer.future);
    await openSettings(tester);

    await tester.tap(find.text('Baixar meus dados'));
    // The progress ring never settles: a few frames, enough for the request to leave.
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Preparando o arquivo…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(bff.calls('GET', '/api/me/export'), hasLength(1));
    await tester.tap(find.text('Preparando o arquivo…'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(bff.calls('GET', '/api/me/export'), hasLength(1));

    answer.complete(const FakeResponse(file));
    await tester.pumpAndSettle();
    expect(find.text('Preparando o arquivo…'), findsNothing);
    expect(saved, hasLength(1));
  });

  testWidgets('past the daily limit it says to try again tomorrow, not "wait a minute"', (tester) async {
    bff.error('GET', '/api/me/export', 429, 'rate_limit.exceeded');
    await openSettings(tester);

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(find.text('Você já baixou seus dados várias vezes hoje. Tente de novo amanhã.'), findsOneWidget);
    expect(find.textContaining('Espere um minuto'), findsNothing);
    expect(saved, isEmpty);
  });

  testWidgets('any other refusal is told as the sentence of its code', (tester) async {
    bff.error('GET', '/api/me/export', 503, 'bff.core_unavailable');
    await openSettings(tester);

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(find.byType(SnackBar), findsOneWidget);
    expect(find.textContaining('Pronto'), findsNothing);
    expect(saved, isEmpty);
  });

  testWidgets('closing the "Save as" says the download was cancelled, and the tile is ready again', (tester) async {
    bff.json('GET', '/api/me/export', file);
    await openSettings(tester, fileSaver: (name, bytes) async => throw const SaveCancelled());

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(find.text('Download cancelado.'), findsOneWidget);
    expect(find.textContaining('Não foi possível'), findsNothing);
    expect(tester.widget<ListTile>(find.widgetWithText(ListTile, 'Baixar meus dados')).enabled, isTrue);
  });

  testWidgets('a file that cannot be written says so, and the tile is ready to try again', (tester) async {
    bff.json('GET', '/api/me/export', file);
    await openSettings(tester, fileSaver: (name, bytes) async => throw Exception('disk full'));

    await tester.tap(find.text('Baixar meus dados'));
    await tester.pumpAndSettle();

    expect(find.text('Não foi possível salvar o arquivo neste aparelho.'), findsOneWidget);
    expect(find.text('Preparando o arquivo…'), findsNothing);
    expect(tester.widget<ListTile>(find.widgetWithText(ListTile, 'Baixar meus dados')).enabled, isTrue);
  });

  group('the file name', () {
    test('comes from Content-Disposition', () {
      expect(DataExportApi.fileNameFrom('attachment; filename="wallet-meus-dados-2026-10-23.json"'),
          'wallet-meus-dados-2026-10-23.json');
    });

    test('is made from the date when the header is not readable (another address, CORS)', () {
      expect(DataExportApi.fileNameFrom(null, now: DateTime(2026, 10, 23, 23, 50)), 'wallet-meus-dados-2026-10-23.json');
    });

    test('is never one that could leave the folder', () {
      for (final hostile in [
        'attachment; filename="../../x.json"',
        r'attachment; filename="C:\x.json"',
        'attachment; filename=".."',
      ]) {
        expect(DataExportApi.fileNameFrom(hostile, now: DateTime(2026, 10, 23)), 'wallet-meus-dados-2026-10-23.json');
      }
    });
  });
}
