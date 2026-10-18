import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/connections_api.dart';
import '../data/pluggy_connect.dart';

/// Opens [url] in another app. With [preferApp], an app that claims the link (the bank's, through
/// Android app links) comes before the browser. False when nothing could open it.
typedef OpenOutside = Future<bool> Function(Uri url, {required bool preferApp});

/// What the screen hears from the page that shows Connect.
abstract interface class ConnectViewListener {
  /// What Connect posted to [PluggyConnect.channel].
  void onMessage(String message);

  /// A navigation the WebView must not follow (see [PluggyConnect.opensOutside]).
  void onLeave(Uri url);

  void onLoaded();

  /// The page itself did not load (no internet, Pluggy down).
  void onFailed();
}

/// The page that shows Connect: [WebConnectView] in the app, a fake in tests.
abstract interface class ConnectView {
  Future<void> load(Uri url, {required Color background});

  /// Delivers [message] to the page as a `message` event, the way Pluggy's SDKs answer Connect.
  Future<void> post(Map<String, Object?> message);

  Widget build(BuildContext context);
}

typedef ConnectViewFactory = ConnectView Function(ConnectViewListener listener);

/// How the app shows Pluggy Connect, provided once at the top; tests swap the WebView and the
/// launcher for fakes.
class PluggyConnectHost {
  const PluggyConnectHost({
    this.view = WebConnectView.new,
    this.openOutside = openOutsideApp,
    this.includeSandbox = kDebugMode,
  });

  final ConnectViewFactory view;
  final OpenOutside openOutside;

  /// Pluggy's sandbox banks, for trying the flow without a real account. Never in a release build.
  final bool includeSandbox;
}

Future<bool> openOutsideApp(Uri url, {required bool preferApp}) async {
  if (preferApp && await _launch(url, LaunchMode.externalNonBrowserApplication)) {
    return true;
  }
  return _launch(url, LaunchMode.externalApplication);
}

Future<bool> _launch(Uri url, LaunchMode mode) async {
  try {
    return await launchUrl(url, mode: mode);
  } on PlatformException {
    return false;
  }
}

/// Connect in a WebView, speaking the protocol of Pluggy's mobile SDKs (see [PluggyConnect]).
class WebConnectView implements ConnectView {
  WebConnectView(this._listener);

  final ConnectViewListener _listener;
  final _controller = WebViewController();

  @override
  Future<void> load(Uri url, {required Color background}) async {
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await _controller.setBackgroundColor(background);
    // A channel becomes a window object with postMessage, there before the page's own scripts run.
    await _controller.addJavaScriptChannel(PluggyConnect.channel,
        onMessageReceived: (message) => _listener.onMessage(message.message));
    await _controller.setNavigationDelegate(NavigationDelegate(
      onNavigationRequest: (request) {
        final target = Uri.tryParse(request.url);
        if (target != null && PluggyConnect.opensOutside(target)) {
          _listener.onLeave(target);
          return NavigationDecision.prevent;
        }
        return NavigationDecision.navigate;
      },
      onPageFinished: (_) => _listener.onLoaded(),
      onWebResourceError: (error) {
        if (error.isForMainFrame ?? false) {
          _listener.onFailed();
        }
      },
    ));
    await _controller.loadRequest(url);
  }

  @override
  Future<void> post(Map<String, Object?> message) =>
      _controller.runJavaScript("document.dispatchEvent(new MessageEvent('message', {data: ${jsonEncode(message)}}));");

  @override
  Widget build(BuildContext context) => WebViewWidget(controller: _controller);
}

/// Pluggy Connect, full screen: the user picks the bank and authorizes in the bank's own app, which
/// sends them back through the deep link. Pops with the item to link, or null when there is none.
///
/// The item comes with `SUCCESS`, once the bank's data is in. Leaving earlier, after the bank
/// authorized, still returns it: the data keeps arriving at Pluggy and comes in with the next sync.
class PluggyConnectScreen extends StatefulWidget {
  const PluggyConnectScreen({super.key});

  /// Opens the screen above the navigation and returns the item id it popped with.
  static Future<String?> open(BuildContext context) => Navigator.of(context, rootNavigator: true).push<String>(
        MaterialPageRoute(fullscreenDialog: true, builder: (_) => const PluggyConnectScreen()),
      );

