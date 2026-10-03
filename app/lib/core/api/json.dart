import '../money/money.dart';

/// Typed reads over a JSON object from the BFF. A wrong type fails loudly here instead of
/// showing up later as a blank widget.
extension type Json(Map<String, dynamic> raw) {
  factory Json.of(Object? value) => Json(value as Map<String, dynamic>);

  String string(String key) => raw[key] as String;

  String? stringOrNull(String key) => raw[key] as String?;

  int? intOrNull(String key) => (raw[key] as num?)?.toInt();

  int integer(String key) => (raw[key] as num).toInt();

  Money money(String key) => Money.parse(raw[key] as String);

  Money? moneyOrNull(String key) => Money.tryParse(raw[key] as String?);

  /// A calendar date (`2026-09-30`), as local midnight.
  DateTime date(String key) => _date(raw[key] as String);

  DateTime? dateOrNull(String key) => raw[key] == null ? null : _date(raw[key] as String);

  /// A moment (`2026-10-01T09:00:00Z`), in the device's time zone.
  DateTime? instantOrNull(String key) => raw[key] == null ? null : DateTime.parse(raw[key] as String).toLocal();

  Json object(String key) => Json(raw[key] as Map<String, dynamic>);

  Json? objectOrNull(String key) => raw[key] == null ? null : Json(raw[key] as Map<String, dynamic>);

  List<Json> list(String key) => [for (final item in (raw[key] as List<dynamic>? ?? const [])) Json.of(item)];

  List<String> strings(String key) => [for (final item in (raw[key] as List<dynamic>? ?? const [])) item as String];

  static List<Json> listOf(Object? value) => [for (final item in value as List<dynamic>) Json.of(item)];

  static DateTime _date(String value) {
    final [year, month, day] = value.split('-').map(int.parse).toList();
    return DateTime(year, month, day);
  }
}
