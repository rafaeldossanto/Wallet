import 'dart:convert';

/// How the app talks to the Pluggy Connect widget (connect.pluggy.ai) inside a WebView.
///
/// Pluggy's mobile SDKs (react-native-pluggy-connect 1.6, flutter_pluggy_connect 3.0) give the
/// page a `window.ReactNativeWebView.postMessage`; Connect then posts JSON strings
/// `{type, message}` to it:
/// - `OAUTH_OPEN`: an Open Finance authorization URL, to open outside the WebView so Android hands
///   it to the bank's app. Connect keeps polling the item meanwhile, so nothing comes back from it;
///   the bank returns the user through the deep link in the connect token.
/// - `LINK_OPEN`: an ordinary link (terms, privacy), for the browser.
/// - `LOCATION`: the page's query string, where Connect appends each event to `events` with a
///   `timestamp`, plus `item_id`, `item_status`, `connector_name` and `error` as they become known.
///
/// Written here instead of using flutter_pluggy_connect 3.0.1: that package closes its screen as soon
/// as it loads if a deep link has already reached the app (so a second connection after a bank's
/// return closes at once), and it builds on the framework's old Material instead of material_ui.
abstract final class PluggyConnect {
  static final widgetUrl = Uri.parse('https://connect.pluggy.ai/');

  /// The name Connect looks for on `window`.
  static const channel = 'ReactNativeWebView';

  /// Where the result of an authorization goes when the app has to report one (see [oauthFailed]).
  static const _oauthSource = 'connector-oauth-login';

  static Uri url({required String connectToken, required bool dark, bool includeSandbox = false}) =>
      widgetUrl.replace(queryParameters: {
        'connect_token': connectToken,
        'lang': 'pt',
        'theme': dark ? 'dark' : 'light',
        if (includeSandbox) 'with_sandbox': 'true',
      });

  /// Pages that leave the WebView for the system: Meu Pluggy (as in Pluggy's SDK) and anything
  /// that is not a web page, like a bank app's own scheme.
  static bool opensOutside(Uri url) => !_inPageSchemes.contains(url.scheme) || url.host == 'meu.pluggy.ai';

  static const _inPageSchemes = {'https', 'http', 'about', 'data', 'blob'};

  /// Tells Connect that the bank could not be opened, so it shows an error instead of waiting.
  static Map<String, Object?> oauthFailed(String reason) => {'source': _oauthSource, 'error': reason};
}

/// What Connect asked for, read from one message.
sealed class ConnectMessage {
  const ConnectMessage();
}

/// The bank's Open Finance authorization: its app when installed, the browser otherwise.
final class OpenAuthorization extends ConnectMessage {
  const OpenAuthorization(this.url);

  final Uri url;
}

final class OpenLink extends ConnectMessage {
  const OpenLink(this.url);

  final Uri url;
}

enum ConnectEventType {
  /// The widget is on screen.
  opened,

  /// The user closed it, from its own X.
  closed,

  /// The bank's data was collected: the item is ready to link.
  succeeded,

  /// An attempt failed; Connect shows it and lets the user try again.
  failed,

  /// Steps in between (institution chosen, consent submitted...).
  other,
}

final class ConnectEvent extends ConnectMessage {
  const ConnectEvent(
    this.type, {
    this.name = '',
    this.itemId,
    this.itemStatus,
    this.executionStatus,
    this.institutionName,
    this.error,
  });

  final ConnectEventType type;

  /// Connect's own name for it (`SELECTED_INSTITUTION`, `SUCCESS`...).
  final String name;
  final String? itemId;

  /// Pluggy's item status (`UPDATING`, `UPDATED`, `LOGIN_ERROR`...).
  final String? itemStatus;

  /// What the item is doing (`LOGIN_IN_PROGRESS`, `ACCOUNTS_IN_PROGRESS`, `SUCCESS`...).
  final String? executionStatus;
  final String? institutionName;
  final String? error;

  /// Past the bank's login: the item is collecting the data, or has it. Leaving Connect now still
  /// leaves an item worth linking. The rule Pluggy's React Native SDK uses to tell the login step
  /// is over, without its failed-login case.
  bool get itemAuthorized {
    if (itemId == null) {
      return false;
    }
    final execution = executionStatus ?? '';
    final collecting = !execution.contains('LOGIN') && execution.endsWith('_IN_PROGRESS');
    return (itemStatus == 'UPDATING' && collecting) || const {'MERGING', 'UPDATED', 'OUTDATED'}.contains(itemStatus);
  }
}

/// Turns Connect's messages into [ConnectMessage]s. Each `LOCATION` repeats the whole query, so an
/// event is new only when its timestamp is.
class ConnectMessageReader {
  String? _lastTimestamp;

  ConnectMessage? read(String raw) {
    final Object? decoded;
    try {
      decoded = jsonDecode(raw);
    } on FormatException {
      return null;
    }
    if (decoded is! Map || decoded['type'] is! String || decoded['message'] is! String) {
      return null;
    }
    final message = decoded['message'] as String;
    return switch (decoded['type']) {
      'OAUTH_OPEN' => _url(message, OpenAuthorization.new),
      'LINK_OPEN' => _url(message, OpenLink.new),
      'LOCATION' => _event(message),
      _ => null,
    };
  }

  static ConnectMessage? _url(String value, ConnectMessage Function(Uri url) build) {
    final url = Uri.tryParse(value);
    return url == null || !url.hasScheme ? null : build(url);
  }

  ConnectEvent? _event(String location) {
    final query = location.contains('?') ? location.substring(location.indexOf('?') + 1) : location;
    final Map<String, String> params;
    try {
      params = Uri.splitQueryString(query);
    } on ArgumentError {
      return null; // a broken percent escape
    }
    final name = params['events']?.split(',').last ?? '';
    final timestamp = params['timestamp'];
    if (name.isEmpty || (timestamp != null && timestamp == _lastTimestamp)) {
      return null;
    }
    _lastTimestamp = timestamp;
    return ConnectEvent(
      switch (name) {
        'OPEN' => ConnectEventType.opened,
        'CLOSE' => ConnectEventType.closed,
        'SUCCESS' => ConnectEventType.succeeded,
        'ERROR' => ConnectEventType.failed,
        _ => ConnectEventType.other,
      },
      name: name,
      itemId: _nonEmpty(params['item_id']),
      itemStatus: _nonEmpty(params['item_status']),
      executionStatus: _nonEmpty(params['execution_status']),
      institutionName: _nonEmpty(params['connector_name']),
      error: _nonEmpty(params['error']),
    );
  }

  static String? _nonEmpty(String? value) => value == null || value.isEmpty ? null : value;
}
