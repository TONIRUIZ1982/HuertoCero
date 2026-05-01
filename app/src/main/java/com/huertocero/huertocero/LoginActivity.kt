package com.huertocero.huertocero

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : HuertoActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onStart() {
        super.onStart()

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnPrivacy = findViewById<Button>(R.id.btnPrivacy)
        val btnLanguage = findViewById<Button>(R.id.btnLanguage)
        val logoMark = findViewById<View>(R.id.logoMark)
        val heroIllustration = findViewById<View>(R.id.heroIllustration)
        val authCard = findViewById<View>(R.id.authCard)

        playIntroAnimation(logoMark, heroIllustration, authCard)

        btnPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        btnLanguage.setOnClickListener {
            LanguageDialog.show(this)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setButtonsLoading(btnLogin, btnRegister, true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    setButtonsLoading(btnLogin, btnRegister, false)
                    if (task.isSuccessful) {
                        startActivity(Intent(this, MapActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.auth_error, task.exception?.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, getString(R.string.password_min), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setButtonsLoading(btnLogin, btnRegister, true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    setButtonsLoading(btnLogin, btnRegister, false)
                    if (task.isSuccessful) {
                        Toast.makeText(this, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MapActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.auth_error, task.exception?.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun playIntroAnimation(vararg views: View) {
        views.forEachIndexed { index, view ->
            val startsAsLogo = view.id == R.id.logoMark

            view.alpha = 0f
            view.translationY = 36f
            view.scaleX = if (startsAsLogo) 0.86f else 1f
            view.scaleY = if (startsAsLogo) 0.86f else 1f

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 36f, 0f),
                    ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 1f),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 1f)
                )
                startDelay = 90L * index
                duration = 560L
                interpolator = if (startsAsLogo) {
                    OvershootInterpolator(1.6f)
                } else {
                    AccelerateDecelerateInterpolator()
                }
                start()
            }
        }
    }

    private fun setButtonsLoading(loginButton: Button, registerButton: Button, isLoading: Boolean) {
        loginButton.isEnabled = !isLoading
        registerButton.isEnabled = !isLoading
        loginButton.text = if (isLoading) getString(R.string.connecting) else getString(R.string.login_button)
        registerButton.text = if (isLoading) getString(R.string.please_wait) else getString(R.string.create_account)
    }
}
