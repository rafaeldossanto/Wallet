import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/files/saved_file.dart';
import '../../../core/l10n/l10n.dart';
import '../data/data_export_api.dart';

/// "Baixar meus dados" (LGPD, access and portability): the file the BFF builds from everything the
/// user has, saved where they find it. The answer is a message with the file's name; a refusal
/// (three a day) or a failure to write says so and nothing is left half done.
class DownloadDataTile extends StatefulWidget {
  const DownloadDataTile({super.key});

  @override
  State<DownloadDataTile> createState() => _DownloadDataTileState();
}

class _DownloadDataTileState extends State<DownloadDataTile> {
  bool _busy = false;

  Future<void> _download() async {
    if (_busy) {
      return;
    }
    final l10n = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    final api = context.read<DataExportApi>();
    final save = context.read<FileSaver>();
    setState(() => _busy = true);
    String message;
    try {
      final export = await api.download();
      final saved = await save(export.fileName, export.bytes);
      message = switch (saved.folder) {
        final folder? => l10n.settingsDownloadDataDoneIn(saved.name, folder),
        null => l10n.settingsDownloadDataDone(saved.name),
      };
    } on ApiException catch (error) {
      // The BFF allows a few a day: the general wait-a-minute sentence would be the wrong advice.
      message = error.status == 429 ? l10n.settingsDownloadDataLimit : l10n.errorMessage(error);
    } on SaveCancelled {
      message = l10n.settingsDownloadDataCancelled;
    } on Exception {
      // The file could not be written (no room, no permission): nothing to tell apart for the user.
      message = l10n.settingsDownloadDataFailed;
    }
    messenger.showSnackBar(SnackBar(content: Text(message)));
    if (mounted) {
      setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return ListTile(
      leading: const Icon(Icons.download_outlined),
      title: Text(l10n.settingsDownloadData),
      subtitle: Text(_busy ? l10n.settingsDownloadDataBusy : l10n.settingsDownloadDataHelp),
      trailing: _busy
          ? const SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 2))
          : null,
      onTap: _busy ? null : _download,
    );
  }
}
