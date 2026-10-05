import 'package:material_ui/material_ui.dart';

/// Colors with a meaning of their own, beyond the Material scheme.
@immutable
class WalletColors extends ThemeExtension<WalletColors> {
  const WalletColors({
    required this.inflow,
    required this.warning,
    required this.chart,
    required this.highlight,
    required this.onHighlight,
    required this.feature,
    required this.onFeature,
  });

  /// Money coming in. Money going out stays in the normal text color: red would make every
  /// purchase look like a problem.
  final Color inflow;
  final Color warning;

  /// Series colors for the charts, in order.
  final List<Color> chart;

  /// The lime accent: the selected destination, the selected day.
  final Color highlight;
  final Color onHighlight;

  /// The electric blue of the card that leads a screen (the spending calendar).
  final Color feature;
  final Color onFeature;

  @override
  WalletColors copyWith({
    Color? inflow,
    Color? warning,
    List<Color>? chart,
    Color? highlight,
    Color? onHighlight,
    Color? feature,
    Color? onFeature,
  }) =>
      WalletColors(
        inflow: inflow ?? this.inflow,
        warning: warning ?? this.warning,
        chart: chart ?? this.chart,
        highlight: highlight ?? this.highlight,
        onHighlight: onHighlight ?? this.onHighlight,
        feature: feature ?? this.feature,
        onFeature: onFeature ?? this.onFeature,
      );

  @override
  WalletColors lerp(WalletColors? other, double t) {
    if (other == null) {
      return this;
    }
    return WalletColors(
      inflow: Color.lerp(inflow, other.inflow, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      chart: [for (var index = 0; index < chart.length; index++) Color.lerp(chart[index], other.chart[index], t)!],
      highlight: Color.lerp(highlight, other.highlight, t)!,
      onHighlight: Color.lerp(onHighlight, other.onHighlight, t)!,
      feature: Color.lerp(feature, other.feature, t)!,
      onFeature: Color.lerp(onFeature, other.onFeature, t)!,
    );
  }
}

/// Dashboard look: rounded cards over a plain background, electric blue for actions and the
/// leading card, lime for what is selected. Dark is near black; light is a soft gray with white
/// cards. Color beyond that only where it means something.
abstract final class AppTheme {
  static const _blue = Color(0xFF3D5AFE);
  static const _lime = Color(0xFFD4F34A);
  static const _ink = Color(0xFF0B0B0C);
  static const _chart = [
    Color(0xFF3D5AFE),
    Color(0xFFD4F34A),
    Color(0xFFF472B6),
    Color(0xFF22D3EE),
    Color(0xFFFB923C),
    Color(0xFFA78BFA),
    Color(0xFF94A3B8),
  ];

  /// [translucent]: the Windows app's see-through window. The pages leave their background to the
  /// window, and cards let a little of it through, so the text stays sharp over the blur.
  static ThemeData dark({bool translucent = false}) => _build(
        translucent: translucent,
        ColorScheme.fromSeed(seedColor: _blue, brightness: Brightness.dark).copyWith(
          primary: _blue,
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFF1C2558),
          onPrimaryContainer: Colors.white,
          secondary: _lime,
          onSecondary: _ink,
          secondaryContainer: _lime,
          onSecondaryContainer: _ink,
          surface: _ink,
          surfaceDim: _ink,
          surfaceBright: const Color(0xFF2A2A2F),
          surfaceContainerLowest: _ink,
          surfaceContainerLow: const Color(0xFF111113),
          surfaceContainer: const Color(0xFF17171A),
          surfaceContainerHigh: const Color(0xFF202024),
          surfaceContainerHighest: const Color(0xFF2A2A2F),
          onSurface: const Color(0xFFF4F4F5),
          onSurfaceVariant: const Color(0xFFA1A1AA),
          outline: const Color(0xFF3F3F46),
          outlineVariant: const Color(0xFF26262B),
        ),
        const WalletColors(
          inflow: Color(0xFF34D399),
          warning: Color(0xFFFBBF24),
          chart: _chart,
          highlight: _lime,
          onHighlight: _ink,
          feature: _blue,
          onFeature: Colors.white,
        ),
      );

  static ThemeData light({bool translucent = false}) => _build(
        translucent: translucent,
        ColorScheme.fromSeed(seedColor: _blue).copyWith(
          primary: _blue,
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFE3E8FF),
          onPrimaryContainer: const Color(0xFF1A2A8C),
          secondary: _lime,
          onSecondary: _ink,
          secondaryContainer: _lime,
          onSecondaryContainer: _ink,
          surface: const Color(0xFFEEF0F4),
          surfaceDim: const Color(0xFFE2E5EA),
          surfaceBright: Colors.white,
          surfaceContainerLowest: Colors.white,
          surfaceContainerLow: const Color(0xFFF7F8FA),
          surfaceContainer: Colors.white,
          surfaceContainerHigh: const Color(0xFFF1F3F6),
          surfaceContainerHighest: const Color(0xFFE6E9EE),
          onSurface: const Color(0xFF0F1115),
          onSurfaceVariant: const Color(0xFF5F6672),
          outline: const Color(0xFFC9CED6),
          outlineVariant: const Color(0xFFE3E6EB),
        ),
        const WalletColors(
          inflow: Color(0xFF059669),
          warning: Color(0xFFD97706),
          chart: _chart,
          highlight: _lime,
          onHighlight: _ink,
          feature: _blue,
          onFeature: Colors.white,
        ),
      );

  static ThemeData _build(ColorScheme scheme, WalletColors colors, {required bool translucent}) {
    final base = ThemeData(colorScheme: scheme);
    final text = base.textTheme;
    final page = translucent ? Colors.transparent : scheme.surface;
    return base.copyWith(
      scaffoldBackgroundColor: page,
      extensions: [colors],
      textTheme: text.copyWith(
        displaySmall: text.displaySmall?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.5),
        headlineMedium: text.headlineMedium?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.3),
        headlineSmall: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
        titleLarge: text.titleLarge?.copyWith(fontWeight: FontWeight.w700),
        titleMedium: text.titleMedium?.copyWith(fontWeight: FontWeight.w600),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: page,
        surfaceTintColor: Colors.transparent,
        scrolledUnderElevation: 0,
        centerTitle: false,
        toolbarHeight: 72,
        titleTextStyle: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700, color: scheme.onSurface),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: translucent ? scheme.surfaceContainer.withValues(alpha: 0.78) : scheme.surfaceContainer,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: scheme.outlineVariant),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          shape: const StadiumBorder(),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(shape: const StadiumBorder())),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        shape: const StadiumBorder(),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainer,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: scheme.surfaceContainer,
        indicatorColor: colors.highlight,
        surfaceTintColor: Colors.transparent,
      ),
      chipTheme: ChipThemeData(shape: const StadiumBorder(), side: BorderSide(color: scheme.outlineVariant)),
      listTileTheme: const ListTileThemeData(contentPadding: EdgeInsets.symmetric(horizontal: 16)),
      dividerTheme: DividerThemeData(color: scheme.outlineVariant),
    );
  }
}

extension WalletTheme on BuildContext {
  WalletColors get walletColors => Theme.of(this).extension<WalletColors>()!;
}
