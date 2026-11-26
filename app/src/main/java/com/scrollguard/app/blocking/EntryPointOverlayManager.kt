package com.scrollguard.app.blocking

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places lightweight, non-intrusive TYPE_APPLICATION_OVERLAY interaction shields directly
 * over validated bottom-navigation entry points (e.g. Reels/Shorts/Spotlight tab bounds)
 * so ordinary touches on those buttons do not launch restricted feeds.
 *
 * Uses raw screen coordinates matching AccessibilityNodeInfo.getBoundsInScreen via
 * FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS and SHORT_EDGES cutout mode.
 * Never covers unrelated UI, never covers system bars, and immediately tears down on
 * app switches, keyboard appearance, full-screen blocks, or permission revocation.
 */
@Singleton
class EntryPointOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private val activeShields = mutableListOf<Pair<String, View>>()
    private var lastSignature: String? = null

    val activeCount: Int
        get() = activeShields.count { it.second.isAttachedToWindow }

    fun showOrUpdate(targets: List<EntryPointTarget>) {
        if (targets.isEmpty() || !Settings.canDrawOverlays(context) || windowManager == null) {
            dismissAll()
            return
        }

        val capped = targets.take(2)
        val signature = capped.joinToString("|") {
            "${it.category.name}:${it.left},${it.top},${it.right},${it.bottom}"
        }

        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) {
                dismissImmediate()
                return@post
            }

            if (signature == lastSignature &&
                activeShields.isNotEmpty() &&
                activeShields.all { it.second.isAttachedToWindow }
            ) {
                return@post
            }

            dismissImmediate()
            lastSignature = signature

            for (target in capped) {
                val view = createShieldView(target)
                val params = WindowManager.LayoutParams(
                    target.width,
                    target.height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = target.left
                    y = target.top
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                runCatching {
                    windowManager?.addView(view, params) ?: error("WindowManager unavailable")
                    activeShields.add(signature to view)
                }.onFailure {
                    dismissImmediate()
                    return@post
                }
            }
        }
    }

    fun dismissAll() {
        mainHandler.post {
            dismissImmediate()
        }
    }

    private fun dismissImmediate() {
        lastSignature = null
        val iterator = activeShields.iterator()
        while (iterator.hasNext()) {
            val (_, view) = iterator.next()
            runCatching { windowManager?.removeViewImmediate(view) }
            iterator.remove()
        }
    }

    private fun createShieldView(target: EntryPointTarget): View {
        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = false
            contentDescription = "${target.label} hidden by ScrollGuard"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    12f,
                    context.resources.displayMetrics
                )
                setColor(Color.argb(248, 15, 23, 42))
                setStroke(2, Color.argb(210, 76, 123, 243))
            }
        }

        val label = TextView(context).apply {
            text = "Hidden"
            setTextColor(Color.argb(235, 226, 232, 240))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        }
        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return container
    }
}
