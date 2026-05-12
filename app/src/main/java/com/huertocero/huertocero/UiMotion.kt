package com.huertocero.huertocero

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object UiMotion {
    fun makePressable(vararg views: View) {
        views.forEach { view ->
            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        target.animate()
                            .scaleX(0.955f)
                            .scaleY(0.955f)
                            .translationY(2f)
                            .alpha(0.92f)
                            .setDuration(85L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(170L)
                            .setInterpolator(OvershootInterpolator(1.7f))
                            .start()
                    }
                }
                false
            }
        }
    }

    fun reveal(vararg views: View) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 34f
            view.scaleX = 0.98f
            view.scaleY = 0.98f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 34f, 0f),
                    ObjectAnimator.ofFloat(view, View.SCALE_X, 0.98f, 1f),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.98f, 1f)
                )
                startDelay = index * 62L
                duration = 420L
                interpolator = DecelerateInterpolator(1.8f)
                start()
            }
        }
    }

    fun showSurface(view: View?, fromY: Float = 22f, delay: Long = 0L) {
        view ?: return
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = fromY
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    fun hideSurface(view: View?, toY: Float = -18f, endAction: (() -> Unit)? = null) {
        view ?: return
        view.animate()
            .alpha(0f)
            .translationY(toY)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(190L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                endAction?.invoke()
            }
            .start()
    }

    fun celebrate(view: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.05f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.05f, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.86f, 1f)
            )
            duration = 420L
            interpolator = OvershootInterpolator(1.8f)
            start()
        }
    }
}
