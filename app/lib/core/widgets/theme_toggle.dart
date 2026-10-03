import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../theme/theme_controller.dart';

/// Sun in the dark, moon in the light: flips the theme on screen now.
class ThemeToggle extends StatelessWidget {
  const ThemeToggle({super.key});

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final l10n = context.l10n;
    return IconButton(
      tooltip: dark ? l10n.themeToLight : l10n.themeToDark,
      onPressed: () => context.read<ThemeController>().toggle(Theme.of(context).brightness),
      icon: Icon(dark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
    );
  }
}
