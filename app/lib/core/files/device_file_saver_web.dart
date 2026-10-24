import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';
import 'dart:typed_data';

import 'saved_file.dart';

/// The browser's download: the bytes become a Blob behind a link that clicks itself. Where the file
/// lands is the user's browser setting, so there is no folder to name.
Future<SavedFile> saveToDevice(String fileName, Uint8List bytes) async {
  final blob = _Blob(<JSAny>[bytes.toJS].toJS, JSObject()..setProperty('type'.toJS, 'application/json'.toJS));
  final url = _createObjectUrl(blob);
  final body = _document.getProperty<JSObject>('body'.toJS);
  final link = _document.callMethod<JSObject>('createElement'.toJS, 'a'.toJS)
    ..setProperty('href'.toJS, url.toJS)
    ..setProperty('download'.toJS, fileName.toJS);
  body.callMethod<JSAny?>('appendChild'.toJS, link);
  link.callMethod<JSAny?>('click'.toJS);
  body.callMethod<JSAny?>('removeChild'.toJS, link);
  // Some browsers start the download after the click returns: the address lives a while longer.
  Timer(const Duration(seconds: 30), () => _revokeObjectUrl(url));
  return SavedFile(name: fileName);
}

@JS('Blob')
extension type _Blob._(JSObject _) implements JSObject {
  external _Blob(JSArray<JSAny> parts, JSObject options);
}

@JS('document')
external JSObject get _document;

@JS('URL.createObjectURL')
external String _createObjectUrl(JSObject blob);

@JS('URL.revokeObjectURL')
external void _revokeObjectUrl(String url);
