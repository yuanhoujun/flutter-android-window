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
  private val engine: FlutterEngine
) {
  private var startX = 0f
  private var startY = 0f
  private var initialX = 0
  private var initialY = 0
  private var dragging = false
  private var flutterView: FlutterView? = null
  private var isOpen = false
  private var windowManager = service.getSystemService(Service.WINDOW_SERVICE) as WindowManager
  private val inflater = service.getSystemService(Service.LAYOUT_INFLATER_SERVICE) as LayoutInflater
  private val metrics = DisplayMetrics()

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
    }, if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
  )

  fun open() {
    if (isOpen) {
      close()
    }

    engine.platformViewsController.attach(inflater.context, engine.renderer, engine.dartExecutor)
    val floatingApi = AndroidWindowApi(this)
    Pigeon.AndroidWindowApi.setUp(engine.dartExecutor.binaryMessenger, floatingApi)
    layoutParams.gravity = Gravity.START or Gravity.TOP
    layoutParams.x = x
    layoutParams.y = y
    rootView.removeOnAttachStateChangeListener(attachStateChangeListener)
    rootView.addOnAttachStateChangeListener(attachStateChangeListener)
    windowManager.addView(rootView, layoutParams)
    @Suppress("Deprecation") windowManager.defaultDisplay.getMetrics(metrics)
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
    isOpen = false
    WindowService.updateWindowRunning(false)
  }

  fun isRunning(): Boolean {
    return isOpen && rootView.isAttachedToWindow
  }

  fun updateLayout() {
    if (!isOpen) {
      return
    }

    @Suppress("Deprecation") windowManager.defaultDisplay.getMetrics(metrics)
    setPosition(layoutParams.x, layoutParams.y)
  }

  fun setLayout(width: Int, height: Int) {
    if (!isOpen) {
      return
    }

    layoutParams.width = width
    layoutParams.height = height
    windowManager.updateViewLayout(rootView, layoutParams)
  }

  fun setPosition(x: Int, y: Int) {
    if (!isOpen) {
      return
    }

    layoutParams.x = min(max(0, x), metrics.widthPixels - layoutParams.width)
    layoutParams.y = min(max(0, y), metrics.heightPixels - layoutParams.height)
    windowManager.updateViewLayout(rootView, layoutParams)
  }
}
