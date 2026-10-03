import 'package:flutter/widgets.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';

import 'app.dart';
import 'core/format/dates.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  Intl.defaultLocale = Dates.locale;
  await initializeDateFormatting(Dates.locale);
  runApp(WalletApp(dependencies: AppDependencies.create()));
}