  @override
  State<PluggyConnectScreen> createState() => _PluggyConnectScreenState();
}

class _PluggyConnectScreenState extends State<PluggyConnectScreen> implements ConnectViewListener {
  late final PluggyConnectHost _host = context.read<PluggyConnectHost>();
  final _reader = ConnectMessageReader();

  ConnectView? _view;
  ApiException? _tokenError;
  bool _pageFailed = false;
  bool _pageShown = false;
  String? _authorizedItemId;
  bool _finished = false;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    final String token;
    try {
      token = await context.read<ConnectionsApi>().connectToken();
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _tokenError = error);
      }
      return;
    }
    if (!mounted) {
      return;
    }
    final theme = Theme.of(context);
    final view = _host.view(this);
    setState(() => _view = view);
    await view.load(
      PluggyConnect.url(
        connectToken: token,
        dark: theme.brightness == Brightness.dark,
        includeSandbox: _host.includeSandbox,
      ),
      background: theme.colorScheme.surface,
    );
  }

  /// A fresh token and page: the old token may be the reason it failed.
  void _retry() {
    setState(() {
      _view = null;
      _tokenError = null;
      _pageFailed = false;
      _pageShown = false;
    });
    _start();
  }

  @override
  void onMessage(String message) {
    final read = _reader.read(message);
    if (read is ConnectEvent && read.itemAuthorized) {
      _authorizedItemId = read.itemId;
    }
    switch (read) {
      case OpenAuthorization(:final url):
        _openAuthorization(url);
      case OpenLink(:final url):
        _host.openOutside(url, preferApp: false);
      case ConnectEvent(type: ConnectEventType.opened):
        onLoaded();
      case ConnectEvent(type: ConnectEventType.succeeded, :final itemId?):
        _finish(itemId);
      case ConnectEvent(type: ConnectEventType.closed):
        _finish(_authorizedItemId);
      case ConnectEvent() || null:
        break;
    }
  }

  /// The bank's app when it claims the link, the browser otherwise. When neither opens, Connect
  /// is told, so it stops waiting for the authorization.
  Future<void> _openAuthorization(Uri url) async {
    if (!await _host.openOutside(url, preferApp: true)) {
      await _view?.post(PluggyConnect.oauthFailed('The authorization page could not be opened'));
    }
  }

  @override
  void onLeave(Uri url) {
    _host.openOutside(url, preferApp: true);
  }

  @override
  void onLoaded() {
    if (mounted && !_pageShown) {
      setState(() => _pageShown = true);
    }
  }

  @override
  void onFailed() {
    if (mounted) {
      setState(() => _pageFailed = true);
    }
  }

  void _finish(String? itemId) {
    if (_finished || !mounted) {
      return;
    }
    _finished = true;
    Navigator.of(context).pop(itemId);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final scheme = Theme.of(context).colorScheme;
    final view = _view;
    final showingConnect = view != null && _pageShown && !_pageFailed;
    final Widget body;
    if (_tokenError != null) {
      body = ErrorView(error: _tokenError!, onRetry: _retry);
    } else if (_pageFailed) {
      body = _PageFailed(onRetry: _retry);
    } else {
      body = Stack(
        fit: StackFit.expand,
        children: [
          if (view != null) view.build(context),
          if (!_pageShown)
            ColoredBox(
              color: scheme.surface,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 16),
                    Text(l10n.connectOpening),
                  ],
                ),
              ),
            ),
        ],
      );
    }
    // Back, or the X while loading, leaves with what there is to link.
    return PopScope<String>(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          _finish(_authorizedItemId);
        }
      },
      child: Scaffold(
        // Connect draws its own header and close button.
        appBar: showingConnect ? null : AppBar(title: Text(l10n.connectionsConnect), leading: const CloseButton()),
        body: SafeArea(top: showingConnect, child: body),
      ),
    );
  }
}

class _PageFailed extends StatelessWidget {
  const _PageFailed({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off_outlined, size: 48, color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 16),
            Text(l10n.connectPageFailed, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(onPressed: onRetry, child: Text(l10n.actionRetry)),
          ],
        ),
      ),
    );
  }
}
