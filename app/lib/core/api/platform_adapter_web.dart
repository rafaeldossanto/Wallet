import 'package:dio/browser.dart';
import 'package:dio/dio.dart';

/// The browser keeps the refresh token in an HttpOnly cookie that only goes along when the call
/// is made with credentials.
HttpClientAdapter platformHttpAdapter() => BrowserHttpClientAdapter(withCredentials: true);
