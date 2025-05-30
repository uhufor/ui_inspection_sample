package com.uhufor.inspector.engine

import android.app.Activity
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.uhufor.inspector.util.ActivityTracker

data class SelectionState(
    val bounds: RectF,
    val isClickable: Boolean = false,
    val parentBounds: RectF? = null,
    val id: Int = 0,
)

internal class InspectorEngine(
    private val topActivityProvider: () -> Activity? = { ActivityTracker.top },
    private val invalidator: () -> Unit,
) {
    var selection: SelectionState? = null
        private set

    var primarySelection: SelectionState? = null
        private set

    var secondarySelection: SelectionState? = null
        private set

    var measurementMode: MeasurementMode = MeasurementMode.Normal
        private set

    var allElements: List<SelectionState> = emptyList()
        private set

    fun handleTap(x: Float, y: Float) {
        val activity = topActivityProvider() ?: return
        val rootView = activity.window.decorView

        if (measurementMode == MeasurementMode.Relative) {
            findElementAt(rootView, x.toInt(), y.toInt())?.let { selectionState ->
                secondarySelection = selectionState
                invalidate()
            }
            return
        }

        findElementAt(rootView, x.toInt(), y.toInt())?.let { selectionState ->
            selection = selectionState
            invalidate()
        }
    }

    fun handleLongPress(x: Float, y: Float) {
        val activity = topActivityProvider() ?: return
        val rootView = activity.window.decorView

        findElementAt(rootView, x.toInt(), y.toInt())?.let { selectionState ->
            if (measurementMode == MeasurementMode.Relative &&
                primarySelection?.id == selectionState.id
            ) {
                measurementMode = MeasurementMode.Normal
                primarySelection = null
                secondarySelection = null
                selection = selectionState
            } else {
                measurementMode = MeasurementMode.Relative
                primarySelection = selectionState
                secondarySelection = null
                selection = null
            }
            invalidate()
        }
    }

    private fun findElementAt(rootView: View, x: Int, y: Int): SelectionState? {
        ComposeHitTester.hitTest(rootView, x, y)?.let { (rect, parentRect, isClickable) ->
            return SelectionState(
                bounds = rect,
                isClickable = isClickable,
                parentBounds = parentRect,
                id = rect.hashCode()
            )
        }

        ViewHitTester.findLeaf(rootView, x, y)?.let { (view, parentView) ->
            val rect = Rect()
            view.getGlobalVisibleRect(rect)

            val parentRect = if (parentView != null) {
                val parentBounds = Rect()
                parentView.getGlobalVisibleRect(parentBounds)
                RectF(parentBounds)
            } else null

            return SelectionState(
                bounds = RectF(rect),
                isClickable = view.isClickable || view.isLongClickable,
                parentBounds = parentRect,
                id = view.hashCode()
            )
        }

        return null
    }

    fun scanAllElements() {
        val activity = topActivityProvider() ?: return
        val rootView = activity.window.decorView

        val elements = mutableListOf<SelectionState>()

        elements.addAll(ComposeHitTester.scanAllElements(rootView))
        elements.addAll(ViewHitTester.scanAllElements(rootView))

        allElements = elements
        invalidate()
    }

    fun clearScan() {
        allElements = emptyList()
        selection = null
        primarySelection = null
        secondarySelection = null
        measurementMode = MeasurementMode.Normal
        invalidate()
    }

    fun getRelativeDistances(): List<Distance> {
        val primary = primarySelection?.bounds ?: return emptyList()
        val secondary = secondarySelection?.bounds ?: return emptyList()

        return RelativeMeasurement.calculateDistances(primary, secondary)
    }

    private fun invalidate() {
        invalidator()
    }
}
