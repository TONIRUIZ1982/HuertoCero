package com.huertocero.huertocero

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class OnboardingActivity : HuertoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.auth_background)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(30))
        }
        scroll.addView(root)

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_huerto_cero_mark)
            contentDescription = getString(R.string.app_name)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(138), dp(138)))

        root.addView(TextView(this).apply {
            text = getString(R.string.onboarding_title)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.ink))
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.onboarding_subtitle)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.muted_ink))
            textSize = 15f
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(16)
        })

        root.addView(featureCard(
            getString(R.string.onboarding_near_title),
            getString(R.string.onboarding_near_body),
            R.drawable.ic_quick_map
        ))
        root.addView(featureCard(
            getString(R.string.onboarding_reserve_title),
            getString(R.string.onboarding_reserve_body),
            R.drawable.ic_reserve
        ))
        root.addView(featureCard(
            getString(R.string.onboarding_alerts_title),
            getString(R.string.onboarding_alerts_body),
            R.drawable.ic_quick_impact
        ))

        val startButton = Button(this).apply {
            text = getString(R.string.onboarding_start)
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.button_primary)
            setOnClickListener { finishOnboarding() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(22)
        })

        UiMotion.makePressable(startButton)
        UiMotion.reveal(root)
        setContentView(scroll)
    }

    private fun featureCard(title: String, body: String, iconRes: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.auth_card_background)
            elevation = dp(8).toFloat()
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        card.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(this@OnboardingActivity, R.color.brand_olive))
            background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.category_chip_background)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, LinearLayout.LayoutParams(dp(58), dp(58)))

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(13)
        })

        copy.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.ink))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })

        copy.addView(TextView(this).apply {
            text = body
            setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.muted_ink))
            textSize = 13f
            setLineSpacing(dp(1).toFloat(), 1f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun finishOnboarding() {
        markSeen(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "huertocero_onboarding"
        private const val KEY_SEEN = "seen"

        fun hasSeen(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)
        }

        fun markSeen(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SEEN, true)
                .apply()
        }
    }
}
