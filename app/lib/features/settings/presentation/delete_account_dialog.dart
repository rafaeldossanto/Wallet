import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_controller.dart';

/// Opens the confirmation. True when the account was deleted: by then the session has ended and
/// the router is on its way to the login.
Future<bool> showDeleteAccountDialog(BuildContext context) async =>
    await showDialog<bool>(context: context, barrierDismissible: false, builder: (_) => const DeleteAccountDialog()) ??
    false;

/// Deleting the account: what goes, an explicit "I understand", and the password again, which the
/// core checks before deleting anything. An empty password never leaves the app. A refusal keeps
/// the dialog open and says why: a wrong password on the field (the session stays), the rest below
/// it (locked until when, offline, timeout, the server down). Without an answer the app cannot know
/// whether the core finished, so it never says nothing was deleted; deleting twice is harmless on
/// the server, so trying again is safe and settles it.
///
/// The answer can take up to 2 minutes (every bank connection is revoked at Pluggy first): the
/// dialog shows it is working and takes no second tap, no Cancel and no back gesture meanwhile.
class DeleteAccountDialog extends StatefulWidget {
  const DeleteAccountDialog({super.key});

  @override
  State<DeleteAccountDialog> createState() => _DeleteAccountDialogState();
}

class _DeleteAccountDialogState extends State<DeleteAccountDialog> {
  final _password = TextEditingController();
  bool _understood = false;
  bool _obscured = true;
  bool _busy = false;
  String? _passwordError;
  String? _error;

  bool get _canSubmit => _understood && !_busy;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_canSubmit) {
      return;
    }
    final l10n = context.l10n;
    // The core would answer validation.failed: a password is never blank.
    if (_password.text.trim().isEmpty) {
      setState(() => _passwordError = l10n.deleteAccountPasswordRequired);
      return;
    }
    final session = context.read<SessionController>();
    setState(() {
      _busy = true;
      _passwordError = null;
      _error = null;
    });
    try {
      await session.deleteAccount(_password.text);
    } on ApiException catch (error) {
      if (mounted) {
        _showRefusal(error, l10n);
      }
      return;
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
    if (mounted) {
      Navigator.of(context).pop(true);
    }
  }

  void _showRefusal(ApiException error, AppLocalizations l10n) {
    setState(() {
      switch (error.code) {
        case 'auth.wrong_password':
          _passwordError = l10n.errorMessage(error);
          _password.clear();
        case 'validation.failed':
          _passwordError = l10n.deleteAccountPasswordRequired;
        default:
          // No answer, or a 5xx, does not say how far the core got: the banks are revoked before the
          // delete's transaction, and a core slower than the BFF's 2 minutes still finishes after
          // the 503. Trying again settles it: an account already gone answers 204.
          final status = error.status;
          _error = status == null || status >= 500
              ? '${l10n.errorMessage(error)} ${l10n.deleteAccountNotConfirmed}'
              : l10n.errorMessage(error);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return PopScope(
      canPop: !_busy,
      child: AlertDialog(
        icon: Icon(Icons.warning_amber_rounded, color: scheme.error),
        title: Text(l10n.deleteAccountTitle),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.deleteAccountWarning, style: theme.textTheme.bodyLarge),
                const SizedBox(height: 8),
                Text(l10n.deleteAccountWhatGoes),
                const SizedBox(height: 12),
                CheckboxListTile(
                  value: _understood,
                  onChanged: _busy ? null : (value) => setState(() => _understood = value ?? false),
                  title: Text(l10n.deleteAccountConfirmCheck),
                  controlAffinity: ListTileControlAffinity.leading,
                  contentPadding: EdgeInsets.zero,
                ),
                const SizedBox(height: 8),
                Text(l10n.deleteAccountPasswordHelp,
                    style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                const SizedBox(height: 8),
                TextField(
                  controller: _password,
                  obscureText: _obscured,
                  enabled: !_busy,
                  autofillHints: const [AutofillHints.password],
                  decoration: InputDecoration(
                    labelText: l10n.fieldPassword,
                    errorText: _passwordError,
                    suffixIcon: IconButton(
                      tooltip: _obscured ? l10n.showPassword : l10n.hidePassword,
                      icon: Icon(_obscured ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                      onPressed: () => setState(() => _obscured = !_obscured),
                    ),
                  ),
                  onChanged: (_) => setState(() => _passwordError = null),
                  onSubmitted: (_) => _submit(),
                ),
                if (_busy) ...[
                  const SizedBox(height: 16),
                  const LinearProgressIndicator(),
                  const SizedBox(height: 8),
                  Text(l10n.deleteAccountInProgress, style: theme.textTheme.bodySmall),
                ] else if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: theme.textTheme.bodyMedium?.copyWith(color: scheme.error)),
                ],
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: _busy ? null : () => Navigator.of(context).pop(false),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: scheme.error, foregroundColor: scheme.onError),
            onPressed: _canSubmit ? _submit : null,
            child: _busy
                ? SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2, color: scheme.onError))
                : Text(l10n.deleteAccountAction),
          ),
        ],
      ),
    );
  }
}
