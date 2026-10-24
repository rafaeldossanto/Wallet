import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../files/saved_file.dart';

/// Android's own "Save as" (the Storage Access Framework), for a file the app built in memory: the
/// user picks the folder (Downloads, Drive...) and the bytes go straight there. Nothing is left in
/// the app's cache or in a temporary folder, and the file never goes through the share sheet, where
/// any app could take it. The native side is
/// `android/app/src/main/kotlin/com/wallet/wallet/DocumentSaver.kt`.
class AndroidDocumentSaver {
  const AndroidDocumentSaver([this._channel = channel]);

  static const channel = MethodChannel('wallet/document_saver');

  final MethodChannel _channel;

  /// The file as the user saved it (they may rename it). Throws [SaveCancelled] when they close the
  /// "Save as" without choosing, and [FileNotSaved] when the write fails.
  Future<SavedFile> save(String fileName, Uint8List bytes) async {
    final String? name;
    try {
      name = await _channel.invokeMethod<String>('save', {'name': fileName, 'bytes': bytes});
    } on PlatformException catch (error) {
      // The code only: the native message can carry the path the user picked.
      debugPrint('[EXPORT] ${error.code}');
      throw const FileNotSaved();
    } on MissingPluginException {
      throw const FileNotSaved();
    }
    if (name == null) {
      throw const SaveCancelled();
    }
    return SavedFile(name: name);
  }
}
