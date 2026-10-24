import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_controller.dart';
import '../../auth/presentation/register_screen.dart';

Future<void> showChangePasswordDialog(BuildContext context) =>
    showDialog<void>(context: context, builder: (_) => const _ChangePasswordDialog());

/// "Trocar senha": the current password, the new one twice. The server ends every session with the
/// change, this one included, so on success the dialog goes with the session and the sign-in says
/// why. What the server would refuse for its shape (short, same as the current one, repeats that
/// differ) never leaves the app; a wrong current password stays on its field.
class _ChangePasswordDialog extends StatefulWidget {
  const _ChangePasswordDialog();

  @override
  State<_ChangePasswordDialog> createState() => _ChangePasswordDialogState();
}

class _ChangePasswordDialogState extends State<_ChangePasswordDialog> {
  final _current = TextEditingController();
  final _new = TextEditingController();
  final _repeat = TextEditingController();
  bool _busy = false;
  String? _currentError;
  String? _newError;
  String? _error;

  @override
  void dispose() {
    _current.dispose();
    _new.dispose();
    _repeat.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) {
      return;
    }
    final l10n = context.l10n;
    final newError = switch ((_new.text, _repeat.text)) {
      (final next, _) when next.length < RegisterScreen.minPasswordLength =>
        l10n.validationPasswordLength(RegisterScreen.minPasswordLength),
      (final next, _) when next == _current.text => l10n.validationPasswordSame,
      (final next, final repeat) when next != repeat => l10n.validationPasswordsDiffer,
      _ => null,
    };
    if (_current.text.isEmpty || newError != null) {
      setState(() {
        _currentError = _current.text.isEmpty ? l10n.validationRequired : null;
        _newError = newError;
      });
      return;
    }
    setState(() {
      _busy = true;
      _currentError = null;
      _newError = null;
      _error = null;
    });
    try {
      await context.read<SessionController>().changePassword(_current.text, _new.text);
      // Signed out: the router is on its way to the sign-in, which tells why.
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          if (error.code == 'auth.wrong_password') {
            _currentError = l10n.errorMessage(error);
            _current.clear();
          } else {
            _error = l10n.errorMessage(error);
          }
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.settingsChangePassword),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.changePasswordHelp),
            const SizedBox(height: 16),
            TextField(
              controller: _current,
              autofocus: true,
              obscureText: true,
              enabled: !_busy,
              autofillHints: const [AutofillHints.password],
              decoration: InputDecoration(labelText: l10n.fieldCurrentPassword, errorText: _currentError, errorMaxLines: 3),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _new,
              obscureText: true,
              enabled: !_busy,
              autofillHints: const [AutofillHints.newPassword],
              decoration: InputDecoration(labelText: l10n.fieldNewPassword, errorText: _newError, errorMaxLines: 3),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _repeat,
              obscureText: true,
              enabled: !_busy,
              autofillHints: const [AutofillHints.newPassword],
              decoration: InputDecoration(labelText: l10n.fieldRepeatNewPassword),
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _busy ? null : _submit, child: Text(l10n.changePasswordAction)),
      ],
    );
  }
}
