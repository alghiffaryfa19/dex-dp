package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.fauzan.nativedex.shizuku.ShizukuSessionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DexViewerActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private lateinit var cursorView: ImageView

    private var surfaceReady = false
    private var activeVirtualWidth = 1920
    private var activeVirtualHeight = 1080

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
                ShizukuSessionManager.injectKeyEvent(down)
                ShizukuSessionManager.injectKeyEvent(up)
                ShizukuSessionManager.stopSession()
                finish()
            }
        })

        if (!ShizukuSessionManager.hasShizukuPermission()) {
            finish()
            return
        }

        val dm = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val extDisplay = dm.displays.firstOrNull { it.displayId != android.view.Display.DEFAULT_DISPLAY }
        val targetDisplay = extDisplay ?: dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = targetDisplay.mode
        
        // Ensure landscape orientation for DeX
        var width = mode.physicalWidth
        var height = mode.physicalHeight
        if (width < height) {
            val temp = width
            width = height
            height = temp
        }
        activeVirtualWidth = width
        activeVirtualHeight = height

        root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            holder.setFixedSize(activeVirtualWidth, activeVirtualHeight)
        }
        root.addView(surfaceView)

        statusText = TextView(this).apply {
            text = "Starting Direct Surface (Shizuku)…"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        root.addView(statusText)

        cursorView = ImageView(this).apply {
            setImageResource(R.drawable.ic_cursor) // requires ic_cursor drawable
            layoutParams = FrameLayout.LayoutParams(24, 24).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            visibility = View.GONE
            elevation = 10f
        }
        root.addView(cursorView)

        setContentView(root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = true
                startShizukuDisplay(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                ShizukuSessionManager.stopSession()
            }
        })

        surfaceView.setOnTouchListener { view, event ->
            forwardShizukuMotionEvent(event, view.width, view.height)
            updateCursor(event)
            true
        }

        surfaceView.setOnGenericMotionListener { view, event ->
            forwardShizukuMotionEvent(event, view.width, view.height)
            updateCursor(event)
            true
        }

        observeShizukuState()
    }

    private fun startShizukuDisplay(surface: android.view.Surface) {
        val width = activeVirtualWidth
        val height = activeVirtualHeight
        val dpi = 240 // Default proper DPI for DeX

        applyAspectRatio(width, height)

        lifecycleScope.launch {
            statusText.text = "Starting Direct Surface (Shizuku)…\nResolution: ${width}x${height}"
            ShizukuSessionManager.startSession(this@DexViewerActivity, surface, width, height, dpi)
        }
    }

    private fun observeShizukuState() {
        lifecycleScope.launch {
            ShizukuSessionManager.state.collectLatest { state ->
                when (state) {
                    is ShizukuSessionManager.State.Idle -> {}
                    is ShizukuSessionManager.State.Connecting -> {
                        statusText.visibility = View.VISIBLE
                        statusText.text = "Initializing Hardware Surface…"
                    }
                    is ShizukuSessionManager.State.Running -> {
                        statusText.visibility = View.GONE
                        applyAspectRatio(state.width, state.height)
                    }
                    is ShizukuSessionManager.State.Error -> {
                        statusText.visibility = View.VISIBLE
                        statusText.text = "Error: ${state.message}"
                    }
                }
            }
        }
    }

    private fun forwardShizukuMotionEvent(event: MotionEvent, viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return

        val scaleX = activeVirtualWidth.toFloat() / viewWidth
        val scaleY = activeVirtualHeight.toFloat() / viewHeight

        val transformedEvent = MotionEvent.obtain(event)
        transformedEvent.setLocation(event.x * scaleX, event.y * scaleY)
        ShizukuSessionManager.injectMotionEvent(transformedEvent)
        transformedEvent.recycle()
    }

    private fun applyAspectRatio(videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return
        root.post {
            val containerWidth = root.width
            val containerHeight = root.height
            if (containerWidth == 0 || containerHeight == 0) return@post

            val scale = minOf(
                containerWidth.toFloat() / videoWidth,
                containerHeight.toFloat() / videoHeight
            )
            val params = surfaceView.layoutParams as FrameLayout.LayoutParams
            params.width = (videoWidth * scale).toInt()
            params.height = (videoHeight * scale).toInt()
            params.gravity = Gravity.CENTER
            surfaceView.layoutParams = params
        }
    }

    private fun updateCursor(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return
            }
        }

        val loc = IntArray(2)
        surfaceView.getLocationInWindow(loc)
        cursorView.translationX = loc[0] + event.x
        cursorView.translationY = loc[1] + event.y

        if (cursorView.visibility != View.VISIBLE) {
            cursorView.visibility = View.VISIBLE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            ShizukuSessionManager.injectKeyEvent(event)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
