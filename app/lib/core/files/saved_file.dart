import 'dart:typed_data';

/// A file the app wrote for the user. [folder] is where, for the platforms where a folder is
/// something the user can open (Windows); null where the browser decides (its downloads).
class SavedFile {
  const SavedFile({required this.name, this.folder});

  final String name;
  final String? folder;
}

/// Puts a file where the user can find it. Replaced in tests, so no file is written.
typedef FileSaver = Future<SavedFile> Function(String fileName, Uint8List bytes);

/// The user closed the "Save as" without choosing: nothing was written, and that is not an error.
class SaveCancelled implements Exception {
  const SaveCancelled();
}

/// The file could not be written where it was meant to go.
class FileNotSaved implements Exception {
  const FileNotSaved();
}
