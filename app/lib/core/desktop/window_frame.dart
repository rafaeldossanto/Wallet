import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import 'desktop.dart';
import 'desktop_settings.dart';

/// The Windows app's window, drawn in Flutter: rounded like a macOS window, with its red, yellow
/// and green buttons, and a background that is solid or lets the blurred desktop through
/// ([DesktopSettings.translucent]). The blur and the rounded edge of the window itself come from
/// windows/runner/window_style.cpp.
class DesktopWindowFrame extends StatefulWidget {
  const DesktopWindowFrame({super.key, required this.window, required this.child});

  /// Same as window_style.cpp's kCornerRadius.
  static const cornerRadius = 12.0;
  static const titleBarHeight = 40.0;

  final WindowControls window;
  final Widget child;

  @override
  State<DesktopWindowFrame> createState() => _DesktopWindowFrameState();
}

class _DesktopWindowFrameState extends State<DesktopWindowFrame> {
  (bool, bool)? _applied;

  @override
  Widget build(BuildContext context) {
    final translucent = context.watch<DesktopSettings?>()?.translucent ?? false;
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    _syncStyle(translucent: translucent, dark: dark);
    final scheme = theme.colorScheme;
    // See-through enough to make out what is behind, tinted enough for the text to stay sharp.
    final background = translucent ? scheme.surface.withValues(alpha: dark ? 0.62 : 0.55) : scheme.surface;
    return ValueListenableBuilder<bool>(
      valueListenable: widget.window.isMaximized,
      builder: (context, maximized, _) {
        final corners = BorderRadius.circular(maximized ? 0 : DesktopWindowFrame.cornerRadius);
        final frame = DecoratedBox(
          position: DecorationPosition.foreground,
          decoration: BoxDecoration(
            borderRadius: corners,
            border: maximized ? null : Border.all(color: scheme.outlineVariant),
          ),
          child: ClipRRect(
            borderRadius: corners,
            child: ColoredBox(
              color: background,
              child: Column(
                children: [
                  _TitleBar(window: widget.window),
                  Expanded(child: widget.child),
                ],
              ),
            ),
          ),
        );
        return maximized ? frame : _ResizeEdges(window: widget.window, child: frame);
      },
    );
  }

  /// The blur and the system parts follow the setting and the theme, once per change.
  void _syncStyle({required bool translucent, required bool dark}) {
    if (_applied == (translucent, dark)) {
      return;
    }
    _applied = (translucent, dark);
    WidgetsBinding.instance.addPostFrameCallback((_) => widget.window.applyStyle(translucent: translucent, dark: dark));
  }
}

/// Drags the window, maximizes on a double click, and holds the three buttons on the left, as on
/// a Mac.
class _TitleBar extends StatelessWidget {
  const _TitleBar({required this.window});

  final WindowControls window;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: DesktopWindowFrame.titleBarHeight,
      child: Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onPanStart: (_) => window.startDragging(),
              onDoubleTap: window.toggleMaximize,
              child: Center(
                child: Text(
                  'Wallet',
                  style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
            ),
          ),
          Positioned(left: 16, top: 0, bottom: 0, child: _TrafficLights(window: window)),
        ],
      ),
    );
  }
}

/// Close, minimize and maximize as macOS draws them; the symbols show while the mouse is over them.
class _TrafficLights extends StatefulWidget {
  const _TrafficLights({required this.window});

  final WindowControls window;

  @override
  State<_TrafficLights> createState() => _TrafficLightsState();
}

class _TrafficLightsState extends State<_TrafficLights> {
  bool _hovering = false;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final window = widget.window;
    return MouseRegion(
      onEnter: (_) => setState(() => _hovering = true),
      onExit: (_) => setState(() => _hovering = false),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 8,
        children: [
          _Light(
            label: l10n.windowClose,
            color: const Color(0xFFFF5F57),
            edge: const Color(0xFFE0443E),
            icon: Icons.close,
            showIcon: _hovering,
            onTap: window.close,
          ),
          _Light(
            label: l10n.windowMinimize,
            color: const Color(0xFFFEBC2E),
            edge: const Color(0xFFDEA123),
            icon: Icons.remove,
            showIcon: _hovering,
            onTap: window.minimize,
          ),
          _Light(
            label: l10n.windowMaximize,
            color: const Color(0xFF28C840),
            edge: const Color(0xFF1AAB29),
            icon: Icons.open_in_full,
            showIcon: _hovering,
            onTap: window.toggleMaximize,
          ),
        ],
      ),
    );
  }
}

class _Light extends StatelessWidget {
  const _Light({
    required this.label,
    required this.color,
    required this.edge,
    required this.icon,
    required this.showIcon,
    required this.onTap,
  });

  static const _size = 13.0;

  final String label;
  final Color color;
  final Color edge;
  final IconData icon;
  final bool showIcon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
        button: true,
        label: label,
        child: GestureDetector(
          onTap: onTap,
          child: Container(
            width: _size,
            height: _size,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle, border: Border.all(color: edge, width: 0.5)),
            child: showIcon ? Icon(icon, size: 9, color: const Color(0x99000000)) : null,
          ),
        ),
      );
}

/// The window's edges and corners resize it, as a framed window's would.
class _ResizeEdges extends StatelessWidget {
  const _ResizeEdges({required this.window, required this.child});

  static const _edge = 6.0;
  static const _corner = 14.0;

  final WindowControls window;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    Widget handle(WindowEdge edge, MouseCursor cursor) => MouseRegion(
          cursor: cursor,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onPanStart: (_) => window.startResizing(edge),
          ),
        );
    return Stack(
      children: [
        Positioned.fill(child: child),
        Positioned(
          left: _corner,
          right: _corner,
          top: 0,
          height: _edge,
          child: handle(WindowEdge.top, SystemMouseCursors.resizeUpDown),
        ),
        Positioned(
          left: _corner,
          right: _corner,
          bottom: 0,
          height: _edge,
          child: handle(WindowEdge.bottom, SystemMouseCursors.resizeUpDown),
        ),
        Positioned(
          top: _corner,
          bottom: _corner,
          left: 0,
          width: _edge,
          child: handle(WindowEdge.left, SystemMouseCursors.resizeLeftRight),
        ),
        Positioned(
          top: _corner,
          bottom: _corner,
          right: 0,
          width: _edge,
          child: handle(WindowEdge.right, SystemMouseCursors.resizeLeftRight),
        ),
        Positioned(
          left: 0,
          top: 0,
          width: _corner,
          height: _corner,
          child: handle(WindowEdge.topLeft, SystemMouseCursors.resizeUpLeftDownRight),
        ),
        Positioned(
          right: 0,
          top: 0,
          width: _corner,
          height: _corner,
          child: handle(WindowEdge.topRight, SystemMouseCursors.resizeUpRightDownLeft),
        ),
        Positioned(
          left: 0,
          bottom: 0,
          width: _corner,
          height: _corner,
          child: handle(WindowEdge.bottomLeft, SystemMouseCursors.resizeUpRightDownLeft),
        ),
        Positioned(
          right: 0,
          bottom: 0,
          width: _corner,
          height: _corner,
          child: handle(WindowEdge.bottomRight, SystemMouseCursors.resizeUpLeftDownRight),
        ),
      ],
    );
  }
}
