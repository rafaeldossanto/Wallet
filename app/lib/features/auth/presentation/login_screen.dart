import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/config/server_address.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_controller.dart';
import 'auth_layout.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _code = TextEditingController();
  final _codeFocus = FocusNode();
  bool _submitting = false;
  String? _error;

  /// The account has the second factor on: the core asked for the authenticator's code.
  bool _needsCode = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _code.dispose();
    _codeFocus.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_form.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await context
          .read<SessionController>()
          .signIn(_email.text, _password.text, code: _needsCode ? _code.text.trim() : null);
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      if (error.code == 'auth.two_factor_required') {
        // The password was right; the same form takes the code and is sent again.
        setState(() => _needsCode = true);
        _codeFocus.requestFocus();
      } else {
        if (error.code == 'auth.invalid_two_factor_code') {
          _code.clear();
        }
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final reason = context.select<SessionController, SignOutReason?>((session) => session.lastSignOutReason);
    final notice = switch (reason) {
      SignOutReason.expired => l10n.loginSessionExpired,
      SignOutReason.idle => l10n.loginSessionIdle,
      SignOutReason.accountDeleted => l10n.loginAccountDeleted,
      SignOutReason.passwordChanged => l10n.loginPasswordChanged,
      SignOutReason.requested || null => null,
    };
    return AuthLayout(
      subtitle: l10n.loginSubtitle,
      children: [
        if (notice != null) FormNoticeText(notice),
        Form(
          key: _form,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _email,
                  decoration: InputDecoration(labelText: l10n.fieldEmail),
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  textInputAction: TextInputAction.next,
                  validator: (value) => (value ?? '').contains('@') ? null : l10n.validationEmail,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _password,
                  decoration: InputDecoration(labelText: l10n.fieldPassword),
                  obscureText: true,
                  autofillHints: const [AutofillHints.password],
                  textInputAction: _needsCode ? TextInputAction.next : TextInputAction.done,
                  onFieldSubmitted: (_) => _needsCode ? _codeFocus.requestFocus() : _submit(),
                  validator: (value) => (value ?? '').isEmpty ? l10n.validationRequired : null,
                ),
                if (_needsCode) ...[
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _code,
                    focusNode: _codeFocus,
                    decoration: InputDecoration(labelText: l10n.fieldTwoFactorCode, helperText: l10n.loginTwoFactorHelp,
                        helperMaxLines: 3),
                    autofillHints: const [AutofillHints.oneTimeCode],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _submit(),
                    validator: (value) => (value ?? '').trim().isEmpty ? l10n.validationRequired : null,
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        if (_error != null) FormErrorText(_error!),
        FilledButton(
          onPressed: _submitting ? null : _submit,
          child: _submitting
              ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(l10n.loginAction),
        ),
        const SizedBox(height: 12),
        TextButton(onPressed: () => context.go('/forgot-password'), child: Text(l10n.loginForgotPassword)),
        TextButton(onPressed: () => context.go('/register'), child: Text(l10n.loginGoToRegister)),
        TextButton(
          onPressed: () => showDialog<void>(context: context, builder: (_) => const _ResendConfirmationDialog()),
          child: Text(l10n.loginNoConfirmation),
        ),
        if (context.watch<ServerAddress?>() case final server?) ...[
          const SizedBox(height: 8),
          _ServerLine(server: server),
        ],
      ],
    );
  }
}

/// Another confirmation link, for whoever lost the first. The same notice for every e-mail, as
/// the server's answer is the same.
class _ResendConfirmationDialog extends StatefulWidget {
  const _ResendConfirmationDialog();

  @override
  State<_ResendConfirmationDialog> createState() => _ResendConfirmationDialogState();
}

class _ResendConfirmationDialogState extends State<_ResendConfirmationDialog> {
  final _email = TextEditingController();
  bool _sending = false;
  String? _notice;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (!_email.text.contains('@')) {
      setState(() => _error = context.l10n.validationEmail);
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await context.read<SessionController>().resendConfirmation(_email.text);
      if (mounted) {
        setState(() => _notice = context.l10n.confirmationResent);
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = context.l10n.errorMessage(error));
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
    return AlertDialog(
      title: Text(l10n.resendDialogTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(_notice ?? l10n.resendDialogHelp),
          if (_notice == null) ...[
            const SizedBox(height: 16),
            TextField(
              controller: _email,
              autofocus: true,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(labelText: l10n.fieldEmail, errorText: _error),
              onSubmitted: (_) => _send(),
            ),
          ],
        ],
      ),
      actions: _notice != null
          ? [FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.twoFactorDone))]
          : [
              TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
              FilledButton(onPressed: _sending ? null : _send, child: Text(l10n.resendAction)),
            ],
    );
  }
}

/// Which BFF the app talks to, and the way to point a phone at the one on the user's PC.
class _ServerLine extends StatelessWidget {
  const _ServerLine({required this.server});

  final ServerAddress server;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final address = Uri.parse(server.current).authority;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Flexible(
          child: Text(
            context.l10n.serverLine(address),
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        TextButton(
          onPressed: () => showDialog<void>(context: context, builder: (_) => _ServerDialog(server: server)),
          child: Text(context.l10n.serverChange),
        ),
      ],
    );
  }
}

class _ServerDialog extends StatefulWidget {
  const _ServerDialog({required this.server});

  final ServerAddress server;

  @override
  State<_ServerDialog> createState() => _ServerDialogState();
}

class _ServerDialogState extends State<_ServerDialog> {
  late final _address = TextEditingController(text: Uri.parse(widget.server.current).authority);
  String? _error;

  @override
  void dispose() {
    _address.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!await widget.server.set(_address.text)) {
      setState(() => _error = context.l10n.serverInvalid);
      return;
    }
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.serverDialogTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.serverDialogHelp),
          const SizedBox(height: 16),
          TextField(
            controller: _address,
            autofocus: true,
            keyboardType: TextInputType.url,
            decoration: InputDecoration(labelText: l10n.serverFieldLabel, errorText: _error),
            onChanged: (_) => setState(() => _error = null),
            onSubmitted: (_) => _save(),
          ),
        ],
      ),
      actions: [
        if (widget.server.isCustom)
          TextButton(
            onPressed: () async {
              await widget.server.reset();
              if (context.mounted) {
                Navigator.of(context).pop();
              }
            },
            child: Text(l10n.serverUseDefault),
          ),
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _save, child: Text(l10n.serverSave)),
      ],
    );
  }
}
