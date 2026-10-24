import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

import '../android/document_saver.dart';
import 'saved_file.dart';

/// Android: its own "Save as", where the user picks the folder. Windows: the Downloads folder,
/// where the user looks for what they asked to download. Anywhere else, the app's own folder. A file
/// that is already there is never overwritten: the new one gets ` (2)`, ` (3)`...
Future<SavedFile> saveToDevice(String fileName, Uint8List bytes) async {
  if (Platform.isAndroid) {
    return const AndroidDocumentSaver().save(fileName, bytes);
  }
  final folder = await _folder();
  await folder.create(recursive: true);
  final file = _freeName(folder, fileName);
  await file.writeAsBytes(bytes, flush: true);
  return SavedFile(name: file.uri.pathSegments.last, folder: folder.path);
}

Future<Directory> _folder() async {
  if (Platform.isWindows) {
    final downloads = await getDownloadsDirectory();
    if (downloads != null) {
      return downloads;
    }
  }
  return getApplicationSupportDirectory();
}

File _freeName(Directory folder, String fileName) {
  final dot = fileName.lastIndexOf('.');
  final stem = dot > 0 ? fileName.substring(0, dot) : fileName;
  final extension = dot > 0 ? fileName.substring(dot) : '';
  var candidate = File('${folder.path}${Platform.pathSeparator}$fileName');
  for (var number = 2; candidate.existsSync(); number++) {
    candidate = File('${folder.path}${Platform.pathSeparator}$stem ($number)$extension');
  }
  return candidate;
}
