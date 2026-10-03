import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../session/session_controller.dart';

/// The browser: a computer is often shared or left unlocked, so the session ends after
/// [timeout] without a click, scroll or key press.
class IdleTimeout extends StatefulWidget {
  const IdleTimeout({
    super.key,
    required this.session,
    required this.child,
    this.timeout = const Duration(minutes: 30),
    this.checkEvery = const Duration(seconds: 30),
    this.now = DateTime.now,
  });

  final SessionController session;
  final Widget child;
  final Duration timeout;
  final Duration checkEvery;
  final DateTime Function() now;

  @override
  State<IdleTimeout> createState() => _IdleTimeoutState();
}

class _IdleTimeoutState extends State<IdleTimeout> {
  late final Timer _timer;
  late DateTime _lastActivity = widget.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(widget.checkEvery, (_) => _check());
    HardwareKeyboard.instance.addHandler(_onKey);
  }

  @override
  void dispose() {
    _timer.cancel();
    HardwareKeyboard.instance.removeHandler(_onKey);
    super.dispose();
  }

  bool _onKey(KeyEvent event) {
    _touch();
    return false;
  }

  void _touch() => _lastActivity = widget.now();

  void _check() {
    if (!widget.session.isSignedIn) {
      _touch();
      return;
    }
    if (widget.now().difference(_lastActivity) >= widget.timeout) {
      _touch();
      widget.session.signOut(reason: SignOutReason.idle);
    }
  }

  @override
  Widget build(BuildContext context) => Listener(
        behavior: HitTestBehavior.translucent,
        onPointerDown: (_) => _touch(),
        onPointerSignal: (_) => _touch(),
        onPointerHover: (_) => _touch(),
        child: widget.child,
      );
}
