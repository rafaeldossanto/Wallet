import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_api.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/widgets/qr_code_view.dart';

Future<void> showEnableTwoFactorDialog(BuildContext context) => showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _EnableTwoFactorDialog(),
    );

Future<void> showDisableTwoFactorDialog(BuildContext context) =>
    showDialog<void>(context: context, builder: (_) => const _DisableTwoFactorDialog());

enum _Step { password, scan, recoveryCodes }

/// Turning the second factor on, in three steps: the password, the QR code with the first code
/// from the app, and the recovery codes, shown this once.
class _EnableTwoFactorDialog extends StatefulWidget {
  const _EnableTwoFactorDialog();

  @override
  State<_EnableTwoFactorDialog> createState() => _EnableTwoFactorDialogState();
}

class _EnableTwoFactorDialogState extends State<_EnableTwoFactorDialog> {
  final _password = TextEditingController();
  final _code = TextEditingController();
  _Step _step = _Step.password;
  TwoFactorSetup? _setup;
  List<String> _recoveryCodes = const [];
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function(SessionController session) action) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action(context.read<SessionController>());
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _start() async {
    if (_password.text.isEmpty) {
      setState(() => _error = context.l10n.validationRequired);
      return;
    }
    await _run((session) async {
      final setup = await session.setUpTwoFactor(_password.text);
      _password.clear();
      setState(() {
        _setup = setup;
        _step = _Step.scan;
      });
    });
  }

  Future<void> _enable() async {
    if (_code.text.trim().isEmpty) {
      setState(() => _error = context.l10n.validationRequired);
      return;
    }
    await _run((session) async {
      final codes = await session.enableTwoFactor(_code.text);
      setState(() {
        _recoveryCodes = codes;
        _step = _Step.recoveryCodes;
      });
    });
  }

  Future<void> _copyCodes() async {
    await Clipboard.setData(ClipboardData(text: _recoveryCodes.join('\n')));
    if (mounted) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(SnackBar(content: Text(context.l10n.twoFactorCopied)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final cancel = TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel));
    return switch (_step) {
      _Step.password => AlertDialog(
          title: Text(l10n.twoFactorSetupTitle),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.twoFactorPasswordHelp),
              const SizedBox(height: 16),
              TextField(
                controller: _password,
                autofocus: true,
                obscureText: true,
                decoration: InputDecoration(labelText: l10n.fieldYourPassword, errorText: _error, errorMaxLines: 3),
                onSubmitted: (_) => _start(),
              ),
            ],
          ),
          actions: [cancel, FilledButton(onPressed: _busy ? null : _start, child: Text(l10n.actionContinue))],
        ),
      _Step.scan => AlertDialog(
          title: Text(l10n.twoFactorSetupTitle),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.twoFactorScanHelp),
                const SizedBox(height: 16),
                Center(child: QrCodeView(data: _setup!.otpauthUri)),
                const SizedBox(height: 12),
                Text(l10n.twoFactorSecretLabel, style: Theme.of(context).textTheme.labelMedium),
                SelectableText(_setup!.groupedSecret, style: const TextStyle(fontFamily: 'monospace', fontSize: 16)),
                const SizedBox(height: 16),
                Text(l10n.twoFactorCodeHelp),
                const SizedBox(height: 8),
                TextField(
                  controller: _code,
                  keyboardType: TextInputType.number,
                  autofillHints: const [AutofillHints.oneTimeCode],
                  decoration: InputDecoration(labelText: l10n.fieldTwoFactorCode, errorText: _error, errorMaxLines: 3),
                  onSubmitted: (_) => _enable(),
                ),
              ],
            ),
          ),
          actions: [cancel, FilledButton(onPressed: _busy ? null : _enable, child: Text(l10n.twoFactorEnableAction))],
        ),
      _Step.recoveryCodes => AlertDialog(
          title: Text(l10n.twoFactorRecoveryTitle),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.twoFactorRecoveryHelp),
              const SizedBox(height: 16),
              SelectableText(_recoveryCodes.join('\n'),
                  textAlign: TextAlign.center, style: const TextStyle(fontFamily: 'monospace', fontSize: 16, height: 1.6)),
            ],
          ),
          actions: [
            TextButton(onPressed: _copyCodes, child: Text(l10n.twoFactorCopyCodes)),
            FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.twoFactorDone)),
          ],
        ),
    };
  }
}

