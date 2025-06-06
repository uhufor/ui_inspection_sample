package com.uhufor.inspector

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.core.content.getSystemService
import com.uhufor.inspector.engine.InspectorEngine
import com.uhufor.inspector.engine.SelectionTraverseType
import com.uhufor.inspector.ui.OverlayCanvas
import com.uhufor.inspector.util.ActivityTracker
import com.uhufor.inspector.util.checkPermission

object Inspector {
    private lateinit var applicationContext: Context
    private var windowManager: WindowManager? = null
    private var overlayCanvas: OverlayCanvas? = null
    private var inspectorEngine: InspectorEngine? = null
    private var floatingTrigger: FloatingTrigger? = null

    private var installed = false
    var isInspectionEnabled: Boolean = false
        private set

    val isDfsTraverseEnabled: Boolean
        get() = inspectorEngine?.selectionTraverseType == SelectionTraverseType.DFS

    private val config: Config = Config()
    private val configProvider = object : ConfigProvider {
        override fun getConfig(): Config {
            return config
        }
    }

    @MainThread
    fun install(context: Context) {
        if (installed) return
        applicationContext = context.applicationContext

        if (!checkPermission(applicationContext)) {
            return
        }

        windowManager = applicationContext.getSystemService()
        ActivityTracker.register(applicationContext)
        installed = true
    }

    @MainThread
    fun enableInspection() {
        if (!installed || isInspectionEnabled) return
        val activity = ActivityTracker.top ?: return

        val engine = InspectorEngine(
            topActivityProvider = { ActivityTracker.top },
            invalidator = { overlayCanvas?.invalidate() }
        )
        inspectorEngine = engine

        val canvas = OverlayCanvas(
            context = activity,
        ).apply {
            setConfigProvider(configProvider)
            setEngine(engine)
            backKeyListener = object : OverlayCanvas.BackKeyListener {
                override fun onBackPressed() {
                    disableInspection()
                }
            }
        }
        overlayCanvas = canvas

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        windowManager?.addView(overlayCanvas, params)

        inspectorEngine?.scanAllElements()
        floatingTrigger?.bringToFront()
        isInspectionEnabled = true
        floatingTrigger?.updateInspectorState(true)
    }

    @MainThread
    fun disableInspection() {
        if (!installed || !isInspectionEnabled) return
        overlayCanvas?.let {
            windowManager?.removeView(it)
        }

        overlayCanvas = null
        inspectorEngine?.clearScan()
        inspectorEngine = null
        isInspectionEnabled = false
        floatingTrigger?.updateInspectorState(false)
    }

    @MainThread
    fun toggleInspection() {
        if (isInspectionEnabled) {
            disableInspection()
        } else {
            enableInspection()
        }
    }

    fun setUnitMode(mode: UnitMode) {
        if (config.unitMode == mode) return

        config.unitMode = mode
        overlayCanvas?.invalidate()
        floatingTrigger?.updateInspectorState(isInspectionEnabled)
    }

    fun getUnitMode(): UnitMode {
        return config.unitMode
    }

    fun enableDfsTraverse() {
        inspectorEngine?.selectionTraverseType = SelectionTraverseType.DFS
    }

    fun disableDfsTraverse() {
        inspectorEngine?.selectionTraverseType = SelectionTraverseType.HIERARCHICAL
    }

    @MainThread
    fun showFloatingTrigger() {
        if (!installed) return

        if (floatingTrigger == null) {
            floatingTrigger = FloatingTrigger(
                context = applicationContext,
                configProvider = configProvider,
                inspector = this
            )
        }

        floatingTrigger?.install()
        floatingTrigger?.updateInspectorState(isInspectionEnabled)
    }

    @MainThread
    fun hideFloatingTrigger() {
        floatingTrigger?.uninstall()
        floatingTrigger = null
    }
}
