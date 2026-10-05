import 'package:flutter/widgets.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';

import 'app.dart';
import 'core/desktop/desktop.dart';
import 'core/format/dates.dart';
import 'core/theme/theme_controller.dart';

/// [args] reach the Windows app from its shortcut or from Windows itself (`--hidden` at sign-in).
Future<void> main(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  final desktop = await startDesktop(args);
  Intl.defaultLocale = Dates.locale;
  await initializeDateFormatting(Dates.locale);
  final theme = await ThemeController.load(SharedThemePreference());
  runApp(WalletApp(dependencies: AppDependencies.create(theme: theme, desktop: desktop)));
}
