import '../../../core/state/loadable.dart';
import '../data/home_api.dart';

class OverviewController extends LoadController<HomeData> {
  OverviewController(this._api, {super.dataChanges});

  final HomeApi _api;

  @override
  Future<HomeData> fetch() => _api.home();
}
