import 'package:flutter/gestures.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../features/connections/presentation/pluggy_connect_screen.dart';
import '../l10n/l10n.dart';

/// "Li e aceito os Termos de Uso e a Política de Privacidade", the two names being links that open
/// in the browser (L15). Used by the sign-up and by the notice that asks again when the texts change.
/// When a link cannot be opened [onLinkFailed] says so: the person must be able to read before accepting.
class AcceptTermsLabel extends StatefulWidget {
  const AcceptTermsLabel({super.key, required this.termsUrl, required this.privacyUrl, required this.onLinkFailed});

  final Uri termsUrl;
  final Uri privacyUrl;
  final VoidCallback onLinkFailed;

  @override
  State<AcceptTermsLabel> createState() => _AcceptTermsLabelState();
}

class _AcceptTermsLabelState extends State<AcceptTermsLabel> {
  late final TapGestureRecognizer _terms = TapGestureRecognizer()..onTap = () => _open(widget.termsUrl);
  late final TapGestureRecognizer _privacy = TapGestureRecognizer()..onTap = () => _open(widget.privacyUrl);

  @override
  void dispose() {
    _terms.dispose();
    _privacy.dispose();
    super.dispose();
  }

  Future<void> _open(Uri url) async {
    final opened = await context.read<OpenOutside>()(url, preferApp: false);
    if (!opened) {
      widget.onLinkFailed();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final link = TextStyle(color: Theme.of(context).colorScheme.primary, decoration: TextDecoration.underline);
    return Text.rich(TextSpan(children: [
      TextSpan(text: l10n.legalAcceptPrefix),
      TextSpan(text: l10n.legalTermsLink, style: link, recognizer: _terms),
      TextSpan(text: l10n.legalAcceptAnd),
      TextSpan(text: l10n.legalPrivacyLink, style: link, recognizer: _privacy),
    ]));
  }
}
