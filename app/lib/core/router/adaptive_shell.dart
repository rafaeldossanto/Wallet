import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../session/session_controller.dart';
import '../theme/app_theme.dart';
import '../widgets/theme_toggle.dart';

/// The same screens on every device; only the navigation around them changes: a bottom bar on
/// phones, a floating rail of round buttons from tablets up.
class AdaptiveShell extends StatelessWidget {
  const AdaptiveShell({super.key, required this.navigationShell});

  static const mediumWidth = 600.0;
  static const expandedWidth = 840.0;

  /// On phones the bar has room for four destinations plus "Mais", which holds the rest.
  static const phonePrimaryCount = 4;

  static const settingsIndex = 6;

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final destinations = destinationsOf(context.l10n);
    if (MediaQuery.sizeOf(context).width >= mediumWidth) {
      return Scaffold(
        body: SafeArea(
          child: Row(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 8, 16),
                child: WalletRail(
                  destinations: destinations,
                  selectedIndex: navigationShell.currentIndex,
                  onSelected: _go,
                ),
              ),
              Expanded(child: navigationShell),
            ],
          ),
        ),
      );
    }
    return _CompactShell(navigationShell: navigationShell, destinations: destinations, onSelected: _go);
  }

  void _go(int index) => navigationShell.goBranch(index, initialLocation: index == navigationShell.currentIndex);

  static List<ShellDestination> destinationsOf(AppLocalizations l10n) => [
        ShellDestination(l10n.navOverview, Icons.home_outlined, Icons.home),
        ShellDestination(l10n.navTransactions, Icons.receipt_long_outlined, Icons.receipt_long),
        ShellDestination(l10n.navCards, Icons.credit_card_outlined, Icons.credit_card),
        ShellDestination(l10n.navInvestments, Icons.savings_outlined, Icons.savings, shortLabel: l10n.navInvestmentsShort),
        ShellDestination(l10n.navInsights, Icons.pie_chart_outline, Icons.pie_chart),
        ShellDestination(l10n.navConnections, Icons.account_balance_outlined, Icons.account_balance),
        ShellDestination(l10n.navSettings, Icons.settings_outlined, Icons.settings),
      ];
}

class ShellDestination {
  const ShellDestination(this.label, this.icon, this.selectedIcon, {String? shortLabel}) : shortLabel = shortLabel ?? label;

  final String label;
  final IconData icon;
  final IconData selectedIcon;

  /// For the phone's bottom bar, where each item has under 80 px.
  final String shortLabel;
}

/// A rounded column of round buttons: the app's mark, the destinations, and at the bottom the
/// theme switch and the user's initials (the way to the settings).
class WalletRail extends StatelessWidget {
  const WalletRail({super.key, required this.destinations, required this.selectedIndex, required this.onSelected});

  final List<ShellDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final colors = context.walletColors;
    final initials = context.select<SessionController, String?>((session) => session.user?.initials);
    return Container(
      width: 72,
      decoration: BoxDecoration(
        // The cards' color, see-through with them in the Windows app's translucent window.
        color: Theme.of(context).cardTheme.color ?? scheme.surfaceContainer,
        borderRadius: BorderRadius.circular(36),
        border: Border.all(color: scheme.outlineVariant),
      ),
      // Scrolls when seven buttons do not fit the height (a phone held sideways).
      child: LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: IntrinsicHeight(
              child: Column(
                children: [
                  const SizedBox(height: 16),
                  Container(
                    width: 44,
                    height: 44,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: scheme.onSurface, borderRadius: BorderRadius.circular(14)),
                    child: Text('W',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(color: scheme.surface, fontWeight: FontWeight.w800)),
                  ),
                  const SizedBox(height: 24),
                  for (var index = 0; index < destinations.length - 1; index++)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: RailButton(
                        icon: index == selectedIndex ? destinations[index].selectedIcon : destinations[index].icon,
                        label: destinations[index].label,
                        selected: index == selectedIndex,
                        onPressed: () => onSelected(index),
                      ),
                    ),
                  const Spacer(),
                  const SizedBox(height: 16),
                  const ThemeToggle(),
                  const SizedBox(height: 8),
                  Tooltip(
                    message: destinations.last.label,
                    child: Semantics(
                      button: true,
                      selected: selectedIndex == AdaptiveShell.settingsIndex,
                      label: destinations.last.label,
                      child: InkWell(
                        customBorder: const CircleBorder(),
                        onTap: () => onSelected(AdaptiveShell.settingsIndex),
                        child: CircleAvatar(
                          radius: 22,
                          backgroundColor: selectedIndex == AdaptiveShell.settingsIndex ? colors.highlight : scheme.primaryContainer,
                          foregroundColor:
                              selectedIndex == AdaptiveShell.settingsIndex ? colors.onHighlight : scheme.onPrimaryContainer,
                          child: initials == null
                              ? const Icon(Icons.person_outline, size: 20)
                              : Text(initials, style: const TextStyle(fontWeight: FontWeight.w700)),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A destination as a round button; lime when selected.
class RailButton extends StatelessWidget {
  const RailButton({super.key, required this.icon, required this.label, required this.selected, required this.onPressed});

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final colors = context.walletColors;
    return Tooltip(
      message: label,
      child: Semantics(
        button: true,
        selected: selected,
        label: label,
        excludeSemantics: true,
        child: Material(
          color: selected ? colors.highlight : Colors.transparent,
          shape: const CircleBorder(),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onPressed,
            child: SizedBox.square(
              dimension: 48,
              child: Icon(icon, color: selected ? colors.onHighlight : scheme.onSurfaceVariant),
            ),
          ),
        ),
      ),
    );
  }
}

class _CompactShell extends StatelessWidget {
  const _CompactShell({required this.navigationShell, required this.destinations, required this.onSelected});

  final StatefulNavigationShell navigationShell;
  final List<ShellDestination> destinations;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    const primary = AdaptiveShell.phonePrimaryCount;
    final index = navigationShell.currentIndex;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: index < primary ? index : primary,
        onDestinationSelected: (selected) => selected < primary ? onSelected(selected) : _showMore(context),
        destinations: [
          for (final destination in destinations.take(primary))
            NavigationDestination(
                icon: Icon(destination.icon),
                selectedIcon: Icon(destination.selectedIcon),
                label: destination.shortLabel,
                tooltip: destination.label),
          NavigationDestination(icon: const Icon(Icons.menu), label: context.l10n.navMore),
        ],
      ),
    );
  }

  void _showMore(BuildContext context) {
    final current = navigationShell.currentIndex;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var index = AdaptiveShell.phonePrimaryCount; index < destinations.length; index++)
              ListTile(
                leading: Icon(index == current ? destinations[index].selectedIcon : destinations[index].icon),
                title: Text(destinations[index].label),
                selected: index == current,
                onTap: () {
                  Navigator.of(sheetContext).pop();
                  onSelected(index);
                },
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
