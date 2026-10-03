import 'package:flutter_svg/flutter_svg.dart';
import 'package:material_ui/material_ui.dart';

import '../format/institution_colors.dart';
import '../l10n/l10n.dart';
import '../money/money.dart';
import '../theme/app_theme.dart';
import 'institution_logo.dart';

/// Keeps content readable on a wide PC window.
class ContentWidth extends StatelessWidget {
  const ContentWidth({super.key, required this.child, this.maxWidth = 1100});

  final Widget child;
  final double maxWidth;

  /// Full width up to [maxWidth], centered beyond it.
  @override
  Widget build(BuildContext context) => Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: SizedBox(width: double.infinity, child: child),
        ),
      );
}

/// A titled block of a screen.
class SectionCard extends StatelessWidget {
  const SectionCard({super.key, required this.title, required this.child, this.trailing, this.flush = false});

  final String title;
  final Widget child;
  final Widget? trailing;

  /// The child runs edge to edge: list tiles bring their own padding.
  final bool flush;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: EdgeInsets.only(top: 16, bottom: flush ? 8 : 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 40),
                  child: Row(
                    children: [
                      Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
                      ?trailing,
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Padding(padding: EdgeInsets.symmetric(horizontal: flush ? 0 : 16), child: child),
            ],
          ),
        ),
      );
}

/// A block that did not load while the rest of the screen did.
class UnavailableNotice extends StatelessWidget {
  const UnavailableNotice({super.key});

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(Icons.info_outline, size: 18, color: context.walletColors.warning),
          const SizedBox(width: 8),
          Expanded(child: Text(context.l10n.partUnavailable)),
        ],
      );
}

/// An amount; inflows in green with a plus sign, outflows in the normal color with a minus.
class MoneyText extends StatelessWidget {
  const MoneyText(this.money, {super.key, this.style, this.inflow, this.textAlign});

  final Money money;
  final TextStyle? style;

  /// Null: a balance, shown as it is. True or false: a movement, signed and colored.
  final bool? inflow;
  final TextAlign? textAlign;

  @override
  Widget build(BuildContext context) {
    final base = style ?? DefaultTextStyle.of(context).style;
    final movement = inflow;
    if (movement == null) {
      return Text(money.format(), style: base, textAlign: textAlign);
    }
    final text = movement ? '+${money.abs().format()}' : '-${money.abs().format()}';
    return Text(text,
        style: movement ? base.copyWith(color: context.walletColors.inflow) : base, textAlign: textAlign);
  }
}

/// The institution's logo, or its initials in the brand's colours while it loads, when there is
/// none, or when it does not load.
class InstitutionAvatar extends StatelessWidget {
  const InstitutionAvatar({super.key, this.name, this.imageUrl, this.radius = 20});

  final String? name;
  final String? imageUrl;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final colors = InstitutionColors.of(name);
    final initials = (name ?? '?')
        .split(RegExp(r'\s+'))
        .where((word) => word.isNotEmpty)
        .take(2)
        .map((word) => word[0].toUpperCase())
        .join();
    final url = imageUrl;
    final isSvg = url != null && Uri.tryParse(url)?.path.toLowerCase().endsWith('.svg') == true;
    final avatar = CircleAvatar(
      radius: radius,
      backgroundColor: colors.background,
      foregroundColor: colors.foreground,
      foregroundImage: url == null || isSvg ? null : NetworkImage(url),
      child: Text(initials, style: TextStyle(fontSize: radius * 0.7, fontWeight: FontWeight.w700)),
    );
    if (!isSvg) {
      return avatar;
    }
    // Pluggy draws its logos for a disc: a transparent circle with the mark in the middle, so a
    // white disc behind keeps every mark legible in both themes.
    return ClipOval(
      child: SizedBox.square(
        dimension: radius * 2,
        child: ColoredBox(
          color: Colors.white,
          child: SvgPicture(
            InstitutionLogoLoader(url),
            semanticsLabel: name,
            placeholderBuilder: (context) => avatar,
            errorBuilder: (context, error, stackTrace) => avatar,
          ),
        ),
      ),
    );
  }
}

/// `‹ Outubro de 2026 ›`; the next arrow stops at the current month.
class MonthSelector extends StatelessWidget {
  const MonthSelector({super.key, required this.month, required this.label, required this.onChanged, this.latest});

  final DateTime month;
  final String label;
  final DateTime? latest;
  final ValueChanged<DateTime> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final last = latest;
    final canGoForward = last == null || DateTime(month.year, month.month + 1).isBefore(DateTime(last.year, last.month + 1));
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: l10n.monthPrevious,
          onPressed: () => onChanged(DateTime(month.year, month.month - 1)),
          icon: const Icon(Icons.chevron_left),
        ),
        ConstrainedBox(
          constraints: const BoxConstraints(minWidth: 160),
          child: Text(label, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleSmall),
        ),
        IconButton(
          tooltip: l10n.monthNext,
          onPressed: canGoForward ? () => onChanged(DateTime(month.year, month.month + 1)) : null,
          icon: const Icon(Icons.chevron_right),
        ),
      ],
    );
  }
}

/// A small figure in its own rounded tile, with a colored icon: the dashboard's summaries.
class StatTile extends StatelessWidget {
  const StatTile({super.key, required this.icon, required this.color, required this.label, required this.value});

  final IconData icon;
  final Color color;
  final String label;
  final Widget value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(color: color.withValues(alpha: 0.18), shape: BoxShape.circle),
            child: Icon(icon, size: 18, color: color),
          ),
          const SizedBox(width: 12),
          Expanded(child: LabeledValue(label: label, value: value)),
        ],
      ),
    );
  }
}

/// Tiles side by side when there is room, stacked on a phone.
class StatTileRow extends StatelessWidget {
  const StatTileRow({super.key, required this.children});

  static const _sideBySideWidth = 560.0;

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < _sideBySideWidth) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var index = 0; index < children.length; index++) ...[
                  if (index > 0) const SizedBox(height: 8),
                  children[index],
                ],
              ],
            );
          }
          return Row(
            children: [
              for (var index = 0; index < children.length; index++) ...[
                if (index > 0) const SizedBox(width: 12),
                Expanded(child: children[index]),
              ],
            ],
          );
        },
      );
}

/// Label above, value below; the small summaries on top of the screens.
class LabeledValue extends StatelessWidget {
  const LabeledValue({super.key, required this.label, required this.value, this.crossAxisAlignment});

  final String label;
  final Widget value;
  final CrossAxisAlignment? crossAxisAlignment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: crossAxisAlignment ?? CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label, style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        const SizedBox(height: 4),
        DefaultTextStyle.merge(style: theme.textTheme.titleMedium, child: value),
      ],
    );
  }
}
