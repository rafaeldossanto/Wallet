import 'package:dio/dio.dart';
import 'package:dio/io.dart';

/// Android and iOS: Dio's own HTTP client.
HttpClientAdapter platformHttpAdapter() => IOHttpClientAdapter();
