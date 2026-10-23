import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_controller.dart';

Future<void> showRenameDialog(BuildContext context, String currentName) =>
    showDialog<void>(context: context, builder: (_) => _RenameDialog(currentName: currentName));

Future<void> showChangeEmailDialog(BuildContext context) =>
    showDialog<void>(context: context, builder: (_) => const _ChangeEmailDialog());

class _RenameDialog extends StatefulWidget {
  const _RenameDialog({required this.currentName});

  final String currentName;

  @override
  State<_RenameDialog> createState() => _RenameDialogState();
}

class _RenameDialogState extends State<_RenameDialog> {
  late final _name = TextEditingController(text: widget.currentName);
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_name.text.trim().isEmpty) {
      setState(() => _error = context.l10n.validationRequired);
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await context.read<SessionController>().rename(_name.text);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.nameDialogTitle),
      content: TextField(
        controller: _name,
        autofocus: true,
        maxLength: 80,
        textCapitalization: TextCapitalization.words,
        decoration: InputDecoration(labelText: l10n.fieldName, errorText: _error),
        onSubmitted: (_) => _save(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _saving ? null : _save, child: Text(l10n.actionSave)),
      ],
    );
  }
}

/// The new address and the password. The account moves only when the link sent there is opened,
/// and the current address is told; meanwhile Settings shows the address waiting.
class _ChangeEmailDialog extends StatefulWidget {
  const _ChangeEmailDialog();

  @override
  State<_ChangeEmailDialog> createState() => _ChangeEmailDialogState();
}

class _ChangeEmailDialogState extends State<_ChangeEmailDialog> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _sending = false;
  String? _emailError;
  String? _passwordError;
  String? _sentTo;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final l10n = context.l10n;
    setState(() {
      _emailError = _email.text.contains('@') ? null : l10n.validationEmail;
      _passwordError = _password.text.isEmpty ? l10n.validationRequired : null;
    });
    if (_emailError != null || _passwordError != null) {
      return;
    }
    setState(() => _sending = true);
    try {
      await context.read<SessionController>().changeEmail(_email.text, _password.text);
      if (mounted) {
        setState(() => _sentTo = _email.text.trim());
      }
    } on ApiException catch (error) {
      if (mounted) {
        _password.clear();
        final message = context.l10n.errorMessage(error);
        setState(() {
          if (error.code == 'auth.email_unchanged') {
            _emailError = message;
          } else {
            _passwordError = message;
          }
        });
      }
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (_sentTo case final email?) {
      return AlertDialog(
        title: Text(l10n.emailDialogTitle),
        content: Text(l10n.emailDialogSent(email)),
        actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.twoFactorDone))],
      );
    }
    return AlertDialog(
      title: Text(l10n.emailDialogTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.emailDialogHelp),
          const SizedBox(height: 16),
          TextField(
            controller: _email,
            autofocus: true,
            keyboardType: TextInputType.emailAddress,
            decoration: InputDecoration(labelText: l10n.fieldNewEmail, errorText: _emailError),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _password,
            obscureText: true,
            decoration: InputDecoration(labelText: l10n.fieldYourPassword, errorText: _passwordError, errorMaxLines: 3),
            onSubmitted: (_) => _send(),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: _sending ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _sending ? null : _send, child: Text(l10n.actionContinue)),
      ],
    );
  }
}
