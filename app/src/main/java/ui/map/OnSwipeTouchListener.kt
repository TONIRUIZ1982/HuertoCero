package com.huertocero.huertocero.ui.map

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

open class OnSwipeTouchListener(ctx: Context) : View.OnTouchListener {

    private val gestureDetector = GestureDetector(ctx, GestureListener())

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        return event != null && gestureDetector.onTouchEvent(event)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {

            val diffX = e2.x - (e1?.x ?: 0f)

            if (kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
            ) {
                if (diffX > 0) onSwipeRight()
                else onSwipeLeft()
                return true
            }

            return false
        }
    }

    open fun onSwipeLeft() {}
    open fun onSwipeRight() {}
}
