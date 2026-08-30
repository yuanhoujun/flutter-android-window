import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'pigeon.g.dart';

final _api = AndroidWindowApi();

/// Android window widget.
class AndroidWindow extends StatefulWidget {
  static const _displayMetricsChannel = MethodChannel(
    'android_window/display_metrics',
  );

  final Widget child;
  final bool dragEnabled;
  final VoidCallback? onDragStart;
  final VoidCallback? onDragEnd;

  const AndroidWindow({
    required this.child,
    this.dragEnabled = true,
    this.onDragStart,
    this.onDragEnd,
    Key? key,
  }) : super(key: key);

  @override
  State<AndroidWindow> createState() => _AndroidWindowState();

  /// Resize android window.
  static void resize(int width, int height) {
    _api.resize(width, height);
  }

  /// Returns the current physical display size reported by Android's window
  /// manager. This remains accurate when an overlay engine survives rotation.
  static Future<Size?> getDisplayPhysicalSize() async {
    final metrics = await _displayMetricsChannel.invokeMapMethod<String, int>(
      'getDisplayPhysicalSize',
    );
    final width = metrics?['width'];
    final height = metrics?['height'];
    if (width == null || height == null) return null;
    return Size(width.toDouble(), height.toDouble());
  }

  /// Set position of window.
  static void setPosition(int x, int y) {
    _api.setPosition(x, y);
  }

  /// Send message to window.
  static Future<Object?> post(String name, [Object? data]) async {
    final response = await _api.post({'name': name, 'data': data});
    if (response.isEmpty) return null;
    return response['data'];
  }

  /// Close android window.
  static void close() {
    _api.close();
  }

  /// Launch main app.
  static void launchApp() {
    _api.launchApp();
  }

  /// Set message handler.
  ///
  /// Receive message from main app.
  static void setHandler(
    Future<Object?> Function(String name, Object? data) handler,
  ) {
    AndroidWindowHandler.setUp(_Handler(handler));
  }
}

class _AndroidWindowState extends State<AndroidWindow> {
  bool start = false;
  bool dragging = false;

  @override
  Widget build(BuildContext context) {
    return RawGestureDetector(
      gestures: {
        PanGestureRecognizer:
            GestureRecognizerFactoryWithHandlers<PanGestureRecognizer>(
          () => PanGestureRecognizer(),
          (instance) {
            instance
              ..onStart = (event) {
                start = true;
              }
              ..onUpdate = (event) {
                if (start && widget.dragEnabled) {
                  _api.dragStart();
                  start = false;
                  dragging = true;
                  widget.onDragStart?.call();
                }
              }
              ..onEnd = (event) {
                if (dragging) {
                  _api.dragEnd();
                  widget.onDragEnd?.call();
                }
                dragging = false;
                start = false;
              };
          },
        ),
      },
      child: widget.child,
    );
  }
}

class _Handler extends AndroidWindowHandler {
  final Future<Object?> Function(String name, Object? data) _handler;

  _Handler(this._handler);

  @override
  Future<Map> handler(Map message) async {
    final name = message['name'];
    final data = message['data'];
    switch (message['name']) {
      case 'resize':
        AndroidWindow.resize(data['width'], data['height']);
        return {};
      case 'setPosition':
        AndroidWindow.setPosition(data['x'], data['y']);
        return {};
      default:
        return {'data': await _handler(name, data)};
    }
  }
}
