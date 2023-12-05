package com.dailysdkdemo.data.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.dailysdkdemo.R

class ConstrainedDragAndDropView : LinearLayout {
    protected var dragHandle: View? = null
    protected var dropTargets: MutableList<View>? = ArrayList()
    protected var dragging = false
    protected var pointerId = 0
    protected var selectedDropTargetIndex = -1
    protected var lastSelectedDropTargetIndex = -1
    protected var lastDroppedIndex = -1
    var isAllowHorizontalDrag = true
    var isAllowVerticalDrag = true
    var dropListener: DropListener? = null

    interface DropListener {
        fun onDrop(dropIndex: Int, dropTarget: View?)
    }

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        applyAttrs(context, attrs)
    }

    @SuppressLint("NewApi")
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    ) {
        applyAttrs(context, attrs)
    }

    @JvmName("getDragHandle1")
    fun getDragHandle(): View? {
        return dragHandle
    }

    @JvmName("setDragHandle1")
    fun setDragHandle(dragHandle: View?) {
        this.dragHandle = dragHandle
        setupDragHandle()
    }

    @JvmName("getDropTargets1")
    fun getDropTargets(): List<View>? {
        return dropTargets
    }

    @JvmName("setDropTargets1")
    fun setDropTargets(dropTargets: MutableList<View>?) {
        this.dropTargets = dropTargets
    }

    fun addDropTarget(target: View) {
        if (dropTargets == null) {
            dropTargets = ArrayList()
        }
        dropTargets!!.add(target)
    }

    protected fun applyAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(
            attrs, R.styleable.ConstrainedDragAndDropView, 0, 0
        )
        try {
            /*
            layoutId = a.getResourceId(R.styleable.ConstrainedDragAndDropView_layoutId, 0);

            if (layoutId > 0) {
                LayoutInflater.from(context).inflate(layoutId, this, true);
            }
            */
        } finally {
            a.recycle()
        }
    }

    protected fun setupDragHandle() {
        setOnTouchListener(DragAreaTouchListener())
    }

    protected inner class DragAreaTouchListener : OnTouchListener {
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            Log.d(TAG, "motionEvent $motionEvent")
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> onActionDown(view, motionEvent)
                MotionEvent.ACTION_UP -> onActionUp(view, motionEvent)
                MotionEvent.ACTION_MOVE -> onActionMove(view, motionEvent)
                else -> {}
            }
            return true
        }
    }

    private var interceptTouchEvents = false

    fun setInterceptTouchEvents(interceptTouchEvents: Boolean) {
        this.interceptTouchEvents = interceptTouchEvents
    }

    fun updateDragPositionToBottomEnd() {
        val dropTargetIndex = findDropTargetIndexUnderDragHandle()
        if (dropTargetIndex != 3) {
            selectDropTarget(3)
            snapDragHandleToDropTarget(3)
        }
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent?): Boolean {
        if (interceptTouchEvents) {
            when (motionEvent?.action) {
                MotionEvent.ACTION_DOWN -> onActionDown(this, motionEvent)
                MotionEvent.ACTION_UP -> onActionUp(this, motionEvent)
                MotionEvent.ACTION_MOVE -> onActionMove(this, motionEvent)
                else -> {}
            }
        }
        return super.onInterceptTouchEvent(motionEvent)
    }

    fun onActionDown(view: View?, motionEvent: MotionEvent) {
        // if we're not already dragging, and the touch position is on the drag handle,
        // then start dragging
        if (!dragging && isDragHandleTouch(motionEvent)) {
            pointerId = motionEvent.getPointerId(0)
            updateDragPosition(motionEvent)
            dragging = true
            Log.d("drag", "drag start")
        }
    }

    fun onActionUp(view: View?, motionEvent: MotionEvent) {

        // if we're dragging, then stop dragging
        if (dragging && motionEvent.getPointerId(0) == pointerId) {
            updateDragPosition(motionEvent)
            dragging = false
            Log.d("drag", "drag end")

            // find out what drop target, if any, the drag handle was dropped on
            val dropTargetIndex = findDropTargetIndexUnderDragHandle()
            if (dropTargetIndex >= 0) { // if drop was on a target, select the target
                Log.d("drag", "drop on target $dropTargetIndex")
                selectDropTarget(dropTargetIndex)
                snapDragHandleToDropTarget(dropTargetIndex)
                lastDroppedIndex = dropTargetIndex
                if (dropListener != null) {
                    dropListener!!.onDrop(dropTargetIndex, dropTargets!![dropTargetIndex])
                }
            } else { // if drop was not on a target, re-select the last selected target
                deselectDropTarget()
                snapDragHandleToDropTarget(lastDroppedIndex)
            }
        }
    }

    fun onActionMove(view: View?, motionEvent: MotionEvent) {
        if (dragging && motionEvent.getPointerId(0) == pointerId) {
            updateDragPosition(motionEvent)
            val dropTargetIndex = findDropTargetIndexUnderDragHandle()
            if (dropTargetIndex >= 0) {
                Log.d("drag", "hover on target $dropTargetIndex")
                selectDropTarget(dropTargetIndex)
            } else {
                deselectDropTarget()
            }
        }
    }

    @SuppressLint("NewApi")
    protected fun updateDragPosition(motionEvent: MotionEvent) {

        // this is where we constrain the movement of the dragHandle
        if (isAllowHorizontalDrag) {
            val candidateX = motionEvent.x - dragHandle!!.width / 2
            if (candidateX > 0 && candidateX + dragHandle!!.width < this.width) {
                dragHandle!!.x = candidateX
            }
        }
        if (isAllowVerticalDrag) {
            val candidateY = motionEvent.y - dragHandle!!.height / 2
            if (candidateY > 0 && candidateY + dragHandle!!.height < this.height) {
                dragHandle!!.y = candidateY
            }
        }
    }

    @SuppressLint("NewApi")
    protected fun snapDragHandleToDropTarget(dropTargetIndex: Int) {
        if (dropTargetIndex > -1) {
            val dropTarget = dropTargets!![dropTargetIndex]
            val xCenter = dropTarget.x + dropTarget.width / 2
            val yCenter = dropTarget.y + dropTarget.height / 2
            val xOffset = (dragHandle!!.width / 2).toFloat()
            val yOffset = (dragHandle!!.height / 2).toFloat()
            val x = xCenter - xOffset
            val y = yCenter - yOffset
            dragHandle!!.x = x
            dragHandle!!.y = y
        }
    }

    protected fun isDragHandleTouch(motionEvent: MotionEvent): Boolean {
        val point = Point(
            motionEvent.rawX.toInt(), motionEvent.rawY.toInt()
        )
        return isPointInView(point, dragHandle)
    }

    protected fun findDropTargetIndexUnderDragHandle(): Int {
        var dropTargetIndex = 3
        for (i in dropTargets!!.indices) {
            if (isCollision(dragHandle, dropTargets!![i])) {
                dropTargetIndex = i
                break
            }
        }
        return dropTargetIndex
    }

    /**
     * Determines whether a raw screen coordinate is within the bounds of the specified view
     * @param point - Point containing screen coordinates
     * @param view - View to test
     * @return true if the point is in the view, else false
     */
    protected fun isPointInView(point: Point, view: View?): Boolean {
        val viewPosition = IntArray(2)
        view!!.getLocationOnScreen(viewPosition)
        val left = viewPosition[0]
        val right = left + view.width
        val top = viewPosition[1]
        val bottom = top + view.height
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
    }

    @SuppressLint("NewApi")
    protected fun isCollision(view1: View?, view2: View): Boolean {
        var collision = false
        do {
            if (view1!!.y + view1.height < view2.y) {
                break
            }
            if (view1.y > view2.y + view2.height) {
                break
            }
            if (view1.x > view2.x + view2.width) {
                break
            }
            if (view1.x + view1.width < view2.x) {
                break
            }
            collision = true
        } while (false)
        return collision
    }

    protected fun selectDropTarget(index: Int) {
        if (index > -1) {
            deselectDropTarget()
            selectedDropTargetIndex = index
            dropTargets!![selectedDropTargetIndex].isSelected = true
        }
    }

    protected fun deselectDropTarget() {
        if (selectedDropTargetIndex > -1) {
            dropTargets!![selectedDropTargetIndex].isSelected = false
            lastSelectedDropTargetIndex = selectedDropTargetIndex
            selectedDropTargetIndex = -1
        }
    }
}