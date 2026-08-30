package qiuxiang.android_window

import android.app.Activity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger

class AndroidWindowPlugin : FlutterPlugin, ActivityAware {
  private lateinit var pluginBinding: FlutterPluginBinding
  private var activityBinding: ActivityPluginBinding? = null
  private var mainApi: MainApi? = null

  companion object {
    var messenger: BinaryMessenger? = null
    var activityClass: Class<Activity>? = null
  }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
    Pigeon.MainApi.setUp(binding.binaryMessenger, null)
    clearMainEngineMessenger(binding.binaryMessenger)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    detachFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    attachToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    detachFromActivity()
  }

  override fun onAttachedToEngine(binding: FlutterPluginBinding) {
    pluginBinding = binding
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    attachToActivity(binding)
  }

  private fun attachToActivity(binding: ActivityPluginBinding) {
    val api = MainApi(binding.activity)
    activityBinding = binding
    mainApi = api
    binding.addActivityResultListener(api)
    Pigeon.MainApi.setUp(pluginBinding.binaryMessenger, api)
    messenger = pluginBinding.binaryMessenger
    activityClass = binding.activity.javaClass
  }

  private fun detachFromActivity() {
    mainApi?.let { activityBinding?.removeActivityResultListener(it) }
    mainApi = null
    activityBinding = null
    Pigeon.MainApi.setUp(pluginBinding.binaryMessenger, null)
    clearMainEngineMessenger(pluginBinding.binaryMessenger)
  }

  private fun clearMainEngineMessenger(detachedMessenger: BinaryMessenger) {
    if (messenger === detachedMessenger) {
      messenger = null
    }
  }
}
