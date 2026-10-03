import 'package:material_ui/material_ui.dart';

/// Colors with a meaning of their own, beyond the Material scheme.
@immutable
class WalletColors extends ThemeExtension<WalletColors> {
  const WalletColors({required this.inflow, required this.warning, required this.chart});

  /// Money coming in. Money going out stays in the normal text color: red would make every
  /// purchase look like a problem.
  final Color inflow;
  final Color warning;

  /// Series colors for the charts, in order.
  final List<Color> chart;

  @override
  WalletColors copyWith({Color? inflow, Color? warning, List<Color>? chart}) =>
      WalletColors(inflow: inflow ?? this.inflow, warning: warning ?? this.warning, chart: chart ?? this.chart);

  @override
  WalletColors lerp(WalletColors? other, double t) {
    if (other == null) {
      return this;
    }
    return WalletColors(
      inflow: Color.lerp(inflow, other.inflow, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      chart: [for (var index = 0; index < chart.length; index++) Color.lerp(chart[index], other.chart[index], t)!],
    );
  }
}

/// Black: the screen is pure black, each layer above it a neutral gray a step lighter, and
/// actions are white. Color is kept for meaning only: money in, warnings, errors and charts.
abstract final class AppTheme {
  static ThemeData dark() {
    final scheme = ColorScheme.fromSeed(
      seedColor: Colors.black,
      brightness: Brightness.dark,
      dynamicSchemeVariant: DynamicSchemeVariant.monochrome,
    ).copyWith(
      surface: Colors.black,
      surfaceDim: Colors.black,
      surfaceBright: const Color(0xFF2A2A2A),
      surfaceContainerLowest: Colors.black,
      surfaceContainerLow: const Color(0xFF0A0A0A),
      surfaceContainer: const Color(0xFF121212),
      surfaceContainerHigh: const Color(0xFF1C1C1C),
      surfaceContainerHighest: const Color(0xFF262626),
      onSurface: const Color(0xFFF2F2F2),
      onSurfaceVariant: const Color(0xFFA3A3A3),
      primary: Colors.white,
      onPrimary: Colors.black,
      primaryContainer: const Color(0xFF2A2A2A),
      onPrimaryContainer: Colors.white,
      secondaryContainer: const Color(0xFF2A2A2A),
      onSecondaryContainer: Colors.white,
      outline: const Color(0xFF5C5C5C),
      outlineVariant: const Color(0xFF2E2E2E),
    );
    return ThemeData(
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      extensions: const [
        WalletColors(
          inflow: Color(0xFF4ADE80),
          warning: Color(0xFFFBBF24),
          chart: [
            Color(0xFF2DD4A3),
            Color(0xFF60A5FA),
            Color(0xFFF472B6),
            Color(0xFFFBBF24),
            Color(0xFFA78BFA),
            Color(0xFFFB923C),
            Color(0xFF94A3B8),
          ],
        ),
      ],
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainer,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
      listTileTheme: const ListTileThemeData(contentPadding: EdgeInsets.symmetric(horizontal: 16)),
    );
  }
}

extension WalletTheme on BuildContext {
  WalletColors get walletColors => Theme.of(this).extension<WalletColors>()!;
}
