package com.usos.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Host con vistas propias, para poder detectar la pulsacion larga. */
class UsosWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        UsosHostView(context)
}

/**
 * AppWidgetHostView que detecta la pulsacion larga sin romper los toques normales: el DOWN y los
 * movimientos pasan a la vista del widget; solo si el dedo sigue quieto (tolerancia 2x slop) 600 ms
 * se cancela el toque hacia el hijo y el resto del gesto se consume aqui.
 */
class UsosHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    init {
        setPadding(0, 0, 0, 0) // sin el padding por defecto del host: el widget usa todo el hueco
        clipToPadding = false
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) = super.setPadding(0, 0, 0, 0)

    private var consumed = false
    private var grayscaleOn: Boolean? = null
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2

    private val fire = Runnable {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        dispatchTouchEvent(cancel) // el hijo recibe CANCEL y deja de mostrarse pulsado
        cancel.recycle()
        consumed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumed = false
                downX = ev.x
                downY = ev.y
                postDelayed(fire, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE ->
                if (!consumed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(fire)
        }
        return consumed
    }

    /** Si el widget pide el gesto (scroll interno), no hay pulsacion larga. */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !consumed) removeCallbacks(fire)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!consumed) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) consumed = false
        return true
    }

    companion object {
        const val LONG_PRESS_MS = 600L
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fire)
        super.onDetachedFromWindow()
    }

    /** Escala de grises como capa de hardware: ColorMatrix con saturacion 0. */
    fun setGrayscale(on: Boolean) {
        if (grayscaleOn == on) return
        grayscaleOn = on
        if (on) {
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
            setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}
