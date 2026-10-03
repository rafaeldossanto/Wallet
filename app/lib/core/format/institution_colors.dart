import 'package:material_ui/material_ui.dart';

/// The color people associate with each bank, for the round badge with its initials. Pluggy sends
/// logos as SVG, which the app does not draw yet, so the color does the recognizing. Banks not
/// listed get a steady color picked from the name.
abstract final class InstitutionColors {
  static ({Color background, Color foreground}) of(String? name) {
    final key = _normalize(name ?? '');
    for (final entry in _known.entries) {
      if (key.contains(entry.key)) {
        return entry.value;
      }
    }
    final background = _fallback[key.codeUnits.fold(0, (hash, unit) => (hash * 31 + unit) & 0x7fffffff) % _fallback.length];
    return (background: background, foreground: Colors.white);
  }

  static String _normalize(String value) => value
      .toLowerCase()
      .replaceAll(RegExp('[áàâã]'), 'a')
      .replaceAll(RegExp('[éê]'), 'e')
      .replaceAll('í', 'i')
      .replaceAll(RegExp('[óôõ]'), 'o')
      .replaceAll('ú', 'u')
      .replaceAll('ç', 'c');

  static const _white = Colors.white;
  static const _black = Color(0xFF111111);

  static const _known = <String, ({Color background, Color foreground})>{
    'nubank': (background: Color(0xFF820AD1), foreground: _white),
    'itau': (background: Color(0xFFEC7000), foreground: _white),
    'bradesco': (background: Color(0xFFCC092F), foreground: _white),
    'santander': (background: Color(0xFFEC0000), foreground: _white),
    'banco do brasil': (background: Color(0xFFFCFC30), foreground: Color(0xFF0038A8)),
    'caixa': (background: Color(0xFF005CA9), foreground: _white),
    'inter': (background: Color(0xFFFF7A00), foreground: _white),
    'c6': (background: _black, foreground: _white),
    'xp': (background: _black, foreground: Color(0xFFFFC709)),
    'btg': (background: Color(0xFF001E62), foreground: _white),
    'picpay': (background: Color(0xFF21C25E), foreground: _white),
    'mercado pago': (background: Color(0xFF00B1EA), foreground: _white),
    'sicredi': (background: Color(0xFF3FA110), foreground: _white),
    'sicoob': (background: Color(0xFF003641), foreground: _white),
  };

  static const _fallback = [
    Color(0xFF3D5AFE),
    Color(0xFF0EA5E9),
    Color(0xFF14B8A6),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFF59E0B),
  ];
}
