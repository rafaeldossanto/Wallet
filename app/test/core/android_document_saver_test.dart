import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/android/document_saver.dart';
import 'package:wallet/core/files/saved_file.dart';

/// The Dart side of Android's "Save as" (the Kotlin side, DocumentSaver.kt, only compiles here): what it
/// sends on the channel and what each answer becomes.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final bytes = Uint8List.fromList('{"schemaVersion":1}'.codeUnits);
  late List<MethodCall> calls;

  void answer(Future<Object?> Function(MethodCall call) handler) {
    calls = [];
    messenger.setMockMethodCallHandler(AndroidDocumentSaver.channel, (call) {
      calls.add(call);
      return handler(call);
    });
  }

  tearDown(() => messenger.setMockMethodCallHandler(AndroidDocumentSaver.channel, null));

  test('sends the suggested name and the bytes, and answers the name the user ended up with', () async {
    answer((_) async => 'meus-dados.json');

    final saved = await const AndroidDocumentSaver().save('wallet-meus-dados-2026-10-23.json', bytes);

    expect(saved.name, 'meus-dados.json');
    expect(saved.folder, isNull);
    expect(calls, hasLength(1));
    expect(calls.single.method, 'save');
    expect(calls.single.arguments['name'], 'wallet-meus-dados-2026-10-23.json');
    expect(calls.single.arguments['bytes'], bytes);
  });

  test('closing the "Save as" is a cancellation, not a failure', () async {
    answer((_) async => null);

    await expectLater(const AndroidDocumentSaver().save('x.json', bytes), throwsA(isA<SaveCancelled>()));
  });

  test('a failure on the native side is a file not saved, without its message', () async {
    answer((_) async => throw PlatformException(code: 'failed', message: '/storage/emulated/0/Download/x.json'));

    await expectLater(const AndroidDocumentSaver().save('x.json', bytes), throwsA(isA<FileNotSaved>()));
  });

  test('a build without the native side is a file not saved too', () async {
    messenger.setMockMethodCallHandler(AndroidDocumentSaver.channel, null);

    await expectLater(const AndroidDocumentSaver().save('x.json', bytes), throwsA(isA<FileNotSaved>()));
  });
}
