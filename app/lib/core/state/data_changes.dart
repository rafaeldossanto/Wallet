import 'package:flutter/foundation.dart';

/// Rings when the user's financial data changed: a connection was linked or removed, or a sync
/// finished. Screens kept alive in other tabs refresh themselves when it rings.
class DataChanges extends ChangeNotifier {
  void changed() => notifyListeners();
}
