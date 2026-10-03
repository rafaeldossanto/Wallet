import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../l10n/l10n.dart';

/// The same screens on every device; only the navigation around them changes with the width.
class AdaptiveShell extends StatelessWidget {
  const AdaptiveShell({super.key, required this.navigationShell});

  static const mediumWidth = 600.0;
  static const expandedWidth = 840.0;

  /// On phones the bar has room for four destinations plus "Mais", which holds the rest.
  static const phonePrimaryCount = 4;

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final destinations = _destinations(context.l10n);
    if (width >= expandedWidth) {
      return _ExpandedShell(navigationShell: navigationShell, destinations: destinations, onSelected: _go);
    }
    if (width >= mediumWidth) {
      return _MediumShell(navigationShell: navigationShell, destinations: destinations, onSelected: _go);
    }
    return _CompactShell(navigationShell: navigationShell, destinations: destinations, onSelected: _go);
  }

  void _go(int index) => navigationShell.goBranch(index, initialLocation: index == navigationShell.currentIndex);

  static List<ShellDestination> _destinations(AppLocalizations l10n) => [
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

class _MediumShell extends StatelessWidget {
  const _MediumShell({required this.navigationShell, required this.destinations, required this.onSelected});

  final StatefulNavigationShell navigationShell;
  final List<ShellDestination> destinations;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Row(
            children: [
              // Scrolls on a phone held sideways, where seven destinations do not fit the height.
              LayoutBuilder(
                builder: (context, constraints) => SingleChildScrollView(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(minHeight: constraints.maxHeight),
                    child: IntrinsicHeight(
                      child: NavigationRail(
                        selectedIndex: navigationShell.currentIndex,
                        onDestinationSelected: onSelected,
                        labelType: NavigationRailLabelType.all,
                        destinations: [
                          for (final destination in destinations)
                            NavigationRailDestination(
                              icon: Icon(destination.icon),
                              selectedIcon: Icon(destination.selectedIcon),
                              label: Text(destination.label),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              const VerticalDivider(width: 1),
              Expanded(child: navigationShell),
            ],
          ),
        ),
      );
}

class _ExpandedShell extends StatelessWidget {
  const _ExpandedShell({required this.navigationShell, required this.destinations, required this.onSelected});

  final StatefulNavigationShell navigationShell;
  final List<ShellDestination> destinations;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Row(
          children: [
            SizedBox(
              width: 260,
              child: NavigationDrawer(
                selectedIndex: navigationShell.currentIndex,
                onDestinationSelected: onSelected,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(28, 24, 16, 16),
                    child: Text(context.l10n.appTitle, style: Theme.of(context).textTheme.headlineSmall),
                  ),
                  for (final destination in destinations)
                    NavigationDrawerDestination(
                      icon: Icon(destination.icon),
                      selectedIcon: Icon(destination.selectedIcon),
                      label: Text(destination.label),
                    ),
                ],
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(child: navigationShell),
          ],
        ),
      );
}
