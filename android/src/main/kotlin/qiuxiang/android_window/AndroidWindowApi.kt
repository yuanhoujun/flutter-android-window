package qiuxiang.android_window

import android.content.Intent

class AndroidWindowApi(private val window: AndroidWindow) : Pigeon.AndroidWindowApi {
  override fun resize(width: Long, height: Long) {
    window.setLayout(width.toInt(), height.toInt())
  }

  override fun setPosition(x: Long, y: Long) {
    window.setPosition(x.toInt(), y.toInt())
  }

  override fun post(message: MutableMap<Any, Any>, result: Pigeon.Result<MutableMap<Any, Any>>) {
    val messenger = AndroidWindowPlugin.messenger
    if (messenger == null) {
      // The activity Flutter engine may have been destroyed while this
      // foreground overlay service (and its own engine) is still alive.
      result.success(mutableMapOf())
      return
    }

    try {
      Pigeon.MainHandler(messenger).handler(
        message,
        object : Pigeon.Result<MutableMap<Any, Any>> {
          override fun success(value: MutableMap<Any, Any>) {
            result.success(value)
          }

          override fun error(error: Throwable) {
            if (error is Pigeon.FlutterError && error.code == "channel-error") {
              // A detach can race with an overlay action. Treat the message as
              // best-effort instead of surfacing an exception in the overlay.
              result.success(mutableMapOf())
            } else {
              result.error(error)
            }
          }
        }
      )
    } catch (_: IllegalStateException) {
      // BinaryMessenger can become unusable between the null check and send.
      result.success(mutableMapOf())
    }
  }

  override fun dragStart() {
    window.dragStart()
  }

  override fun dragEnd() {
    window.dragEnd()
  }

  override fun close() {
    window.service.stopSelf()
  }

  override fun launchApp() {
    val intent = Intent(window.service, AndroidWindowPlugin.activityClass)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    window.service.startActivity(intent)
  }
}
