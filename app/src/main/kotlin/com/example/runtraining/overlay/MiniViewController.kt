package com.example.runtraining.overlay

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.runtraining.MainActivity
import com.example.runtraining.service.NotificationBuilder
import com.example.runtraining.util.Log
import com.example.runtraining.workout.engine.RunUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns a TYPE_APPLICATION_OVERLAY window hosting the Compose mini view.
 * Drag + tap + close handling lives inside Compose (pointerInput / clickable
 * / IconButton) so we don't fight WindowManager's NOT_FOCUSABLE touch routing.
 */
class MiniViewController(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var hostView: ComposeView? = null
    private var owner: MiniViewLifecycleOwner? = null
    private val params: WindowManager.LayoutParams = buildParams()

    val isAttached: Boolean get() = hostView != null

    /** Attach the overlay and start mirroring `stateFlow` into it. Idempotent. */
    fun attach(stateFlow: StateFlow<RunUiState>) {
        if (hostView != null) return
        val newOwner = MiniViewLifecycleOwner().also { it.onCreate() }

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeViewModelStoreOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent {
                val state by stateFlow.collectAsState()
                MiniViewContent(
                    state = state,
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        runCatching { windowManager.updateViewLayout(this, params) }
                    },
                    onTapBody = { bringHostToForeground() },
                    onClose = { detach() },
                )
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (t: Throwable) {
            Log.w("MiniView attach failed", t)
            newOwner.onDestroy()
            return
        }
        hostView = view
        owner = newOwner
    }

    fun detach() {
        hostView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        hostView = null
        owner?.onDestroy()
        owner = null
    }

    fun shutdown() {
        detach()
    }

    /** FR-032: tap → bring our app to the foreground on the run page. */
    private fun bringHostToForeground() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = NotificationBuilder.ACTION_OPEN_RUN_PAGE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).send()
        }.onFailure { Log.w("MiniView foreground PI failed", it) }
        detach()
    }

    private fun buildParams(): WindowManager.LayoutParams {
        // NOT FLAG_NOT_FOCUSABLE — that flag on Compose-hosted overlays
        // confuses gesture routing on some Android versions. Use
        // FLAG_NOT_TOUCH_MODAL instead so touches outside the overlay
        // still reach the underlying app, but our overlay gets its own.
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 220
        }
    }
}