/// The password and a code (or a recovery code): a session alone cannot turn it off.
class _DisableTwoFactorDialog extends StatefulWidget {
  const _DisableTwoFactorDialog();

  @override
  State<_DisableTwoFactorDialog> createState() => _DisableTwoFactorDialogState();
}

class _DisableTwoFactorDialogState extends State<_DisableTwoFactorDialog> {
  final _password = TextEditingController();
  final _code = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _disable() async {
    if (_password.text.isEmpty || _code.text.trim().isEmpty) {
      setState(() => _error = context.l10n.validationRequired);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<SessionController>().disableTwoFactor(_password.text, _code.text);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } on ApiException catch (error) {
      if (mounted) {
        _code.clear();
        setState(() => _error = context.l10n.errorMessage(error));
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
      title: Text(l10n.twoFactorDisableTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.twoFactorDisableHelp),
          const SizedBox(height: 16),
          TextField(
            controller: _password,
            autofocus: true,
            obscureText: true,
            decoration: InputDecoration(labelText: l10n.fieldYourPassword),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _code,
            autofillHints: const [AutofillHints.oneTimeCode],
            decoration: InputDecoration(labelText: l10n.fieldTwoFactorCode, errorText: _error, errorMaxLines: 3),
            onSubmitted: (_) => _disable(),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _busy ? null : _disable, child: Text(l10n.twoFactorDisableAction)),
      ],
    );
  }
}

Future<void> showNewRecoveryCodesDialog(BuildContext context) =>
    showDialog<void>(context: context, barrierDismissible: false, builder: (_) => const _NewRecoveryCodesDialog());

/// New recovery codes in place of the old ones: the password and a code (or a recovery code) first,
/// like turning the factor off, and then the ten codes, shown this once like at turn-on.
class _NewRecoveryCodesDialog extends StatefulWidget {
  const _NewRecoveryCodesDialog();

  @override
  State<_NewRecoveryCodesDialog> createState() => _NewRecoveryCodesDialogState();
}

class _NewRecoveryCodesDialogState extends State<_NewRecoveryCodesDialog> {
  final _password = TextEditingController();
  final _code = TextEditingController();
  List<String>? _recoveryCodes;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _generate() async {
    if (_password.text.isEmpty || _code.text.trim().isEmpty) {
      setState(() => _error = context.l10n.validationRequired);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final codes = await context.read<SessionController>().renewRecoveryCodes(_password.text, _code.text);
      if (mounted) {
        setState(() => _recoveryCodes = codes);
      }
    } on ApiException catch (error) {
      if (mounted) {
        _code.clear();
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _copyCodes() async {
    await Clipboard.setData(ClipboardData(text: _recoveryCodes!.join('\n')));
    if (mounted) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(SnackBar(content: Text(context.l10n.twoFactorCopied)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (_recoveryCodes case final codes?) {
      return AlertDialog(
        title: Text(l10n.twoFactorRecoveryTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.twoFactorRecoveryHelp),
            const SizedBox(height: 16),
            SelectableText(codes.join('\n'),
                textAlign: TextAlign.center, style: const TextStyle(fontFamily: 'monospace', fontSize: 16, height: 1.6)),
          ],
        ),
        actions: [
          TextButton(onPressed: _copyCodes, child: Text(l10n.twoFactorCopyCodes)),
          FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.twoFactorDone)),
        ],
      );
    }
    return AlertDialog(
      title: Text(l10n.newRecoveryCodesTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.newRecoveryCodesHelp),
          const SizedBox(height: 16),
          TextField(
            controller: _password,
            autofocus: true,
            obscureText: true,
            decoration: InputDecoration(labelText: l10n.fieldYourPassword),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _code,
            autofillHints: const [AutofillHints.oneTimeCode],
            decoration: InputDecoration(labelText: l10n.fieldTwoFactorCode, errorText: _error, errorMaxLines: 3),
            onSubmitted: (_) => _generate(),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(l10n.actionCancel)),
        FilledButton(onPressed: _busy ? null : _generate, child: Text(l10n.newRecoveryCodesAction)),
      ],
    );
  }
}
