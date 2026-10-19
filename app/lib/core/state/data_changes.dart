import 'package:flutter/foundation.dart';

/// Rings when the user's financial data changed: a connection was linked or removed, a sync
/// finished, or the month's plan was adjusted. Screens kept alive in other tabs refresh themselves
/// when it rings.
class DataChanges extends ChangeNotifier {
  void changed() => notifyListeners();
}
