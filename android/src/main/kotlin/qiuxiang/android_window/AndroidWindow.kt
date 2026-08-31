package qiuxiang.android_window

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.*
import android.widget.LinearLayout
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidWindow(
  val service: Service,
  private val focusable: Boolean,
  width: Int,
  height: Int,
  private val x: Int,
  private val y: Int,
  private val engine: FlutterEngine,
  private val excludeFromCapture: Boolean = false,
) {
  private var startX = 0f
  private var startY = 0f
  private var initialX = 0
  private var initialY = 0
  private var dragging = false
  private var keepHorizontallyCentered = x < 0
  private var flutterView: FlutterView? = null
  private var isOpen = false
  private var windowManager = service.getSystemService(Service.WINDOW_SERVICE) as WindowManager
  private val inflater = service.getSystemService(Service.LAYOUT_INFLATER_SERVICE) as LayoutInflater
  private val metrics = DisplayMetrics()
  private val displayMetricsChannel = MethodChannel(
    engine.dartExecutor.binaryMessenger,
    "android_window/display_metrics"
  )

  @SuppressLint("InflateParams")
  private var rootView = inflater.inflate(R.layout.floating, null) as ViewGroup
  private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View) {
      WindowService.updateWindowRunning(true)
    }

    override fun onViewDetachedFromWindow(v: View) {
      WindowService.updateWindowRunning(false)
    }
  }
  private val layoutParams = WindowManager.LayoutParams(
    width, height, if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("Deprecation") WindowManager.LayoutParams.TYPE_TOAST
    },
    (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
      (if (excludeFromCapture) WindowManager.LayoutParams.FLAG_SECURE else 0),
    PixelFormat.TRANSLUCENT
  )

  fun open() {
    if (isOpen) {
      close()
    }

    engine.platformViewsController.attach(inflater.context, engine.renderer, engine.dartExecutor)
    val floatingApi = AndroidWindowApi(this)
    Pigeon.AndroidWindowApi.setUp(engine.dartExecutor.binaryMessenger, floatingApi)
    layoutParams.gravity = Gravity.START or Gravity.TOP
    @Suppress("Deprecation")
    windowManager.defaultDisplay.getRealMetrics(metrics)
    layoutParams.x = if (x < 0) max(0, (metrics.widthPixels - layoutParams.width) / 2) else x
    layoutParams.y = y
    rootView.removeOnAttachStateChangeListener(attachStateChangeListener)
    rootView.addOnAttachStateChangeListener(attachStateChangeListener)
    windowManager.addView(rootView, layoutParams)
    @Suppress("Deprecation") windowManager.defaultDisplay.getRealMetrics(metrics)
    publishCaptureExclusion()
    displayMetricsChannel.setMethodCallHandler { call, result ->
      if (call.method != "getDisplayPhysicalSize") {
        result.notImplemented()
        return@setMethodCallHandler
      }
      @Suppress("Deprecation")
      windowManager.defaultDisplay.getRealMetrics(metrics)
      result.success(
        mapOf(
          "width" to metrics.widthPixels,
          "height" to metrics.heightPixels
        )
      )
    }
    val nextFlutterView = FlutterView(inflater.context, FlutterTextureView(inflater.context))
    flutterView = nextFlutterView
    nextFlutterView.attachToFlutterEngine(engine)
    @Suppress("ClickableViewAccessibility") nextFlutterView.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_MOVE -> {
          if (dragging) {
            setPosition(
              initialX + (event.rawX - startX).roundToInt(), initialY + (event.rawY - startY).roundToInt()
            )
          } else {
            startX = event.rawX
            startY = event.rawY
            initialX = layoutParams.x
            initialY = layoutParams.y
          }
        }

        MotionEvent.ACTION_DOWN -> {
          if (focusable) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(rootView, layoutParams)
          }
        }
      }
      false
    }
    @Suppress("ClickableViewAccessibility") rootView.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
          windowManager.updateViewLayout(rootView, layoutParams)
          true
        }

        else -> false
      }
    }
    engine.lifecycleChannel.appIsResumed()
    rootView.findViewById<LinearLayout>(R.id.floating_window).addView(
        nextFlutterView, ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
      )
    isOpen = true
    if (rootView.isAttachedToWindow) {
      WindowService.updateWindowRunning(true)
    }
  }

  fun dragStart() {
    dragging = true
  }

  fun dragEnd() {
    dragging = false
  }

  fun close() {
    if (!isOpen) {
      return
    }

    flutterView?.detachFromFlutterEngine()
    flutterView = null
    rootView.findViewById<LinearLayout>(R.id.floating_window).removeAllViews()
    try {
      windowManager.removeView(rootView)
    } catch (_: IllegalArgumentException) {
    }
    rootView.removeOnAttachStateChangeListener(attachStateChangeListener)
    displayMetricsChannel.setMethodCallHandler(null)
    isOpen = false
    WindowService.updateWindowRunning(false)
    if (excludeFromCapture) {
      CaptureExclusionStore.clear(service.applicationContext)
    }
  }

  fun isRunning(): Boolean {
    return isOpen && rootView.isAttachedToWindow
  }

  fun updateLayout() {
    if (!isOpen) {
      return
    }

    @Suppress("Deprecation") windowManager.defaultDisplay.getRealMetrics(metrics)
    val nextX = if (keepHorizontallyCentered) {
      max(0, (metrics.widthPixels - layoutParams.width) / 2)
    } else {
      layoutParams.x
    }
    applyPosition(nextX, layoutParams.y)
  }

  fun setLayout(width: Int, height: Int) {
    if (!isOpen) {
      return
    }

    layoutParams.width = width
    layoutParams.height = height
    if (keepHorizontallyCentered) {
      @Suppress("Deprecation")
      windowManager.defaultDisplay.getRealMetrics(metrics)
      layoutParams.x = max(0, (metrics.widthPixels - layoutParams.width) / 2)
      windowManager.updateViewLayout(rootView, layoutParams)
      publishCaptureExclusion()
      return
    }
    // A smaller window may make the previous bottom/right position invalid.
    // Re-applying the position keeps the entire overlay on screen.
    setPosition(layoutParams.x, layoutParams.y)
  }

  fun setPosition(x: Int, y: Int) {
    if (!isOpen) {
      return
    }

    keepHorizontallyCentered = false
    applyPosition(x, y)
  }

  private fun applyPosition(x: Int, y: Int) {
    val maximumX = max(0, metrics.widthPixels - layoutParams.width)
    val maximumY = max(0, metrics.heightPixels - layoutParams.height)
    layoutParams.x = min(max(0, x), maximumX)
    layoutParams.y = min(max(0, y), maximumY)
    windowManager.updateViewLayout(rootView, layoutParams)
    publishCaptureExclusion()
  }

  private fun publishCaptureExclusion() {
    // Every call site is reached only after addView succeeds or while the
    // window is already open. Do not make the first publication depend on the
    // platform reporting attachment synchronously from addView.
    if (!excludeFromCapture) return
    @Suppress("Deprecation") windowManager.defaultDisplay.getRealMetrics(metrics)
    CaptureExclusionStore.publish(
      context = service.applicationContext,
      layoutParams = layoutParams,
      displayWidth = metrics.widthPixels,
      displayHeight = metrics.heightPixels,
    )
  }
}
