package qiuxiang.android_window

import android.content.Context
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Publishes the physical bounds of an overlay which must never become a
 * MediaProjection recognition target.
 *
 * The screen-recognition service lives in another Flutter plugin, so the
 * contract intentionally uses application SharedPreferences rather than a
 * direct module dependency. Both services run in the application process;
 * apply() updates the in-process preference cache synchronously while keeping
 * drag handling off disk I/O.
 */
internal object CaptureExclusionStore {
  const val PREFERENCES_NAME = "android_window_capture_exclusion"
  const val KEY_ACTIVE = "active"
  const val KEY_LEFT = "left"
  const val KEY_TOP = "top"
  const val KEY_RIGHT = "right"
  const val KEY_BOTTOM = "bottom"
  const val KEY_DISPLAY_WIDTH = "display_width"
  const val KEY_DISPLAY_HEIGHT = "display_height"
  const val KEY_GENERATION = "generation"

  private val generation = AtomicLong(0L)

  fun publish(
    context: Context,
    layoutParams: WindowManager.LayoutParams,
    displayWidth: Int,
    displayHeight: Int,
  ) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val right = layoutParams.x + layoutParams.width
    val bottom = layoutParams.y + layoutParams.height
    val unchanged = preferences.getBoolean(KEY_ACTIVE, false) &&
      preferences.getInt(KEY_LEFT, Int.MIN_VALUE) == layoutParams.x &&
      preferences.getInt(KEY_TOP, Int.MIN_VALUE) == layoutParams.y &&
      preferences.getInt(KEY_RIGHT, Int.MIN_VALUE) == right &&
      preferences.getInt(KEY_BOTTOM, Int.MIN_VALUE) == bottom &&
      preferences.getInt(KEY_DISPLAY_WIDTH, 0) == displayWidth &&
      preferences.getInt(KEY_DISPLAY_HEIGHT, 0) == displayHeight
    if (unchanged) return
    val storedGeneration = preferences.getLong(KEY_GENERATION, 0L)
    val nextGeneration = generation.updateAndGet { current ->
      maxOf(current, storedGeneration) + 1L
    }
    preferences
      .edit()
      .putBoolean(KEY_ACTIVE, true)
      .putInt(KEY_LEFT, layoutParams.x)
      .putInt(KEY_TOP, layoutParams.y)
      .putInt(KEY_RIGHT, right)
      .putInt(KEY_BOTTOM, bottom)
      .putInt(KEY_DISPLAY_WIDTH, displayWidth)
      .putInt(KEY_DISPLAY_HEIGHT, displayHeight)
      .putLong(KEY_GENERATION, nextGeneration)
      .apply()
  }

  fun clear(context: Context) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    if (!preferences.getBoolean(KEY_ACTIVE, false)) return
    val storedGeneration = preferences.getLong(KEY_GENERATION, 0L)
    val nextGeneration = generation.updateAndGet { current ->
      maxOf(current, storedGeneration) + 1L
    }
    preferences
      .edit()
      .putBoolean(KEY_ACTIVE, false)
      .putLong(KEY_GENERATION, nextGeneration)
      .apply()
  }
}
