import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:wallet/features/connections/presentation/pluggy_connect_screen.dart';

/// Pluggy Connect without a WebView: tests play the messages the page would post, and every app
/// or browser the screen asks to open is kept.
class FakeConnect {
  FakeConnectView? view;
  final opened = <({Uri url, bool preferApp})>[];

  /// Whether the device has something to open a link with.
  bool opens = true;

  late final host = PluggyConnectHost(
    view: (listener) => view = FakeConnectView(listener),
    openOutside: _open,
    includeSandbox: false,
  );

  Future<bool> _open(Uri url, {required bool preferApp}) async {
    opened.add((url: url, preferApp: preferApp));
    return opens;
  }
}

class FakeConnectView implements ConnectView {
  FakeConnectView(this.listener);

  final ConnectViewListener listener;
  Uri? url;
  final posted = <Map<String, Object?>>[];
  var _timestamp = 1760800000000;
  var _events = <String>[];

  @override
  Future<void> load(Uri url, {required Color background}) async => this.url = url;

  @override
  Future<void> post(Map<String, Object?> message) async => posted.add(message);

  @override
  Widget build(BuildContext context) => const SizedBox.expand(key: Key('connect-page'));

  /// Connect appends the event to `events` and posts the whole query string, as the real page does.
  void event(String name, {String? itemId, String? itemStatus, String? executionStatus, String? connector}) {
    _events = [..._events, name];
    _timestamp++;
    final query = Uri(queryParameters: {
      'connect_token': 'connect-token-1',
      'events': _events.join(','),
      'timestamp': '$_timestamp',
      'item_id': ?itemId,
      'item_status': ?itemStatus,
      'execution_status': ?executionStatus,
      'connector_name': ?connector,
    }).query;
    send('LOCATION', '?$query');
  }

  /// A message from the page, as Connect posts it to window.ReactNativeWebView.
  void send(String type, String message) => listener.onMessage(jsonEncode({'type': type, 'message': message}));
}
