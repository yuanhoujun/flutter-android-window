package qiuxiang.android_window

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.PluginRegistry

class MainApi(private val activity: Activity) : Pigeon.MainApi, PluginRegistry.ActivityResultListener {
  companion object {
    private const val overlayPermissionRequestCode = 42042
  }

  private var onActivityResultCallback: (() -> Unit)? = null

  override fun open(entry: String, width: Long, height: Long, x: Long, y: Long, focusable: Boolean) {
    val intent = Intent(activity, WindowService::class.java)
    intent.putExtra("entry", entry)
    intent.putExtra("width", width.toInt())
    intent.putExtra("height", height.toInt())
    intent.putExtra("x", x.toInt())
    intent.putExtra("y", y.toInt())
    intent.putExtra("focusable", focusable)
    if (canDrawOverlays()) {
      activity.startService(intent)
    } else {
      requestPermission {
        if (canDrawOverlays()) {
          activity.startService(intent)
        }
      }
    }
  }

  override fun close() {
    activity.stopService(Intent(activity, WindowService::class.java))
  }

  override fun canDrawOverlays(result: Pigeon.Result<Boolean>) {
    result.success(canDrawOverlays())
  }

  override fun requestPermission(result: Pigeon.VoidResult) {
    requestPermission { result.success() }
  }

  override fun isRunning(result: Pigeon.Result<Boolean>) {
    result.success(WindowService.isWindowRunning)
  }

  override fun post(message: MutableMap<Any, Any>, result: Pigeon.Result<MutableMap<Any, Any>>) {
    FlutterEngineCache.getInstance().get(engineId)?.dartExecutor?.binaryMessenger?.let {
      Pigeon.AndroidWindowHandler(it).handler(message,result)
    }
  }

  private fun canDrawOverlays(): Boolean {
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(activity)
    } else {
      true
    }
  }

  private fun requestPermission(callback: () -> Unit) {
    onActivityResultCallback = callback
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
      val isAndroid15Tablet =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
          activity.resources.configuration.smallestScreenWidthDp >= 600
      if (isAndroid15Tablet) {
        // On Android 15 large screens the app-specific Settings deep link can
        // immediately finish and return to the app when launched for a result.
        // A regular launch of the generic list remains open and lets the user
        // select this app manually.
        activity.startActivity(
          Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:")
          ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        completePermissionRequest()
      } else {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, overlayPermissionRequestCode)
      }
    } else {
      completePermissionRequest()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode != overlayPermissionRequestCode) return false
    completePermissionRequest()
    return true
  }

  private fun completePermissionRequest() {
    val callback = onActivityResultCallback
    onActivityResultCallback = null
    callback?.invoke()
  }
}
